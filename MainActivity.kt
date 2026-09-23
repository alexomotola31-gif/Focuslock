package com.focuslock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class AppInfo(val label: String, val pkg: String)

class MainActivity : ComponentActivity() {
    private var tick by mutableStateOf(0)   // bumped in onResume to refresh setup status

    override fun onResume() { super.onResume(); tick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apps = loadApps()
        setContent {
            MaterialTheme {
                val refresh = tick
                val a11y = remember(refresh) { isAccessibilityOn() }
                val admin = remember(refresh) { isAdminOn() }
                val battery = remember(refresh) { isBatteryExempt() }

                var picked by remember { mutableStateOf(setOf<String>()) }
                var minutes by remember { mutableStateOf("30") }
                var active by remember(refresh) { mutableStateOf(SessionStore.active(this) != null) }
                var shownCode by remember { mutableStateOf<String?>(null) }

                shownCode?.let { code ->
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Your emergency code") },
                        text = { Text("$code\n\nThis is shown only once. You need it to end the session early (max ${Vault.MAX_DAILY_USES} times a day).") },
                        confirmButton = { TextButton(onClick = { shownCode = null }) { Text("I've saved it") } }
                    )
                }

                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("FocusLock", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))

                    if (active) {
                        Text("A session is running. Locked apps can't be opened until it ends.")
                    } else {
                        SetupRow("Accessibility service (required)", a11y, "Enable") {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                        SetupRow("Uninstall protection", admin, "Enable") { requestAdmin() }
                        SetupRow("Run in background (recommended)", battery, "Allow") {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = minutes, onValueChange = { minutes = it.filter(Char::isDigit) },
                            label = { Text("Duration (minutes)") }, singleLine = true
                        )
                        LazyColumn(Modifier.weight(1f)) {
                            items(apps) { a ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(a.pkg in picked, onCheckedChange = {
                                        picked = if (it) picked + a.pkg else picked - a.pkg
                                    })
                                    Text(a.label)
                                }
                            }
                        }
                        Button(
                            enabled = a11y && picked.isNotEmpty() && (minutes.toLongOrNull() ?: 0) > 0,
                            onClick = {
                                shownCode = Vault.newCode(this@MainActivity)
                                SessionStore.start(this@MainActivity, picked, minutes.toLong() * 60_000)
                                active = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (a11y) "Start lock" else "Enable accessibility first") }
                    }
                }
            }
        }
    }

    @Composable
    private fun SetupRow(label: String, done: Boolean, action: String, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text((if (done) "✓ " else "○ ") + label, Modifier.weight(1f))
            if (!done) OutlinedButton(onClick = onClick) { Text(action) }
        }
    }

    private fun isAccessibilityOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return s.contains("$packageName/") && s.contains("LockService")
    }

    private fun adminComponent() = ComponentName(this, AdminReceiver::class.java)

    private fun isAdminOn() =
        (getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(adminComponent())

    private fun isBatteryExempt() =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun requestAdmin() {
        startActivity(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent())
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Stops FocusLock from being uninstalled during a focus session.")
        )
    }

    private fun loadApps(): List<AppInfo> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(i, PackageManager.MATCH_ALL)
            .map { AppInfo(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }
}
