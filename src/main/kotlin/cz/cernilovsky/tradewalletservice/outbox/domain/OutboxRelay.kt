package cz.cernilovsky.tradewalletservice.outbox.domain

import cz.cernilovsky.tradewalletservice.outbox.persistence.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OutboxRelay(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxRelayPublisher: OutboxRelayPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * TODO(learning) Phase 3 — Publish unpublished outbox rows to Kafka.
     *
     * Why a relay (and not `KafkaTemplate.send` inside `OrderService.create`)?
     * If you send inside the request transaction:
     * - send succeeds + DB rolls back → ghost event
     * - DB commits + send fails → silent loss
     * Outbox: the event row commits atomically with the order. This job is at-least-once.
     *
     * Implement `publishUnpublishedEvents`:
     * 1. Load a batch: `findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()`
     *    (or add a query limited by `outboxProperties.batchSize`).
     * 2. For each row, call a **separate** method annotated with
     *    `@Transactional(propagation = Propagation.REQUIRES_NEW)`:
     *    - `kafkaTemplate.send(event.topic, event.aggregateId, event.payload).get()`
     *      (key = `aggregateId` so all events for one order stay in one partition)
     *    - set `event.publishedAt = Instant.now()` and save
     * 3. `REQUIRES_NEW` so a Kafka failure on row N does not roll back N-1's `publishedAt`.
     * 4. On Kafka failure, leave `publishedAt` null — the next poll retries (at-least-once).
     *    Consumers must be idempotent.
     * 5. Log at INFO when a row is published; WARN on failure and continue the batch.
     *
     * Avoid: sending from `OrderService`. Avoid: `REQUIRED` on the per-row publish
     * if the outer scheduled method is also transactional (you'd share one TX).
     * Keep the scheduled method **non-transactional**; only the per-row method opens TX.
     */
    @Scheduled(fixedDelayString = "\${app.outbox.poll-interval:1s}")
    fun publishUnpublishedEvents() {
        val batch = outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()
        batch.forEach { event -> try {
                outboxRelayPublisher.publishEvent(event)
                log.info("Published $event to Kafka")
            } catch(e: Exception) {
                log.warn("Publishing event $event to Kafka failed because of exception $e.")
            }
        }
    }
}
