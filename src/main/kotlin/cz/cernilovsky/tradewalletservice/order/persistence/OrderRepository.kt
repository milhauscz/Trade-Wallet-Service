package cz.cernilovsky.tradewalletservice.order.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderRepository : JpaRepository<OrderEntity, UUID> {
    fun findByIdAndUserId(id: UUID, userId: String): OrderEntity?

    fun findByUserIdAndIdempotencyKey(userId: String, idempotencyKey: String): OrderEntity?
}
