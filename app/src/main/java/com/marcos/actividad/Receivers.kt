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

const val CH_ALARM = "alarma_actividad"
const val CH_AVISO = "aviso_actividad"

fun ensureChannels(c: Context) {
    val nm = c.getSystemService(NotificationManager::class.java)
    if (nm.getNotificationChannel(CH_ALARM) == null) {
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "Alarmas a pantalla completa", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                )
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }
    if (nm.getNotificationChannel(CH_AVISO) == null) {
        nm.createNotificationChannel(
            NotificationChannel(CH_AVISO, "Avisos con temporizador", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        val idx = i.getIntExtra("idx", -1)
        val p = Store.load(c).firstOrNull { it.id == id && it.enabled } ?: return
        Scheduler.schedule(c, p) // programa el siguiente aviso
        val d = p.days.getOrNull(idx) ?: return
        ensureChannels(c)

        val open = PendingIntent.getActivity(
            c, id,
            Intent(c, AlarmActivity::class.java)
                .putExtra("id", id).putExtra("label", p.label).putExtra("dur", d.durationMin)
                .putExtra("auto", !d.fullScreen) // notificación: al tocarla arranca el temporizador
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val b = NotificationCompat.Builder(c, if (d.fullScreen) CH_ALARM else CH_AVISO)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(p.label)
            .setContentIntent(open)
            .setAutoCancel(true)
        if (d.fullScreen) {
            b.setContentText("Toca para empezar (${d.durationMin} min)")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(open, true)
        } else {
            b.setContentText("Toca para abrir el temporizador (${d.durationMin} min)")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
        }
        val n = b.build()
        if (d.fullScreen) n.flags = n.flags or Notification.FLAG_INSISTENT // suena en bucle hasta abrirla
        c.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Scheduler.rescheduleAll(c)
}
