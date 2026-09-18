package cz.cernilovsky.tradewalletservice.common.web

import cz.cernilovsky.tradewalletservice.common.api.ApiError
import cz.cernilovsky.tradewalletservice.common.exception.BadRequestException
import cz.cernilovsky.tradewalletservice.common.exception.InsufficientFundsException
import cz.cernilovsky.tradewalletservice.common.exception.NotImplementedYetException
import cz.cernilovsky.tradewalletservice.common.exception.ResourceNotFoundException
import jakarta.validation.ConstraintViolationException
import org.hibernate.annotations.OptimisticLocking
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotImplementedYetException::class)
    fun handleNotImplemented(ex: NotImplementedYetException): ResponseEntity<ApiError> =
        error(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", ex.message ?: "Not implemented")

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException): ResponseEntity<ApiError> =
        error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.message ?: "Not found")

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException): ResponseEntity<ApiError> =
        error(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds")

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.message ?: "Bad request")

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "MISSING_HEADER", "Required header '${ex.headerName}' is missing")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val details = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ApiError> {
        val details = ex.constraintViolations.map { "${it.propertyPath}: ${it.message}" }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details)
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLockingException(ex: ObjectOptimisticLockingFailureException): ResponseEntity<ApiError> {
        return error(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK", "Reload the order and retry with the current version")
    }

    /**
     * TODO(learning) Phase 2 — Optimistic locking HTTP 409:
     *
     * 1. Import `org.springframework.orm.ObjectOptimisticLockingFailureException`
     *    (or `org.springframework.dao.OptimisticLockingFailureException`).
     * 2. Add `@ExceptionHandler` for that type.
     * 3. Return HTTP 409 Conflict with code `OPTIMISTIC_LOCK` and a message telling the
     *    client to reload the order and retry with the current `version`.
     * 4. Do **not** catch it inside `OrderService.update` — let it bubble to this advice.
     */
    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
        details: List<String> = emptyList(),
    ): ResponseEntity<ApiError> =
        ResponseEntity.status(status).body(ApiError(status.value(), code, message, details))
}
