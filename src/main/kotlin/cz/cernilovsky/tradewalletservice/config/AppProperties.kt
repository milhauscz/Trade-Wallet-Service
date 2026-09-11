package cz.cernilovsky.tradewalletservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val secret: String,
    val issuer: String,
    val ttl: Duration,
)

@ConfigurationProperties(prefix = "app.kafka")
data class KafkaTopicsProperties(
    val ordersTopic: String,
    val ordersDltTopic: String,
)

@ConfigurationProperties(prefix = "app.outbox")
data class OutboxProperties(
    val pollInterval: Duration,
    val batchSize: Int,
)

@ConfigurationProperties(prefix = "app.idempotency")
data class IdempotencyProperties(
    val ttl: Duration,
    val headerName: String,
)
