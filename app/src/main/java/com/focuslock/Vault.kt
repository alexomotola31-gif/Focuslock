package com.focuslock

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed class VaultResult {
    object Ok : VaultResult()
    data class Wrong(val triesBeforeLockout: Int) : VaultResult()
    data class LockedOut(val ms: Long) : VaultResult()
    object DailyLimit : VaultResult()
    object NoSession : VaultResult()
}

/**
 * Stores only a salted PBKDF2 hash of each timer's passcode (never the code itself),
 * in Keystore-backed encrypted prefs. Also enforces brute-force delays and the daily limit.
 * The main session uses id "main"; extra timers use their own id.
 */
object Vault {
    const val MAX_DAILY_USES = 3
    private const val FREE_TRIES = 3
    private val DELAYS_MS = longArrayOf(30_000, 120_000, 600_000) // then stays at 10 min

    /** Which timer's code matched the last successful tryCode. */
    @Volatile var lastMatched: String? = null

    private fun prefs(c: Context): SharedPreferences {
        val key = MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            c, "vault", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun sfx(id: String) = if (id == "main") "" else "_$id"

    private fun hasAnyCode(p: SharedPreferences) = p.all.keys.any { it == "salt" || it.startsWith("salt_") }

    /** Wall clock that can't move backwards: blunts "set the date back" cheats. Forward jumps need a server to catch. */
    private fun trustedNow(p: SharedPreferences): Long {
        val now = maxOf(System.currentTimeMillis(), p.getLong("lastSeen", 0))
        p.edit().putLong("lastSeen", now).apply()
        return now
    }

    private fun hash(code: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(code.toCharArray(), salt, 100_000, 256)).encoded

    /** Call at timer start. Returns the 6-digit code to show the user once. */
    fun newCode(c: Context, id: String = "main"): String {
        val rnd = SecureRandom()
        val code = rnd.nextInt(1_000_000).toString().padStart(6, '0')
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val p = prefs(c)
        val first = !hasAnyCode(p)
        val e = p.edit()
            .putString("salt" + sfx(id), android.util.Base64.encodeToString(salt, 0))
            .putString("hash" + sfx(id), android.util.Base64.encodeToString(hash(code, salt), 0))
        // Starting another timer must never reset the brute-force delay
        if (first) e.putInt("fails", 0).putLong("lockUntil", 0)
        e.apply()
        return code
    }

    /** Call when a timer ends. Keeps the daily-use counter. */
    fun wipe(c: Context, id: String = "main") {
        val p = prefs(c)
        p.edit().remove("salt" + sfx(id)).remove("hash" + sfx(id)).apply()
        if (!hasAnyCode(p)) p.edit().remove("fails").remove("lockUntil").apply()
    }

    fun usesToday(c: Context): Int {
        val p = prefs(c)
        return if (p.getString("day", "") == day(trustedNow(p))) p.getInt("uses", 0) else 0
    }

    private fun day(ms: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    /** Checks the code against every timer in [ids] that is still running. */
    fun tryCode(c: Context, input: String, ids: List<String> = listOf("main")): VaultResult {
        val p = prefs(c)
        val now = trustedNow(p)
        val present = ids.filter { p.getString("salt" + sfx(it), null) != null && p.getString("hash" + sfx(it), null) != null }
        if (present.isEmpty()) return VaultResult.NoSession

        val lockLeft = p.getLong("lockUntil", 0) - now
        if (lockLeft > 0) return VaultResult.LockedOut(lockLeft)
        if (usesToday(c) >= MAX_DAILY_USES) return VaultResult.DailyLimit

        var matched: String? = null
        for (id in present) {
            val salt = android.util.Base64.decode(p.getString("salt" + sfx(id), "")!!, 0)
            val expected = android.util.Base64.decode(p.getString("hash" + sfx(id), "")!!, 0)
            if (MessageDigest.isEqual(hash(input, salt), expected) && matched == null) matched = id // constant-time compare
        }
        if (matched != null) {
            val today = day(now)
            val used = if (p.getString("day", "") == today) p.getInt("uses", 0) else 0
            p.edit().putString("day", today).putInt("uses", used + 1).putInt("fails", 0).apply()
            lastMatched = matched
            return VaultResult.Ok
        }

        val fails = p.getInt("fails", 0) + 1
        p.edit().putInt("fails", fails).apply()
        if (fails >= FREE_TRIES) {
            val step = (fails - FREE_TRIES).coerceAtMost(DELAYS_MS.size - 1)
            p.edit().putLong("lockUntil", now + DELAYS_MS[step]).apply()
            return VaultResult.LockedOut(DELAYS_MS[step])
        }
        return VaultResult.Wrong(FREE_TRIES - fails)
    }
}
