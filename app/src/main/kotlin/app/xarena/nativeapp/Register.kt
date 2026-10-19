package app.xarena.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/** Tournament squad registration - same fields, proof upload and register_squad RPC as the site's form. */
@Composable
fun RegisterScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var squad by remember { mutableStateOf("") }
    var captain by remember { mutableStateOf("") }
    var uid by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var agree by remember { mutableStateOf(false) }
    var proofBytes by remember { mutableStateOf<ByteArray?>(null) }
    var proofName by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var successId by remember { mutableStateOf<String?>(null) }
    var holdNote by remember { mutableStateOf(false) }
    var entryCost by remember { mutableStateOf(0) }
    var apNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { entryCost = Api.tournamentEntryCost("xr-s1") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                proofBytes = withContext(Dispatchers.IO) {
                    try {
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            val raw = input.readBytes()
                            BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
                            var sample = 1
                            while (maxOf(opts.outWidth, opts.outHeight) / sample > 1280) sample *= 2
                            val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
                            val bos = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
                            bos.toByteArray()
                        }
                    } catch (e: Exception) { null }
                }
                proofName = if (proofBytes != null) "Screenshot attached" else "Could not read that image - try another."
            }
        }
    }

    val uidOk = Regex("^[0-9]{8,12}$").matches(uid)
    val phoneOk = Regex("^[0-9]{10}$").matches(phone)
    val ready = squad.isNotBlank() && captain.isNotBlank() && uidOk && phoneOk && agree && proofBytes != null && !busy

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Brand()
        if (successId != null) {
            PanelCard {
                Text("REGISTRATION CONFIRMED", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(successId!!, color = Gold, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text("Screenshot this ID - it is your proof of entry for Season 1.", color = Color.White, fontSize = 13.sp)
                apNote?.let { Text(it, color = Gold2, fontSize = 13.sp) }
                if (holdNote) Text("Note: your entry is under a quick security review (routine). It shows in the management panel and gets verified there.", color = Gold2, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                GoldButton("BACK TO TOURNAMENTS", onClick = onBack)
            }
            return@Column
        }
        PanelCard {
            Text("Season 1 - Squad Registration", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Takes under a minute. Your Registration ID is your proof of entry - screenshot it.", color = Muted, fontSize = 12.sp)
            if (entryCost > 0) {
                Spacer(Modifier.height(8.dp))
                Text("Entry fee: " + entryCost + " AP (arena points - refunded automatically if your entry is rejected or the tournament is cancelled)", color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(squad, { squad = it }, label = { Text("Squad name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(captain, { captain = it }, label = { Text("Captain in-game name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(uid, { uid = it.filter { c -> c.isDigit() }.take(12) }, label = { Text("Captain Free Fire UID (8-12 digits)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() }.take(10) }, label = { Text("WhatsApp number (10 digits)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = agree, onCheckedChange = { agree = it })
                Text("We accept the XARENA Rules and Regulations - no hacks, no teaming, no match-fixing.", color = Color.White, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text("UID screenshot proof (required)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Free Fire lobby profile with your UID visible. The owner personally verifies it. Fake proof = rejected + reported.", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            GoldButton(if (proofBytes != null) "SCREENSHOT ATTACHED - TAP TO CHANGE" else "ATTACH UID SCREENSHOT") {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            proofName?.let { Text(it, color = if (proofBytes != null) Color(0xFF4ADE80) else ErrRed, fontSize = 12.sp) }
            err?.let { Spacer(Modifier.height(8.dp)); Text(it, color = ErrRed, fontSize = 13.sp) }
            Spacer(Modifier.height(14.dp))
            GoldButton(if (busy) "SUBMITTING..." else "REGISTER SQUAD", enabled = ready) {
                busy = true; err = null
                scope.launch {
                    try {
                        val regId = "XR-S1-" + Random.nextInt(1000, 10000)
                        val path = Api.uploadProof(regId, proofBytes!!)
                        val r = if (entryCost > 0) Api.registerSquadAp(regId, squad.trim(), captain.trim(), uid, phone, path)
                            else Api.registerSquad(regId, squad.trim(), captain.trim(), uid, phone, path)
                        if (r.optBoolean("ok")) {
                            Api.myRegsAdd(regId, squad.trim())
                            holdNote = r.optBoolean("hold") || r.optBoolean("review")
                            val charged = r.optInt("ap_charged", 0)
                            if (charged > 0) apNote = charged.toString() + " AP charged. Balance: " + r.optInt("balance", -1).takeIf { it >= 0 }?.toString() + " AP."
                            successId = regId
                        } else {
                            val cost = r.optInt("cost", 0); val bal = r.optInt("balance", -1)
                            err = if (cost > 0 && bal >= 0) "Need " + cost + " AP to enter - you have " + bal + " AP."
                                else r.s("error").ifBlank { "Could not register right now. Try again." }
                        }
                    } catch (e: Exception) { err = e.message ?: "Could not register right now. Try again." }
                    busy = false
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("One UID and one phone number get exactly one registration - enforced server-side.", color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("Back to tournaments", color = Gold2, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally)
            .clip(RoundedCornerShape(6.dp)).background(Color(0x22FFFFFF))
            .clickable { onBack() }
            .padding(horizontal = 14.dp, vertical = 8.dp))
        Spacer(Modifier.height(24.dp))
    }
}
