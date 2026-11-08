package app.xarena.nativeapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

/* Binding-aware email recovery (xarena-email-auth recovery_* actions, provisional v4 contract).
   Ships dark behind remote flag "email_recovery". Works ONLY for accounts the server has an
   email-ownership binding for; phone-key recovery remains the existing separate path.
   Verify sets a new password server-side (old refresh sessions revoked by the server; already-issued
   access tokens may live until expiry) and returns NO session - the player logs in normally after. */
@Composable
fun EmailRecoveryScreen(onGoLogin: () -> Unit,
    initStep: Int = 0, initChallenge: String? = null, initErr: String? = null, initResendIn: Int = 0) {
    var step by remember { mutableIntStateOf(initStep) } // 0 = email, 1 = code + new password, 2 = done
    var email by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf<String?>(initChallenge) }
    var code by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(initErr) }
    var resendIn by remember { mutableIntStateOf(initResendIn) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(resendIn) { if (resendIn > 0) { kotlinx.coroutines.delay(1000); resendIn-- } }
    AuthCard("Recover with email") {
        when (step) {
            0 -> {
                Text("For accounts created with email verification. For phone accounts use your recovery key.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Your email address") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), colors = authFieldColors(), modifier = Modifier.fillMaxWidth())
                err?.let { Spacer(Modifier.height(6.dp)); Text(it, color = ErrRed, fontSize = 13.sp) }
                Spacer(Modifier.height(10.dp))
                GoldButton(if (busy) "SENDING CODE..." else "SEND CODE", enabled = !busy && email.contains("@")) {
                    busy = true; err = null
                    scope.launch {
                        try {
                            val j = Api.emailAuth("recovery_start", JSONObject().put("email", email.trim()))
                            if (!j.optBoolean("ok")) throw Api.ApiError(j.optString("error").ifBlank { "Could not start recovery." })
                            challenge = j.optString("challenge_id")
                            if (challenge.isNullOrBlank()) throw Api.ApiError("Server did not start recovery.")
                            resendIn = j.optInt("resend_after", 60)
                            step = 1
                        } catch (e: Exception) { err = e.message ?: "Could not send code." }
                        busy = false
                    }
                }
            }
            1 -> {
                Text("Enter the 6-digit code sent to your email and choose a new password.", color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(code, { v -> if (v.length <= 6 && v.all { it.isDigit() }) code = v }, label = { Text("6-digit code") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), colors = authFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(pw, { pw = it }, label = { Text("New password (8+ chars)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = authFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(pw2, { pw2 = it }, label = { Text("Confirm new password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = authFieldColors(), modifier = Modifier.fillMaxWidth())
                err?.let { Spacer(Modifier.height(6.dp)); Text(it, color = ErrRed, fontSize = 13.sp) }
                Spacer(Modifier.height(10.dp))
                GoldButton(if (busy) "RESETTING..." else "SET NEW PASSWORD", enabled = !busy && code.length == 6 && pw.length >= 8 && pw == pw2) {
                    busy = true; err = null
                    scope.launch {
                        try {
                            val j = Api.emailAuth("recovery_verify", JSONObject().put("challenge_id", challenge).put("code", code).put("password", pw))
                            if (!j.optBoolean("ok")) throw Api.ApiError(j.optString("error").ifBlank { "Reset failed." })
                            step = 2
                        } catch (e: Exception) { err = e.message ?: "Reset failed." }
                        busy = false
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(if (resendIn > 0) "Resend code in ${resendIn}s" else "Resend code", color = if (resendIn > 0 || busy) Muted else Gold2, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clickable(enabled = resendIn <= 0 && !busy) {
                        busy = true; err = null
                        scope.launch {
                            try {
                                val j = Api.emailAuth("recovery_resend", JSONObject().put("email", email.trim()))
                                if (!j.optBoolean("ok")) throw Api.ApiError(j.optString("error").ifBlank { "Could not resend." })
                                challenge = j.optString("challenge_id").ifBlank { challenge }
                                resendIn = j.optInt("resend_after", 60)
                                code = ""
                            } catch (e: Exception) { err = e.message ?: "Could not resend." }
                            busy = false
                        }
                    }.padding(6.dp))
            }
            else -> {
                Text("Password updated.", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Log in with your email and new password.", color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                GoldButton("GO TO LOGIN") { onGoLogin() }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Back to login", color = Gold2, fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onGoLogin() }.padding(4.dp))
    }
}
