package cz.cernilovsky.tradewalletservice.messaging

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OrderCreatedEvent(
    val orderId: UUID,
    val userId: String,
    val symbol: String,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val createdAt: Instant,
)
