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
        val label = intent.getStringExtra("label") ?: "Actividad"
        val dur = intent.getIntExtra("dur", 10).coerceAtLeast(1)
        val auto = intent.getBooleanExtra("auto", false)
        if (auto) stopRinging(id)
        setContent { MaterialTheme { Screen(id, label, dur, auto) } }
    }

    private fun stopRinging(id: Int) =
        getSystemService(NotificationManager::class.java).cancel(id)

    private fun markDone(id: Int) =
        Store.save(this, Store.load(this).map { if (it.id == id) it.copy(done = it.done + 1) else it })

    @Composable
    private fun Screen(id: Int, label: String, dur: Int, auto: Boolean) {
        // fase: 0 suena · 1 cuenta atrás · 2 terminada
        var phase by rememberSaveable { mutableStateOf(if (auto) 1 else 0) }
        var end by rememberSaveable { mutableStateOf(if (auto) System.currentTimeMillis() + dur * 60_000L else 0L) }
        var left by rememberSaveable { mutableStateOf(dur * 60) }

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
            Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
            Arrangement.Center, Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 28.sp)
            Spacer(Modifier.height(24.dp))
            if (phase > 0) Text("%02d:%02d".format(left / 60, left % 60), fontSize = 72.sp)
            Spacer(Modifier.height(24.dp))
            when (phase) {
                0 -> Button(onClick = {
                    stopRinging(id)
                    end = System.currentTimeMillis() + dur * 60_000L
                    phase = 1
                }) { Text("Empezar ($dur min)") }
                1 -> Text("En marcha…")
                else -> Button(onClick = { markDone(id); finish() }) { Text("Lo hice ✔") }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { stopRinging(id); finish() }) { Text("Omitir") }
        }
    }
}
