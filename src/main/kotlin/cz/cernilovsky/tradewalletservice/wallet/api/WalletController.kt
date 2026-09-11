package cz.cernilovsky.tradewalletservice.wallet.api

import cz.cernilovsky.tradewalletservice.common.security.CurrentUser
import cz.cernilovsky.tradewalletservice.wallet.domain.WalletService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/wallets")
class WalletController(
    private val walletService: WalletService,
    private val currentUser: CurrentUser,
) {
    @GetMapping("/me")
    fun me(): WalletResponse = walletService.getByUserId(currentUser.userId())

    @PostMapping("/me/credit")
    fun credit(@Valid @RequestBody request: CreditWalletRequest): WalletResponse =
        walletService.credit(currentUser.userId(), request.amount)
}
