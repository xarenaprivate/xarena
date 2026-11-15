package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

object VoiceLookSeams { var newLook = true }

/* Voice in-call PTT "new look" (v0.17.60) from the owner's mockup spec (screen 8),
   restyled into the dark/gold XARENA theme per owner direction. ADDITIVE ONLY -
   the "Classic" chip returns to the previous in-call layout.
   Real data only: speaking glow = real remote audio level (WebRTC stats), latency =
   real RTT, mic/listener state = presence track. Push-to-talk is a real mic mode:
   the mic starts muted and transmits only while the pill is held. No screen share
   and no live streaming controls - those features are rejected. Per-user volume
   uses real WebRTC track volume. */
@Composable
fun VoicePttLook(
    roomLabel: String,
    peers: List<VoicePeer>,
    engine: VoiceEngine?,
    muted: Boolean,
    inLobby: Boolean,
    handRaised: Boolean,
    onExit: () -> Unit,
    onMute: () -> Unit,
    onHand: () -> Unit,
    onLeave: () -> Unit,
    onClassic: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var rttMs by remember { mutableStateOf(engine?.lastRttMs) }
    var speakerUi by remember { mutableStateOf(engine?.speakerOn ?: true) }
    var deafenUi by remember { mutableStateOf(engine?.deafened ?: false) }
    val volumes = remember { mutableStateMapOf<String, Float>() }
    val mutedNow = rememberUpdatedState(muted)
    var holding by remember { mutableStateOf(false) }
    val green = Color(0xFF22C55E)
    /* PTT semantics: mic starts muted when the room view opens. */
    LaunchedEffect(Unit) { if (engine?.listenOnly != true && engine?.muted == false) onMute() }
    LaunchedEffect(Unit) { while (isActive) { rttMs = engine?.lastRttMs; speakerUi = engine?.speakerOn ?: true; delay(2000) } }
    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("<", color = Color.White, fontSize = 24.sp, modifier = Modifier.clickable { onExit() }.padding(horizontal = 10.dp, vertical = 2.dp))
                Column(Modifier.weight(1f)) {
                    Text("VOICE ROOM", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(green))
                        Spacer(Modifier.width(5.dp))
                        Text("LIVE - " + (peers.size + 1) + " player" + (if (peers.isEmpty()) "" else "s"), color = green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("SET", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Muted, RoundedCornerShape(8.dp)).clickable { showSettings = true }.padding(horizontal = 10.dp, vertical = 6.dp))
                Spacer(Modifier.width(8.dp))
                Text("Classic", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Muted, RoundedCornerShape(8.dp)).clickable { onClassic() }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(roomLabel, color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("PUSH-TO-TALK ROOM", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Gold).padding(horizontal = 8.dp, vertical = 3.dp))
            }
            /* Speakers: big circular cards, gold glow ring on the live speaker (real audio level). */
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(Modifier.clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Brush.verticalGradient(listOf(if (muted) Look.cardBorder else Gold, Look.cardBorder)), RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF1A0E33), Look.cardBg)))
                        .padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!muted && holding) Box(Modifier.size(96.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Gold.copy(alpha = .45f), Color.Transparent))))
                            Box(Modifier.size(76.dp).clip(CircleShape).background(Look.cardBorder).border(3.dp, if (!muted) Gold else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                                Text("YOU", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("You", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (engine?.listenOnly == true) "LISTENING" else if (muted) "QUIET - HOLD TO TALK" else "SPEAKING",
                            color = if (muted) Muted else green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                items(peers) { p ->
                    Column(Modifier.clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Brush.verticalGradient(listOf(if (p.speaking) Gold else Look.cardBorder, Look.cardBorder)), RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(listOf(if (p.speaking) Color(0xFF1A0E33) else Look.cardBg, Look.cardBg)))
                        .padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            if (p.speaking) Box(Modifier.size(96.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Gold.copy(alpha = .45f), Color.Transparent))))
                            Box(Modifier.size(76.dp).clip(CircleShape).background(Look.cardBorder).border(3.dp, if (p.speaking) Gold else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                                Text(xrInitial(p.handle), color = Gold2, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(p.handle, color = if (p.speaking) Color.White else Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (p.muted) "MUTED" else if (p.speaking) "SPEAKING" else if (p.listener) "LISTENING" else "QUIET",
                            color = if (p.muted) Color(0xFFF59E0B) else if (p.speaking) green else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        if (p.conn.isNotBlank() && p.conn != "connected") Text(p.conn, color = if (p.conn == "failed") ErrRed else Muted, fontSize = 9.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(green))
                Spacer(Modifier.width(6.dp))
                Text("You are connected to voice" + (rttMs?.let { " - latency " + it + "ms" } ?: ""), color = Muted, fontSize = 11.sp)
            }
            if (engine?.listenOnly == true) {
                Box(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(14.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(14.dp)).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text("LISTENING - MIC OFF", color = Gold2, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (holding) Gold else Color(0xFF1A0E33))
                    .border(2.dp, Gold, RoundedCornerShape(18.dp))
                    .pointerInput(engine) {
                        detectTapGestures(onPress = {
                            holding = true
                            if (mutedNow.value) onMute()
                            tryAwaitRelease()
                            holding = false
                            if (!mutedNow.value) onMute()
                        })
                    }
                    .padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        XrIconMic(if (holding) Ink else Gold, 22.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(if (holding) "SPEAKING - RELEASE TO MUTE" else "PUSH TO TALK", color = if (holding) Ink else Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (speakerUi) Color(0xFFF59E0B) else Look.cardBorder).clickable { speakerUi = engine?.toggleSpeaker() ?: speakerUi }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text("SPEAKER", color = if (speakerUi) Ink else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF7A2E2E)).border(1.dp, ErrRed, RoundedCornerShape(12.dp)).clickable { onLeave() }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text("LEAVE", color = ErrRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (showSettings) {
            Column(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { showSettings = false }, verticalArrangement = Arrangement.Bottom) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(Ink).border(1.dp, Look.cardBorder, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).clickable(enabled = false) {}.padding(16.dp)) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Muted).align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Voice Room Settings & Controls", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("X", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showSettings = false }.padding(6.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(green))
                        Spacer(Modifier.width(6.dp))
                        Text("LIVE - " + roomLabel, color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("PLAYERS - " + (peers.size + 1), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                    peers.forEach { p ->
                        val vol = volumes[p.id] ?: 100f
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(Look.cardBorder), contentAlignment = Alignment.Center) {
                                Text(xrInitial(p.handle), color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.handle + (if (p.muted) " (muted)" else if (p.speaking) " (speaking)" else ""), color = Color.White, fontSize = 13.sp, maxLines = 1)
                                Slider(value = vol, onValueChange = { volumes[p.id] = it; engine?.setPeerVolume(p.id, (it / 100f).toDouble()) }, valueRange = 0f..100f)
                            }
                            Text(vol.toInt().toString() + "%", color = Muted, fontSize = 11.sp, modifier = Modifier.width(36.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(1.dp, Gold.copy(alpha = .7f), RoundedCornerShape(10.dp))
                            .clickable { peers.forEach { volumes[it.id] = 0f; engine?.setPeerVolume(it.id, 0.0) } }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text("MUTE ALL", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(1.dp, if (deafenUi) ErrRed else Gold.copy(alpha = .7f), RoundedCornerShape(10.dp))
                            .clickable { val next = !deafenUi; deafenUi = next; engine?.setDeafened(next) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(if (deafenUi) "UNDEAFEN" else "DEAFEN", color = if (deafenUi) ErrRed else Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (inLobby) {
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(1.dp, if (handRaised) Muted else Gold.copy(alpha = .7f), RoundedCornerShape(10.dp))
                                .clickable { onHand() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(if (handRaised) "LOWER HAND" else "RAISE HAND", color = if (handRaised) Muted else Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Gold).clickable { showSettings = false }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                        Text("DONE", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
