package cz.cernilovsky.tradewalletservice.common.api

enum class ApiErrorCode(val status: Int, val message: String) {
    IDEMPOTENCY_IN_PROGRESS(409, "Idempotent operation is already running."),
    MISSING_HEADER(400, "Idempotency header is missing.");

    fun createApiError(message: String, details: List<String> = emptyList()): ApiError {
        return ApiError(status, name, message, details)
    }
}
