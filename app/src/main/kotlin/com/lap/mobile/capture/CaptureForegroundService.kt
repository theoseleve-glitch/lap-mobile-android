package com.lap.mobile.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lap.mobile.R

/**
 * Foreground service that hosts a long-running capture session and
 * keeps it alive across backgrounding (T063). The persistent
 * notification is mandatory under Android 8+ FGS rules; we pick
 * [CaptureNotification.IMPORTANCE] (== `IMPORTANCE_LOW`) so the user
 * doesn't get pinged for the duration of a track session.
 *
 * Dispatch is hoisted into [CaptureForegroundCommand] so onStartCommand
 * stays trivial. Actual capture wiring (location stream, motion stream,
 * journal heartbeats) is layered on by T076 SessionCoordinator — this
 * task ships the host shell only.
 *
 * Live "survives backgrounding" verification needs an Android device
 * (T083 instrumented test); JVM tests cover the dispatch contract +
 * notification config constants.
 */
class CaptureForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (CaptureForegroundCommand.from(intent?.action)) {
            CaptureForegroundCommand.START -> startForeground(
                CaptureNotification.NOTIFICATION_ID,
                buildNotification(),
            )
            CaptureForegroundCommand.STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            CaptureForegroundCommand.UNKNOWN -> stopSelf()
        }
        return START_STICKY
    }

    // Started service — no binder.
    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CaptureNotification.CHANNEL_ID,
            getString(R.string.capture_notification_channel_name),
            CaptureNotification.IMPORTANCE,
        ).apply {
            lockscreenVisibility = CaptureNotification.LOCK_SCREEN_VISIBILITY
        }
        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CaptureNotification.CHANNEL_ID)
        .setContentTitle(getString(R.string.capture_notification_title))
        .setContentText(getString(R.string.capture_notification_text))
        .setSmallIcon(R.mipmap.ic_launcher)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setVisibility(CaptureNotification.LOCK_SCREEN_VISIBILITY)
        .setCategory(CaptureNotification.CATEGORY)
        .build()

    companion object {
        fun startIntent(context: Context): Intent = Intent(context, CaptureForegroundService::class.java)
            .setAction(CaptureForegroundCommand.ACTION_START)

        fun stopIntent(context: Context): Intent = Intent(context, CaptureForegroundService::class.java)
            .setAction(CaptureForegroundCommand.ACTION_STOP)
    }
}
