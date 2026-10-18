package com.marcos.actividad

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { MaterialTheme { Main() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Main() {
    val ctx = LocalContext.current
    var plans by remember { mutableStateOf(Store.load(ctx)) }
    var editing by remember { mutableStateOf<Plan?>(null) }
    fun commit(l: List<Plan>) { plans = l; Store.save(ctx, l) }

    val e = editing
    if (e != null) {
        Editor(e, onCancel = { editing = null }) { saved ->
            val np = if (saved.id == 0) saved.copy(id = (plans.maxOfOrNull { it.id } ?: 0) + 1) else saved
            if (np.enabled) Scheduler.schedule(ctx, np) else Scheduler.cancel(ctx, np.id)
            commit(if (plans.any { it.id == np.id }) plans.map { if (it.id == np.id) np else it } else plans + np)
            editing = null
        }
    } else {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Actividad periódica") }) },
            floatingActionButton = {
                FloatingActionButton(onClick = { editing = Plan(0, "") }) { Text("+") }
            }
        ) { pad ->
            LazyColumn(
                Modifier.padding(pad).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Permisos(ctx) }
                if (plans.isNotEmpty()) item {
                    val anyOn = plans.any { it.enabled }
                    OutlinedButton(
                        onClick = {
                            val nm = ctx.getSystemService(NotificationManager::class.java)
                            val updated = plans.map { it.copy(enabled = !anyOn) }
                            updated.forEach {
                                if (it.enabled) Scheduler.schedule(ctx, it)
                                else { Scheduler.cancel(ctx, it.id); nm.cancel(it.id) }
                            }
                            commit(updated)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (anyOn) "Desactivar todos los recordatorios" else "Reactivar todos los recordatorios") }
                }
                items(plans, key = { it.id }) { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.label, style = MaterialTheme.typography.titleMedium)
                                Text(summary(p))
                                Text("Hechas: ${p.done}")
                                Row {
                                    TextButton(onClick = { editing = p }) { Text("Editar") }
                                    TextButton(onClick = {
                                        Scheduler.cancel(ctx, p.id)
                                        commit(plans.filter { it.id != p.id })
                                    }) { Text("Borrar") }
                                }
                            }
                            Switch(checked = p.enabled, onCheckedChange = { on ->
                                val np = p.copy(enabled = on)
                                if (on) Scheduler.schedule(ctx, np) else Scheduler.cancel(ctx, p.id)
                                commit(plans.map { if (it.id == p.id) np else it })
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Permisos(ctx: Context) {
    val am = ctx.getSystemService(AlarmManager::class.java)
    val nm = ctx.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
        OutlinedButton(onClick = {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
        }) { Text("Permitir alarmas exactas") }
    if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent())
        OutlinedButton(onClick = {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
        }) { Text("Permitir pantalla completa") }
}

@Composable
fun Editor(p: Plan, onCancel: () -> Unit, onSave: (Plan) -> Unit) {
    var label by remember { mutableStateOf(p.label) }
    var days by remember { mutableStateOf(p.days) }
    var sel by remember { mutableStateOf(0) }
    val d = days[sel]
    fun upd(f: (DayCfg) -> DayCfg) { days = days.mapIndexed { i, x -> if (i == sel) f(x) else x } }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                label, { label = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("¿Qué hay que hacer?") }
            )
            Text("Día de la semana (toca uno para configurarlo)")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DIAS.forEachIndexed { i, n ->
                    DayChip(n, i == sel, days[i].active, { sel = i }, Modifier.weight(1f))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(d.active, { v -> upd { it.copy(active = v) } })
                Spacer(Modifier.width(8.dp))
                Text("Activo este día")
            }
            if (d.active) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(d.fixed, { v -> upd { it.copy(fixed = v) } })
                    Spacer(Modifier.width(8.dp))
                    Text(if (d.fixed) "A una hora concreta" else "Cada X minutos dentro de una ventana")
                }
                if (d.fixed) {
                    TimeButton("Hora", d.fixedMin) { m -> upd { it.copy(fixedMin = m) } }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeButton("Desde", d.startMin) { m -> upd { it.copy(startMin = m) } }
                        TimeButton("Hasta", d.endMin) { m -> upd { it.copy(endMin = m) } }
                    }
                    if (d.endMin < d.startMin)
                        Text("El fin debe ser posterior al inicio", color = MaterialTheme.colorScheme.error)
                    NumField("Cada (min)", d.every) { v -> upd { it.copy(every = v) } }
                }
                NumField("Duración de la actividad (min)", d.durationMin) { v -> upd { it.copy(durationMin = v) } }
                Text("Tipo de aviso")
                Opcion("Alarma a pantalla completa", d.fullScreen) { upd { it.copy(fullScreen = true) } }
                Opcion("Notificación que abre el temporizador", !d.fullScreen) { upd { it.copy(fullScreen = false) } }
            }
            TextButton(onClick = { days = days.map { d } }) { Text("Copiar este día a todos los demás") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            Button(
                onClick = {
                    onSave(
                        p.copy(
                            label = label.ifBlank { "Actividad" },
                            days = days.map {
                                it.copy(every = it.every.coerceAtLeast(1), durationMin = it.durationMin.coerceAtLeast(1))
                            }
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("Guardar") }
        }
    }
}

@Composable
fun DayChip(text: String, selected: Boolean, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val bg = if (selected) cs.primary else if (active) cs.secondaryContainer else cs.surfaceVariant
    val fg = if (selected) cs.onPrimary else cs.onSurface
    Box(
        modifier.height(40.dp).background(bg, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = fg) }
}

@Composable
fun TimeButton(label: String, minutes: Int, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    OutlinedButton(onClick = {
        TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, minutes / 60, minutes % 60, true).show()
    }) { Text("$label ${hhmm(minutes)}") }
}

@Composable
fun NumField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { onChange(it.filter(Char::isDigit).take(4).toIntOrNull() ?: 0) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
fun Opcion(texto: String, sel: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = sel, onClick = onClick)
        Text(texto)
    }
}
