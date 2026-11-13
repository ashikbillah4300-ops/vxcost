package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class BroadcastForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "voxcast_broadcast_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.action.START_BROADCAST"
        const val ACTION_STOP = "com.example.action.STOP_BROADCAST"
        const val ACTION_STOP_FROM_NOTIF = "com.example.action.STOP_FROM_NOTIF"
        const val EXTRA_HAS_MIC = "extra_has_mic"

        @Volatile
        var onStopBroadcastRequested: (() -> Unit)? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_FROM_NOTIF -> {
                onStopBroadcastRequested?.invoke()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val hasMic = intent.getBooleanExtra(EXTRA_HAS_MIC, true)
                createNotificationChannel()
                val notification = buildNotification(hasMic)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasMic) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    } else 0
                    if (fgsType != 0) {
                        startForeground(NOTIFICATION_ID, notification, fgsType)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Broadcast",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps live megaphone and audio broadcast active"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(hasMic: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BroadcastForegroundService::class.java).apply {
                action = ACTION_STOP_FROM_NOTIF
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoxCast Live")
            .setContentText(
                if (hasMic) "Megaphone microphone broadcast is active"
                else "Broadcasting audio clip"
            )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }
}
