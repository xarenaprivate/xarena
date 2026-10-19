package app.xarena.nativeapp

import android.os.Build
import java.security.MessageDigest
import java.util.UUID

/** Stable install-bound device id for the anti-fake-account system.
 *  UUID generated once in app storage, mixed with a hashed ANDROID_ID (survives reinstall on the same device).
 *  p_fp is a weak model/OS profile string only - never raw identifiers. */
object DeviceIds {
    private val p get() = Api.ctx.getSharedPreferences("xr_device", 0)
    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    val id: String by lazy {
        var u = p.getString("install_uuid", null)
        if (u.isNullOrBlank()) { u = UUID.randomUUID().toString(); p.edit().putString("install_uuid", u).apply() }
        val aid = runCatching {
            android.provider.Settings.Secure.getString(Api.ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        }.getOrNull() ?: "noaid"
        sha256(u + ":" + aid).take(24)
    }
    val fp: String get() = (Build.MANUFACTURER + " " + Build.MODEL + " / Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")").take(80)
}
