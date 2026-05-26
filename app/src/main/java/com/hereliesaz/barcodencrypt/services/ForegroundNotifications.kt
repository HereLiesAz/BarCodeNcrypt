package com.hereliesaz.barcodencrypt.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hereliesaz.barcodencrypt.R

/**
 * Shared notification plumbing for the two foreground services
 * ([MessageDetectionService] and [OverlayService]).
 *
 * Android 14+ rejects foreground starts that don't call [Service.startForeground]
 * within a few seconds and that don't declare a `foregroundServiceType`. Both services
 * declare `specialUse` in the manifest and use this helper at the top of
 * `onStartCommand` / `onServiceConnected`.
 */
internal object ForegroundNotifications {

    private const val CHANNEL_ID = "barcodencrypt_services"
    const val ID_MESSAGE_DETECTION = 1001
    const val ID_OVERLAY = 1002

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "BarCodeNcrypt background services"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    fun build(context: Context, contentText: String): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * On API 34+ services with `foregroundServiceType="specialUse"` must call the
     * type-aware overload of [Service.startForeground]. Older API levels ignore the
     * type argument.
     */
    fun start(service: Service, notificationId: Int, contentText: String) {
        val notification = build(service, contentText)
        // A background-initiated foreground start can throw
        // ForegroundServiceStartNotAllowedException on Android 12+; swallow it so the
        // service degrades to a normal (killable) service instead of crashing.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                service.startForeground(
                    notificationId,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                service.startForeground(notificationId, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed; running without foreground promotion", e)
        }
    }

    private const val TAG = "ForegroundNotifications"
}
