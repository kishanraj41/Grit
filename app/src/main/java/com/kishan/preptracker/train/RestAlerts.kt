package com.kishan.preptracker.train

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.R
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object RestAlerts {
    const val CHANNEL = "grit-rest"
    private const val ID = 41

    fun ensureChannel(ctx: Context) {
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Counts down between sets and pings when rest is over."
                },
            )
        }
    }

    fun tick(ctx: Context, left: Int, total: Int) {
        ensureChannel(ctx)
        val text = if (left > 0) "%d:%02d left".format(left / 60, left % 60) else "Rest over — next set"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_popup_reminder)
            .setContentTitle(if (left > 0) "Grit rest" else "Grit — go")
            .setContentText(text)
            .setOnlyAlertOnce(left > 0)
            .setOngoing(left > 0)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setProgress(total.coerceAtLeast(1), (total - left).coerceAtLeast(0), false)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID, n) }
    }

    fun clear(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID)
    }
}
