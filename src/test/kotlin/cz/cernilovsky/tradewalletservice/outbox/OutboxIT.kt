package cz.cernilovsky.tradewalletservice.outbox

import cz.cernilovsky.tradewalletservice.support.BaseIntegrationTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * TODO(learning) Phase 3 + 5 — Outbox row is written in the same TX; Kafka sees the event.
 *
 * Setup:
 * - Autowire `OutboxEventRepository`, `KafkaTemplate` / a test `@KafkaListener`,
 *   or `org.springframework.kafka.test.utils.KafkaTestUtils` consumer on `orders`.
 * - Spring Boot `@ServiceConnection` already points the app at the Testcontainers broker.
 *
 * Scenario A — commit path:
 * - Successful `POST /api/v1/orders`.
 * - Immediately: an `outbox_events` row exists (`published_at` may still be null).
 * - Within a few seconds (relay poll interval is 1s): `published_at` is set AND a
 *   record appears on topic `orders` whose value deserializes to `OrderCreatedEvent`
 *   with the same `orderId`.
 *
 * Scenario B — rollback path (the interview one):
 * - Temporarily force a failure **after** you would have sent to Kafka in a naive design
 *   (e.g. throw after `outboxService.enqueue` if you inject a stub, or fail wallet CHECK
 *   by reserving more than available).
 * - Assert: no order row, no outbox row. Kafka topic has no event for that id.
 * - This proves you did **not** call `KafkaTemplate.send` inside the request transaction.
 *
 * Scenario C — DLT (optional):
 * - Create an order with symbol `FAIL-DLT` if your listener throws on that symbol.
 * - After retries, a record lands on `orders.DLT`.
 */
class OutboxIT : BaseIntegrationTest() {
    @Test
    @Disabled("TODO(learning): Phase 3 + 5 outbox then Kafka")
    fun createdOrderWritesOutboxAndIsPublishedToKafka() {
    }
}
