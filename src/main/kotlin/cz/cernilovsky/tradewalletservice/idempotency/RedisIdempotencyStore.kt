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
    /**
     * TODO(learning) Phase 4 — Redis response cache.
     *
     * Key design: `idempotency:{userId}:{key}` so Alice cannot replay Bob's response.
     * TTL: `properties.ttl` (24h in config).
     *
     * `get`: `opsForValue().get(redisKey)` and deserialize JSON to `CachedHttpResponse`.
     * Store status/body/contentType as JSON (or three hash fields). Keep it simple: one JSON string.
     *
     * `tryBegin`: `SET key "in-progress" NX EX ttl`. In Spring Data Redis:
     * `redis.opsForValue().setIfAbsent(redisKey, IN_PROGRESS, properties.ttl)`.
     * Return true when the SET happened (this request owns the work).
     * Return false when the key already exists (duplicate or in-flight).
     *
     * `save`: overwrite the key with the final JSON response and the same TTL
     * (`set(key, json, ttl)` — not NX, you already own it).
     *
     * Redis is **not** the source of truth. `orders(user_id, idempotency_key)` UNIQUE is.
     * If Redis is flushed, a retry hits the unique constraint / `findByUserIdAndIdempotencyKey`
     * in `OrderService.create` and still must not create a second order.
     */
    override fun get(userId: String, key: String): CachedHttpResponse? {
        val value = redis.opsForValue().get(createRedisKey(userId, key)) ?: return null
        if (value == IDEMPOTENT_OPERATION_IN_PROGRESS_VALUE) return null
        return objectMapper.readValue(value, CachedHttpResponse::class.java)
    }

    override fun tryBegin(userId: String, key: String): Boolean {
        return redis.opsForValue().setIfAbsent(
            createRedisKey(userId, key),
            IDEMPOTENT_OPERATION_IN_PROGRESS_VALUE,
            properties.ttl
        )
    }

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
