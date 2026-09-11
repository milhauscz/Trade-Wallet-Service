package cz.cernilovsky.tradewalletservice.wallet.api

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class WalletResponse(
    val userId: String,
    val balance: BigDecimal,
    val reservedAmount: BigDecimal,
    val available: BigDecimal,
    val currency: String,
)

data class CreditWalletRequest(
    @field:NotNull
    @field:DecimalMin("0.01")
    val amount: BigDecimal,
)
