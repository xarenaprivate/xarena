package app.xarena.nativeapp

/* Support the Team (native) - mirrors support.html exactly: gold/black, "SCAN TO DONATE" QR from
   site_settings key "support" (public), voluntary note, optional I-paid acknowledgement
   (private support-proofs bucket, 3/day server-side, support_ack_submit RPC).
   No amounts, no pay buttons, no rewards - payment happens in the user's own UPI app. */

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SpGold = Color(0xFFF2C94C)
private val SpGoldSoft = Color(0xFF5C4513)
private val SpBlack = Color(0xFF0B0B0B)
private val SpCream = Color(0xFFBFae86)

@Composable
fun DonateScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var supporters by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        cfg = Api.supportConfig()
        supporters = try {
            val a = Api.supportSupporters(); (0 until a.length()).map { a.getJSONObject(it) }
        } catch (e: Exception) { emptyList() }
        loaded = true
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF050505))) {
        PageHeader("Support the Team", "Voluntary - keeps XARENA free", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!loaded) {
                Spacer(Modifier.height(40.dp)); CircularProgressIndicator(color = SpGold)
            } else {
                val img = cfg?.s("image_url") ?: ""
                val hasQr = img.startsWith("https://")
                // Gold frame
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .border(2.dp, SpGold, RoundedCornerShape(22.dp))
                    .background(SpBlack).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("XARENA ESPORTS", color = SpGold, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    Text("Support the Team", color = SpCream, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(12.dp))
                    if (hasQr) {
                        Text("SCAN TO DONATE", color = SpGold, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Spacer(Modifier.height(8.dp))
                        // Downsampled decode: coil bounds the bitmap to 560px, safe on 2 GB phones.
                        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).border(2.dp, Color(0xFFD4A017), RoundedCornerShape(14.dp)).padding(10.dp)) {
                            AsyncImage(
                                model = ImageRequest.Builder(ctx).data(img).size(560).crossfade(false).build(),
                                contentDescription = "Payment QR code",
                                modifier = Modifier.sizeIn(maxWidth = 280.dp, maxHeight = 280.dp).fillMaxWidth(),
                                contentScale = ContentScale.Fit)
                        }
                        val extra = cfg?.s("text") ?: ""
                        if (extra.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(extra, color = SpCream, fontSize = 13.sp) }
                    } else {
                        Text("COMING SOON", color = SpGold, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 22.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Voluntary support for XARENA. No rewards, no refunds, not linked to any tournament prize. Payment happens in your own UPI app; XARENA does not see or store payment details.",
                        color = SpCream, fontSize = 12.sp, lineHeight = 17.sp)
                }
                if (hasQr) {
                    Spacer(Modifier.height(16.dp))
                    SupportAckCard()
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, SpGoldSoft, RoundedCornerShape(16.dp)).background(SpBlack).padding(14.dp)) {
                    Text("SUPPORTERS", color = SpGold, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("Newest first. Only payments checked by XARENA admins are listed.", color = SpCream, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    if (supporters.isEmpty()) Text("No supporters listed yet.", color = SpCream, fontSize = 13.sp)
                    supporters.take(30).forEach { sp ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(sp.s("name").ifBlank { "Anonymous" }, color = Color(0xFFF3E9CF), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(fmtCr(sp.s("date") + "T00:00:00Z").substringBefore(","), color = SpCream, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportAckCard() {
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<ByteArray?>(null) }
    var txn by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        msg = null
        scope.launch {
            try {
                val cr = Api.ctx.contentResolver
                val mime = cr.getType(uri) ?: "image/jpeg"
                if (mime != "image/jpeg" && mime != "image/png") { msg = "Only JPG or PNG."; return@launch }
                val raw = withContext(Dispatchers.IO) { cr.openInputStream(uri)!!.use { it.readBytes() } }
                if (raw.size > 8 * 1024 * 1024) { msg = "File too large (max 5 MB)."; return@launch }
                picked = Img.downscaleJpeg(raw, maxDim = 1280, quality = 85)
                if (picked == null) msg = "Could not read that image."
            } catch (e: Exception) { msg = "Could not read that image." }
        }
    }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, SpGoldSoft, RoundedCornerShape(16.dp)).background(SpBlack).padding(14.dp)) {
        Text("PAYMENT VERIFICATION (OPTIONAL)", color = SpGold, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        Text("Paid already? You can send a screenshot so we know it arrived. This is optional and gives no reward or benefit.", color = SpCream, fontSize = 12.sp, lineHeight = 16.sp)
        Text("Hide your phone number and UPI id in the screenshot if you can.", color = SpGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (done) {
            Text("Thank you for supporting the XARENA community.", color = SpGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        } else {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, SpGoldSoft, RoundedCornerShape(10.dp))
                .clickable(enabled = !busy) { picker.launch(arrayOf("image/jpeg", "image/png")) }.padding(11.dp)) {
                Text(if (picked != null) "Screenshot picked - ready to send" else "Choose payment screenshot (JPG or PNG)",
                    color = if (picked != null) SpGold else SpCream, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(txn, { txn = it.take(40) }, placeholder = { Text("Transaction id (optional)", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name, { name = it.take(40) }, placeholder = { Text("Name (optional)", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().clickable { consent = !consent }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, SpGold, RoundedCornerShape(4.dp))
                    .background(if (consent) SpGold else Color.Transparent), contentAlignment = Alignment.Center) {
                    if (consent) Text("✓", color = Color(0xFF1A1200), fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(8.dp))
                Text("Show my name publicly on the supporters list. If unticked, you appear as Anonymous.", color = SpCream, fontSize = 12.sp, lineHeight = 15.sp)
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.clip(RoundedCornerShape(10.dp))
                .background(if (picked != null && !busy) SpGold else Color(0xFF3A3018))
                .clickable(enabled = picked != null && !busy) {
                    val bytes = picked ?: return@clickable
                    busy = true; msg = "Sending..."
                    scope.launch {
                        try {
                            val path = Api.uploadSupportProof(bytes)
                            val r = Api.supportAckSubmit(path, txn.trim(), name.trim(), consent)
                            if (r.optBoolean("ok")) done = true
                            else { msg = r.s("error").ifBlank { "Could not send." } }
                        } catch (e: Exception) { msg = e.message ?: "Failed." }
                        busy = false
                    }
                }.padding(horizontal = 18.dp, vertical = 11.dp)) {
                Text(if (busy) "SENDING..." else "SEND", color = Color(0xFF1A1200), fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            msg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = SpGold, fontSize = 12.sp) }
        }
    }
}
