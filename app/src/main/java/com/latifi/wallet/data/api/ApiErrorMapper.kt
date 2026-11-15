package com.latifi.wallet.data.api

import com.latifi.wallet.data.api.dto.ApiErrorDto
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/**
 * Turns an HTTP answer or a network failure into the [AppError] vocabulary of the UI-SPEC table
 * "API error code to copy mapping". Only the status and the `code` of the error body count. The
 * server `message` is never read into an [AppError], so a screen cannot show it.
 */
object ApiErrorMapper {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** The error for a failed HTTP answer. [errorBody] is the raw body text, if any. */
    fun map(status: Int, errorBody: String?, retryAfter: String?): AppError {
        // These two follow from the status alone, so a proxy page cannot hide them.
        if (status == HTTP_UNAUTHORIZED) return AppError.Unauthorized
        if (status == HTTP_TOO_MANY_REQUESTS) return AppError.RateLimited(parseRetryAfter(retryAfter))

        val dto = parse(errorBody) ?: return AppError.Server
        val code = dto.code
            ?: return if (status >= HTTP_SERVER_ERROR) AppError.Server else AppError.Unknown(null)
        return when (code) {
            "UNAUTHORIZED", "INVALID_CREDENTIALS" -> AppError.Unauthorized
            "TOO_MANY_REQUESTS" -> AppError.RateLimited(parseRetryAfter(retryAfter))
            "PROFILE_NOT_FOUND" -> AppError.ProfileNotFound
            "REPUTATION_NOT_FOUND" -> AppError.ReputationNotFound
            "LOAN_NOT_FOUND", "NOT_FOUND" -> AppError.NotFound
            "RATE_UNAVAILABLE" -> AppError.RateUnavailable
            "VALIDATION_FAILED", "MALFORMED_REQUEST", "INVALID_ADDRESS", "UNSUPPORTED_CURRENCY" -> AppError.Validation()
            "INTERNAL_ERROR" -> AppError.Server
            else -> when {
                status >= HTTP_SERVER_ERROR -> AppError.Server
                status == HTTP_NOT_FOUND -> AppError.NotFound
                else -> AppError.Unknown(code)
            }
        }
    }

    fun map(response: Response<*>): AppError =
        map(
            status = response.code(),
            errorBody = runCatching { response.errorBody()?.string() }.getOrNull(),
            retryAfter = response.headers()["Retry-After"],
        )

    /** IO failures, timeouts and unknown hosts are Offline. Anything else, such as an unreadable payload, is a server problem. */
    fun map(error: Throwable): AppError =
        when (error) {
            is AppException -> error.error
            is IOException -> AppError.Offline
            else -> AppError.Server
        }

    private fun parseRetryAfter(value: String?): Int? =
        value?.trim()?.toIntOrNull()?.takeIf { it >= 0 }

    private fun parse(body: String?): ApiErrorDto? {
        if (body.isNullOrBlank()) return null
        return try {
            json.decodeFromString(ApiErrorDto.serializer(), body)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVER_ERROR = 500
}

/**
 * Runs one Retrofit call and maps the answer. A 2xx with a body goes through [transform], any
 * other answer becomes `Result.failure(AppException(...))`. A cancelled coroutine is rethrown, it
 * is never an error of the app.
 */
internal suspend fun <T, R> apiResult(call: suspend () -> Response<T>, transform: (T) -> R): Result<R> =
    try {
        val response = call()
        val body = response.body()
        when {
            !response.isSuccessful -> Result.failure(AppException(ApiErrorMapper.map(response)))
            body == null -> Result.failure(AppException(AppError.Server))
            else -> Result.success(transform(body))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: AppException) {
        Result.failure(e)
    } catch (e: IOException) {
        Result.failure(AppException(ApiErrorMapper.map(e), e))
    } catch (e: SerializationException) {
        Result.failure(AppException(AppError.Server, e))
    } catch (e: IllegalArgumentException) {
        Result.failure(AppException(AppError.Server, e))
    }
