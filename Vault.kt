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
 * Stores only a salted PBKDF2 hash of the session passcode (never the code itself),
 * in Keystore-backed encrypted prefs. Also enforces brute-force delays and the daily limit.
 */
object Vault {
    const val MAX_DAILY_USES = 3
    private const val FREE_TRIES = 3
    private val DELAYS_MS = longArrayOf(30_000, 120_000, 600_000) // then stays at 10 min

    private fun prefs(c: Context): SharedPreferences {
        val key = MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            c, "vault", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Wall clock that can't move backwards: blunts "set the date back" cheats. Forward jumps need a server to catch. */
    private fun trustedNow(p: SharedPreferences): Long {
        val now = maxOf(System.currentTimeMillis(), p.getLong("lastSeen", 0))
        p.edit().putLong("lastSeen", now).apply()
        return now
    }

    private fun hash(code: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(code.toCharArray(), salt, 100_000, 256)).encoded

    /** Call at session start. Returns the 6-digit code to show the user once. */
    fun newCode(c: Context): String {
        val rnd = SecureRandom()
        val code = rnd.nextInt(1_000_000).toString().padStart(6, '0')
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        prefs(c).edit()
            .putString("salt", android.util.Base64.encodeToString(salt, 0))
            .putString("hash", android.util.Base64.encodeToString(hash(code, salt), 0))
            .putInt("fails", 0).putLong("lockUntil", 0)
            .apply()
        return code
    }

    /** Call when a session ends. Keeps the daily-use counter. */
    fun wipe(c: Context) {
        prefs(c).edit().remove("salt").remove("hash").remove("fails").remove("lockUntil").apply()
    }

    fun usesToday(c: Context): Int {
        val p = prefs(c)
        return if (p.getString("day", "") == day(trustedNow(p))) p.getInt("uses", 0) else 0
    }

    private fun day(ms: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    fun tryCode(c: Context, input: String): VaultResult {
        val p = prefs(c)
        val now = trustedNow(p)
        val saltStr = p.getString("salt", null) ?: return VaultResult.NoSession
        val hashStr = p.getString("hash", null) ?: return VaultResult.NoSession

        val lockLeft = p.getLong("lockUntil", 0) - now
        if (lockLeft > 0) return VaultResult.LockedOut(lockLeft)
        if (usesToday(c) >= MAX_DAILY_USES) return VaultResult.DailyLimit

        val salt = android.util.Base64.decode(saltStr, 0)
        val expected = android.util.Base64.decode(hashStr, 0)
        if (MessageDigest.isEqual(hash(input, salt), expected)) { // constant-time compare
            val today = day(now)
            val used = if (p.getString("day", "") == today) p.getInt("uses", 0) else 0
            p.edit().putString("day", today).putInt("uses", used + 1).putInt("fails", 0).apply()
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
