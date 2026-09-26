package com.nuvio.tv.data.mediaserver

import androidx.annotation.StringRes
import com.nuvio.tv.R

@StringRes
fun ServerFailure.messageRes(): Int = when (this) {
    ServerFailure.AUTH_REQUIRED -> R.string.servers_failure_auth
    ServerFailure.UNREACHABLE -> R.string.servers_failure_unreachable
    ServerFailure.NOT_FOUND -> R.string.servers_failure_not_found
    ServerFailure.INCOMPLETE -> R.string.servers_failure_incomplete
    ServerFailure.FORBIDDEN -> R.string.servers_error_forbidden
    ServerFailure.UNSUPPORTED -> R.string.servers_failure_unsupported
    ServerFailure.FAILED -> R.string.servers_error_failed
}

@StringRes
fun Throwable.serverPlaybackMessageRes(): Int =
    serverFailure().takeUnless { it == ServerFailure.FAILED }?.messageRes() ?: R.string.servers_playback_failed
