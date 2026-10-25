package app.xarena.nativeapp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/* 3D effects set (user asked for all five, Oct 25 2026 - "Don't see my device": full experience
   by default, Lite available as a toggle, never lite-only). Everything is drawn programmatically:
   no 3D engine, no asset packs, so APK size stays flat and 2GB phones stay safe.
   Master switch: Settings > Feel > "3D effects". Data Saver also turns them off. */

object Fx3D {
    private val p get() = Api.ctx.getSharedPreferences("xr_prefs", 0)
    var on: Boolean get() = p.getBoolean("fx_3d", true); set(v) = p.edit().putBoolean("fx_3d", v).apply()
    fun active() = on && !DataSaver.on
}

/** Calibrated gravity-sensor tilt: first reading is the neutral pose, deltas drive the tilt.
    Low-pass filtered, registered only while a 3D-enabled screen is visible. */
object Tilt3D : android.hardware.SensorEventListener {
    var x by mutableFloatStateOf(0f); private set
    var y by mutableFloatStateOf(0f); private set
    private var registered = false
    private var base: FloatArray? = null

    fun start() {
        if (registered || !Fx3D.active()) return
        runCatching {
            val sm = Api.ctx.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
            val s = sm.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
                ?: sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) ?: return@runCatching
            base = null
            registered = sm.registerListener(this, s, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        if (!registered) return
        runCatching {
            (Api.ctx.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager).unregisterListener(this)
        }
        registered = false; x = 0f; y = 0f; base = null
    }

    override fun onSensorChanged(e: android.hardware.SensorEvent) {
        val b = base ?: run { base = e.values.clone(); return }
        val tx = (-(e.values[0] - b[0]) / 4f).coerceIn(-1f, 1f)
        val ty = ((e.values[1] - b[1]) / 4f).coerceIn(-1f, 1f)
        x += (tx - x) * 0.15f
        y += (ty - y) * 0.15f
    }

    override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
}

/** Subtle gyro parallax tilt for cards. No-op when 3D effects are off. */
fun Modifier.tilt3d(maxDeg: Float = 4f): Modifier {
    if (!Fx3D.active()) return this
    return this.graphicsLayer {
        rotationX = -Tilt3D.y * maxDeg
        rotationY = Tilt3D.x * maxDeg
        cameraDistance = 14f * density
    }
}

/** Animated holographic shine band sweeping diagonally across the element. */
@Composable
fun Modifier.holoShine(): Modifier {
    if (!Fx3D.active()) return this
    val t = rememberInfiniteTransition(label = "holo")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep")
    return this.drawWithContent {
        drawContent()
        val w = size.width
        if (w <= 0f) return@drawWithContent
        val cx = (p * 2.4f - 0.7f) * w
        val band = w * 0.35f
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color(0x26FFF3C4), Color(0x2EFFFFFF), Color(0x26FFF3C4), Color.Transparent),
                start = Offset(cx - band, size.height),
                end = Offset(cx + band, 0f)
            )
        )
    }
}

/* ---- Minimal shaded-3D renderer (Compose Canvas): perspective projection, painter's-algorithm
   depth sort, flat-lit faces. A real lit 3D look with zero native code and zero assets. ---- */

private class V3(val x: Float, val y: Float, val z: Float) {
    fun rotY(a: Float): V3 { val c = cos(a); val s = sin(a); return V3(x * c + z * s, y, -x * s + z * c) }
    fun rotX(a: Float): V3 { val c = cos(a); val s = sin(a); return V3(x, y * c - z * s, y * s + z * c) }
    fun rotZ(a: Float): V3 { val c = cos(a); val s = sin(a); return V3(x * c - y * s, x * s + y * c, z) }
}

private class Mesh(val verts: List<V3>, val faces: List<IntArray>, val color: Color)

/** A cuboid bar: width w, height h, depth d, then rotated `rz` around Z (builds the X bars). */
private fun bar(w: Float, h: Float, d: Float, rz: Float, color: Color): Mesh {
    val base = listOf(
        V3(-w / 2, -h / 2, -d / 2), V3(w / 2, -h / 2, -d / 2), V3(w / 2, h / 2, -d / 2), V3(-w / 2, h / 2, -d / 2),
        V3(-w / 2, -h / 2, d / 2), V3(w / 2, -h / 2, d / 2), V3(w / 2, h / 2, d / 2), V3(-w / 2, h / 2, d / 2)
    ).map { it.rotZ(rz) }
    val faces = listOf(
        intArrayOf(0, 1, 2, 3), intArrayOf(4, 5, 6, 7), intArrayOf(0, 1, 5, 4),
        intArrayOf(2, 3, 7, 6), intArrayOf(1, 2, 6, 5), intArrayOf(0, 3, 7, 4)
    )
    return Mesh(base, faces, color)
}

private fun shade(c: Color, f: Float): Color =
    Color((c.red * f).coerceIn(0f, 1f), (c.green * f).coerceIn(0f, 1f), (c.blue * f).coerceIn(0f, 1f), 1f)

/** The arena scene: rotating lit 3D X over a perspective floor grid with orbiting particles.
    Gyro tilt adds parallax. When 3D effects are off, renders one static frame. */
