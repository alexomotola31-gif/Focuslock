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
    private var goTo by mutableStateOf<String?>(null)

    override fun onResume() { super.onResume(); tick++ }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        goTo = intent.getStringExtra("go")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePrefs.init(this)
        FocusSound.init(this)
        goTo = intent?.getStringExtra("go")
        PomodoroPrefs.init(this)
        ScheduleAlarms.rescheduleAll(this)
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
        NxSplash()
    }

    @Composable
    private fun MainScreen(apps: List<AppInfo>) {
        val refresh = tick
        val a11y = remember(refresh) { isAccessibilityOn() }
        val admin = remember(refresh) { isAdminOn() }
        val battery = remember(refresh) { isBatteryExempt() }

        var page by remember { mutableStateOf(Page.Home) }
        BackHandler(enabled = page == Page.Insights || page == Page.Goals) { page = Page.Home }
        LaunchedEffect(goTo) {
            when (goTo) {
                "apps" -> page = Page.Apps
                "insights" -> page = Page.Insights
                "home" -> page = Page.Home
            }
            goTo = null
        }
        var picked by remember { mutableStateOf(PickedStore.load(this@MainActivity).filter { p -> apps.any { it.pkg == p } }.toSet()) }
        LaunchedEffect(picked) { PickedStore.save(this@MainActivity, picked) }
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

        NxBackground { Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NxGlassBar { NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
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
                } }
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
                        SoundCard()
                        Spacer(Modifier.height(12.dp))
                        if (active) {
                            ActiveSessionCard { active = false }
                        } else {
                            ModeToggle()
                            Spacer(Modifier.height(12.dp))
                            if (PomodoroPrefs.enabled) { PomodoroSetupCard(); Spacer(Modifier.height(12.dp)) }
                            if (!PomodoroPrefs.enabled) FocusCard {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    val mins = minutes.toLongOrNull() ?: 0L
                                    NxRing(1f, 240.dp) {
                                        Text("FOCUS SESSION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("%02d:%02d:00".format(mins / 60, mins % 60), fontSize = 44.sp, fontWeight = FontWeight.Light)
                                        Text("${picked.size} apps selected", style = MaterialTheme.typography.bodySmall, color = Amber)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
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
                                enabled = a11y && picked.isNotEmpty() && (PomodoroPrefs.enabled || (minutes.toLongOrNull() ?: 0) > 0),
                                onClick = {
                                    shownCode = Vault.newCode(this@MainActivity)
                                    if (PomodoroPrefs.enabled) Pomodoro.startRun(this@MainActivity, picked) else SessionStore.start(this@MainActivity, picked, minutes.toLong() * 60_000)
                                    active = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NxColors.Blue, contentColor = Color.White, disabledContainerColor = Color.White.copy(alpha = 0.10f), disabledContentColor = NxColors.TextMuted),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
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
                            val shown = remember(query) { apps.filter { it.label.contains(query, ignoreCase = true) } }
                            var savedMsg by remember { mutableStateOf(false) }
                            LaunchedEffect(picked) { savedMsg = false }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${picked.size} of ${apps.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { picked = picked + shown.map { it.pkg } }) { Text("Select all") }
                                TextButton(onClick = { picked = emptySet() }) { Text("Clear") }
                            }
                            Text("Essential system apps such as Settings, Phone and your launcher can't be locked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LazyColumn(Modifier.weight(1f)) {
                                items(shown, key = { it.pkg }) { a ->
                                    val on = a.pkg in picked
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (on) Amber.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
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
                            Spacer(Modifier.height(8.dp))
                            if (savedMsg) Text("Selection saved. ${picked.size} apps will be locked in sessions.", color = Amber, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { PickedStore.save(this@MainActivity, picked); savedMsg = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Text("Save selection", fontWeight = FontWeight.Bold) }
                        }
                    }

                    Page.Insights -> InsightsScreen()
                    Page.Goals -> GoalsScreen()

                    Page.Setup -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text("Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap Enable and we'll take you straight to the right screen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        NxSteps(listOf(a11y, admin, battery, androidx.core.app.NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()))
                        val protection = when {
                            active && !a11y -> "Interrupted" to "A session is running but the accessibility service is off, so apps are NOT locked. Turn it on below."
                            active -> "Protection active" to "Accessibility is on and your session is running."
                            !a11y -> "Needs attention" to "Accessibility is off. Turn it on so FocusLock can lock apps."
                            else -> "Ready" to "Everything required is on. Start a session or add a schedule."
                        }
                        FocusCard {
                            Text(protection.first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Amber)
                            Spacer(Modifier.height(4.dp))
                            Text(protection.second, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { tick++ }) { Text("Recheck") }
                        }
                        Spacer(Modifier.height(10.dp))
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
                        SetupCard("Run in background", "Recommended. Tap Allow, then confirm in the box that appears.", battery, "Allow") {
                            requestBatteryExemption()
                        }
                        if (packageManager.getLaunchIntentForPackage("com.transsion.phonemaster") != null) {
                            SetupCard("Keep running (Tecno / Infinix)", "Open Phone Master, go to Autostart and turn FocusLock on. Also lock FocusLock in your recent apps.", false, "Open") {
                                packageManager.getLaunchIntentForPackage("com.transsion.phonemaster")?.let { startActivity(it) }
                            }
                        }
                        val notifOk = remember(refresh) { androidx.core.app.NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled() }
                        SetupCard("Notifications", "Optional. Lets FocusLock tell you when sessions, breaks and goals finish.", notifOk, "Allow") {
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                androidx.core.app.ActivityCompat.requestPermissions(this@MainActivity, arrayOf("android.permission.POST_NOTIFICATIONS"), 7)
                            } else {
                                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                            }
                        }
                        TextButton(onClick = { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }) { Text("Notification settings") }
                        NotifSettingsCard()
                        FocusCard {
                            Text("Troubleshooting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("Apps aren't being blocked: tap Recheck and make sure Accessibility shows On.\n\nAccessibility is greyed out: open App info, tap the three dots, then Allow restricted settings.\n\nLocks stop after a while: use Run in background above, and add FocusLock to any 'autostart' or 'sleeping apps' list your phone has (Xiaomi, Oppo, Vivo, Samsung).\n\nStuck in a session: use Emergency on the lock screen with your code (3 uses a day). Scheduled locks can't be skipped and end at their set time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { openAppInfo() }) { Text("Open App info") }
                        }
                        Spacer(Modifier.height(10.dp))
                        PrivacyCard()
                        Spacer(Modifier.height(10.dp))
                        AppearanceCard()
                    }

                    Page.Schedule -> ScheduleScreen(picked)
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
        NxGlassCard(Modifier.fillMaxWidth(), content = content)
    }

    @Composable
    private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
        NxChip(text, selected, onClick)
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
        runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
            .onFailure {
                runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    .onFailure { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            }
    }

    // App info screen: where "Allow restricted settings" lives for sideloaded apps.
    private fun openAppInfo() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
    }

    private fun loadApps(): List<AppInfo> {
        val homes = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL
        ).map { it.activityInfo.packageName }.toSet()
        val essential = setOf(
            "com.android.settings", "com.android.phone", "com.android.dialer", "com.google.android.dialer",
            "com.samsung.android.dialer", "com.android.systemui", "com.android.emergency",
            "com.android.contacts", "com.google.android.contacts",
            "com.google.android.packageinstaller", "com.android.packageinstaller", "com.samsung.android.packageinstaller"
        )
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(i, PackageManager.MATCH_ALL)
            .map { AppInfo(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != packageName && it.pkg !in homes && it.pkg !in essential }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }
}
