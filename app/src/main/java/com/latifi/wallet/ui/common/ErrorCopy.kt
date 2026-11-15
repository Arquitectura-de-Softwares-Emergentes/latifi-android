package com.latifi.wallet.ui.common

import androidx.annotation.StringRes
import com.latifi.wallet.R
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException

/**
 * Maps a failure to the copy of the UI-SPEC "API error code to copy" table. The screens show
 * these resource strings and never the server message. [Text.title] is null for the two errors
 * that have one sentence only.
 */
object ErrorCopy {
    data class Text(@StringRes val title: Int?, @StringRes val body: Int)

    /** The kind for an error. Anything the table does not name reads as a server problem. */
    fun kindOf(error: Throwable?): ErrorKind =
        when ((error as? AppException)?.error) {
            AppError.Offline -> ErrorKind.Offline
            is AppError.RateLimited -> ErrorKind.RateLimited
            AppError.NotFound -> ErrorKind.NotFound
            else -> ErrorKind.Server
        }

    fun textOf(kind: ErrorKind): Text =
        when (kind) {
            ErrorKind.Offline -> Text(R.string.err_offline_title, R.string.err_offline_body)
            ErrorKind.Server -> Text(R.string.err_server_title, R.string.err_server_body)
            ErrorKind.RateLimited -> Text(null, R.string.err_rate_limit)
            ErrorKind.NotFound -> Text(null, R.string.err_not_found)
        }
}
