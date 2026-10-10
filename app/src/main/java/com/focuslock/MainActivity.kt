package com.focuslock

import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay

data class AppInfo(val label: String, val pkg: String)

// Order: Home, Apps, Setup, Schedule
private enum class Page(val title: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Apps("Apps", Icons.Filled.Menu),
    Setup("Setup", Icons.Filled.Settings),
    Schedule("Schedule", Icons.Filled.DateRange),
    Insights("Insights", Icons.Filled.Menu),
    Goals("Goals", Icons.Filled.Menu)
}

private val Navy: Color
    @Composable get() = MaterialTheme.colorScheme.background
private val CardBg: Color
    @Composable get() = MaterialTheme.colorScheme.surface
private val Amber: Color
    @Composable get() = MaterialTheme.colorScheme.primary
private val OnAmber: Color
    @Composable get() = MaterialTheme.colorScheme.onPrimary

private fun Page.inBar() = this != Page.Insights && this != Page.Goals

private fun fmtTime(min: Int): String {
    val h = min / 60
    val m = min % 60
    val ap = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, ap)
}

class MainActivity : ComponentActivity() {
    private var tick by mutableStateOf(0)   // bumped in onResume to refresh setup status

    override fun onResume() { super.onResume(); tick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePrefs.init(this)
        val apps = loadApps()
        setContent {
            FocusTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var showSplash by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) { delay(1400); showSplash = false }
                    Crossfade(targetState = showSplash, animationSpec = tween(500), label = "splash") { splash ->
                        if (splash) SplashScreen() else MainScreen(apps)
                    }
                }
            }
        }
    }

    @Composable
    private fun SplashScreen() {
        val t = rememberInfiniteTransition(label = "splashPulse")
        val s by t.animateFloat(1f, 1.07f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "sp")
        Box(Modifier.fillMaxSize().background(Navy), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(R.drawable.logo), null,
                    Modifier.size(150.dp).graphicsLayer { scaleX = s; scaleY = s }
                )
                Spacer(Modifier.height(20.dp))
                Text("FocusLock", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Lock in. Stay focused.", color = Amber)
            }
        }
    }

    @Composable
    private fun MainScreen(apps: List<AppInfo>) {
        val refresh = tick
        val a11y = remember(refresh) { isAccessibilityOn() }
        val admin = remember(refresh) { isAdminOn() }
        val battery = remember(refresh) { isBatteryExempt() }

        var page by remember { mutableStateOf(Page.Home) }
        BackHandler(enabled = page == Page.Insights || page == Page.Goals) { page = Page.Home }
        var picked by remember { mutableStateOf(setOf<String>()) }
        var minutes by remember { mutableStateOf("30") }
        var query by remember { mutableStateOf("") }
        var active by remember(refresh) { mutableStateOf(SessionStore.active(this@MainActivity) != null) }
        var shownCode by remember { mutableStateOf<String?>(null) }
        var showDisclosure by remember { mutableStateOf(false) }

        // Schedule page state
        var schedules by remember { mutableStateOf(ScheduleStore.all(this@MainActivity)) }
        var startMin by remember { mutableStateOf(19 * 60) }   // 7:00 PM
        var endMin by remember { mutableStateOf(22 * 60) }     // 10:00 PM

        shownCode?.let { code ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Your emergency code") },
                text = { Text("$code\n\nThis is shown only once. You need it to end the session early (max ${Vault.MAX_DAILY_USES} times a day).") },
                confirmButton = { TextButton(onClick = { shownCode = null }) { Text("I've saved it") } }
            )
        }

        if (showDisclosure) {
            AlertDialog(
                onDismissRequest = { showDisclosure = false },
                title = { Text("Accessibility permission") },
                text = {
                    Text(
                        "FocusLock uses Android's Accessibility Service only to detect when an app " +
                        "you chose to lock is opened, and to block it during your lock timer or schedule.\n\n" +
                        "It does not read your screen content, passwords or messages, and no data leaves your device."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDisclosure = false
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("Agree & continue") }
                },
                dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("No thanks") } }
            )
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Page.values().filter { it.inBar() }.forEach { p ->
                        NavigationBarItem(
                            selected = page == p || (p == Page.Home && !page.inBar()),
                            onClick = { page = p },
                            icon = { Icon(p.icon, contentDescription = p.title) },
                            label = { Text(p.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Amber, selectedTextColor = Amber,
                                indicatorColor = Amber.copy(alpha = 0.18f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
                when (page) {
                    Page.Home -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Header(active)
                        Spacer(Modifier.height(18.dp))
                        DailyProgressCard(active)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { page = Page.Insights }, modifier = Modifier.weight(1f)) { Text("Insights") }
                            OutlinedButton(onClick = { page = Page.Goals }, modifier = Modifier.weight(1f)) { Text("Goals") }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (active) {
                            ActiveSessionCard { active = false }
                        } else {
                            FocusCard {
                                Text("Focus time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(if (minutes.isEmpty()) "0" else minutes, fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Amber)
                                    Spacer(Modifier.width(8.dp))
                                    Text("minutes", Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("15", "30", "60", "90").forEach { m ->
                                        Chip("${m}m", minutes == m) { minutes = m }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = minutes, onValueChange = { minutes = it.filter(Char::isDigit) },
                                    label = { Text("Custom duration (minutes)") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            FocusCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Apps to lock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("${picked.size} selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(onClick = { page = Page.Apps }) { Text("Choose apps") }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(
                                enabled = a11y && picked.isNotEmpty() && (minutes.toLongOrNull() ?: 0) > 0,
                                onClick = {
                                    shownCode = Vault.newCode(this@MainActivity)
                                    SessionStore.start(this@MainActivity, picked, minutes.toLong() * 60_000)
                                    active = true
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().height(54.dp)
                            ) {
                                Text(
                                    when {
                                        !a11y -> "Enable accessibility first"
                                        picked.isEmpty() -> "Choose apps first"
                                        else -> "Start lock"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (!a11y) {
                                TextButton(onClick = { page = Page.Setup }) { Text("Go to Setup") }
                            }
                        }
                    }

                    Page.Apps -> if (active) {
                        Column(Modifier.fillMaxSize()) {
                            Header(true)
                            Spacer(Modifier.height(18.dp))
                            FocusCard {
                                Text("Session running", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Amber)
                                Spacer(Modifier.height(6.dp))
                                Text("You can pick new apps after the session ends.")
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Text("Choose apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = query, onValueChange = { query = it },
                                label = { Text("Search apps") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("${picked.size} selected", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val shown = remember(query) { apps.filter { it.label.contains(query, ignoreCase = true) } }
                            LazyColumn(Modifier.weight(1f)) {
                                items(shown, key = { it.pkg }) { a ->
                                    val on = a.pkg in picked
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (on) Amber.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
                                            .clickable { picked = if (on) picked - a.pkg else picked + a.pkg }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIcon(a.pkg, 40.dp)
                                        Spacer(Modifier.width(14.dp))
                                        Text(a.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                        Checkbox(checked = on, onCheckedChange = null)
                                    }
                                }
                            }
                        }
                    }

                    Page.Insights -> InsightsScreen()
                    Page.Goals -> GoalsScreen()

                    Page.Setup -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text("Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap Enable and we'll take you straight to the right screen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        SetupCard("Accessibility service", "Required. Lets FocusLock block the apps you pick.", a11y, "Enable") {
                            showDisclosure = true
                        }
                        if (!a11y) {
                            Text(
                                "If Accessibility is greyed out: open App info, tap ⋮ (top right), then \"Allow restricted settings\", and come back.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { openAppInfo() }) { Text("Open App info") }
                        }
                        SetupCard("Uninstall protection", "Stops FocusLock from being removed during a session.", admin, "Enable") {
                            requestAdmin()
                        }
                        SetupCard("Run in background", "Recommended. Keeps the lock running when the phone is idle.", battery, "Allow") {
                            requestBatteryExemption()
                        }
                        AppearanceCard()
                    }

                    Page.Schedule -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text("Schedule", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Lock your selected apps automatically, like 7 PM to 10 PM.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        FocusCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        TimePickerDialog(this@MainActivity, { _, h, m -> startMin = h * 60 + m },
                                            startMin / 60, startMin % 60, false).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("From ${fmtTime(startMin)}") }
                                OutlinedButton(
                                    onClick = {
                                        TimePickerDialog(this@MainActivity, { _, h, m -> endMin = h * 60 + m },
                                            endMin / 60, endMin % 60, false).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("To ${fmtTime(endMin)}") }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "${picked.size} apps selected (change them on the Apps page)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                enabled = picked.isNotEmpty() && startMin != endMin,
                                onClick = {
                                    ScheduleStore.add(
                                        this@MainActivity,
                                        Schedule(System.currentTimeMillis(), startMin, endMin, picked)
                                    )
                                    schedules = ScheduleStore.all(this@MainActivity)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (picked.isEmpty()) "Choose apps first" else "Add schedule", fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.height(14.dp))
                        if (schedules.isEmpty()) {
                            Text("No schedules yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        schedules.forEach { s ->
                            FocusCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${fmtTime(s.startMin)} – ${fmtTime(s.endMin)}", fontWeight = FontWeight.Bold, color = Amber)
                                        Text("${s.pkgs.size} apps", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = {
                                        ScheduleStore.remove(this@MainActivity, s.id)
                                        schedules = ScheduleStore.all(this@MainActivity)
                                    }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(active: Boolean) {
        val t = rememberInfiniteTransition(label = "headerPulse")
        val s by t.animateFloat(1f, 1.09f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "hp")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.logo), null,
                Modifier.size(56.dp).graphicsLayer {
                    val k = if (active) s else 1f
                    scaleX = k; scaleY = k
                }
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("FocusLock", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (active) "Session running" else "Lock distracting apps until the timer ends",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) Amber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun FocusCard(content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface).padding(16.dp),
            content = content
        )
    }

    @Composable
    private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(if (selected) Amber else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text, color = if (selected) OnAmber else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        }
    }

    @Composable
    private fun SetupCard(title: String, desc: String, done: Boolean, action: String, onClick: () -> Unit) {
        FocusCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (done) "✓" else "○", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = if (done) Amber else MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                if (done) {
                    Text("On", color = Amber, fontWeight = FontWeight.Bold)
                } else {
                    Button(onClick = onClick, shape = RoundedCornerShape(12.dp)) { Text(action) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    @Composable
    private fun AppIcon(pkg: String, size: Dp) {
        val icon = remember(pkg) {
            runCatching { packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull()
        }
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)))
        } else {
            Spacer(Modifier.size(size))
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

    // Opens the system "allow in background" dialog directly; falls back to the list screen.
    private fun requestBatteryExemption() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }.onFailure {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // App info screen: where "Allow restricted settings" lives for sideloaded apps.
    private fun openAppInfo() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
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
