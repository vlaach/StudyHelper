package ru.vlaach.studyhelper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

object NotificationHelper {
    const val CHANNEL_PERSISTENT = "channel_persistent_status"
    const val CHANNEL_ALERTS = "channel_lesson_alerts"

    const val NOTIFICATION_ID_PERSISTENT = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val persistentChannel = NotificationChannel(
                CHANNEL_PERSISTENT,
                "Study Helper Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows current or next lesson"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Lesson Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when lessons start or end"
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(listOf(persistentChannel, alertChannel))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, ScheduleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}