package cz.cernilovsky.tradewalletservice.wallet

import com.google.common.truth.Truth.assertThat
import cz.cernilovsky.tradewalletservice.common.exception.BadRequestException
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

// Reserve and release math without a database.
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

    @Test
    fun releaseDecreasesReservedAmount() {
        val entity = walletWithReserved(BigDecimal(90))
        every { walletRepository.findByUserIdForUpdate(any()) } returns entity
        every { walletRepository.save(any()) } answers { firstArg() }

        walletService.release("5", BigDecimal(50))

        assertThat(entity.reservedAmount).isEqualToIgnoringScale(BigDecimal(40))
        verify(exactly = 1) { walletRepository.findByUserIdForUpdate("5") }
        verify(exactly = 0) { walletRepository.findByUserId("5") }
        verify(exactly = 1) { walletRepository.save(any()) }
        confirmVerified(walletRepository)
    }

    @Test
    fun releaseThrowsWhenAmountExceedsReserved() {
        val entity = walletWithReserved(BigDecimal(40))
        every { walletRepository.findByUserIdForUpdate(any()) } returns entity

        assertThrows<BadRequestException> {
            walletService.release("5", BigDecimal(50))
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
