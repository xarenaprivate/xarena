package app.xarena.nativeapp

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.lang.ref.WeakReference

/** All share entry points use the foreground Activity's task, with explicit URI grants. */
object ShareLauncher {
    private var foreground = WeakReference<Activity>(null)
    fun attach(activity: Activity) { foreground = WeakReference(activity) }
    fun detach(activity: Activity) { if (foreground.get() === activity) foreground.clear() }
    private fun activity(ctx: Context): Activity? {
        var c = ctx
        while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
        return foreground.get()?.takeUnless { it.isFinishing || it.isDestroyed }
    }
    fun text(ctx: Context, title: String, text: String) = launch(ctx, title,
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text))
    fun image(ctx: Context, title: String, file: File, text: String? = null) {
        try {
            require(file.isFile && file.length() > 0) { "Share image is missing." }
            val uri = FileProvider.getUriForFile(ctx, BuildConfig.APPLICATION_ID + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri("XARENA image", uri)
            if (text != null) send.putExtra(Intent.EXTRA_TEXT, text)
            launch(ctx, title, send)
        } catch (_: Exception) { failure(ctx) }
    }
    private fun launch(ctx: Context, title: String, send: Intent) {
        Handler(Looper.getMainLooper()).post {
            try {
                val host = activity(ctx)
                val chooser = Intent.createChooser(send, title)
                if (send.clipData != null) { chooser.clipData = send.clipData; chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                if (host == null) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                (host ?: ctx.applicationContext).startActivity(chooser)
            } catch (_: Exception) { failure(ctx) }
        }
    }
    private fun failure(ctx: Context) { Handler(Looper.getMainLooper()).post {
        Toast.makeText(ctx, "Could not open sharing. Check that a sharing app is installed and try again.", Toast.LENGTH_LONG).show()
    } }
}
