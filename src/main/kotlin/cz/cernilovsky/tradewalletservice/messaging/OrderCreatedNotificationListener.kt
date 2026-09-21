package cz.cernilovsky.tradewalletservice.messaging

import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class OrderCreatedNotificationListener(
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * TODO(learning) Phase 3 — Consume `OrderCreatedEvent` from Kafka.
     *
     * 1. Add `@KafkaListener(topics = ["\${app.kafka.orders-topic}"], groupId = "trade-wallet-notifications")`.
     *    A dedicated `groupId` keeps this consumer independent from other listeners.
     * 2. Deserialize `payload` to `OrderCreatedEvent` (Jackson `ObjectMapper`).
     * 3. Simulate a notification: log INFO with orderId, userId, symbol.
     * 4. To test DLT: throw a RuntimeException for a specific symbol (e.g. `FAIL-DLT`).
     *    `DefaultErrorHandler` in `KafkaConfig` retries 3 times then publishes to `orders.DLT`.
     *
     * Interview talking points:
     * - Partition key was `orderId` (set by the relay) → ordering per order, not globally.
     * - Offset commits after the listener returns (record ack-mode in application.yml).
     * - DLT is for poison pills; do not infinite-retry business bugs.
     */
    @KafkaListener(topics = ["\${app.kafka.orders-topic}"], groupId = "trade-wallet-notifications")
    fun onOrderCreated(payload: String) {
        val event = objectMapper.readValue(payload, OrderCreatedEvent::class.java)
        log.info("Order created - order ID: ${event.orderId}, user ID: ${event.userId}, symbol: ${event.symbol}")
        if (event.symbol == "FAIL-DLT") throw RuntimeException("Wrong order symbol ${event.symbol}")
    }
}
