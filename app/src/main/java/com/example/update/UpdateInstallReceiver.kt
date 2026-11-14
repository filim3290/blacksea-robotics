package com.example.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
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

        fun buildUserFriendlyMessage(status: Int, rawMessage: String): String {
            val lower = rawMessage.lowercase()
            return when (status) {
                PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                    when {
                        lower.contains("signatures") || lower.contains("update_incompatible") || lower.contains("inconsistent certificates") ->
                            "APK imza sertifikası telefondaki kurulu uygulamayla eşleşmiyor (INSTALL_FAILED_UPDATE_INCOMPATIBLE). Yeni APK mutlaka aynı kalıcı keystore ile imzalanmalıdır."
                        lower.contains("downgrade") || lower.contains("version_downgrade") ->
                            "Sürüm düşürme engellendi (INSTALL_FAILED_VERSION_DOWNGRADE). İndirilen APK'nın versionCode değeri telefondaki kurulu sürümden büyük olmalıdır."
                        else ->
                            "Mevcut uygulamayla paket/imza çakışması oluştu (STATUS_FAILURE_CONFLICT). Kurulu uygulama ile yeni APK'nın imza anahtarı veya paket yapılandırması uyuşmuyor."
                    }
                }
                PackageInstaller.STATUS_FAILURE_INVALID ->
                    "İndirilen APK dosyası bozuk veya geçersiz paket yapısına sahip (STATUS_FAILURE_INVALID)."
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                    if (lower.contains("downgrade") || lower.contains("version")) {
                        "Yeni APK'nın sürüm kodu (versionCode) kurulu sürümden düşük veya uyumsuz (STATUS_FAILURE_INCOMPATIBLE)."
                    } else {
                        "APK cihazınız veya Android sürümünüzle uyumlu değil (STATUS_FAILURE_INCOMPATIBLE)."
                    }
                }
                PackageInstaller.STATUS_FAILURE_BLOCKED ->
                    "Kurulum cihaz politikası veya güvenlik ayarları tarafından engellendi (STATUS_FAILURE_BLOCKED). 'Bilinmeyen uygulamaları yükle' iznini kontrol edin."
                PackageInstaller.STATUS_FAILURE_STORAGE ->
                    "Kurulum için cihazda yeterli depolama alanı yok (STATUS_FAILURE_STORAGE)."
                PackageInstaller.STATUS_FAILURE_ABORTED ->
                    "Kurulum kullanıcı veya sistem tarafından iptal edildi (STATUS_FAILURE_ABORTED)."
                else ->
                    "Kurulum başarısız oldu (${statusNameFor(status)}).${if (rawMessage.isNotBlank()) " Detay: $rawMessage" else ""}"
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val rawMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val pkgName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME).orEmpty()
        val statusName = statusNameFor(status)

        Log.i(TAG, "PackageInstaller callback -> status=$statusName ($status), package=$pkgName, message=$rawMessage")

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
                        Log.e(TAG, "Failed to launch system install confirmation: ${e.message}", e)
                        UpdateInstallResultBus.update(
                            InstallResultState.Error(
                                statusCode = PackageInstaller.STATUS_FAILURE,
                                statusName = "CONFIRM_INTENT_ERROR",
                                rawMessage = e.message.orEmpty(),
                                userMessage = "Android kurulum onay penceresi açılamadı: ${e.message}"
                            )
                        )
                    }
                } else {
                    Log.e(TAG, "STATUS_PENDING_USER_ACTION received without EXTRA_INTENT")
                    UpdateInstallResultBus.update(
                        InstallResultState.Error(
                            statusCode = status,
                            statusName = statusName,
                            rawMessage = "EXTRA_INTENT missing",
                            userMessage = "Sistem kurulum onay ekranı bulunamadı."
                        )
                    )
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "APK installation succeeded for $pkgName")
                UpdateInstallResultBus.update(InstallResultState.Success)
            }

            else -> {
                Log.e(TAG, "APK installation failed: status=$statusName ($status), message=$rawMessage")
                val userMessage = buildUserFriendlyMessage(status, rawMessage)
                UpdateInstallResultBus.update(
                    InstallResultState.Error(
                        statusCode = status,
                        statusName = statusName,
                        rawMessage = rawMessage,
                        userMessage = userMessage
                    )
                )
            }
        }
    }
}
