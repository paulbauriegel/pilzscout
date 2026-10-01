package de.pilzscout.app.pack

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Why a pack operation failed, as shown to the user. */
sealed interface PackError {
    /** No network connection (or the server could not be reached). Retrying later can help. */
    data object Offline : PackError

    /** Only Wi-Fi downloads are allowed and the current network is metered. */
    data object WifiRequired : PackError

    /** The repository has no pack this app version can read (app too old, or nothing published). */
    data object NoCompatiblePack : PackError

    /** Not enough free storage for the download plus the unpacked files. */
    data class Storage(val neededBytes: Long) : PackError

    data class Failed(val message: String) : PackError

    /** Worth retrying automatically (the worker backs off and tries again). */
    val transient: Boolean get() = this is Offline || this is Failed

    companion object {
        fun from(e: Throwable): PackError = when (e) {
            is NoCompatiblePackException -> NoCompatiblePack
            is InsufficientStorageException -> Storage(e.neededBytes)
            is UnknownHostException, is SocketTimeoutException, is java.net.ConnectException -> Offline
            else -> Failed(e.message ?: e.toString())
        }
    }
}

class NoCompatiblePackException(message: String) : IOException(message)

class InsufficientStorageException(val neededBytes: Long) : IOException("Not enough storage: $neededBytes bytes needed")

class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code for $url")
