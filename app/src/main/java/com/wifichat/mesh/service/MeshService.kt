package com.wifichat.mesh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wifichat.mesh.MeshApplication
import com.wifichat.mesh.R
import com.wifichat.mesh.ui.MainActivity
import com.wifichat.mesh.util.MeshLog

/**
 * Foreground service that keeps discovery and the mesh alive while the app is
 * backgrounded.
 *
 * Honesty note (see docs/KNOWN_LIMITATIONS.md): on Android Go and under
 * aggressive OEM battery management, the OS may still suspend or kill this
 * service. The app is designed to stay useful when that happens — the message
 * queue is persisted and resumes when the service restarts.
 */
class MeshService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        val container = (application as MeshApplication).container
        try {
            container.transport.start()
            container.discovery.start()
            container.router.start()
        } catch (t: Throwable) {
            MeshLog.e("Failed to start mesh components", t)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        val container = (application as MeshApplication).container
        runCatching { container.router.stop() }
        runCatching { container.discovery.stop() }
        runCatching { container.transport.stop() }
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_mesh)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = getString(R.string.notif_channel_desc) }
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "mesh_service"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, MeshService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeshService::class.java))
        }
    }
}
