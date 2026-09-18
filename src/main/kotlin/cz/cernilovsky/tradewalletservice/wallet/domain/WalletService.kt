package cz.cernilovsky.tradewalletservice.wallet.domain

import cz.cernilovsky.tradewalletservice.common.exception.InsufficientFundsException
import cz.cernilovsky.tradewalletservice.common.exception.NotImplementedYetException
import cz.cernilovsky.tradewalletservice.common.exception.ResourceNotFoundException
import cz.cernilovsky.tradewalletservice.wallet.api.WalletResponse
import cz.cernilovsky.tradewalletservice.wallet.persistence.WalletEntity
import cz.cernilovsky.tradewalletservice.wallet.persistence.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

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

    /**
     * TODO(learning) Phase 1 — Reserve funds with pessimistic locking.
     *
     * Goal: concurrent order creates for the same user must never push `reservedAmount`
     * above `balance` (double-spend).
     *
     * Implement:
     * 1. Annotate this method with `@Transactional` (default `Propagation.REQUIRED`).
     *    `OrderService.create` will call it inside the same transaction.
     * 2. Load the wallet with `findByUserIdForUpdate(userId)` — this is the `FOR UPDATE` lock.
     *    If missing, throw `ResourceNotFoundException("Wallet", userId)`.
     * 3. Compute `required = price * quantity` using `BigDecimal` (`multiply`).
     * 4. If `wallet.available() < required`, throw `InsufficientFundsException`.
     * 5. `wallet.reservedAmount += required` and `save`.
     * 6. Return the reserved amount so the caller can store it on the order if needed.
     *
     * Do **not** call Kafka from here. Do **not** use `@Lock` on the service. The lock belongs
     * on the repository query.
     *
     * Interview check: two threads entering this method for the same `userId` — the second
     * waits on the row lock until the first transaction commits or rolls back.
     */
    @Transactional
    fun reserve(userId: String, price: BigDecimal, quantity: BigDecimal): BigDecimal {
        val wallet = walletRepository.findByUserIdForUpdate(userId) ?: throw ResourceNotFoundException("Wallet", userId)
        val required = price * quantity
        if (wallet.available() < required) throw InsufficientFundsException(wallet.available().toString(), required.toString())
        wallet.reservedAmount = wallet.reservedAmount.add(required)
        walletRepository.save(wallet)
        return required
    }

    /**
     * TODO(learning) Phase 1 (optional follow-up): release reserved funds when an order is cancelled.
     * Same locking rules as `reserve` — `FOR UPDATE`, then subtract from `reservedAmount`.
     */
    fun release(userId: String, amount: BigDecimal) {
        throw NotImplementedYetException("WalletService.release")
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
