package app.xarena.nativeapp

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
    override fun onNewToken(token: String) { PushRegistrar.upload(this, token) }
    override fun onMessageReceived(msg: RemoteMessage) {
        PushRegistrar.ensureChannel(this)
        val title = msg.notification?.title ?: msg.data["title"] ?: "XARENA"
        val body = msg.notification?.body ?: msg.data["body"] ?: ""
        val n = NotificationCompat.Builder(this, PushRegistrar.CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        try { NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % 100000).toInt(), n) }
        catch (e: SecurityException) { }
    }
}
