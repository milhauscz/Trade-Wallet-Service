package cz.cernilovsky.tradewalletservice.idempotency

interface IdempotencyStore {
    fun get(userId: String, key: String): CachedHttpResponse?

    /**
     * Atomically mark the key as in-progress. Return `true` if this caller won the race.
     */
    fun tryBegin(userId: String, key: String): Boolean

    fun processResponse(userId: String, key: String, response: CachedHttpResponse)
}
