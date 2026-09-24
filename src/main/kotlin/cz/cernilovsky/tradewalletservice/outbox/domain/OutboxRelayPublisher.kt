package cz.cernilovsky.tradewalletservice.outbox.domain

import cz.cernilovsky.tradewalletservice.outbox.persistence.OutboxEventEntity
import cz.cernilovsky.tradewalletservice.outbox.persistence.OutboxEventRepository
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class OutboxRelayPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
) {
    // Sends the payload to Kafka with aggregateId as the key, then marks the row published.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun publishEvent(event: OutboxEventEntity) {
        kafkaTemplate.send(event.topic, event.aggregateId, event.payload).get()
        event.publishedAt = Instant.now()
        outboxEventRepository.save(event)
    }
}