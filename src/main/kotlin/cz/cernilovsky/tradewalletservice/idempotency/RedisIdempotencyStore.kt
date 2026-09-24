package cz.cernilovsky.tradewalletservice.idempotency

import cz.cernilovsky.tradewalletservice.config.IdempotencyProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class RedisIdempotencyStore(
    private val redis: StringRedisTemplate,
    private val properties: IdempotencyProperties,
    private val objectMapper: ObjectMapper
) : IdempotencyStore {
    // Returns a cached HTTP response for idempotency:{userId}:{key}.
    // An in-progress sentinel is not a replay and returns null.
    override fun get(userId: String, key: String): CachedHttpResponse? {
        val value = redis.opsForValue().get(createRedisKey(userId, key)) ?: return null
        if (value == IDEMPOTENT_OPERATION_IN_PROGRESS_VALUE) return null
        return objectMapper.readValue(value, CachedHttpResponse::class.java)
    }

    // Claims the key with SET NX. False means another request already owns it.
    override fun tryBegin(userId: String, key: String): Boolean {
        return redis.opsForValue().setIfAbsent(
            createRedisKey(userId, key),
            IDEMPOTENT_OPERATION_IN_PROGRESS_VALUE,
            properties.ttl
        )
    }

    // Caches 2xx and 4xx for the configured TTL. Deletes the key on 5xx so the client can retry.
    override fun processResponse(userId: String, key: String, response: CachedHttpResponse) {
        val redisKey = createRedisKey(userId, key)
        with(HttpStatusCode.valueOf(response.status)) {
            when {
                is2xxSuccessful || is4xxClientError -> storeCachedResponseToRedis(redisKey, response)
                is5xxServerError -> removeCachedResponseFromRedis(redisKey)
            }
        }
    }

    private fun storeCachedResponseToRedis(
        redisKey: String,
        response: CachedHttpResponse
    ) {
        redis.opsForValue().set(
            redisKey,
            objectMapper.writeValueAsString(response),
            properties.ttl
        )
    }

    private fun removeCachedResponseFromRedis(redisKey: String) {
        redis.delete(redisKey)
    }

    private fun createRedisKey(userId: String, key: String): String = "idempotency:$userId:$key"

    companion object {
        const val IDEMPOTENT_OPERATION_IN_PROGRESS_VALUE = "in-progress"
    }
}
