package com.example.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.example.util.AppMessages
import com.example.util.logDebug
import com.example.util.logDebugError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class InstallResultState {
    object Idle : InstallResultState()
    object Installing : InstallResultState()
    object PendingUserAction : InstallResultState()
    object Success : InstallResultState()
    data class Error(
        val statusCode: Int,
        val statusName: String,
        val rawMessage: String,
        val userMessage: String
    ) : InstallResultState()
}

object UpdateInstallResultBus {
    private val _state = MutableStateFlow<InstallResultState>(InstallResultState.Idle)
    val state: StateFlow<InstallResultState> = _state.asStateFlow()

    fun update(newState: InstallResultState) {
        _state.value = newState
    }

    fun reset() {
        _state.value = InstallResultState.Idle
    }
}

class UpdateInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AppUpdateInstaller"

        fun statusNameFor(status: Int): String = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "STATUS_PENDING_USER_ACTION"
            PackageInstaller.STATUS_SUCCESS -> "STATUS_SUCCESS"
            PackageInstaller.STATUS_FAILURE -> "STATUS_FAILURE"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "STATUS_FAILURE_BLOCKED"
            PackageInstaller.STATUS_FAILURE_ABORTED -> "STATUS_FAILURE_ABORTED"
            PackageInstaller.STATUS_FAILURE_INVALID -> "STATUS_FAILURE_INVALID"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "STATUS_FAILURE_CONFLICT"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "STATUS_FAILURE_STORAGE"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "STATUS_FAILURE_INCOMPATIBLE"
            else -> "STATUS_UNKNOWN($status)"
        }

        fun buildUserFriendlyMessage(status: Int, @Suppress("UNUSED_PARAMETER") rawMessage: String): String {
            return when (status) {
                PackageInstaller.STATUS_FAILURE_BLOCKED ->
                    "Kurulum cihaz güvenlik ayarları tarafından engellendi. Yükleme iznini kontrol edip tekrar deneyin."
                PackageInstaller.STATUS_FAILURE_STORAGE ->
                    "Kurulum için cihazda yeterli depolama alanı yok. Yer açıp tekrar deneyin."
                PackageInstaller.STATUS_FAILURE_ABORTED ->
                    "Güncelleme kurulumu iptal edildi."
                else ->
                    AppMessages.UPDATE_INSTALL_ERROR
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val rawMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val pkgName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME).orEmpty()
        val statusName = statusNameFor(status)

        logDebug(TAG, "PackageInstaller callback -> status=$statusName ($status), package=$pkgName, message=$rawMessage")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmIntent != null) {
                    try {
                        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        UpdateInstallResultBus.update(InstallResultState.PendingUserAction)
                        context.startActivity(confirmIntent)
                    } catch (e: Exception) {
                        logDebugError(TAG, "Failed to launch system install confirmation", e)
                        UpdateInstallResultBus.update(
                            InstallResultState.Error(
                                statusCode = PackageInstaller.STATUS_FAILURE,
                                statusName = "CONFIRM_INTENT_ERROR",
                                rawMessage = "",
                                userMessage = AppMessages.UPDATE_INSTALL_ERROR
                            )
                        )
                    }
                } else {
                    logDebugError(TAG, "STATUS_PENDING_USER_ACTION received without EXTRA_INTENT")
                    UpdateInstallResultBus.update(
                        InstallResultState.Error(
                            statusCode = status,
                            statusName = statusName,
                            rawMessage = "",
                            userMessage = AppMessages.UPDATE_INSTALL_ERROR
                        )
                    )
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                logDebug(TAG, "APK installation succeeded for $pkgName")
                UpdateInstallResultBus.update(InstallResultState.Success)
            }

            else -> {
                logDebugError(TAG, "APK installation failed: status=$statusName ($status), message=$rawMessage")
                val userMessage = buildUserFriendlyMessage(status, rawMessage)
                UpdateInstallResultBus.update(
                    InstallResultState.Error(
                        statusCode = status,
                        statusName = statusName,
                        rawMessage = "",
                        userMessage = userMessage
                    )
                )
            }
        }
    }
}
