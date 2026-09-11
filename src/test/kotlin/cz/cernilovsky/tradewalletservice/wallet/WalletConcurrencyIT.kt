package cz.cernilovsky.tradewalletservice.wallet

import cz.cernilovsky.tradewalletservice.support.BaseIntegrationTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

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
class WalletConcurrencyIT : BaseIntegrationTest() {
    @Test
    @Disabled("TODO(learning): Phase 1 + 5 concurrent reserve")
    fun concurrentOrdersMustNotOverReserveWallet() {
    }
}
