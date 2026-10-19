package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class SquadData(
    val squads: JSONArray,
    val mySquadId: Long?,
    val myRole: String?,
    val invites: JSONArray,
    val rankLine: String?,
    val wars: JSONArray,
    val creator: Boolean,
    val creatorPending: Boolean
)

private suspend fun loadSquads(): SquadData {
    val squads = Api.squadsPublic()
    var myId: Long? = null; var myRole: String? = null
    try {
        val uid = Api.uid
        if (uid != null) {
            val m = Api.authedGetArray("/rest/v1/squad_members?user_id=eq.$uid&select=squad_id,role")
            if (m.length() > 0) { myId = m.getJSONObject(0).optLong("squad_id"); myRole = m.getJSONObject(0).s("role") }
        }
    } catch (e: Exception) { }
    val invites = try { Api.authedRpc("my_squad_invites", JSONObject()).optJSONArray("invites") ?: JSONArray() } catch (e: Exception) { JSONArray() }
    val rankLine = try {
        val r = Api.authedRpc("my_squad_rank", JSONObject())
        if (r.optBoolean("ok") && r.has("rank") && !r.isNull("rank")) {
            val row = r.optJSONObject("row")
            "Battle board: #" + r.optInt("rank") + " of " + r.optInt("total") + " squads" +
                (if (row != null) " - " + row.optInt("total") + " pts" else "")
        } else null
    } catch (e: Exception) { null }
    val wars = try { Api.authedRpc("wars_board", JSONObject()).optJSONArray("wars") ?: JSONArray() } catch (e: Exception) { JSONArray() }
    var creator = false; var pending = false
    try {
        val st = Api.authedRpc("squad_creator_status", JSONObject())
        if (st.optBoolean("ok")) { creator = st.optBoolean("creator"); pending = st.optBoolean("pending") }
    } catch (e: Exception) { }
    return SquadData(squads, myId, myRole, invites, rankLine, wars, creator, pending)
}

