package app.xarena.nativeapp

import app.cash.paparazzi.Paparazzi
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import org.junit.Rule
import org.junit.Test

/* Synthetic render fixtures only - no real emails, accounts or sends. States injected via init params. */
class EmailVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.Light.NoActionBar")
    @Test fun signupEmailStep() {
        Api.init(paparazzi.context)
        paparazzi.snapshot { MaterialTheme { EmailSignupScreen({}, {}) } }
    }
    @Test fun signupCodeStepCooldown() {
        Api.init(paparazzi.context)
        paparazzi.snapshot { MaterialTheme { EmailSignupScreen({}, {}, initStep = 1, initChallenge = "fixture-challenge", initMasked = "p***@xarena", initResendIn = 42) } }
    }
    @Test fun signupErrorState() {
        Api.init(paparazzi.context)
        paparazzi.snapshot { MaterialTheme { EmailSignupScreen({}, {}, initStep = 1, initChallenge = "fixture-challenge", initMasked = "p***@xarena", initErr = "Wrong or expired code.") } }
    }
    @Test fun signupDone() {
        Api.init(paparazzi.context)
        paparazzi.snapshot { MaterialTheme { EmailSignupScreen({}, {}, initStep = 2, initMasked = "p***@xarena") } }
    }
    @Test fun recoveryEmailStep() {
        Api.init(paparazzi.context)
        paparazzi.snapshot { MaterialTheme { EmailRecoveryScreen({}) } }
    }
    @Test fun recoveryCodeStep() {
        Api.init(paparazzi.context)
        paparazzi.snapshot { MaterialTheme { EmailRecoveryScreen({}, initStep = 1, initChallenge = "fixture-challenge", initResendIn = 30) } }
    }
    @Test fun recoveryDone() {
        Api.init(paparazzi.context)
        paparazzi.snapshot { MaterialTheme { EmailRecoveryScreen({}, initStep = 2) } }
    }
}
