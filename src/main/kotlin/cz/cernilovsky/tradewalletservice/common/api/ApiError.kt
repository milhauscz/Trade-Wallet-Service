package cz.cernilovsky.tradewalletservice.common.api

data class ApiError(
    val status: Int,
    val code: String,
    val message: String,
    val details: List<String> = emptyList(),
)
