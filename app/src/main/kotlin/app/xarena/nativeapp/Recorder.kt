package app.xarena.nativeapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.Gravity
import android.widget.*
import android.graphics.Color as GColor

/* XARENA screen recorder - records the screen to the phone only (Movies/XARENA), nothing uploaded.
   One shared implementation used by the main app and the X-Panel. */

object RecStore {
    @Volatile var recording = false
    @Volatile var startedAt = 0L
}

class RecorderActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(GColor.parseColor("#F20B0B10")); setPadding(48, 64, 48, 48)
        }
        root.addView(TextView(this).apply { text = "Screen Recorder"; textSize = 20f; setTextColor(GColor.parseColor("#FFD700")) })
        root.addView(TextView(this).apply {
            text = "\nRecords what is on your screen, plus your mic if you allowed it.\n\nSaved ONLY to this phone (Movies > XARENA). Nothing is uploaded, ever.\n\nHeads up: on this phone, recording while gaming can lower your FPS.\n\nAndroid will now ask for your permission."
            textSize = 14f; setTextColor(GColor.WHITE)
        })
        root.addView(Button(this).apply {
            text = "Start recording"
            setOnClickListener {
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                @Suppress("DEPRECATION") startActivityForResult(mpm.createScreenCaptureIntent(), 7)
            }
        })
        root.addView(TextView(this).apply { text = "Cancel"; setTextColor(GColor.parseColor("#8B8FA3")); textSize = 14f; setPadding(0, 32, 0, 0)
            setOnClickListener { finish() } })
        setContentView(root)
    }

    @Deprecated("deprecated")
    override fun onActivityResult(rc: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(rc, resultCode, data)
        if (rc == 7 && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, RecorderService::class.java).setAction("start").putExtra("code", resultCode).putExtra("data", data)
            androidx.core.content.ContextCompat.startForegroundService(this, i)
        }
        finish()
    }
}

class RecorderService : Service() {
    private var proj: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var rec: MediaRecorder? = null
    private var outUri: Uri? = null

    override fun onBind(i: Intent?) = null

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        if (i?.action == "stop" || RecStore.recording) { stopRec(); return START_NOT_STICKY }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("xrec", "Screen recording", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(this, 1, Intent(this, RecorderService::class.java).setAction("stop"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, if (Build.VERSION.SDK_INT >= 26) "xrec" else "")
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("XARENA is recording your screen")
            .setContentText("Saved only to this phone. Tap Stop to finish.")
            .addAction(Notification.Action.Builder(null, "Stop", stopPi).build())
            .setOngoing(true).build()
        androidx.core.app.ServiceCompat.startForeground(this, 43, n,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
        try { begin(i!!.getIntExtra("code", -1), i.getParcelableExtra("data")!!) }
        catch (t: Throwable) { stopRec() }
        return START_NOT_STICKY
    }

    private fun begin(code: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = mpm.getMediaProjection(code, data)
        p.registerCallback(object : MediaProjection.Callback() {}, Handler(Looper.getMainLooper()))
        proj = p
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
        val w = minOf(dm.widthPixels, 1280); val h = minOf(dm.heightPixels, 720)
        val micOk = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        try {
        if (micOk) r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        if (micOk) { r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); r.setAudioEncodingBitRate(96000); r.setAudioSamplingRate(44100) }
        r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        r.setVideoSize(w, h); r.setVideoFrameRate(30); r.setVideoEncodingBitRate(4000000)
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = android.content.ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "xarena-" + System.currentTimeMillis() + ".mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/XARENA")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: throw IllegalStateException("no store")
            outUri = uri
            contentResolver.openFileDescriptor(uri, "w")!!.use { r.setOutputFile(it.fileDescriptor) }
        } else {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)!!
            r.setOutputFile(dir.absolutePath + "/xarena-" + System.currentTimeMillis() + ".mp4")
        }
        r.prepare()
        vd = p.createVirtualDisplay("xrec", w, h, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
        r.start()
        rec = r
        } catch (t: Throwable) {
            // setup failed before rec was assigned - release the local recorder so it cannot leak
            runCatching { r.release() }
            runCatching { vd?.release() }; vd = null
            throw t
        }
        RecStore.recording = true; RecStore.startedAt = System.currentTimeMillis()
        XPanelStore.sync(this)
    }

    private fun stopRec() {
        RecStore.recording = false
        runCatching { rec?.stop() }; runCatching { rec?.reset() }; runCatching { rec?.release() }; rec = null
        runCatching { vd?.release() }; vd = null
        runCatching { proj?.stop() }; proj = null
        outUri?.let { uri ->
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = android.content.ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                runCatching { contentResolver.update(uri, cv, null, null) }
            }
        }
        outUri = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        XPanelStore.sync(this)
    }
}
