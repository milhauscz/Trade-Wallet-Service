package cz.cernilovsky.tradewalletservice.idempotency

data class CachedHttpResponse(
    val status: Int,
    val body: String,
    val contentType: String,
)
