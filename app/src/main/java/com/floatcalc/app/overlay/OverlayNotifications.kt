package com.floatcalc.app.overlay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.floatcalc.app.MainActivity
import com.floatcalc.app.R

/** The ongoing, low-importance notification shown while the floating calculator runs. */
object OverlayNotifications {
    const val NOTIFICATION_ID = 1001
    private const val CHANNEL_ID = "floating_calculator"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, hidden: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            MainActivity.launchIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggle = servicePendingIntent(
            context,
            if (hidden) FloatingCalculator.ACTION_SHOW else FloatingCalculator.ACTION_HIDE,
            requestCode = 1,
        )
        val close = servicePendingIntent(context, FloatingCalculator.ACTION_CLOSE, requestCode = 2)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_calculate)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(
                context.getString(if (hidden) R.string.notification_text_hidden else R.string.notification_text),
            )
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp)
            .addAction(0, context.getString(if (hidden) R.string.action_show else R.string.action_hide), toggle)
            .addAction(0, context.getString(R.string.action_close), close)
            .build()
    }

    /** Updates the posted notification; without the notification permission there is nothing to update. */
    fun update(context: Context, hidden: Boolean) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, build(context, hidden))
    }

    private fun servicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, FloatingCalculatorService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
