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

    // Logs a created order. Symbol FAIL-DLT is rejected so retries end on orders.DLT.
    @KafkaListener(topics = ["\${app.kafka.orders-topic}"], groupId = "trade-wallet-notifications")
    fun onOrderCreated(payload: String) {
        val event = objectMapper.readValue(payload, OrderCreatedEvent::class.java)
        log.info("Order created - order ID: ${event.orderId}, user ID: ${event.userId}, symbol: ${event.symbol}")
        if (event.symbol == "FAIL-DLT") throw RuntimeException("Wrong order symbol ${event.symbol}")
    }
}
