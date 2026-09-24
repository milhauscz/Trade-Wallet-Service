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

/**
 * TODO(learning) Phase 1 + 5 — Prove pessimistic locking prevents double-spend.
 *
 * Setup:
 * - Extend [cz.cernilovsky.tradewalletservice.support.BaseIntegrationTest] (already done).
 * - Autowire `MockMvc`, `JwtEncoder` (or call `POST /api/v1/auth/token` for alice).
 * - Autowire `WalletRepository` to reset alice's wallet before the test:
 *   `balance = 100`, `reservedAmount = 0`.
 *
 * Scenario:
 * - 20 parallel `POST /api/v1/orders` for alice, each order `price=10`, `quantity=1`
 *   (required 10). Unique `X-Idempotency-Key` per request.
 * - Available funds cover only 10 orders (100 / 10).
 *
 * Assert:
 * - Count of HTTP 201 == 10.
 * - Count of HTTP 422 (`INSUFFICIENT_FUNDS`) == 10.
 * - No other statuses (no 500).
 * - Reload wallet: `reservedAmount == 100`, `balance == 100`, `available == 0`.
 * - `reservedAmount` never went negative (CHECK constraint + assertion).
 *
 * How to run in parallel: `IntStream.range(0, 20).parallel()` or `CountDownLatch` +
 * 20 threads / Kotlin coroutines with a thread pool. `MockMvc` is thread-safe enough
 * for this; alternatively `TestRestTemplate` against `RANDOM_PORT`.
 *
 * If this test flakes (all 20 succeed): you loaded the wallet **without** `FOR UPDATE`.
 * If it deadlocks: lock order is wrong or you opened nested transactions that wait on
 * each other — keep a single `@Transactional` on `OrderService.create`.
 */
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
