package cz.cernilovsky.tradewalletservice.wallet

import cz.cernilovsky.tradewalletservice.wallet.domain.WalletService
import cz.cernilovsky.tradewalletservice.wallet.persistence.WalletRepository
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * TODO(learning) Phase 1 — Unit-test reserve math without a database.
 *
 * After you add `findByUserIdForUpdate` and implement `WalletService.reserve`:
 * - Mock the repository to return a wallet with `balance=100`, `reservedAmount=40`.
 * - `reserve(price=10, quantity=5)` → reserved becomes 90, return value 50.
 * - `reserve(price=10, quantity=7)` → `InsufficientFundsException` (available 60 < 70).
 * - Verify `findByUserIdForUpdate` was called, `findByUserId` was **not**.
 *
 * Use MockK: `every { walletRepository.findByUserIdForUpdate("alice") } returns wallet`
 */
@ExtendWith(MockKExtension::class)
class WalletServiceTest {
    @MockK
    lateinit var walletRepository: WalletRepository

    @InjectMockKs
    lateinit var walletService: WalletService

    @Test
    @Disabled("TODO(learning): Phase 1 unit test reserve")
    fun reserveIncreasesReservedAmountWhenFundsAvailable() {
        walletService.hashCode()
        walletRepository.hashCode()
    }
}
