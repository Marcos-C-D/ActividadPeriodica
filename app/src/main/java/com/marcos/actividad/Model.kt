package com.marcos.actividad

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Plan(
    val id: Int,
    val label: String,
    val fixed: Boolean,   // false = cada X minutos, true = a una hora concreta cada día
    val everyMin: Int,
    val hour: Int,
    val minute: Int,
    val durationMin: Int,
    val enabled: Boolean = true,
    val done: Int = 0
)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("plans", Context.MODE_PRIVATE)

    fun load(c: Context): List<Plan> {
        val a = JSONArray(prefs(c).getString("list", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Plan(
                o.getInt("id"), o.getString("label"), o.getBoolean("fixed"),
                o.getInt("everyMin"), o.getInt("hour"), o.getInt("minute"),
                o.getInt("durationMin"), o.getBoolean("enabled"), o.optInt("done")
            )
        }
    }

    fun save(c: Context, list: List<Plan>) {
        val a = JSONArray()
        list.forEach { p ->
            a.put(
                JSONObject().put("id", p.id).put("label", p.label).put("fixed", p.fixed)
                    .put("everyMin", p.everyMin).put("hour", p.hour).put("minute", p.minute)
                    .put("durationMin", p.durationMin).put("enabled", p.enabled).put("done", p.done)
            )
        }
        prefs(c).edit().putString("list", a.toString()).apply()
    }
}

object Scheduler {
    private fun pi(c: Context, id: Int) = PendingIntent.getBroadcast(
        c, id, Intent(c, AlarmReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun nextTime(p: Plan): Long {
        val now = System.currentTimeMillis()
        if (!p.fixed) return now + p.everyMin * 60_000L
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, p.hour); set(Calendar.MINUTE, p.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun schedule(c: Context, p: Plan) {
        val am = c.getSystemService(AlarmManager::class.java)
        val at = nextTime(p)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, p.id))
        else
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, p.id))
    }

    fun cancel(c: Context, id: Int) = c.getSystemService(AlarmManager::class.java).cancel(pi(c, id))

    fun rescheduleAll(c: Context) = Store.load(c).filter { it.enabled }.forEach { schedule(c, it) }
}
