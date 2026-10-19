package com.marcos.actividad

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Configuración de un día de la semana. */
data class DayCfg(
    val active: Boolean = true,
    val fixed: Boolean = false,      // true = a una hora concreta; false = cada X min dentro de la ventana
    val startMin: Int = 9 * 60,      // inicio de la ventana (minutos desde medianoche)
    val endMin: Int = 21 * 60,       // fin de la ventana
    val every: Int = 60,             // cada cuántos minutos avisa dentro de la ventana
    val fixedMin: Int = 8 * 60,      // hora concreta
    val durationMin: Int = 10,       // duración de la actividad
    val fullScreen: Boolean = true   // true = alarma a pantalla completa; false = notificación que abre el temporizador
)

data class Plan(
    val id: Int,
    val label: String,
    val enabled: Boolean = true,
    val done: Int = 0,
    val days: List<DayCfg> = List(7) { DayCfg() },   // 0 = lunes ... 6 = domingo
    val notes: String = ""
)

val DIAS = listOf("L", "M", "X", "J", "V", "S", "D")

fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

fun summary(p: Plan): String {
    val act = p.days.withIndex().filter { it.value.active }
    if (act.isEmpty()) return "Ningún día activo"
    val dias = act.joinToString(" ") { DIAS[it.index] }
    val cfgs = act.map { it.value }.distinct()
    val c = cfgs.first()
    val det = when {
        cfgs.size > 1 -> "horarios distintos"
        c.fixed -> "a las ${hhmm(c.fixedMin)}"
        else -> "${hhmm(c.startMin)}–${hhmm(c.endMin)} cada ${c.every} min"
    }
    return "$dias · $det"
}

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("plans", Context.MODE_PRIVATE)

    fun load(c: Context): List<Plan> {
        val a = JSONArray(prefs(c).getString("list2", "[]"))
        return (0 until a.length()).map { n ->
            val o = a.getJSONObject(n)
            val d = o.getJSONArray("days")
            Plan(
                id = o.getInt("id"), label = o.getString("label"), enabled = o.getBoolean("enabled"),
                done = o.optInt("done"), notes = o.optString("notes", ""),
                days = (0 until 7).map { i ->
                    val x = d.getJSONObject(i)
                    DayCfg(
                        x.getBoolean("active"), x.getBoolean("fixed"), x.getInt("startMin"), x.getInt("endMin"),
                        x.getInt("every"), x.getInt("fixedMin"), x.getInt("durationMin"), x.getBoolean("fullScreen")
                    )
                }
            )
        }
    }

    fun save(c: Context, list: List<Plan>) {
        val a = JSONArray()
        list.forEach { p ->
            val d = JSONArray()
            p.days.forEach { x ->
                d.put(
                    JSONObject().put("active", x.active).put("fixed", x.fixed)
                        .put("startMin", x.startMin).put("endMin", x.endMin).put("every", x.every)
                        .put("fixedMin", x.fixedMin).put("durationMin", x.durationMin)
                        .put("fullScreen", x.fullScreen)
                )
            }
            a.put(
                JSONObject().put("id", p.id).put("label", p.label).put("enabled", p.enabled)
                    .put("done", p.done).put("notes", p.notes).put("days", d)
            )
        }
        prefs(c).edit().putString("list2", a.toString()).apply()
    }
}

object Scheduler {
    private fun pi(c: Context, id: Int, idx: Int) = PendingIntent.getBroadcast(
        c, id, Intent(c, AlarmReceiver::class.java).putExtra("id", id).putExtra("idx", idx),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Próximo aviso (instante, índice del día 0=lunes..6=domingo) o null si no hay ningún día activo. */
    fun next(p: Plan): Pair<Long, Int>? {
        val now = System.currentTimeMillis()
        for (ahead in 0..7) {
            val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, ahead) }
            val idx = (day.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val d = p.days[idx]
            if (!d.active) continue
            val minutes: Iterable<Int> =
                if (d.fixed) listOf(d.fixedMin) else d.startMin..d.endMin step d.every.coerceAtLeast(1)
            for (m in minutes) {
                val t = (day.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                if (t > now) return t to idx
            }
        }
        return null
    }

    fun schedule(c: Context, p: Plan) {
        cancel(c, p.id)
        val (at, idx) = next(p) ?: return
        val am = c.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, p.id, idx))
        else
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, p.id, idx))
    }

    fun cancel(c: Context, id: Int) = c.getSystemService(AlarmManager::class.java).cancel(pi(c, id, 0))

    fun rescheduleAll(c: Context) = Store.load(c).filter { it.enabled }.forEach { schedule(c, it) }
}
