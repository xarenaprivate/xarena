package app.xarena.nativeapp

import android.app.PendingIntent
import android.content.Intent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

/**
 * FCM support. This file lives in src/push and is only compiled when
 * app/google-services.json exists (gated in app/build.gradle.kts).
 * Called reflectively from MainActivity so the no-push build never touches it.
 *
 * Token registration: RPC register_device(p_token, p_platform="android-native",
 * p_app_version) on the same Supabase project (per the web builder). Only called
 * while logged in; failures set the "push_pending" flag and retry on next sync.
 */
object PushRegistrar {
    const val CHANNEL = "xarena_general"
    @JvmStatic fun sync(ctx: Context) {
        ensureChannel(ctx)
        FirebaseMessaging.getInstance().token.addOnSuccessListener { t -> if (!t.isNullOrBlank()) upload(ctx, t) }
    }
    @JvmStatic fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "XARENA alerts", NotificationManager.IMPORTANCE_HIGH))
        }
    }
    @JvmStatic fun upload(ctx: Context, token: String) {
        Api.prefs.edit().putString("fcm_token", token).apply()
        Thread {
            try {
                if (Api.access == null) { Api.prefs.edit().putBoolean("push_pending", true).apply(); return@Thread }
                val body = JSONObject().put("p_token", token)
                    .put("p_platform", "android-native")
                    .put("p_app_version", BuildConfig.VERSION_NAME)
                val (code, _) = Api.authedRaw("POST", "/rest/v1/rpc/register_device", body)
                Api.prefs.edit().putBoolean("push_pending", code !in 200..299).apply()
            } catch (e: Exception) {
                Api.prefs.edit().putBoolean("push_pending", true).apply()
            }
        }.start()
    }
}

class XarenaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { Api.init(this); Api.restore(); PushRegistrar.upload(this, token) }
    override fun onMessageReceived(msg: RemoteMessage) {
        Api.init(this)
        if (Api.access == null) Api.restore()
        PushRegistrar.ensureChannel(this)
        val kind = msg.data["kind"] ?: ""
        val title = msg.notification?.title ?: msg.data["title"] ?: "XARENA"
        val body = msg.notification?.body ?: msg.data["body"] ?: ""
        if (kind == "dm") {
            val posted = runCatching {
                runBlocking(Dispatchers.IO) {
                    withTimeoutOrNull(7_000) {
                        val uid = Api.uid ?: return@withTimeoutOrNull false
                        if (!Api.loggedIn) return@withTimeoutOrNull false
                        val summaries = DmUnread.refresh().filter { it.optInt("unread_count") > 0 }
                        var sent = false
                        for (thread in summaries.take(8)) {
                            val tid = thread.s("thread_id"); val peer = thread.s("peer_id")
                            val rows = Api.dmMessages(tid)
                            val latest = (0 until rows.length()).map { rows.getJSONObject(it) }
                                .firstOrNull { it.s("id") == thread.s("last_message_id") && it.s("sender_id") == peer }
                            val preview = latest?.let { decryptPreview(it, peer) } ?: "Encrypted message - open XARENA to read"
                            if (Api.uid != uid || !Api.loggedIn) return@withTimeoutOrNull false
                            val name = thread.s("peer_ign").ifBlank { thread.s("peer_username").ifBlank { "Player" } }
                            val count = thread.optInt("unread_count")
                            val detail = preview + if (count > 1) "\n$count new messages" else ""
                            post(name, detail, tid.hashCode(), "messages", true, count)
                            sent = true
                        }
                        sent
                    } ?: false
                }
            }.getOrDefault(false)
            if (!posted && Api.loggedIn) post(title, body, 90001, "messages", true, 0)
        } else post(title, body, (System.currentTimeMillis() % 100000).toInt(), if (kind == "gift") "notifications" else null, false, 0)
    }

    private suspend fun decryptPreview(m: JSONObject, peer: String): String? { return try {
        if (m.s("kind", "text") != "text") {
            when (m.s("kind")) { "image" -> "Photo"; "video" -> "Video"; "audio" -> "Voice message"; else -> "Attachment" }
        } else {
            // Never generate/reassign keys from a background notification.
            val keys = Api.ctx.getSharedPreferences("dm_keys", 0)
            if (!keys.contains("priv") || !keys.contains("pub")) return null
            val mt = m.optJSONObject("meta") ?: JSONObject()
            val k = if (mt.optInt("v") == 2) {
                val wrapped = mt.optJSONObject("w")?.optJSONObject(DmCrypto.myKeyId()) ?: return null
                val devs = Api.dmDeviceKeys(peer)
                val sender = (0 until devs.length()).map { devs.getJSONObject(it) }
                    .firstOrNull { it.s("key_id") == mt.s("sk") }?.optJSONObject("public_key") ?: return null
                javax.crypto.spec.SecretKeySpec(DmCrypto.decrypt(DmCrypto.ecdhKey(sender), wrapped.s("c"), wrapped.s("i")), "AES")
            } else {
                val jwk = Api.dmPeerKey(peer) ?: return null
                DmCrypto.threadKey(peer, jwk)
            }
            String(DmCrypto.decrypt(k, m.s("ciphertext"), m.s("iv")), Charsets.UTF_8)
                .replace(Regex("[\\p{Cc}&&[^\\n\\t]]"), "").take(240)
        }
    } catch (_: Exception) { null }
    }

    private fun post(title: String, body: String, id: Int, route: String?, private: Boolean, count: Int) {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (route != null) intent.putExtra("open", route)
        val pending = PendingIntent.getActivity(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(this, PushRegistrar.CHANNEL).setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("XARENA").setContentText("New encrypted message").build()
        val n = NotificationCompat.Builder(this, PushRegistrar.CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pending)
            .setVisibility(if (private) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(public).setNumber(count).setCategory(if (private) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        try { NotificationManagerCompat.from(this).notify(id, n) } catch (_: SecurityException) { }
    }
}
