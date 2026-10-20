package app.xarena.nativeapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/* Home-screen countdown widget - shows the next big tournament countdown.
   Reads the cached target the app writes whenever Home loads (no network in the widget itself). */
class CupWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { CupWidget.refresh(ctx) }
}

object CupWidget {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("xr_prefs", 0)

    /** Called by the app whenever fresh tournament data lands. */
    fun setTarget(ctx: Context, name: String, startMs: Long, dateText: String) {
        prefs(ctx).edit().putString("cup_name", name).putLong("cup_start_ms", startMs).putString("cup_date", dateText).apply()
        refresh(ctx)
    }

    fun refresh(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx) ?: return
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, CupWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val p = prefs(ctx)
        val name = p.getString("cup_name", "") ?: ""
        val start = p.getLong("cup_start_ms", 0L)
        val date = p.getString("cup_date", "") ?: ""
        val cd = if (start <= 0L) "Dates soon" else {
            val diff = start - System.currentTimeMillis()
            if (diff <= 0) "Live now / starting"
            else {
                val d = diff / 86400000; val h = (diff % 86400000) / 3600000
                if (d > 0) "${d}d ${h}h left" else "${h}h ${(diff % 3600000) / 60000}m left"
            }
        }
        val pi = PendingIntent.getActivity(ctx, 9, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        ids.forEach { id ->
            val rv = RemoteViews(ctx.packageName, R.layout.widget_cup)
            rv.setTextViewText(R.id.widget_title, if (name.isBlank()) "XARENA" else name)
            rv.setTextViewText(R.id.widget_countdown, cd)
            rv.setTextViewText(R.id.widget_sub, date)
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
            mgr.updateAppWidget(id, rv)
        }
    }
}
