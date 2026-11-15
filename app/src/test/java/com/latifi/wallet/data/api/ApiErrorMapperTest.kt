package com.latifi.wallet.data.api

import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ApiErrorMapperTest {
    private fun body(code: String) = """{"status":0,"code":"$code","message":"text from the server"}"""

    private fun map(status: Int, code: String, retryAfter: String? = null) =
        ApiErrorMapper.map(status, body(code), retryAfter)

    @Test
    fun aUnauthorizedStatusMapsToUnauthorizedWhateverTheBody() {
        assertEquals(AppError.Unauthorized, map(401, "UNAUTHORIZED"))
        assertEquals(AppError.Unauthorized, map(401, "INVALID_CREDENTIALS"))
        assertEquals(AppError.Unauthorized, ApiErrorMapper.map(401, "<html>proxy</html>", null))
        assertEquals(AppError.Unauthorized, ApiErrorMapper.map(401, null, null))
    }

    @Test
    fun aTooManyRequestsStatusCarriesRetryAfterInSeconds() {
        assertEquals(AppError.RateLimited(30), map(429, "TOO_MANY_REQUESTS", "30"))
        assertEquals(AppError.RateLimited(null), map(429, "TOO_MANY_REQUESTS", null))
        assertEquals(AppError.RateLimited(null), map(429, "TOO_MANY_REQUESTS", "Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals(AppError.RateLimited(null), map(429, "TOO_MANY_REQUESTS", "-5"))
    }

    @Test
    fun theNotFoundCodesMapToTheirOwnErrors() {
        assertEquals(AppError.ProfileNotFound, map(404, "PROFILE_NOT_FOUND"))
        assertEquals(AppError.ReputationNotFound, map(404, "REPUTATION_NOT_FOUND"))
        assertEquals(AppError.NotFound, map(404, "LOAN_NOT_FOUND"))
        assertEquals(AppError.NotFound, map(404, "NOT_FOUND"))
        assertEquals(AppError.NotFound, map(404, "SOMETHING_NEW"))
    }

    @Test
    fun aMissingRateIsRateUnavailable() {
        assertEquals(AppError.RateUnavailable, map(503, "RATE_UNAVAILABLE"))
    }

    @Test
    fun theBadRequestCodesMapToValidation() {
        for (code in listOf("VALIDATION_FAILED", "MALFORMED_REQUEST", "INVALID_ADDRESS", "UNSUPPORTED_CURRENCY")) {
            assertEquals(code, AppError.Validation(), map(400, code))
        }
    }

    @Test
    fun serverFailuresMapToServer() {
        assertEquals(AppError.Server, map(500, "INTERNAL_ERROR"))
        assertEquals(AppError.Server, map(503, "SOMETHING_NEW"))
    }

    @Test
    fun anOwnershipFailureKeepsItsCodeForLogsOnly() {
        assertEquals(AppError.Unknown("FORBIDDEN_NOT_OWNER"), map(403, "FORBIDDEN_NOT_OWNER"))
    }

    @Test
    fun aBodyThatIsNotAnApiErrorBecomesServer() {
        assertEquals(AppError.Server, ApiErrorMapper.map(502, "<html>Bad Gateway</html>", null))
        assertEquals(AppError.Server, ApiErrorMapper.map(502, "", null))
        assertEquals(AppError.Server, ApiErrorMapper.map(400, null, null))
    }

    @Test
    fun ioFailuresAreOffline() {
        assertEquals(AppError.Offline, ApiErrorMapper.map(IOException("no route")))
        assertEquals(AppError.Offline, ApiErrorMapper.map(SocketTimeoutException("timeout")))
        assertEquals(AppError.Offline, ApiErrorMapper.map(UnknownHostException("host")))
    }

    @Test
    fun aRefusedConnectionAndAReadTimeoutMapToOfflineThroughTheRealStack() = runBlocking {
        val rig = ApiTestRig()
        val repository = ApiLoanRepository(rig.api)

        // A read timeout: the server accepts the request and never answers.
        rig.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val slow = ApiClientFactory.api(
            ApiClientFactory.retrofit(
                baseUrl = rig.server.url("/api/v1/").toString(),
                client = ApiClientFactory.okHttpClient(false, emptyList(), null).newBuilder()
                    .readTimeout(200, java.util.concurrent.TimeUnit.MILLISECONDS).build(),
            ),
        )
        val timedOut = ApiLoanRepository(slow).get(java.math.BigInteger.ONE).exceptionOrNull() as AppException
        assertEquals(AppError.Offline, timedOut.error)

        // A refused connection: the server is gone.
        rig.close()
        val refused = repository.get(java.math.BigInteger.ONE).exceptionOrNull() as AppException
        assertEquals(AppError.Offline, refused.error)
    }

    @Test
    fun anUnreadableSuccessBodyBecomesServer() = runBlocking {
        val rig = ApiTestRig()
        rig.enqueueJson("""{"items": "not a list"}""")

        val failure = ApiLoanRepository(rig.api).list(com.latifi.wallet.domain.loan.LoanQuery()).exceptionOrNull() as AppException

        assertEquals(AppError.Server, failure.error)
        rig.close()
    }
}
