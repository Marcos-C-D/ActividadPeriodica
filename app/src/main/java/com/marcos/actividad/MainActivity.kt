package com.marcos.actividad

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.app.AlarmManager

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { MaterialTheme { Main() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Main() {
        var plans by remember { mutableStateOf(Store.load(this)) }
        var adding by remember { mutableStateOf(false) }
        fun commit(l: List<Plan>) { plans = l; Store.save(this, l) }

        Scaffold(
            topBar = { TopAppBar(title = { Text("Actividad periódica") }) },
            floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Text("+") } }
        ) { pad ->
            LazyColumn(
                Modifier.padding(pad).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Permisos() }
                items(plans, key = { it.id }) { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.label, style = MaterialTheme.typography.titleMedium)
                                val cuando = if (p.fixed) "Cada día a las %02d:%02d".format(p.hour, p.minute)
                                             else "Cada ${p.everyMin} min"
                                Text("$cuando · ${p.durationMin} min · hechas: ${p.done}")
                                TextButton(onClick = {
                                    Scheduler.cancel(this@MainActivity, p.id)
                                    commit(plans.filter { it.id != p.id })
                                }) { Text("Borrar") }
                            }
                            Switch(checked = p.enabled, onCheckedChange = { on ->
                                val np = p.copy(enabled = on)
                                if (on) Scheduler.schedule(this@MainActivity, np) else Scheduler.cancel(this@MainActivity, p.id)
                                commit(plans.map { if (it.id == p.id) np else it })
                            })
                        }
                    }
                }
            }
        }

        if (adding) AddDialog(onDismiss = { adding = false }) { p ->
            val np = p.copy(id = (plans.maxOfOrNull { it.id } ?: 0) + 1)
            Scheduler.schedule(this, np)
            commit(plans + np)
            adding = false
        }
    }

    @Composable
    private fun Permisos() {
        val am = getSystemService(AlarmManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
            OutlinedButton(onClick = {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }) { Text("Permitir alarmas exactas") }
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent())
            OutlinedButton(onClick = {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }) { Text("Permitir pantalla completa") }
    }

    @Composable
    private fun AddDialog(onDismiss: () -> Unit, onSave: (Plan) -> Unit) {
        var label by remember { mutableStateOf("") }
        var fixed by remember { mutableStateOf(false) }
        var every by remember { mutableStateOf("60") }
        var time by remember { mutableStateOf("08:00") }
        var dur by remember { mutableStateOf("10") }
        val num = KeyboardOptions(keyboardType = KeyboardType.Number)

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Nueva actividad") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text("¿Qué hay que hacer?") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(fixed, { fixed = it })
                        Spacer(Modifier.width(8.dp))
                        Text(if (fixed) "A una hora concreta (cada día)" else "Cada X minutos")
                    }
                    if (fixed) OutlinedTextField(time, { time = it }, label = { Text("Hora (HH:MM)") })
                    else OutlinedTextField(every, { every = it }, label = { Text("Cada (min)") }, keyboardOptions = num)
                    OutlinedTextField(dur, { dur = it }, label = { Text("Duración (min)") }, keyboardOptions = num)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parts = time.split(":")
                    onSave(
                        Plan(
                            0, label.ifBlank { "Actividad" }, fixed,
                            (every.toIntOrNull() ?: 60).coerceAtLeast(1),
                            (parts.getOrNull(0)?.toIntOrNull() ?: 8).coerceIn(0, 23),
                            (parts.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59),
                            (dur.toIntOrNull() ?: 10).coerceAtLeast(1)
                        )
                    )
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
        )
    }
}
