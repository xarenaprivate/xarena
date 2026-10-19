package app.xarena.nativeapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color as GColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** X-Panel: Poco Game Turbo-style edge handle + draggable ball over other apps (SYSTEM_ALERT_WINDOW).
 *  Plain views only (2 GB phone friendly), no screen reading, no accessibility. Turn it off any time. */
object XPanelStore {
    private val p get() = Api.ctx.getSharedPreferences("xr_xpanel", 0)
    var enabled: Boolean get() = p.getBoolean("enabled", false); set(v) = p.edit().putBoolean("enabled", v).apply()
    var ballX: Int get() = p.getInt("ballx", 24); set(v) = p.edit().putInt("ballx", v).apply()
    var ballY: Int get() = p.getInt("bally", 300); set(v) = p.edit().putInt("bally", v).apply()
    fun sync(ctx: Context) {
        if (enabled && Settings.canDrawOverlays(ctx)) ctx.startService(Intent(ctx, FloatingXPanelService::class.java))
        else ctx.stopService(Intent(ctx, FloatingXPanelService::class.java))
    }
}

class FloatingXPanelService : Service() {
    private lateinit var wm: WindowManager
    private var ball: View? = null
    private var handle: View? = null
    private var panel: View? = null
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val GOLD = GColor.parseColor("#F5C451")
    private val GOLD2 = GColor.parseColor("#FFE9A3")
    private val BG = GColor.parseColor("#F2120A24")
    private val CARD = GColor.parseColor("#FF1E1338")
    private val MUT = GColor.parseColor("#B8A8D9")
    private val RED = GColor.parseColor("#FFFF5252")
    private val refreshTick = object : Runnable { override fun run() { refreshPanel(); ui.postDelayed(this, 20_000) } }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel("xpanel", "X-Panel", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val n = Notification.Builder(this, "xpanel").setContentTitle("XARENA X-Panel is showing")
            .setContentText("Drag the X ball or swipe the right edge. Turn off in Settings.").setSmallIcon(R.mipmap.ic_launcher).build()
        startForeground(42, n)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showBall(); showHandle()
    }

    private fun overlayParams(w: Int, h: Int, x: Int, y: Int, gravity: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT).apply { this.gravity = gravity; this.x = x; this.y = y }

