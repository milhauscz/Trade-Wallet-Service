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

    // Polls unpublished outbox rows and publishes each in its own transaction.
    // A Kafka failure leaves publishedAt null so the next poll retries that row only.
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