@Composable
fun SquadsScreen(onBack: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    var nonce by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        if (onBack != null) PageHeader("Squads", "Find or build your squad", onBack) else Brand()
        LoadState({ loadSquads() }) { d ->
            LazyColumn {
                item {
                    PanelCard {
                        if (d.mySquadId != null) {
                            val mine = (0 until d.squads.length()).map { d.squads.getJSONObject(it) }.firstOrNull { it.optLong("id") == d.mySquadId }
                            Text("Your squad: " + (mine?.s("name") ?: "Squad") + (mine?.s("tag")?.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""),
                                color = Gold2, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Role: " + (d.myRole ?: "member"), color = Muted, fontSize = 13.sp)
                            d.rankLine?.let { Text(it, color = Color.White, fontSize = 13.sp) }
                        } else {
                            Text("You are not in a squad yet", color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Join any squad below. One squad per player.", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
                if (d.mySquadId == null) item {
                    when {
                        d.creator -> CreateSquadCard { notice = it; nonce++ }
                        d.creatorPending -> PanelCard {
                            Text("Creator access requested", color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("The owner reviews every request - you will be able to create your squad here once approved.", color = Muted, fontSize = 13.sp)
                        }
                        else -> RequestCreatorCard { notice = it; nonce++ }
                    }
                }
                if (d.mySquadId != null && d.myRole == "captain") item {
                    CaptainManageCard(d.mySquadId) { notice = it; nonce++ }
                }
                if (d.invites.length() > 0) item {
                    PanelCard {
                        Text("Squad invites", color = Gold, fontWeight = FontWeight.Bold)
                        (0 until d.invites.length()).forEach { i ->
                            val inv = d.invites.getJSONObject(i)
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(inv.s("squad") + " (by " + inv.s("by", "the captain") + ")",
                                    color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("Accept", color = Color(0xFF2A1400), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Gold)
                                        .clickable {
                                            scope.launch {
                                                try {
                                                    val r = Api.authedRpc("squad_invite_respond", JSONObject().put("p_id", inv.optLong("id")).put("p_accept", true))
                                                    notice = if (r.optBoolean("ok")) "Invite accepted." else r.s("error").ifBlank { "Could not answer the invite." }
                                                } catch (e: Exception) { notice = e.message }
                                                nonce++
                                            }
                                        }.padding(horizontal = 10.dp, vertical = 6.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Decline", color = Muted, fontSize = 12.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x22FFFFFF))
                                        .clickable {
                                            scope.launch {
                                                try { Api.authedRpc("squad_invite_respond", JSONObject().put("p_id", inv.optLong("id")).put("p_accept", false)) } catch (e: Exception) { }
                                                nonce++
                                            }
                                        }.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
                item { Text("All squads", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                if (d.squads.length() == 0) item { EmptyNote("No squads yet", "The first approved creator squad will appear here. Join requests open as soon as squads exist.") }
                items((0 until d.squads.length()).map { d.squads.getJSONObject(it) }) { s ->
                    PanelCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(s.s("name") + (s.s("tag").takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""),
                                color = Gold2, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(if (s.has("member_count")) s.optInt("member_count").toString() + "/6" else "", color = Muted, fontSize = 12.sp)
                        }
                        if (s.s("bio").isNotBlank()) Text(s.s("bio"), color = Muted, fontSize = 13.sp)
                        val members = s.optJSONArray("members")
                        if (members != null && members.length() > 0) {
                            Text((0 until members.length()).joinToString("  ") { i ->
                                val m = members.getJSONObject(i)
                                (m.s("ign").ifBlank { m.s("username") }) + (if (m.s("role") == "captain") " (captain)" else "")
                            }, color = Color.White, fontSize = 12.sp)
                        }
                        if (d.mySquadId == null && s.has("id") && !s.isNull("id") && s.optInt("member_count", 0) < 6) {
                            Spacer(Modifier.height(8.dp))
                            GoldButton("JOIN THIS SQUAD") {
                                scope.launch {
                                    try {
                                        val r = Api.authedRpc("join_squad", JSONObject().put("p_squad", s.optLong("id")))
                                        notice = if (r.optBoolean("ok")) "Joined " + s.s("name") + "!" else r.s("error").ifBlank { "Could not join right now." }
                                    } catch (e: Exception) { notice = e.message ?: "Could not join right now." }
                                    nonce++
                                }
                            }
                        }
                    }
                }
                if (d.wars.length() > 0) {
                    item { Text("Squad wars", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                    items((0 until d.wars.length()).map { d.wars.getJSONObject(it) }) { w ->
                        PanelCard {
                            Text(w.s("challenger") + " vs " + w.s("opponent"), color = Color.White, fontWeight = FontWeight.Bold)
                            Text(w.s("status").uppercase() + (if (w.s("winner").isNotBlank()) " - Winner: " + w.s("winner") else ""),
                                color = Gold, fontSize = 12.sp)
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
        notice?.let {
            Text(it, color = Gold2, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun SquadField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    BasicTextField(value, onChange, modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ink).padding(11.dp),
        textStyle = TextStyle(color = Color.White, fontSize = 14.sp), singleLine = true,
        decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = Muted, fontSize = 14.sp); inner() })
}

@Composable
private fun CreateSquadCard(onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    PanelCard {
        Text("Create your squad", color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("You are an approved creator. You become the captain - one squad per player, 6 players max.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        SquadField(name, { name = it }, "Squad name")
        Spacer(Modifier.height(6.dp))
        SquadField(tag, { tag = it.uppercase().take(5) }, "Tag (up to 5 letters)")
        Spacer(Modifier.height(6.dp))
        SquadField(bio, { bio = it }, "Bio (optional)")
        Spacer(Modifier.height(10.dp))
        GoldButton(if (busy) "CREATING..." else "CREATE SQUAD") {
            if (busy) return@GoldButton
            if (name.trim().length < 3) { onDone("Give the squad a name (3+ characters)."); return@GoldButton }
            busy = true
            scope.launch {
                try {
                    val r = Api.authedRpc("create_squad", JSONObject().put("p_name", name.trim()).put("p_tag", tag.trim()).put("p_bio", bio.trim()))
                    onDone(if (r.optBoolean("ok")) "Squad created - you are the captain." else r.s("error").ifBlank { "Could not create the squad." })
                } catch (e: Exception) { onDone(e.message ?: "Could not create the squad.") ; busy = false }
            }
        }
    }
}

@Composable
private fun RequestCreatorCard(onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    PanelCard {
        Text("Want to create a squad?", color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Only approved creators can start squads (this keeps squads real). Request access and the owner will review it.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        SquadField(note, { note = it }, "Why should you host a squad? (optional)")
        Spacer(Modifier.height(10.dp))
        GoldButton(if (busy) "SENDING..." else "REQUEST CREATOR ACCESS") {
            if (busy) return@GoldButton
            busy = true
            scope.launch {
                try {
                    val r = Api.authedRpc("request_squad_creator", JSONObject().put("p_note", note.trim()))
                    onDone(if (r.optBoolean("ok")) "Request sent - the owner will review it." else r.s("error").ifBlank { "Could not send the request." })
                } catch (e: Exception) { onDone(e.message ?: "Could not send the request."); busy = false }
            }
        }
    }
}

@Composable
private fun CaptainManageCard(squadId: Long, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var invite by remember { mutableStateOf("") }
    var showGallery by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    if (showGallery) {
        GallerySheet(emptyList(), onPick = { uri ->
            showGallery = false; busy = true
            scope.launch {
                try {
                    val cr = Api.ctx.contentResolver
                    val bytes = withContext(Dispatchers.IO) { cr.openInputStream(uri)!!.use { it.readBytes() } }
                    if (bytes.size > 8 * 1024 * 1024) throw Exception("Image too large (max 8 MB).")
                    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw Exception("Pick an image file (PNG or JPG).")
                    if (maxOf(bmp.width, bmp.height) > 512) {
                        val f = 512f / maxOf(bmp.width, bmp.height)
                        bmp = android.graphics.Bitmap.createScaledBitmap(bmp, Math.round(bmp.width * f), Math.round(bmp.height * f), true)
                    }
                    val baos = java.io.ByteArrayOutputStream()
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
                    val url = Api.cloudinaryUpload(baos.toByteArray())
                    val r = Api.authedRpc("set_squad_logo", JSONObject().put("p_squad", squadId).put("p_logo", url))
                    onDone(if (r.optBoolean("ok")) "Team logo saved." else r.s("error").ifBlank { "Could not save the logo." })
                } catch (e: Exception) { onDone(e.message ?: "Could not save the logo.") }
                busy = false
            }
        }, onConfirm = { showGallery = false }, onCancel = { showGallery = false })
    }
    PanelCard {
        Text("Captain tools", color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquadField(invite, { invite = it }, "Invite player by name", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("Invite", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable {
                    val nm = invite.trim()
                    if (nm.isEmpty()) return@clickable
                    scope.launch {
                        try {
                            val r = Api.authedRpc("squad_invite", JSONObject().put("p_name", nm))
                            onDone(if (r.optBoolean("ok")) "Invite sent to " + nm + "." else r.s("error").ifBlank { "Could not send the invite." })
                            invite = ""
                        } catch (e: Exception) { onDone(e.message ?: "Could not send the invite.") }
                    }
                }.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(if (busy) "Uploading logo..." else "Change team logo", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { if (!busy) showGallery = true }.padding(vertical = 6.dp))
        Spacer(Modifier.height(4.dp))
        Row {
            Text("Leave squad", color = Muted, fontSize = 13.sp,
                modifier = Modifier.clickable {
                    scope.launch {
                        try {
                            val r = Api.authedRpc("leave_squad", JSONObject())
                            onDone(if (r.optBoolean("ok")) "You left the squad." else r.s("error").ifBlank { "Could not leave." })
                        } catch (e: Exception) { onDone(e.message ?: "Could not leave.") }
                    }
                }.padding(vertical = 6.dp))
            Spacer(Modifier.width(18.dp))
            Text("Disband squad", color = ErrRed, fontSize = 13.sp,
                modifier = Modifier.clickable {
                    scope.launch {
                        try {
                            val r = Api.authedRpc("disband_squad", JSONObject().put("p_squad", squadId))
                            onDone(if (r.optBoolean("ok")) "Squad disbanded." else r.s("error").ifBlank { "Could not disband." })
                        } catch (e: Exception) { onDone(e.message ?: "Could not disband.") }
                    }
                }.padding(vertical = 6.dp))
        }
    }
}
