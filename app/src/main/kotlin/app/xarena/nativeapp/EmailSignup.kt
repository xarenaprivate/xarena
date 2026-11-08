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

/* Additive email-code signup against the xarena-email-auth edge function (provisional contract).
   Ships dark: entry points only appear when the server feature flag "email_signup" is explicitly on.
   Verify creates the account server-side (email confirmed) and returns NO session - the player then
   logs in normally with email + password. No recovery is offered here: email recovery is a separate,
   untested flow and is not claimed. */
@Composable
fun EmailSignupScreen(onDone: () -> Unit, onGoLogin: () -> Unit) {
    var step by remember { mutableIntStateOf(0) } // 0 = email, 1 = code + password, 2 = created
    var email by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf<String?>(null) }
    var masked by remember { mutableStateOf("") }
    var expiresIn by remember { mutableIntStateOf(0) }
    var code by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var resendIn by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(resendIn) { if (resendIn > 0) { kotlinx.coroutines.delay(1000); resendIn-- } }
    AuthCard("Sign up with email") {
        when (step) {
            0 -> {
                OutlinedTextField(email, { email = it }, label = { Text("Your email address") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                Text("We send a 6-digit verification code to this email. The email becomes your login ID.", color = Muted, fontSize = 12.sp)
                err?.let { Spacer(Modifier.height(6.dp)); Text(it, color = ErrRed, fontSize = 13.sp) }
                Spacer(Modifier.height(10.dp))
                GoldButton(if (busy) "SENDING CODE..." else "SEND CODE", enabled = !busy && email.contains("@")) {
                    busy = true; err = null
                    scope.launch {
                        try {
                            val j = Api.emailAuth("signup_start", JSONObject().put("email", email.trim()))
                            if (!j.optBoolean("ok")) throw Api.ApiError(j.optString("error").ifBlank { "Could not start verification." })
                            challenge = j.optString("challenge_id")
                            if (challenge.isNullOrBlank()) throw Api.ApiError("Server did not start verification.")
                            masked = j.optString("masked_email").ifBlank { email.trim() }
                            expiresIn = j.optInt("expires_in", 600)
                            resendIn = j.optInt("resend_after", 60)
                            step = 1
                        } catch (e: Exception) { err = e.message ?: "Could not send code." }
                        busy = false
                    }
                }
            }
            1 -> {
                Text("Enter the 6-digit code sent to $masked." + if (expiresIn > 0) " It expires in ${expiresIn / 60} minutes." else "", color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(code, { v -> if (v.length <= 6 && v.all { it.isDigit() }) code = v }, label = { Text("6-digit code") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(pw, { pw = it }, label = { Text("Password (8+ chars)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(pw2, { pw2 = it }, label = { Text("Confirm password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                err?.let { Spacer(Modifier.height(6.dp)); Text(it, color = ErrRed, fontSize = 13.sp) }
                Spacer(Modifier.height(10.dp))
                GoldButton(if (busy) "CREATING..." else "VERIFY + CREATE ACCOUNT", enabled = !busy && code.length == 6 && pw.length >= 8 && pw == pw2) {
                    busy = true; err = null
                    scope.launch {
                        try {
                            val j = Api.emailAuth("signup_verify", JSONObject().put("challenge_id", challenge).put("code", code).put("password", pw))
                            if (!j.optBoolean("ok")) throw Api.ApiError(j.optString("error").ifBlank { "Verification failed." })
                            step = 2
                        } catch (e: Exception) { err = e.message ?: "Verification failed." }
                        busy = false
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(if (resendIn > 0) "Resend code in ${resendIn}s" else "Resend code", color = if (resendIn > 0 || busy) Muted else Gold2, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clickable(enabled = resendIn <= 0 && !busy) {
                        busy = true; err = null
                        scope.launch {
                            try {
                                val j = Api.emailAuth("signup_resend", JSONObject().put("email", email.trim()))
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
                Text("Account created.", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Log in with $masked and your password.", color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                GoldButton("GO TO LOGIN") { onGoLogin() }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Already have an account? Login", color = Gold2, fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onGoLogin() }.padding(4.dp))
    }
}
