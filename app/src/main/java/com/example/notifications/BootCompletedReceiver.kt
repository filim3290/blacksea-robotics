package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.util.logDebugWarn
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Restarts notification background workers and the persistent foreground listener
 * when the device boots or the app updates.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context?.applicationContext ?: return
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            try {
                TeamNotificationDispatcher.ensureNotificationChannels(ctx)
                if (Firebase.auth.currentUser != null) {
                    BackgroundNotificationWorker.schedulePeriodicSync(ctx)
                    BackgroundNotificationWorker.triggerImmediateSync(ctx)
                    RealtimeNotificationService.startIfEnabled(ctx)
                }
            } catch (e: Exception) {
                logDebugWarn("BootCompletedReceiver", "Failed to resume background sync after $action", e)
            }
        }
    }
}
