package cz.cernilovsky.tradewalletservice.order

import cz.cernilovsky.tradewalletservice.support.BaseIntegrationTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * TODO(learning) Phase 2 + 5 — Optimistic locking returns HTTP 409.
 *
 * Setup:
 * - Authenticate as alice, create one PENDING order (or insert via `OrderRepository`).
 * - Read `version` from `GET /api/v1/orders/{id}`.
 *
 * Scenario A — happy path:
 * - `PATCH` with that version and a new price → 200, response `version` incremented by 1.
 *
 * Scenario B — lost update:
 * - Two threads (or two sequential PATCHes) both send the **same original version**.
 * - First PATCH 200, second PATCH **409** with code `OPTIMISTIC_LOCK`.
 * - `GET` shows only the first change and `version = original + 1`.
 *
 * Implementation notes:
 * - You need `@Version` on `OrderEntity.version` and the 409 handler in
 *   `GlobalExceptionHandler` before this test can pass.
 * - Use `saveAndFlush` in `OrderService.update` so the conflict surfaces in the request.
 */
class OrderOptimisticLockIT : BaseIntegrationTest() {
    @Test
    @Disabled("TODO(learning): Phase 2 + 5 optimistic lock 409")
    fun staleVersionOnPatchReturnsConflict() {
    }
}
