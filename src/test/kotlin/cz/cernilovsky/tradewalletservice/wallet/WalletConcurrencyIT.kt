package cz.cernilovsky.tradewalletservice.wallet

import com.google.common.truth.Truth.assertThat
import cz.cernilovsky.tradewalletservice.support.BaseIntegrationTest
import cz.cernilovsky.tradewalletservice.support.TestAuth
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request
import java.math.BigDecimal
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import kotlin.test.BeforeTest

// Parallel orders for one wallet must not reserve more than the balance.
class WalletConcurrencyIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jwtEncoder: JwtEncoder,
) : BaseIntegrationTest() {
    @BeforeTest
    fun resetWalletToSmallBalance() {
        val wallet = walletRepository.findByUserId("alice") ?: throw IllegalStateException("alice's wallet not found")
        wallet.balance = BigDecimal(100)
        wallet.reservedAmount = BigDecimal.ZERO
        walletRepository.saveAndFlush(wallet)
    }

    @Test
    fun concurrentOrdersMustNotOverReserveWallet() {
        val http201Count = AtomicInteger(0)
        val http422Count = AtomicInteger(0)
        val otherCount = AtomicInteger(0)
        val authorization = TestAuth.bearerToken(jwtEncoder)
        IntStream.range(0, 20).parallel().forEach {
            val status = mockMvc.request(
                HttpMethod.POST,
                URI("/api/v1/orders")
            ) {
                configureHeaders(authorization)
                content = """{"symbol":"AAPL","price":10,"quantity":1}"""
            }.andReturn().response.status
            when (status) {
                201 -> http201Count.incrementAndGet()
                422 -> http422Count.incrementAndGet()
                else -> otherCount.incrementAndGet()
            }
        }
        assertThat(http201Count.get()).isEqualTo(10)
        assertThat(http422Count.get()).isEqualTo(10)
        assertThat(otherCount.get()).isEqualTo(0)
        val wallet = walletRepository.findByUserId("alice")!!
        assertThat(wallet.reservedAmount).isEqualToIgnoringScale(BigDecimal(100))
        assertThat(wallet.balance).isEqualToIgnoringScale(BigDecimal(100))
        assertThat(wallet.available()).isEqualToIgnoringScale(BigDecimal.ZERO)
    }
}
