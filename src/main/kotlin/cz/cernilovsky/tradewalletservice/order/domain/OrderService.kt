package cz.cernilovsky.tradewalletservice.order.domain

import cz.cernilovsky.tradewalletservice.common.exception.BadRequestException
import cz.cernilovsky.tradewalletservice.common.exception.ResourceNotFoundException
import cz.cernilovsky.tradewalletservice.config.KafkaTopicsProperties
import cz.cernilovsky.tradewalletservice.messaging.OrderCreatedEvent
import cz.cernilovsky.tradewalletservice.order.api.CreateOrderRequest
import cz.cernilovsky.tradewalletservice.order.api.OrderResponse
import cz.cernilovsky.tradewalletservice.order.api.UpdateOrderRequest
import cz.cernilovsky.tradewalletservice.order.persistence.OrderEntity
import cz.cernilovsky.tradewalletservice.order.persistence.OrderRepository
import cz.cernilovsky.tradewalletservice.order.persistence.OrderStatus
import cz.cernilovsky.tradewalletservice.outbox.domain.OutboxService
import cz.cernilovsky.tradewalletservice.wallet.domain.WalletService
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.ObjectProvider
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.*

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val walletService: WalletService,
    private val outboxService: OutboxService,
    private val kafkaTopicsProperties: KafkaTopicsProperties,
    private val entityManager: EntityManager,
    private val objectMapper: ObjectMapper,
    private val self: ObjectProvider<OrderService>
) {
    @Transactional(readOnly = true)
    fun get(userId: String, orderId: UUID): OrderResponse =
        orderRepository.findByIdAndUserId(orderId, userId)?.toResponse()
            ?: throw ResourceNotFoundException("Order", orderId)

    // Calls createInTx through the Spring proxy so the transaction is applied.
    // After a unique-key rollback, returns the order that won the race.
    fun create(userId: String, idempotencyKey: String, request: CreateOrderRequest): OrderResponse {
        return try {
            self.getObject().createInTx(userId, idempotencyKey, request)
        } catch (e: DataIntegrityViolationException) {
            orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)?.toResponse() ?: throw e
        }
    }

    // Reserves funds, inserts the order, and enqueues OrderCreatedEvent in one transaction.
    // An existing idempotency key returns that order without reserving again.
    @Transactional
    fun createInTx(userId: String, idempotencyKey: String, request: CreateOrderRequest): OrderResponse {
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
        outboxService.enqueue(
            topic = kafkaTopicsProperties.ordersTopic,
            aggregateId = order.id.toString(),
            payload = objectMapper.writeValueAsString(
                OrderCreatedEvent(
                    orderId = order.id,
                    userId = userId,
                    symbol = order.symbol,
                    price = order.price,
                    quantity = order.quantity,
                    createdAt = order.createdAt
                )
            )
        )
        return order.toResponse()
    }

    // Updates price and stopLoss of a PENDING order.
    // A stale version fails the write before the wallet changes.
    // A price change reserves or releases the notional difference in the same transaction.
    @Transactional
    fun update(userId: String, orderId: UUID, request: UpdateOrderRequest): OrderResponse {
        val order = orderRepository.findByIdAndUserId(
            id = orderId,
            userId = userId
        ) ?: throw ResourceNotFoundException("Order", orderId)

        if (order.status != OrderStatus.PENDING) throw BadRequestException("Order's status should be ${OrderStatus.PENDING}, actual status is ${order.status}.")

        entityManager.detach(order)
        order.version = request.version
        val originalPrice = order.price
        request.price?.let { order.price = it }
        request.stopLoss?.let { order.stopLoss = it }

        val orderResponse = orderRepository.saveAndFlush(order).toResponse()

        request.price?.let { updatePrice ->
            val diff = (updatePrice - originalPrice) * order.quantity
            when (diff.signum()) {
                -1 -> walletService.release(userId,  diff.abs())
                1 -> walletService.reserve(userId, diff, BigDecimal(1))
            }
        }

        return orderResponse

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
