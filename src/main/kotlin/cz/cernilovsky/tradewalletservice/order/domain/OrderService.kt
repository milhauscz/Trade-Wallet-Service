package cz.cernilovsky.tradewalletservice.order.domain

import cz.cernilovsky.tradewalletservice.common.exception.BadRequestException
import cz.cernilovsky.tradewalletservice.common.exception.NotImplementedYetException
import cz.cernilovsky.tradewalletservice.common.exception.ResourceNotFoundException
import cz.cernilovsky.tradewalletservice.config.KafkaTopicsProperties
import cz.cernilovsky.tradewalletservice.order.api.CreateOrderRequest
import cz.cernilovsky.tradewalletservice.order.api.OrderResponse
import cz.cernilovsky.tradewalletservice.order.api.UpdateOrderRequest
import cz.cernilovsky.tradewalletservice.order.persistence.OrderEntity
import cz.cernilovsky.tradewalletservice.order.persistence.OrderRepository
import cz.cernilovsky.tradewalletservice.order.persistence.OrderStatus
import cz.cernilovsky.tradewalletservice.outbox.domain.OutboxService
import cz.cernilovsky.tradewalletservice.wallet.domain.WalletService
import jakarta.persistence.EntityManager
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Suppress("unused") // walletService, outboxService, kafkaTopicsProperties are for Phase 1/3
class OrderService(
    private val orderRepository: OrderRepository,
    private val walletService: WalletService,
    private val outboxService: OutboxService,
    private val kafkaTopicsProperties: KafkaTopicsProperties,
    private val entityManager: EntityManager
) {
    @Transactional(readOnly = true)
    fun get(userId: String, orderId: UUID): OrderResponse =
        orderRepository.findByIdAndUserId(orderId, userId)?.toResponse()
            ?: throw ResourceNotFoundException("Order", orderId)

    /**
     * TODO(learning) Phase 1 + 3 + 4 — Create order atomically.
     *
     * This is the orchestration method. Put **one** `@Transactional` on it (REQUIRED).
     * Everything below must join that transaction. Do **not** call `KafkaTemplate.send` here.
     *
     * Steps:
     * 1. Phase 4 (optional first check): if `findByUserIdAndIdempotencyKey` returns a row,
     *    return that order as the original response (DB unique is the source of truth).
     * 2. Phase 1: `walletService.reserve(userId, request.price, request.quantity)`.
     * 3. Insert `OrderEntity` with `status = PENDING` and the given `idempotencyKey`.
     * 4. Phase 3: `outboxService.enqueue(...)` with JSON `OrderCreatedEvent` (same TX).
     * 5. Return `OrderResponse`. If the unique constraint fires, catch
     *    `DataIntegrityViolationException` and return the existing order instead of 500.
     *
     * Transaction boundary: wallet row lock + order insert + outbox insert commit together.
     * If any step fails, all three roll back.
     */
    @Transactional
    fun create(userId: String, idempotencyKey: String, request: CreateOrderRequest): OrderResponse {
        val existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
        if (existing != null) return existing.toResponse()

        walletService.reserve(userId, request.price, request.quantity)
        val order = OrderEntity(
            userId = userId,
            idempotencyKey = idempotencyKey,
            quantity = request.quantity,
            price = request.price,
            symbol = request.symbol,
            stopLoss = request.stopLoss,
            status = OrderStatus.PENDING
        )
        orderRepository.saveAndFlush(order)
        return order.toResponse()
    }

    /**
     * TODO(learning) Phase 2 — Update order with optimistic locking.
     *
     * 1. Load the order with `findByIdAndUserId`. 404 if missing.
     * 2. Only allow updates while `status == PENDING`.
     * 3. Copy `request.version` onto `entity.version` **before** applying field changes.
     *    Hibernate uses the loaded entity version in the UPDATE WHERE clause. If the client
     *    sent a stale version, you want that stale value on the entity so the UPDATE fails.
     *    Alternative: compare `request.version != entity.version` and throw 409 yourself —
     *    that is also valid, but using `@Version` teaches the JPA path.
     * 4. Apply `price` / `stopLoss` if present, `saveAndFlush` (flush so the exception
     *    happens inside this method's transaction, not after the controller returns).
     * 5. Let `ObjectOptimisticLockingFailureException` propagate to `GlobalExceptionHandler`.
     *
     * Do not change reserved wallet funds when only price changes in this exercise
     * (keeps the locking lesson focused). Mention the product gap in a code comment.
     */
    @Transactional
    fun update(userId: String, orderId: UUID, request: UpdateOrderRequest): OrderResponse {
        val order = orderRepository.findByIdAndUserId(
            id = orderId,
            userId = userId
        ) ?: throw ResourceNotFoundException("Order", orderId)

        if (order.status != OrderStatus.PENDING) throw BadRequestException("Order's status should be ${OrderStatus.PENDING}, actual status is ${order.status}.")

        entityManager.detach(order)
        order.version = request.version
        request.price?.let { order.price = it }
        request.stopLoss?.let { order.stopLoss = it }
        // we intentionally do not change wallet funds - lesson focused on locking
        return orderRepository.saveAndFlush(order).toResponse()
    }

    private fun OrderEntity.toResponse() = OrderResponse(
        id = id,
        userId = userId,
        symbol = symbol,
        price = price,
        quantity = quantity,
        stopLoss = stopLoss,
        status = status,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
