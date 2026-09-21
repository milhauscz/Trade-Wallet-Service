package cz.cernilovsky.tradewalletservice.outbox.domain

import cz.cernilovsky.tradewalletservice.common.exception.NotImplementedYetException
import cz.cernilovsky.tradewalletservice.outbox.persistence.OutboxEventEntity
import cz.cernilovsky.tradewalletservice.outbox.persistence.OutboxEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OutboxService(
    private val outboxEventRepository: OutboxEventRepository,
) {
    /**
     * TODO(learning) Phase 3 — Persist an unpublished outbox row.
     *
     * Must run in the **same** transaction as order insert + wallet reserve
     * (`Propagation.REQUIRED`, which is the default — do not use `REQUIRES_NEW` here).
     *
     * Implement:
     * 1. Build `OutboxEventEntity(topic, aggregateId, payload)` with `publishedAt = null`.
     * 2. `outboxEventRepository.save(entity)`.
     * 3. Do not send to Kafka here. The relay publishes after commit.
     *
     * `payload` should be JSON of `OrderCreatedEvent`. Serialize with the injected Jackson
     * `ObjectMapper` (`tools.jackson.databind.ObjectMapper` on Spring Boot 4).
     */
    @Transactional
    fun enqueue(topic: String, aggregateId: String, payload: String) {
        val outboxEventEntity = OutboxEventEntity(
            topic = topic,
            aggregateId = aggregateId,
            payload = payload
        )
        outboxEventRepository.save(outboxEventEntity)
    }
}
