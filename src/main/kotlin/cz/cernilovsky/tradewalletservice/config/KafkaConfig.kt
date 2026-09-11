package cz.cernilovsky.tradewalletservice.config

import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff
import org.apache.kafka.clients.admin.NewTopic

@Configuration
class KafkaConfig(
    private val kafkaTopicsProperties: KafkaTopicsProperties,
) {
    @Bean
    fun ordersTopic(): NewTopic =
        TopicBuilder.name(kafkaTopicsProperties.ordersTopic)
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun ordersDltTopic(): NewTopic =
        TopicBuilder.name(kafkaTopicsProperties.ordersDltTopic)
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        kafkaTemplate: KafkaTemplate<String, String>,
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.setConsumerFactory(consumerFactory)
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
            TopicPartition(kafkaTopicsProperties.ordersDltTopic, record.partition())
        }
        factory.setCommonErrorHandler(DefaultErrorHandler(recoverer, FixedBackOff(500L, 3)))
        return factory
    }
}
