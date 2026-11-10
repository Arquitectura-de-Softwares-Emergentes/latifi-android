package com.latifi.wallet.ui.common

/** The failures a data screen can explain to a person. [ErrorCopy] gives each one its words. */
enum class ErrorKind { Offline, Server, RateLimited, NotFound }

/** What a screen knows about data it is loading: nothing yet, the data, or why it failed. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>

    data class Success<T>(val data: T) : Loadable<T>

    data class Error(val kind: ErrorKind) : Loadable<Nothing>
}
