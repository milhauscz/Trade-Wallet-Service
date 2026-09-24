package cz.cernilovsky.tradewalletservice.outbox.domain

import cz.cernilovsky.tradewalletservice.outbox.persistence.OutboxEventEntity
import cz.cernilovsky.tradewalletservice.outbox.persistence.OutboxEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OutboxService(
    private val outboxEventRepository: OutboxEventRepository,
) {
    // Inserts an unpublished event into the caller's transaction. Does not talk to Kafka.
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
