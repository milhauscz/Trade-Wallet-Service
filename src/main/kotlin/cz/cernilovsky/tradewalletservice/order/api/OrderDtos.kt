package cz.cernilovsky.tradewalletservice.order.api

import cz.cernilovsky.tradewalletservice.order.persistence.OrderStatus
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateOrderRequest(
    @field:NotBlank val symbol: String,
    @field:NotNull @field:DecimalMin("0.01") val price: BigDecimal,
    @field:NotNull @field:DecimalMin("0.01") val quantity: BigDecimal,
    val stopLoss: BigDecimal? = null,
)

data class UpdateOrderRequest(
    @field:DecimalMin("0.01") val price: BigDecimal? = null,
    val stopLoss: BigDecimal? = null,
    @field:NotNull val version: Long,
)

data class OrderResponse(
    val id: UUID,
    val userId: String,
    val symbol: String,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val stopLoss: BigDecimal?,
    val status: OrderStatus,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)