    private fun circleBg(color: Int, stroke: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color); setStroke(4, stroke) }
    private fun cardBg() = GradientDrawable().apply { setColor(BG); cornerRadius = 28f; setStroke(3, GOLD) }
    private fun rowBg() = GradientDrawable().apply { setColor(CARD); cornerRadius = 16f }

    private fun showBall() {
        if (ball != null) return
        val size = (56 * resources.displayMetrics.density).toInt()
        val v = TextView(this).apply {
            text = "X"; textSize = 22f; setTextColor(GOLD); gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = circleBg(GColor.parseColor("#F21A0E33"), GOLD); elevation = 8f
        }
        val p = overlayParams(size, size, XPanelStore.ballX, XPanelStore.ballY, Gravity.TOP or Gravity.START)
        var downX = 0f; var downY = 0f; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX - downX) + Math.abs(e.rawY - downY) > 14) moved = true
                    if (moved) { p.x = (e.rawX - size / 2).toInt(); p.y = (e.rawY - size / 2).toInt(); runCatching { wm.updateViewLayout(v, p) }; true } else false
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) { XPanelStore.ballX = p.x; XPanelStore.ballY = p.y } else togglePanel()
                    true
                }
                else -> false
            }
        }
        wm.addView(v, p); ball = v
    }

    private fun showHandle() {
        if (handle != null) return
        val w = (14 * resources.displayMetrics.density).toInt()
        val h = (110 * resources.displayMetrics.density).toInt()
        val v = View(this).apply { background = GradientDrawable().apply { setColor(GColor.parseColor("#66F5C451")); cornerRadius = 12f }; alpha = 0.55f }
        val p = overlayParams(w, h, 0, 0, Gravity.CENTER_VERTICAL or Gravity.END)
        var startX = 0f
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { startX = e.rawX; true }
                MotionEvent.ACTION_UP -> { if (startX - e.rawX > 40) togglePanel(); true }
                else -> false
            }
        }
        wm.addView(v, p); handle = v
    }

    private fun togglePanel() { if (panel != null) hidePanel() else showPanel() }

    private fun hidePanel() {
        panel?.let { runCatching { wm.removeView(it) } }; panel = null
        ui.removeCallbacks(refreshTick)
    }

    private fun label(txt: String, size: Float = 12f, color: Int = MUT, bold: Boolean = false) = TextView(this).apply {
        text = txt; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 6, 0, 2)
    }
    private fun goldBtn(txt: String, onClick: () -> Unit) = Button(this).apply {
        text = txt; textSize = 11f; setTextColor(GColor.parseColor("#1A0E33")); isAllCaps = false
        background = GradientDrawable().apply { setColor(GOLD); cornerRadius = 14f }
        setOnClickListener { onClick() }
    }
    private fun lineBtn(txt: String, color: Int = GOLD, onClick: () -> Unit) = Button(this).apply {
        text = txt; textSize = 11f; setTextColor(color); isAllCaps = false
        background = GradientDrawable().apply { setColor(CARD); cornerRadius = 14f; setStroke(2, color) }
        setOnClickListener { onClick() }
    }

    private var panelBody: LinearLayout? = null

    private fun showPanel() {
        if (panel != null) return
        val dm = resources.displayMetrics
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = cardBg()
            setPadding(28, 20, 28, 24)
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(label("XARENA", 18f, GOLD, true).also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        head.addView(lineBtn("Close") { hidePanel() })
        root.addView(head)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panelBody = body
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val p = overlayParams((dm.widthPixels * 0.82).toInt(), (dm.heightPixels * 0.78).toInt(), 0, 0, Gravity.CENTER_VERTICAL or Gravity.END)
        p.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS // focusable: chat keyboard needs it
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        wm.addView(root, p); panel = root
        refreshPanel(); ui.postDelayed(refreshTick, 20_000)
    }

    /** Pulls every panel section from the real APIs, off the UI thread. */
    private fun refreshPanel() {
        val body = panelBody ?: return
        io.execute {
            val hdr = runCatching { kotlinx.coroutines.runBlocking { Api.authedRpc("my_header", JSONObject()) } }.getOrNull()
            val room = runCatching { kotlinx.coroutines.runBlocking { Api.authedRpc("room_info", JSONObject()) } }.getOrNull()
            val feed = runCatching { kotlinx.coroutines.runBlocking { Api.authedRpc("room_feed", JSONObject().put("p_after", 0)) } }.getOrNull()
            val matches = runCatching { kotlinx.coroutines.runBlocking { Api.publicGet("matches_public?select=*&order=starts_at.asc.nullslast&limit=3") } }.getOrNull()
            val dmCount = runCatching { kotlinx.coroutines.runBlocking { Api.dmRequestsIncoming().length() + Api.dmMessageRequests().length() } }.getOrDefault(0)
            ui.post {
                if (panelBody !== body) return@post
                body.removeAllViews()
                // EXP meter
                if (hdr != null && hdr.optBoolean("ok")) {
                    val xp = hdr.optInt("exp"); val (lvl, into, need) = levelFromXp(xp)
                    body.addView(label("LEVEL $lvl  ·  $into / $need EXP  ·  ${hdr.optInt("ap")} AP", 13f, GOLD2, true))
                    val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = need; progress = into
                        progressDrawable = GradientDrawable().apply { setColor(GOLD); cornerRadius = 8f }
                    }
                    body.addView(pb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 18))
                }
                // Clan Room
                body.addView(label("CLAN ROOM", 12f, GOLD, true))
                if (room != null && room.optBoolean("ok")) {
                    body.addView(label(room.s("name") + (room.s("tag").takeIf { it.isNotBlank() }?.let { " [$it]" } ?: "") +
                        (if (room.optInt("members") > 0) " · " + room.optInt("members") + " members" else ""), 14f, GColor.WHITE, true))
                } else body.addView(label(if (room?.s("error") == "no_squad") "You are not in a clan yet - join one in Squads." else "Log in to see your clan room.", 12f, MUT))
                val rows = feed?.optJSONArray("rows")
                if (rows != null && rows.length() > 0) {
                    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rowBg(); setPadding(16, 10, 16, 10) }
                    val start = maxOf(0, rows.length() - 10)
                    for (i in start until rows.length()) {
                        val m = rows.getJSONObject(i)
                        box.addView(label((m.s("name").ifBlank { m.s("ign").ifBlank { m.s("username").ifBlank { "Clan" } } }) + ":  " + m.s("body"), 12f, GColor.WHITE))
                    }
                    body.addView(box)
                } else body.addView(label("No clan messages yet - say hello first.", 11f, MUT))
                val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val input = EditText(this).apply {
                    hint = "Message your clan"; setHintTextColor(MUT); setTextColor(GColor.WHITE); textSize = 12f
                    background = rowBg(); setPadding(16, 4, 16, 4)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                inputRow.addView(input)
                inputRow.addView(goldBtn("Send") {
                    val txt = input.text.toString().trim(); if (txt.isEmpty()) return@goldBtn
                    input.setText("")
                    io.execute { runCatching { kotlinx.coroutines.runBlocking { Api.authedRpc("room_post", JSONObject().put("p_body", txt)) } }; ui.post { refreshPanel() } }
                })
                body.addView(inputRow)
                // Match / room details
                body.addView(label("MATCH", 12f, GOLD, true))
                val m0 = matches?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.s("status") != "done" } }
                if (m0 == null) body.addView(label("No matches scheduled yet.", 12f, MUT))
                else {
                    body.addView(label(m0.s("title") + " · " + dmTime(m0.s("starts_at")) + " · " + m0.s("status").uppercase(), 13f, GColor.WHITE, true))
                    val roomLine = label("Checking room access...", 12f, MUT)
                    body.addView(roomLine)
                    io.execute {
                        val rr = runCatching { kotlinx.coroutines.runBlocking { Api.authedRpc("match_room", JSONObject().put("p_match", m0.optLong("id"))) } }.getOrNull()
                        ui.post {
                            if (rr != null && rr.optBoolean("ok")) {
                                roomLine.text = "Room ID: " + rr.s("room_id") + "   Pass: " + rr.s("room_pass").ifBlank { "(none)" } + "   (tap to copy)"
                                roomLine.setTextColor(GOLD2)
                                roomLine.setOnClickListener {
                                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("room", rr.s("room_id") + " " + rr.s("room_pass")))
                                    roomLine.text = "Copied - paste it in Free Fire."
                                }
                            } else {
                                roomLine.text = "Room unlocks shortly before start (registered players)."
                                roomLine.setTextColor(MUT)
                            }
                        }
                    }
                }
                // Voice Lobby (existing feature - deep link)
                body.addView(goldBtn("Voice Lobby - talk to your clan") { openApp("voice") })
                // DM
                body.addView(if (dmCount > 0) goldBtn("$dmCount new message requests - open") { openApp("messages") }
                    else lineBtn("Messages - nothing new") { openApp("messages") })
                // Screen recorder (same shared engine as the in-app one)
                body.addView(label("SCREEN RECORDER", 12f, GOLD, true))
                body.addView(if (RecStore.recording)
                    goldBtn("Stop recording (" + ((System.currentTimeMillis() - RecStore.startedAt) / 1000) + "s)") {
                        startService(Intent(this@FloatingXPanelService, RecorderService::class.java).setAction("stop"))
                        Handler(Looper.getMainLooper()).postDelayed({ refreshPanel() }, 600)
                    }
                else lineBtn("Record my screen - saved on phone only") {
                    startActivity(Intent(this@FloatingXPanelService, RecorderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                // XBoost - honest small cleanups only
                body.addView(label("XBOOST", 12f, GOLD, true))
                body.addView(label("Small cleanups Android actually allows. No fake FPS boost.", 10f, MUT))
                val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
                val bm = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
                val batt = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                body.addView(label("RAM free: " + (mi.availMem / 1048576) + " MB · Battery: " + (if (batt > 0) "$batt%" else "?"), 12f, GColor.WHITE))
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                body.addView(lineBtn("Do Not Disturb: " + if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) "OFF" else "ON") {
                    if (nm.isNotificationPolicyAccessGranted) {
                        nm.setInterruptionFilter(if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
                        refreshPanel()
                    } else startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                body.addView(lineBtn("Clear app cache (" + (cacheSize() / 1048576) + " MB)") {
                    io.execute {
                        runCatching { cacheDir?.deleteRecursively() }
                        ui.post { refreshPanel() }
                    }
                })
                body.addView(label("Tips: close other apps before a match, keep the phone cool, charge above 20%.", 10f, MUT))
                // Off
                body.addView(lineBtn("Turn X-Panel off", MUT) {
                    XPanelStore.enabled = false; XPanelStore.sync(this@FloatingXPanelService)
                })
            }
        }
    }

    private fun cacheSize(): Long {
        var t = 0L
        runCatching { cacheDir?.walkTopDown()?.forEach { t += it.length() } }
        return t
    }

    private fun openApp(what: String) {
        MainActivity.pendingOpen = what
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(i); hidePanel()
    }

    override fun onDestroy() {
        ui.removeCallbacks(refreshTick)
        listOf(ball, handle, panel).forEach { it?.let { v -> runCatching { wm.removeView(v) } } }
        ball = null; handle = null; panel = null
        super.onDestroy()
    }
}
