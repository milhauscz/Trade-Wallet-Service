package cz.cernilovsky.tradewalletservice.wallet.domain

import cz.cernilovsky.tradewalletservice.common.exception.BadRequestException
import cz.cernilovsky.tradewalletservice.common.exception.InsufficientFundsException
import cz.cernilovsky.tradewalletservice.common.exception.ResourceNotFoundException
import cz.cernilovsky.tradewalletservice.wallet.api.WalletResponse
import cz.cernilovsky.tradewalletservice.wallet.persistence.WalletEntity
import cz.cernilovsky.tradewalletservice.wallet.persistence.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.plus

@Service
class WalletService(
    private val walletRepository: WalletRepository,
) {
    @Transactional(readOnly = true)
    fun getByUserId(userId: String): WalletResponse = requireWallet(userId).toResponse()

    @Transactional
    fun credit(userId: String, amount: BigDecimal): WalletResponse {
        val wallet = requireWallet(userId)
        wallet.balance = wallet.balance.add(amount)
        return walletRepository.save(wallet).toResponse()
    }

    // Locks the wallet row and reserves price * quantity. Returns that amount.
    // Throws InsufficientFundsException when available balance is too low.
    @Transactional
    fun reserve(userId: String, price: BigDecimal, quantity: BigDecimal): BigDecimal {
        val wallet = walletRepository.findByUserIdForUpdate(userId) ?: throw ResourceNotFoundException("Wallet", userId)
        val required = price * quantity
        if (wallet.available() < required) throw InsufficientFundsException(wallet.available().toString(), required.toString())
        wallet.reservedAmount += required
        walletRepository.save(wallet)
        return required
    }

    // Locks the wallet row and subtracts a positive amount from reservedAmount.
    @Transactional
    fun release(userId: String, amount: BigDecimal) {
        val wallet = walletRepository.findByUserIdForUpdate(userId)
            ?: throw ResourceNotFoundException("Wallet", userId)
        if (amount.signum() <= 0) {
            throw BadRequestException("Release amount must be positive")
        }
        if (wallet.reservedAmount < amount) {
            throw BadRequestException("Cannot release $amount; reserved is ${wallet.reservedAmount}")
        }
        wallet.reservedAmount -= amount
        walletRepository.save(wallet)
    }

    private fun requireWallet(userId: String): WalletEntity =
        walletRepository.findByUserId(userId)
            ?: throw ResourceNotFoundException("Wallet", userId)

    private fun WalletEntity.toResponse() = WalletResponse(
        userId = userId,
        balance = balance,
        reservedAmount = reservedAmount,
        available = available(),
        currency = currency,
    )
}
