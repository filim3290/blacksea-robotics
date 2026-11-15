package com.example.util

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object AppMessages {
    const val GENERIC_ERROR = "Bir sorun oluştu. Lütfen tekrar deneyin."
    const val CONNECTION_ERROR = "Bağlantı sırasında bir sorun oluştu. Lütfen tekrar deneyin."
    const val PERMISSION_ERROR = "Bu işlem için yetkiniz bulunmuyor."
    const val GOOGLE_SIGN_IN_ERROR = "Google ile giriş yapılamadı. Lütfen tekrar deneyin."
    const val UPDATE_CHECK_ERROR = "Güncellemeler kontrol edilemedi. İnternet bağlantınızı kontrol edip tekrar deneyin."
    const val UPDATE_DOWNLOAD_ERROR = "Güncelleme indirilemedi. Lütfen tekrar deneyin."
    const val UPDATE_INSTALL_ERROR = "Güncelleme kurulamadı. Lütfen tekrar deneyin."
}

fun logDebug(tag: String, message: String) {
    if (BuildConfig.DEBUG) {
        Log.d(tag, message)
    }
}

fun logDebugWarn(tag: String, message: String, throwable: Throwable? = null) {
    if (BuildConfig.DEBUG) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }
}

fun logDebugError(tag: String, message: String, throwable: Throwable? = null) {
    if (BuildConfig.DEBUG) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}

/**
 * Converts any technical exception into a clean, non-technical message suitable for end users.
 * Never exposes exception class names, stack traces, HTTP codes, endpoints, Firebase/GitHub
 * internals, or raw platform error details.
 */
fun sanitizeUserErrorMessage(
    error: Throwable?,
    fallback: String = AppMessages.GENERIC_ERROR
): String {
    if (error == null) return fallback
    val causes = generateSequence(error) { it.cause }
        .filter { it !is CancellationException }
        .toList()

    val isPermissionDenied = causes.any {
        it is FirebaseFirestoreException && it.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    if (isPermissionDenied) {
        return AppMessages.PERMISSION_ERROR
    }

    val isNetworkIssue = causes.any { cause ->
        cause is UnknownHostException ||
            cause is SocketTimeoutException ||
            cause is ConnectException ||
            cause is IOException ||
            cause is FirebaseNetworkException ||
            (cause is FirebaseFirestoreException &&
                (cause.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                    cause.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED))
    }
    if (isNetworkIssue) {
        return AppMessages.CONNECTION_ERROR
    }

    return fallback
}
