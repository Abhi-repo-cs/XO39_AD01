package org.example.supervisorx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class VisorXProtectionService : Service() {

    companion object {
        const val CHANNEL_ID = "visorx_protection_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val notification = createNotification()

        startForeground(
            NOTIFICATION_ID,
            notification
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        /*
         * VISORX ENGINE WILL RUN HERE
         *
         * Next:
         * CameraX
         *      ↓
         * BlazeFace / YuNet
         *      ↓
         * Face detection
         *      ↓
         * RiskEngine
         */

        return START_STICKY
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "VisorX Protection",
                NotificationManager.IMPORTANCE_LOW
            )

            channel.description =
                "VisorX shoulder surfing protection"

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("VisorX Protection Active")
                .setContentText(
                    "VisorX is protecting your screen"
                )
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build()

        } else {

            Notification.Builder(this)
                .setContentTitle("VisorX Protection Active")
                .setContentText(
                    "VisorX is protecting your screen"
                )
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}