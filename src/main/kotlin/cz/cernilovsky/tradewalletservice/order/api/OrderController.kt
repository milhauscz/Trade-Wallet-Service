package cz.cernilovsky.tradewalletservice.order.api

import cz.cernilovsky.tradewalletservice.common.security.CurrentUser
import cz.cernilovsky.tradewalletservice.common.web.RequestHeaderConsts
import cz.cernilovsky.tradewalletservice.order.domain.OrderService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
    private val currentUser: CurrentUser,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestHeader(RequestHeaderConsts.X_IDEMPOTENCY_KEY) idempotencyKey: String,
        @Valid @RequestBody request: CreateOrderRequest,
    ): OrderResponse = orderService.create(currentUser.userId(), idempotencyKey, request)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): OrderResponse = orderService.get(currentUser.userId(), id)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateOrderRequest,
    ): OrderResponse = orderService.update(currentUser.userId(), id, request)
}
