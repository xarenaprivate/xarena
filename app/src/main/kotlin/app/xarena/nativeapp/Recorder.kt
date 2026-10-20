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
import android.widget.*
import android.graphics.Color as GColor

/* XARENA screen recorder - records the screen to the phone only (Movies/XARENA), nothing uploaded.
   One shared implementation used by the main app and the X-Panel.
   Every failure is shown to the user (Toast + saved in RecStore.lastError) - never silent. */

object RecStore {
    @Volatile var recording = false
    @Volatile var startedAt = 0L
    @Volatile var lastError: String? = null
}

class RecorderActivity : Activity() {
    private var askedMic = false
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
        RecStore.lastError?.let { err ->
            root.addView(TextView(this).apply { text = "\nLast try failed: $err"; textSize = 12f; setTextColor(GColor.parseColor("#FF8A80")) })
        }
        root.addView(Button(this).apply {
            text = "Start recording"
            setOnClickListener { askMicThenConsent() }
        })
        root.addView(TextView(this).apply { text = "Cancel"; setTextColor(GColor.parseColor("#8B8FA3")); textSize = 14f; setPadding(0, 32, 0, 0)
            setOnClickListener { finish() } })
        setContentView(root)
    }

    private fun askMicThenConsent() {
        if (!askedMic && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            askedMic = true
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 5)
            return
        }
        launchConsent()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (rc == 5) launchConsent()  // continue either way - without the mic the video is just silent
    }

    private fun launchConsent() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION") startActivityForResult(mpm.createScreenCaptureIntent(), 7)
    }

    @Deprecated("deprecated")
    override fun onActivityResult(rc: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(rc, resultCode, data)
        if (rc == 7) {
            if (resultCode == RESULT_OK && data != null) {
                val i = Intent(this, RecorderService::class.java).setAction("start").putExtra("code", resultCode).putExtra("data", data)
                runCatching { androidx.core.content.ContextCompat.startForegroundService(this, i) }
                    .onFailure {
                        RecStore.lastError = "recorder service refused to start (" + it.javaClass.simpleName + ")"
                        Toast.makeText(this, "Recording could not start - " + RecStore.lastError, Toast.LENGTH_LONG).show()
                    }
            }
            // resultCode != OK just means the user cancelled the system prompt - not an error
        }
        finish()
    }
}

class RecorderService : Service() {
    private var proj: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var rec: MediaRecorder? = null
    private var outUri: Uri? = null
    private var outPath: String? = null
    private var stage = "setup"

    override fun onBind(i: Intent?) = null

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        if (i == null || i.action == "stop") { stopRec(); return START_NOT_STICKY }
        if (RecStore.recording) { stopRec(); return START_NOT_STICKY }
        RecStore.lastError = null
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("xrec", "Screen recording", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(this, 1, Intent(this, RecorderService::class.java).setAction("stop"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, if (Build.VERSION.SDK_INT >= 26) "xrec" else "")
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("XARENA is recording your screen")
            .setContentText("Saved only to this phone. Tap Stop to finish.")
            .addAction(Notification.Action.Builder(null, "Stop", stopPi).build())
            .setOngoing(true).build()
        stage = "foreground service"
        try {
            androidx.core.app.ServiceCompat.startForeground(this, 43, n,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
        } catch (t: Throwable) { fail(t); return START_NOT_STICKY }
        val data: Intent? = i.getParcelableExtra("data")
        val code = i.getIntExtra("code", -1)
        if (code == -1 || data == null) {
            RecStore.lastError = "screen capture permission result missing"
            stopRec(); return START_NOT_STICKY
        }
        try { begin(code, data) } catch (t: Throwable) { fail(t) }
        return START_NOT_STICKY
    }

    private fun fail(t: Throwable) {
        RecStore.lastError = stage + ": " + (t.message ?: t.javaClass.simpleName)
        // a failed start must not leave a stuck empty "pending" video in the gallery
        outUri?.let { runCatching { contentResolver.delete(it, null, null) } }; outUri = null
        stopRec()
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, "Recording could not start - " + RecStore.lastError, Toast.LENGTH_LONG).show()
        }
    }

    private fun begin(code: Int, data: Intent) {
        stage = "screen capture access"
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = mpm.getMediaProjection(code, data)
        proj = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { Handler(Looper.getMainLooper()).post { stopRec() } }
        }, Handler(Looper.getMainLooper()))
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
        // keep the screen's real shape, cap the long side at 1280, even numbers (encoders require them)
        val scale = minOf(1f, 1280f / maxOf(dm.widthPixels, dm.heightPixels))
        fun even(x: Int) = x - (x % 2)
        val w = maxOf(2, even((dm.widthPixels * scale).toInt()))
        val h = maxOf(2, even((dm.heightPixels * scale).toInt()))
        val micOk = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        stage = "saving location"
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = android.content.ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "xarena-" + System.currentTimeMillis() + ".mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/XARENA")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            outUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
                ?: throw IllegalStateException("phone storage refused the video file")
        } else {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)!!
            outPath = dir.absolutePath + "/xarena-" + System.currentTimeMillis() + ".mp4"
        }

        stage = "video encoder"
        // Try mic first (if allowed), then without; finally a lighter encoder config. Never fail silently.
        val tries = mutableListOf<Triple<Boolean, Int, Int>>()
        if (micOk) { tries += Triple(true, 4000000, 30); tries += Triple(false, 4000000, 30) } else tries += Triple(false, 4000000, 30)
        tries += Triple(false, 2000000, 24)
        var err: Throwable? = null
        for ((mic, br, fps) in tries) {
            try { startEncoder(p, w, h, dm.densityDpi, mic, br, fps); err = null; break }
            catch (t: Throwable) { err = t }
        }
        if (err != null) throw IllegalStateException(err!!.message ?: err!!.javaClass.simpleName)

        RecStore.recording = true; RecStore.startedAt = System.currentTimeMillis()
        XPanelStore.sync(this)
    }

    private fun startEncoder(p: MediaProjection, w: Int, h: Int, dpi: Int, mic: Boolean, br: Int, fps: Int) {
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            if (mic) r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            if (mic) { r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); r.setAudioEncodingBitRate(96000); r.setAudioSamplingRate(44100) }
            r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            r.setVideoSize(w, h); r.setVideoFrameRate(fps); r.setVideoEncodingBitRate(br)
            if (Build.VERSION.SDK_INT >= 29) contentResolver.openFileDescriptor(outUri!!, "w")!!.use { r.setOutputFile(it.fileDescriptor) }
            else r.setOutputFile(outPath!!)
            r.prepare()
            vd = p.createVirtualDisplay("xrec", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
            r.start()
            rec = r
        } catch (t: Throwable) {
            runCatching { r.release() }
            runCatching { vd?.release() }; vd = null
            throw t
        }
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
        outUri = null; outPath = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        XPanelStore.sync(this)
    }
}
