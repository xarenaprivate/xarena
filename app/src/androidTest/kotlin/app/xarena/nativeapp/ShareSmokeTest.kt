package app.xarena.nativeapp

import android.graphics.Bitmap
import android.os.Environment
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.*
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
        device.waitForIdle(); Thread.sleep(800)
        device.takeScreenshot(File(out, "02-text-chooser.png"))
        Assert.assertNotEquals("Share chooser should leave app foreground", ctx.packageName, device.currentPackageName)
        device.pressBack(); device.waitForIdle()
        launch.scenario.onActivity { Assert.assertFalse(it.isFinishing) }
        val image = File(ctx.cacheDir, "share/emulator-fixture.png").apply { parentFile!!.mkdirs() }
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) }
            .let { b -> image.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle() }
        launch.scenario.onActivity { ShareLauncher.image(it.applicationContext, "Share image smoke test", image) }
        device.waitForIdle(); Thread.sleep(800)
        device.takeScreenshot(File(out, "03-image-chooser.png"))
        Assert.assertNotEquals(ctx.packageName, device.currentPackageName)
        device.pressBack(); device.waitForIdle()
        launch.scenario.onActivity { Assert.assertFalse(it.isFinishing) }
        device.takeScreenshot(File(out, "04-return-to-login.png"))
    }
}
