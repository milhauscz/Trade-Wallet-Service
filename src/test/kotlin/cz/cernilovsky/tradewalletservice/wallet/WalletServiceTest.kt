package cz.cernilovsky.tradewalletservice.wallet

import com.google.common.truth.Truth.assertThat
import cz.cernilovsky.tradewalletservice.common.exception.InsufficientFundsException
import cz.cernilovsky.tradewalletservice.wallet.domain.WalletService
import cz.cernilovsky.tradewalletservice.wallet.persistence.WalletEntity
import cz.cernilovsky.tradewalletservice.wallet.persistence.WalletRepository
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal

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
    fun reserveIncreasesReservedAmountWhenFundsAvailable() {
        val entity = walletWithReserved(BigDecimal(40))
        every { walletRepository.findByUserIdForUpdate(any()) } returns entity
        every { walletRepository.save(any()) } answers { firstArg() }

        val reservedAmount = walletService.reserve("5", BigDecimal(10), BigDecimal(5))

        assertThat(reservedAmount).isEqualToIgnoringScale(BigDecimal(50))
        assertThat(entity.reservedAmount).isEqualToIgnoringScale(BigDecimal(90))
        verify(exactly = 1) { walletRepository.findByUserIdForUpdate("5") }
        verify(exactly = 0) { walletRepository.findByUserId("5") }
        verify(exactly = 1) { walletRepository.save(any()) }
        confirmVerified(walletRepository)
    }

    @Test
    fun reserveThrowsWhenFundsAreInsufficient() {
        val entity = walletWithReserved(BigDecimal(40))
        every { walletRepository.findByUserIdForUpdate(any()) } returns entity

        assertThrows<InsufficientFundsException> {
            walletService.reserve("5", BigDecimal(10), BigDecimal(7))
        }

        assertThat(entity.reservedAmount).isEqualToIgnoringScale(BigDecimal(40))
        verify(exactly = 1) { walletRepository.findByUserIdForUpdate("5") }
        verify(exactly = 0) { walletRepository.findByUserId("5") }
        verify(exactly = 0) { walletRepository.save(any()) }
        confirmVerified(walletRepository)
    }

    private fun walletWithReserved(reservedAmount: BigDecimal) = WalletEntity(
        userId = "5",
        balance = BigDecimal(100),
        reservedAmount = reservedAmount,
    )
}
