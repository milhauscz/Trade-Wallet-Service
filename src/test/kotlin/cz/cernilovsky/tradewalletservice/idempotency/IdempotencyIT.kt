package cz.cernilovsky.tradewalletservice.idempotency

import cz.cernilovsky.tradewalletservice.support.BaseIntegrationTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * TODO(learning) Phase 4 + 5 — Idempotency key replays the original response.
 *
 * Setup:
 * - Authenticate as alice. Pick one UUID as `X-Idempotency-Key`.
 *
 * Scenario A — retry after success:
 * - `POST /api/v1/orders` twice with the **same** key and the **same** body.
 * - First: 201, one row in `orders`, wallet reserved once.
 * - Second: 201 (or 200 if you choose replay-as-200 — pick one and stick to it),
 *   **same order id**, `orders` count still 1, `reservedAmount` unchanged.
 *
 * Scenario B — uniqueness without Redis:
 * - Flush Redis (`redisTemplate.connectionFactory.connection.serverCommands().flushAll()`)
 *   after the first POST, then retry with the same key.
 * - Still a single order row (DB unique / `findByUserIdAndIdempotencyKey`).
 *
 * Scenario C — concurrent duplicates:
 * - Two parallel POSTs with the same key: exactly one 201 from business logic,
 *   the other is either a cached replay or 409 in-progress then retry → still one row.
 */
class IdempotencyIT : BaseIntegrationTest() {
    @Test
    @Disabled("TODO(learning): Phase 4 + 5 idempotent POST /orders")
    fun duplicateIdempotencyKeyDoesNotCreateSecondOrder() {
    }
}
