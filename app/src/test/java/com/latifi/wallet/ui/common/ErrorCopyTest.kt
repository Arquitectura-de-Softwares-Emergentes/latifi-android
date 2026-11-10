package com.latifi.wallet.ui.common

import com.latifi.wallet.R
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class ErrorCopyTest {
    private fun kind(error: AppError) = ErrorCopy.kindOf(AppException(error))

    @Test
    fun eachKnownErrorMapsToItsKind() {
        assertEquals(ErrorKind.Offline, kind(AppError.Offline))
        assertEquals(ErrorKind.Server, kind(AppError.Server))
        assertEquals(ErrorKind.RateLimited, kind(AppError.RateLimited(retryAfterSeconds = 30)))
        assertEquals(ErrorKind.RateLimited, kind(AppError.RateLimited(retryAfterSeconds = null)))
        assertEquals(ErrorKind.NotFound, kind(AppError.NotFound))
    }

    @Test
    fun everythingElseReadsAsAServerProblem() {
        assertEquals(ErrorKind.Server, kind(AppError.Unknown("X")))
        assertEquals(ErrorKind.Server, kind(AppError.Unauthorized))
        assertEquals(ErrorKind.Server, kind(AppError.RateUnavailable))
        assertEquals(ErrorKind.Server, ErrorCopy.kindOf(IOException("boom")))
        assertEquals(ErrorKind.Server, ErrorCopy.kindOf(null))
    }

    @Test
    fun theCopyIsTheStringPairOfTheUiSpec() {
        assertEquals(ErrorCopy.Text(R.string.err_offline_title, R.string.err_offline_body), ErrorCopy.textOf(ErrorKind.Offline))
        assertEquals(ErrorCopy.Text(R.string.err_server_title, R.string.err_server_body), ErrorCopy.textOf(ErrorKind.Server))
    }

    @Test
    fun rateLimitAndNotFoundHaveOneSentenceAndNoTitle() {
        assertNull(ErrorCopy.textOf(ErrorKind.RateLimited).title)
        assertEquals(R.string.err_rate_limit, ErrorCopy.textOf(ErrorKind.RateLimited).body)
        assertNull(ErrorCopy.textOf(ErrorKind.NotFound).title)
        assertEquals(R.string.err_not_found, ErrorCopy.textOf(ErrorKind.NotFound).body)
    }
}
