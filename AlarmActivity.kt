package com.marcos.actividad

import android.app.NotificationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class AlarmActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getIntExtra("id", -1)
        val plan = Store.load(this).firstOrNull { it.id == id } ?: run { finish(); return }
        setContent { MaterialTheme { Screen(plan) } }
    }

    private fun stopRinging(id: Int) =
        getSystemService(NotificationManager::class.java).cancel(id)

    private fun markDone(id: Int) =
        Store.save(this, Store.load(this).map { if (it.id == id) it.copy(done = it.done + 1) else it })

    @Composable
    private fun Screen(p: Plan) {
        var phase by rememberSaveable { mutableStateOf(0) }       // 0 suena · 1 cuenta atrás · 2 terminada
        var end by rememberSaveable { mutableStateOf(0L) }
        var left by rememberSaveable { mutableStateOf(p.durationMin * 60) }

        LaunchedEffect(phase) {
            if (phase == 1) {
                while (left > 0) {
                    left = ((end - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                    delay(500)
                }
                ToneGenerator(AudioManager.STREAM_ALARM, 100)
                    .startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2000)
                phase = 2
            }
        }

        Column(
            Modifier.fillMaxSize().padding(32.dp),
            Arrangement.Center, Alignment.CenterHorizontally
        ) {
            Text(p.label, fontSize = 28.sp)
            Spacer(Modifier.height(24.dp))
            if (phase > 0) Text("%02d:%02d".format(left / 60, left % 60), fontSize = 72.sp)
            Spacer(Modifier.height(24.dp))
            when (phase) {
                0 -> Button(onClick = {
                    stopRinging(p.id)
                    end = System.currentTimeMillis() + p.durationMin * 60_000L
                    phase = 1
                }) { Text("Empezar (${p.durationMin} min)") }
                1 -> Text("En marcha…")
                else -> Button(onClick = { markDone(p.id); finish() }) { Text("Lo hice ✔") }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { stopRinging(p.id); finish() }) { Text("Omitir") }
        }
    }
}