@Composable
fun Scene3D(modifier: Modifier) {
    val live = Fx3D.active()
    val angle: Float
    if (live) {
        val t = rememberInfiniteTransition(label = "rot")
        val a by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "a")
        angle = a
    } else angle = 0.7f
    val tiltX = if (live) Tilt3D.x else 0f
    val tiltY = if (live) Tilt3D.y else 0f

    val meshes = listOf(
        bar(0.85f, 3.6f, 0.6f, 0.66f, Color(0xFFF5C451)),
        bar(0.85f, 3.6f, 0.6f, -0.66f, Color(0xFFFFE9A3))
    )
    val light = run { val l = Math.sqrt((0.16 + 0.64 + 0.36)).toFloat(); V3(-0.4f / l, 0.8f / l, -0.6f / l) }
    val particles = listOf(
        V3(2.6f, 0.6f, -1.5f), V3(-2.8f, 1.1f, -0.8f), V3(1.8f, 2.2f, 0.6f), V3(-1.9f, -0.4f, 1.2f),
        V3(3.1f, -1.2f, 0.2f), V3(-3.2f, 2.0f, -1.8f), V3(0.9f, -1.8f, -2.2f), V3(-0.7f, 2.6f, 1.6f),
        V3(2.2f, 1.6f, -2.6f), V3(-2.4f, -1.5f, -0.4f), V3(1.2f, 3.0f, -0.9f), V3(-1.3f, 0.4f, 2.2f)
    )

    Canvas(modifier) {
        val cx = size.width / 2f + tiltX * 26f
        val cy = size.height * 0.46f + tiltY * 18f
        val focal = size.minDimension * 1.15f
        val dist = 7f
        val wobble = sin(angle * 2f) * 0.12f + tiltY * 0.18f

        fun project(v: V3): Offset {
            val s = focal / (focal + v.z + dist)
            return Offset(cx + v.x * focal * s / 3.2f, cy - v.y * focal * s / 3.2f)
        }

        // floor grid: plane y = -2.1, slowly sliding for motion
        val gy = -2.1f
        val slide = (angle * 0.35f) % 1f
        for (i in -6..6) {
            val gx = i.toFloat()
            val a = project(V3(gx, gy, -6f).rotY(angle * 0.15f))
            val b = project(V3(gx, gy, 3f).rotY(angle * 0.15f))
            drawLine(Color(0x33F5C451), a, b, strokeWidth = 1.5f)
        }
        for (j in -6..3) {
            val gz = j.toFloat() + slide
            val a = project(V3(-6f, gy, gz).rotY(angle * 0.15f))
            val b = project(V3(6f, gy, gz).rotY(angle * 0.15f))
            val alpha = ((gz + 6f) / 9f).coerceIn(0.06f, 0.28f)
            drawLine(Color(0xFFFFE9A3).copy(alpha = alpha), a, b, strokeWidth = 1.5f)
        }

        // particles orbiting with the scene
        for (p0 in particles) {
            val p = p0.rotY(angle)
            val s = focal / (focal + p.z + dist)
            val o = project(p)
            val alpha = (s * 1.6f - 0.5f).coerceIn(0.08f, 0.85f)
            drawCircle(Color(0xFFFFE9A3).copy(alpha = alpha), radius = 5f * s + 2f, center = o)
        }

        // the X: collect all faces, sort far-to-near, draw with flat lighting
        data class F(val pts: List<Offset>, val zAvg: Float, val col: Color)
        val all = ArrayList<F>()
        for (m in meshes) {
            val rv = m.verts.map { it.rotY(angle).rotX(wobble) }
            for (f in m.faces) {
                val vs = f.map { rv[it] }
                val e1 = V3(vs[1].x - vs[0].x, vs[1].y - vs[0].y, vs[1].z - vs[0].z)
                val e2 = V3(vs[3].x - vs[0].x, vs[3].y - vs[0].y, vs[3].z - vs[0].z)
                val nx = e1.y * e2.z - e1.z * e2.y
                val ny = e1.z * e2.x - e1.x * e2.z
                val nz = e1.x * e2.y - e1.y * e2.x
                val nl = Math.sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat().takeIf { it > 0f } ?: continue
                val dot = (nx * light.x + ny * light.y + nz * light.z) / nl
                val bright = 0.38f + 0.62f * kotlin.math.abs(dot)
                all.add(F(vs.map { project(it) }, vs.map { it.z }.average().toFloat(), shade(m.color, bright)))
            }
        }
        all.sortBy { it.zAvg }
        val path = Path()
        for (f in all) {
            path.reset()
            path.moveTo(f.pts[0].x, f.pts[0].y)
            for (k in 1 until f.pts.size) path.lineTo(f.pts[k].x, f.pts[k].y)
            path.close()
            drawPath(path, f.col)
        }
    }
}

/** Full-screen 3D arena page (More > 3D Arena). */
@Composable
fun Arena3DScreen(onBack: () -> Unit) {
    androidx.compose.runtime.DisposableEffect(Unit) {
        Tilt3D.start()
        onDispose { Tilt3D.stop() }
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("3D Arena", "Live 3D - tilts with your phone", onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Scene3D(Modifier.fillMaxSize())
            Text(
                if (Fx3D.active()) "Move your phone - the arena tilts with you"
                else "3D effects are off - turn them on in Settings > Feel > 3D effects",
                color = Muted, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp)
            )
        }
    }
}
