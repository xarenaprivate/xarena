package app.xarena.nativeapp

import android.graphics.Bitmap
import android.os.Environment
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.*
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShareSmokeTest {
    @get:Rule val launch = ActivityScenarioRule(MainActivity::class.java)
    @Test fun shareTextAndImageReturnToLiveActivity() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val ctx = inst.targetContext
        val out = File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "smoke").apply { mkdirs() }
        Thread.sleep(2200)
        device.takeScreenshot(File(out, "01-login.png"))
        launch.scenario.onActivity { ShareLauncher.text(it, "Share smoke test", "Local emulator test only, not sent") }
        device.waitForIdle(); Thread.sleep(1800)
        device.takeScreenshot(File(out, "02-text-chooser.png"))
        Assert.assertNotEquals("Share chooser should leave app foreground", ctx.packageName, device.currentPackageName)
        device.pressBack(); device.waitForIdle()
        launch.scenario.onActivity { Assert.assertFalse(it.isFinishing) }
        val image = File(ctx.cacheDir, "share/emulator-fixture.png").apply { parentFile!!.mkdirs() }
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) }
            .let { b -> image.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle() }
        launch.scenario.onActivity { ShareLauncher.image(it.applicationContext, "Share image smoke test", image) }
        device.waitForIdle(); Thread.sleep(1800)
        device.takeScreenshot(File(out, "03-image-chooser.png"))
        Assert.assertNotEquals(ctx.packageName, device.currentPackageName)
        device.pressBack(); device.waitForIdle()
        launch.scenario.onActivity { Assert.assertFalse(it.isFinishing) }
        device.takeScreenshot(File(out, "04-return-to-login.png"))
    }
    @Test fun actualPosterAndCertificateRenderersWithoutAccountEffects() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val ctx = inst.targetContext
        val out = File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "smoke").apply { mkdirs() }
        val poster = runBlocking { ShareCard.buildStory(ctx, JSONObject().put("name", "TEST FIXTURE CUP").put("date_text", "LOCAL RENDER TEST").put("slots", 12).put("prize", "Cosmetic rewards only")) }
        Assert.assertNotNull(poster)
        Assert.assertTrue(poster!!.length() > 0)
        poster.copyTo(File(out, "05-poster-FIXTURE.png"), true)
        launch.scenario.onActivity { ShareCard.share(it, poster) }
        device.waitForIdle(); Thread.sleep(1800)
        device.takeScreenshot(File(out, "06-poster-chooser.png"))
        Assert.assertNotEquals(ctx.packageName, device.currentPackageName)
        device.pressBack(); device.waitForIdle()
        val cert = certPngBitmap("champion", "TEST FIXTURE PLAYER", "LOCAL TEST CUP", "TEST ONLY")
        val file = File(ctx.cacheDir, "certs/fixture.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { cert.compress(Bitmap.CompressFormat.PNG, 100, it) }; cert.recycle()
        file.copyTo(File(out, "07-certificate-FIXTURE.png"), true)
        launch.scenario.onActivity { ShareLauncher.image(it.applicationContext, "Share certificate", file) }
        device.waitForIdle(); Thread.sleep(1800)
        device.takeScreenshot(File(out, "08-certificate-chooser.png"))
        Assert.assertNotEquals(ctx.packageName, device.currentPackageName)
        device.pressBack(); device.waitForIdle()
        for (label in listOf("Share", "Share squad link", "Share invite code", "Share victory")) {
            launch.scenario.onActivity { ShareLauncher.text(it.applicationContext, label, "LOCAL TEST FIXTURE - NOT SENT") }
            device.waitForIdle(); Thread.sleep(500)
            Assert.assertNotEquals(ctx.packageName, device.currentPackageName)
            device.pressBack(); device.waitForIdle()
        }
        launch.scenario.onActivity { Assert.assertFalse(it.isFinishing) }
        device.takeScreenshot(File(out, "09-final-return.png"))
    }

}
