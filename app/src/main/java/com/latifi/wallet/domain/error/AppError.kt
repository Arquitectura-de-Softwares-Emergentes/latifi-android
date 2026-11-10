package com.latifi.wallet.domain.error

/** Everything that can go wrong in the app, in the vocabulary the screens can explain. */
sealed interface AppError {
    /** No network or the server cannot be reached. */
    data object Offline : AppError

    /** The server answered with a failure (5xx, INTERNAL_ERROR) or an unreadable body. */
    data object Server : AppError

    /** HTTP 429. [retryAfterSeconds] comes from `Retry-After` when present. */
    data class RateLimited(val retryAfterSeconds: Int?) : AppError

    /** NOT_FOUND or LOAN_NOT_FOUND. */
    data object NotFound : AppError

    /** UNAUTHORIZED or INVALID_CREDENTIALS. */
    data object Unauthorized : AppError

    data object ProfileNotFound : AppError

    data object ReputationNotFound : AppError

    /** The exchange-rate provider is down and nothing is cached. */
    data object RateUnavailable : AppError

    /** VALIDATION_FAILED or MALFORMED_REQUEST. [field] names the form field when the API did. */
    data class Validation(val field: String? = null) : AppError

    /** An error code the app does not know. [code] is kept for logs only. */
    data class Unknown(val code: String? = null) : AppError
}

/** Carries an [AppError] through `Result.failure`. */
class AppException(val error: AppError, cause: Throwable? = null) : Exception(error.toString(), cause)
