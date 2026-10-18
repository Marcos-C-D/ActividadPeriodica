package com.marcos.actividad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat

const val CHANNEL = "alarma_actividad"

fun ensureChannel(c: Context) {
    val nm = c.getSystemService(NotificationManager::class.java)
    if (nm.getNotificationChannel(CHANNEL) != null) return
    val ch = NotificationChannel(CHANNEL, "Alarmas de actividad", NotificationManager.IMPORTANCE_HIGH).apply {
        setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        )
        enableVibration(true)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    }
    nm.createNotificationChannel(ch)
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        val p = Store.load(c).firstOrNull { it.id == id && it.enabled } ?: return
        Scheduler.schedule(c, p) // programa la siguiente
        ensureChannel(c)
        val open = PendingIntent.getActivity(
            c, id,
            Intent(c, AlarmActivity::class.java).putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(p.label)
            .setContentText("Toca para empezar (${p.durationMin} min)")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT // suena en bucle hasta que la abras
        c.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Scheduler.rescheduleAll(c)
}
