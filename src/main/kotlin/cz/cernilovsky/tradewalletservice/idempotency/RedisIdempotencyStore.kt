package cz.cernilovsky.tradewalletservice.idempotency

import cz.cernilovsky.tradewalletservice.common.exception.NotImplementedYetException
import cz.cernilovsky.tradewalletservice.config.IdempotencyProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisIdempotencyStore(
    private val redis: StringRedisTemplate,
    private val properties: IdempotencyProperties,
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
        throw NotImplementedYetException("RedisIdempotencyStore.get")
    }

    override fun tryBegin(userId: String, key: String): Boolean {
        throw NotImplementedYetException("RedisIdempotencyStore.tryBegin")
    }

    override fun save(userId: String, key: String, response: CachedHttpResponse) {
        throw NotImplementedYetException("RedisIdempotencyStore.save")
    }
}
