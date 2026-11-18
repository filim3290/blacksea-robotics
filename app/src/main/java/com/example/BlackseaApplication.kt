package com.example

import android.app.Application
import com.example.notifications.BackgroundNotificationWorker
import com.example.notifications.RealtimeNotificationService
import com.example.notifications.TeamNotificationDispatcher
import com.example.util.logDebugWarn
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class BlackseaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            TeamNotificationDispatcher.ensureNotificationChannels(this)
            TeamNotificationDispatcher.cleanupLegacyRealtimeSyncArtifacts(this)
            RealtimeNotificationService.registerNetworkObserver(this)
            if (Firebase.auth.currentUser != null) {
                RealtimeNotificationService.startIfEnabled(this)
                BackgroundNotificationWorker.schedulePeriodicSync(this)
            }
        } catch (e: Exception) {
            logDebugWarn("BlackseaApplication", "Startup notification initialization failed", e)
        }
    }
}
