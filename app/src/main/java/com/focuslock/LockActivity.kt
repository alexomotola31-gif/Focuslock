package com.focuslock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePrefs.init(this)
        // Back sends the user Home instead of back into the locked app
        onBackPressedDispatcher.addCallback(this) { goHome() }
        setContent {
            FocusTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { LockContent() }
            }
        }
    }

    @Composable
    private fun LockContent() {
        val cs = MaterialTheme.colorScheme
        var remaining by remember { mutableStateOf(0L) }
        var scheduled by remember { mutableStateOf(false) }
        var emergency by remember { mutableStateOf(false) }
        var input by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        var skipWait by remember { mutableStateOf(-1) }
        LaunchedEffect(Unit) {
            while (true) {
                val s = SessionStore.active(this@LockActivity)
                if (s != null) {
                    if (SessionStore.breakUnlocked(this@LockActivity)) { finish(); break }
                    scheduled = false
                    remaining = SessionStore.remainingMs(this@LockActivity, s)
                } else {
                    val w = Schedules.activeWindows(this@LockActivity)
                    if (w.isEmpty()) { finish(); break }
                    scheduled = true
                    remaining = w.maxOf { it.second } - System.currentTimeMillis()
                }
                delay(1000)
            }
        }
        LaunchedEffect(skipWait) { if (skipWait > 0) { delay(1000); skipWait -= 1 } }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (scheduled) "Scheduled focus lock" else "Locked", fontSize = 28.sp)
            Spacer(Modifier.height(12.dp))
            Text(format(remaining), fontSize = 48.sp, color = cs.primary)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { goHome() }) { Text("Go home") }
            Spacer(Modifier.height(16.dp))
            if (scheduled) {
                val left = Schedules.skipsLeft(this@LockActivity)
                when {
                    left <= 0 -> Text("No skips left today.", color = cs.onSurfaceVariant)
                    skipWait < 0 -> TextButton(onClick = { skipWait = 30 }) { Text("Skip this window ($left left today)") }
                    skipWait > 0 -> Text("Take a breath. You can confirm in ${skipWait}s.")
                    else -> Button(onClick = { Schedules.skipActive(this@LockActivity); finish() }) { Text("Confirm skip") }
                }
            } else if (!emergency) {
                TextButton(onClick = { emergency = true }) { Text("Emergency") }
            } else {
                OutlinedTextField(
                    value = input, onValueChange = { if (it.length <= 6) input = it.filter(Char::isDigit) },
                    label = { Text("6-digit code") }, singleLine = true
                )
                Button(enabled = input.length == 6, onClick = {
                    when (val r = Vault.tryCode(this@LockActivity, input)) {
                        is VaultResult.Ok -> { SessionStore.end(this@LockActivity, early = true); finish() }
                        is VaultResult.Wrong -> message = "Wrong code. ${r.triesBeforeLockout} tries before a delay."
                        is VaultResult.LockedOut -> message = "Too many attempts. Try again in ${r.ms / 1000}s."
                        is VaultResult.DailyLimit -> message = "Daily emergency limit (${Vault.MAX_DAILY_USES}) reached."
                        is VaultResult.NoSession -> finish()
                    }
                    input = ""
                }) { Text("Unlock") }
                if (message.isNotEmpty()) Text(message)
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun format(ms: Long): String {
        val t = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d:%02d".format(t / 3600, (t % 3600) / 60, t % 60)
    }
}
