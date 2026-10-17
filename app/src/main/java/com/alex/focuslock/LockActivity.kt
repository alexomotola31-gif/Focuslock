package com.alex.focuslock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class LockActivity : ComponentActivity() {
    companion object {
        @Volatile var visible = false
        @Volatile var shownPkg: String? = null
    }
    private var pkg: String? = null
    private var legit = false
    override fun finish() { legit = true; super.finish() }
    override fun onResume() { super.onResume(); legit = false; visible = true; shownPkg = pkg }
    override fun onPause() { visible = false; super.onPause() }
    override fun onDestroy() {
        visible = false
        super.onDestroy()
        if (!legit && !isChangingConfigurations) LockService.nudge()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra("pkg")
        ThemePrefs.init(this)
        // Back sends the user Home instead of back into the locked app
        onBackPressedDispatcher.addCallback(this) { goHome() }
        setContent {
            FocusTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NxBackground { LockContent() }
                }
            }
        }
    }

    /** One lock covering the app on screen. manualId: "main" or an extra timer id; null = a schedule. */
    private class Cover(val endMs: Long, val totalMs: Long, val manualId: String?)

    private fun covers(): List<Cover> {
        val p = pkg
        val now = System.currentTimeMillis()
        val out = ArrayList<Cover>()
        val s = SessionStore.active(this)
        if (s != null && !SessionStore.breakUnlocked(this) && (p == null || p in s.lockedApps || p in LockService.GUARDED)) {
            out.add(Cover(now + SessionStore.remainingMs(this, s), SessionStore.plannedMs(this), "main"))
        }
        ExtraTimers.active(this).filter { p == null || p in it.apps || p in LockService.GUARDED }.forEach {
            out.add(Cover(now + ExtraTimers.remainingMs(this, it), it.planned, it.id))
        }
        Schedules.activeWindows(this).filter { p == null || p in it.first.pkgs }.forEach {
            out.add(Cover(it.second, 1L, null))
        }
        return out
    }

    @Composable
    private fun LockContent() {
        val cs = MaterialTheme.colorScheme
        var remaining by remember { mutableStateOf(0L) }
        var total by remember { mutableStateOf(1L) }
        var scheduled by remember { mutableStateOf(false) }
        var untilMs by remember { mutableStateOf(0L) }
        var manualIds by remember { mutableStateOf(listOf<String>()) }
        var emergency by remember { mutableStateOf(false) }
        var input by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            while (true) {
                val cv = covers()
                if (cv.isEmpty()) { finish(); break }
                val last = cv.maxByOrNull { it.endMs }!!
                scheduled = cv.any { it.manualId == null }
                manualIds = cv.mapNotNull { it.manualId }
                untilMs = last.endMs
                remaining = untilMs - System.currentTimeMillis()
                total = last.totalMs.coerceAtLeast(remaining).coerceAtLeast(1)
                delay(1000)
            }
        }
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (scheduled) "SCHEDULED LOCK ACTIVE" else "MANUAL FOCUS TIMER",
                color = cs.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(14.dp))
            NxGlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("STRICT LOCK", color = NxColors.Crimson, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(16.dp))
                    NxRing(if (scheduled) 1f else (1f - remaining.toFloat() / total).coerceIn(0f, 1f), 190.dp) {
                        Text("\uD83D\uDD12", fontSize = 40.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(if (scheduled) "Locked by schedule" else "Locked", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    if (scheduled) {
                        Text("UNLOCKS AT ${Schedules.clockText(untilMs)}", color = cs.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    }
                    Text(format(remaining), fontSize = 48.sp, fontWeight = FontWeight.Light, color = cs.primary)
                    Text(if (scheduled) "remaining until this lock ends" else "remaining", color = cs.onSurfaceVariant)
                    if (manualIds.size + (if (scheduled) 1 else 0) > 1) {
                        Spacer(Modifier.height(6.dp))
                        Text("More than one lock covers this app. It opens when the last one ends.", color = cs.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Stay focused, you've got this.", color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(16.dp))
            NxPillButton("Go home") { goHome() }
            if (!scheduled && manualIds.isNotEmpty()) {
                if (!emergency) {
                    TextButton(onClick = { emergency = true }) { Text("Emergency") }
                } else {
                    Spacer(Modifier.height(12.dp))
                    NxGlassCard(Modifier.fillMaxWidth()) {
                        Text("Emergency unlock", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Enter your 6-digit code. Limited to ${Vault.MAX_DAILY_USES} uses per day.", color = cs.onSurfaceVariant, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = input, onValueChange = { input = it.filter(Char::isDigit).take(6) },
                            label = { Text("6-digit code") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(enabled = input.length == 6, modifier = Modifier.fillMaxWidth(), onClick = {
                            val r = try { Vault.tryCode(this@LockActivity, input, manualIds) } catch (e: Exception) { null }
                            when (r) {
                                null -> message = "Could not check the code. Try again."
                                is VaultResult.Ok -> {
                                    val id = Vault.lastMatched
                                    if (id == "main") SessionStore.end(this@LockActivity, early = true)
                                    else if (id != null) ExtraTimers.end(this@LockActivity, id, early = true)
                                    if (covers().isEmpty()) finish()
                                    else message = "That timer ended, but another lock still covers this app."
                                }
                                is VaultResult.Wrong -> message = "Wrong code. ${r.triesBeforeLockout} tries before a delay."
                                is VaultResult.LockedOut -> message = "Too many attempts. Try again in ${r.ms / 1000}s."
                                is VaultResult.DailyLimit -> message = "Daily emergency limit (${Vault.MAX_DAILY_USES}) reached."
                                is VaultResult.NoSession -> message = "Emergency unlock is not available right now. This lock ends when the timer does."
                            }
                            input = ""
                        }) { Text("Unlock now") }
                        if (message.isNotEmpty()) Text(message, color = cs.error)
                        TextButton(onClick = { emergency = false; input = ""; message = "" }) { Text("Cancel / return to focus") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Phone and emergency calls are always available.", color = cs.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }

    private fun goHome() {
        legit = true
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun format(ms: Long): String {
        val t = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d:%02d".format(t / 3600, (t % 3600) / 60, t % 60)
    }
}
