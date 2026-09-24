package cz.cernilovsky.tradewalletservice.outbox

import cz.cernilovsky.tradewalletservice.messaging.OrderCreatedEvent
import cz.cernilovsky.tradewalletservice.order.api.CreateOrderRequest
import cz.cernilovsky.tradewalletservice.order.api.OrderResponse
import cz.cernilovsky.tradewalletservice.outbox.persistence.OutboxEventRepository
import cz.cernilovsky.tradewalletservice.support.BaseIntegrationTest
import cz.cernilovsky.tradewalletservice.support.TestAuth
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.time.Duration
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

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
class OutboxIT @Autowired constructor(
    private val outboxEventRepository: OutboxEventRepository,
    private val mockMvc: MockMvc,
    private val jwtEncoder: JwtEncoder,
    private val objectMapper: ObjectMapper,
    private val consumerFactory: ConsumerFactory<String,String>
) : BaseIntegrationTest() {
    @Test
    fun createdOrderWritesOutboxAndIsPublishedToKafka() {
        // Go to the end of the queue to ignore old records from other tests
        val consumer = consumerFactory.createConsumer("outbox-it-${UUID.randomUUID()}", null)
        val partitions = consumer.partitionsFor("orders").map { TopicPartition("orders", it.partition()) }
        consumer.assign(partitions)
        consumer.seekToEnd(partitions)
        consumer.poll(Duration.ofMillis(500))

        // Make a POST request for creating an order
        val authorization = TestAuth.bearerToken(jwtEncoder, "alice")
        val result = mockMvc.request(
            HttpMethod.POST,
            URI("/api/v1/orders")
        ) {
            configureHeaders(authorization)
            content = objectMapper.writeValueAsString(CreateOrderRequest(
                "BTC",
                BigDecimal(100),
                BigDecimal(2)
            ))
        }.andReturn()

        val orderResponse = objectMapper.readValue(result.response.contentAsString, OrderResponse::class.java)

        // Check outbox service published the event to the repository and outbox relay published it to Kafka
        // (publishedAt is set at that moment)
        var orderOutboxEventEntity = outboxEventRepository.findAll().single { it.aggregateId == orderResponse.id.toString() }
        val deadline = Clock.System.now().plus(5.seconds)
        // outbox events are read and published to Kafka via @Scheduled function every second
        while (orderOutboxEventEntity.publishedAt == null && Clock.System.now() < deadline) {
            Thread.sleep(200)
            orderOutboxEventEntity = outboxEventRepository.findAll().single { it.aggregateId == orderResponse.id.toString() }
        }
        assertNotNull(orderOutboxEventEntity.publishedAt)

        // find the record in Kafka
        val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5))
        assertThat(records.count()).isEqualTo(1)
        val orderCreatedEventFromKafka = objectMapper.readValue(records.single().value(), OrderCreatedEvent::class.java)
        val orderFromOutboxEventEntityPayload = objectMapper.readValue(orderOutboxEventEntity.payload, OrderCreatedEvent::class.java)
        assertThat(orderCreatedEventFromKafka).isEqualTo(orderFromOutboxEventEntityPayload)
        assertThat(records.single().key()).isEqualTo(orderCreatedEventFromKafka.orderId.toString())
        assertThat(orderOutboxEventEntity.aggregateId).isEqualTo(orderResponse.id.toString())
        consumer.close()

        // try reserving too much
        val outboxEventsBeforeOverReserve = outboxEventRepository.findAll()
        val overReserve = mockMvc.request(
            HttpMethod.POST,
            URI("/api/v1/orders")
        ) {
            configureHeaders(authorization)
            content = objectMapper.writeValueAsString(CreateOrderRequest(
                "BTC",
                BigDecimal(100000000),
                BigDecimal(500)
            ))
        }.andReturn()

        assertThat(overReserve.response.status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value())
        assertThat(outboxEventsBeforeOverReserve).hasSameSizeAs(outboxEventRepository.findAll())
    }
}
