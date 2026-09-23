# FocusLock – Phase 1 + 2 + 3 (Android)

Core lock: pick apps + duration, start a session, locked apps are blocked until the timer ends.

## Run
1. Open this folder in Android Studio (Koala or newer) and let Gradle sync.
2. Run on a device or emulator (API 24+).
3. Tap "Enable accessibility service" and turn on FocusLock.
4. Pick apps, set minutes, tap Start lock, then open a locked app.

## What's in Phase 1
- Session model + store, timer based on elapsedRealtime (immune to clock changes; falls back to wall clock after a reboot)
- Accessibility service that detects locked apps and shows LockActivity
- Lock screen with countdown; Back goes Home

## Phase 2
- Random 6-digit code generated at session start, shown once; only a salted PBKDF2 hash is stored (Keystore-encrypted)
- Emergency button on the lock screen; wrong-code delays (30s / 2m / 10m) persist across restarts
- Max 3 emergency uses per day; clock can't be moved backwards (forward jumps need a server check, a later add-on)

## Phase 3
- Device Admin: Android blocks uninstalling FocusLock while it is active
- During a session the lock also covers Settings and the package installer, so the user can't turn off the accessibility service, the admin, or uninstall from there
- Setup checklist on the home screen (accessibility, uninstall protection, battery exemption); Start is disabled until accessibility is on

## Known limits
- Rebooting into safe mode, or force-stopping via ADB, can still bypass it
- Blocking all of Settings also blocks Wi-Fi/Bluetooth toggles during a session (quick-settings panel still works)
- Some brands (Samsung, Xiaomi, Oppo) use extra installer/settings packages; add them to `guarded` in LockService.kt
- Forward clock jumps can reset the daily emergency counter (needs a server time check)

## Not yet built
- iOS version, backend-backed daily limit, delay-reveal setting
