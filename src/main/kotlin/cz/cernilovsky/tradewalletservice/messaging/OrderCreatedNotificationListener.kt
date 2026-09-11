package cz.cernilovsky.tradewalletservice.messaging

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class OrderCreatedNotificationListener {
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
    fun onOrderCreated(payload: String) {
        log.debug("Listener stub received payload length={}", payload.length)
    }
}
