package cz.cernilovsky.tradewalletservice.outbox

import com.google.common.truth.Truth.assertThat
import cz.cernilovsky.tradewalletservice.messaging.OrderCreatedEvent
import cz.cernilovsky.tradewalletservice.order.api.CreateOrderRequest
import cz.cernilovsky.tradewalletservice.order.api.OrderResponse
import cz.cernilovsky.tradewalletservice.outbox.persistence.OutboxEventRepository
import cz.cernilovsky.tradewalletservice.support.BaseIntegrationTest
import cz.cernilovsky.tradewalletservice.support.TestAuth
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
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
    private val consumerFactory: ConsumerFactory<String, String>,
) : BaseIntegrationTest() {
    @Test
    fun createdOrderWritesOutboxAndIsPublishedToKafka() {
        val consumer = ordersConsumerAtEnd()
        consumer.use { consumer ->
            val authorization = TestAuth.bearerToken(jwtEncoder, "alice")
            val result = postOrder(
                authorization,
                CreateOrderRequest("BTC", BigDecimal(100), BigDecimal(2)),
            )
            assertThat(result.response.status).isEqualTo(HttpStatus.CREATED.value())
            val orderResponse = objectMapper.readValue(result.response.contentAsString, OrderResponse::class.java)

            val published = waitUntilPublished(orderResponse.id.toString())
            assertThat(published.publishedAt).isNotNull()

            val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5))
                .filter { it.key() == orderResponse.id.toString() }
            assertThat(records).hasSize(1)
            val record = records.single()
            val orderCreatedEventFromKafka = objectMapper.readValue(record.value(), OrderCreatedEvent::class.java)
            val orderFromOutbox = objectMapper.readValue(published.payload, OrderCreatedEvent::class.java)
            assertThat(orderCreatedEventFromKafka).isEqualTo(orderFromOutbox)
            assertThat(record.key()).isEqualTo(orderCreatedEventFromKafka.orderId.toString())
            assertThat(published.aggregateId).isEqualTo(orderResponse.id.toString())
        }
    }

    @Test
    fun failedReserveDoesNotWriteOutboxOrPublish() {
        val authorization = TestAuth.bearerToken(jwtEncoder, "alice")
        val outboxCountBefore = outboxEventRepository.count()

        val overReserve = postOrder(
            authorization,
            CreateOrderRequest("BTC", BigDecimal(100000000), BigDecimal(500)),
        )

        assertThat(overReserve.response.status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value())
        assertThat(outboxEventRepository.count()).isEqualTo(outboxCountBefore)
    }

    private fun postOrder(
        authorization: String,
        request: CreateOrderRequest,
    ) = mockMvc.request(
        HttpMethod.POST,
        URI("/api/v1/orders")
    ) {
        configureHeaders(authorization)
        content = objectMapper.writeValueAsString(request)
    }.andReturn()

    private fun waitUntilPublished(aggregateId: String) = run {
        val deadline = Clock.System.now().plus(5.seconds)
        var event = outboxEventRepository.findAll().single { it.aggregateId == aggregateId }
        while (event.publishedAt == null && Clock.System.now() < deadline) {
            Thread.sleep(200)
            event = outboxEventRepository.findAll().single { it.aggregateId == aggregateId }
        }
        event
    }

    private fun ordersConsumerAtEnd(): Consumer<String, String> {
        val consumer = consumerFactory.createConsumer("outbox-it-${UUID.randomUUID()}", null)
        val partitions = consumer.partitionsFor("orders").map { TopicPartition("orders", it.partition()) }
        consumer.assign(partitions)
        consumer.seekToEnd(partitions)
        consumer.poll(Duration.ofMillis(500))
        return consumer
    }
}
