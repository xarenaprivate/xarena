/* XARENA Voice Lobby - real peer-to-peer voice over WebRTC.
   Signaling: Supabase Realtime broadcast + presence (single open room).
   No fake participants, no fake speaking states: every person in the list
   is a real connected peer; "Speaking" comes from real audio levels. */
(function () {
  "use strict";
  var SUPA_URL = "https://oskccuajgbyabogbvedj.supabase.co";
  var SUPA_KEY = "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7";
  var ROOM = "voice:arena-lobby";
  // Supabase JS adds the realtime: wire prefix. Never add it twice.
  var vroom = ROOM, clanMode = new URLSearchParams(location.search).get('clan') === '1';
  var clanName = '';
  function resolveRoom() {
    if (!clanMode) return Promise.resolve(); // Voice tab is always the global lobby.
    if (!window.xrApi) return Promise.reject(new Error('Log in and open Clan Room to join clan voice.'));
    return window.xrApi.user().then(function(user) {
      if (!user) throw new Error('Log in to join your clan voice room.');
      return window.xrApi.uRpc('room_info', {});
    }).then(function(info) {
      if (!info || !info.ok || !/^[0-9a-f-]{36}$/i.test(info.squad || '')) throw new Error('Join a squad before opening clan voice.');
      vroom = 'voice:squad:' + info.squad;
      clanName = info.name || 'Your clan';
    });
  }
  var RTC_CFG = { iceServers: [{ urls: ["stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"] }] };

  var $ = function (s) { return document.querySelector(s); };
  var joinBox = $("#voice-join"), roomBox = $("#voice-room");
  if (!joinBox) return;

  var sb = null, chan = null, myId = null, myHandle = null;
  var localStream = null, joined = false, muted = false, deafened = false;
  var joinAt = 0, timerIv = null, statsIv=null, statsBusy=false;
  var peers = {};       /* id -> {pc, handle, card, audio, analyser, data} */
  var levels = {};      /* id -> last speaking bool (incl. "me") */
  var actx = null;

  function esc(t) { return String(t == null ? "" : t).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function toast(msg) {
    var t = document.createElement("div");
    t.className = "xclip-toast"; t.textContent = msg;
    document.body.appendChild(t);
    requestAnimationFrame(function () { t.classList.add("on"); });
    setTimeout(function () { t.classList.remove("on"); setTimeout(function () { t.remove(); }, 300); }, 2400);
  }
  function fmtClock(ms) {
    var s = Math.floor(ms / 1000), h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), r = s % 60;
    var p = function (n) { return (n < 10 ? "0" : "") + n; };
    return p(h) + ":" + p(m) + ":" + p(r);
  }

  /* ---------- identity ---------- */
  function pickHandle() {
    var h = (localStorage.getItem("xr_chat_handle") || "").trim();
    if (h) return h;
    return "Guest-" + Math.random().toString(36).slice(2, 6).toUpperCase();
  }

  /* ---------- presence + roster ---------- */
  var roster = {}; /* id -> {handle, muted} */

  function renderRoster() {
    var list = $("#xv-list"), count = Object.keys(roster).length + (joined ? 1 : 0);
    $("#xv-count").textContent = count + " IN VOICE";
    list.innerHTML = "";
    list.appendChild(personCard("me", myHandle + " (You)", muted ? "Muted" : null, true));
    Object.keys(roster).forEach(function (id) {
      var r = roster[id];
      list.appendChild(personCard(id, r.handle, r.muted ? "Muted" : null, false));
    });
    if (!Object.keys(roster).length) {
      var d = document.createElement("p");
      d.className = "xv-alone";
      d.textContent = "You're the only one here right now - invite your squad with the Invite button.";
      list.appendChild(d);
    }
  }

  function personCard(id, name, forceStatus, isMe) {
    var c = document.createElement("div");
    c.className = "xv-card"; c.id = "xvc-" + id;
    var speaking = !!levels[id];
    var status = forceStatus || (speaking ? "Speaking" : "Connected");
    c.innerHTML =
      '<div class="xv-ava' + (speaking ? " on" : "") + '">' + esc((name || "P")[0].toUpperCase()) + "</div>" +
      '<div class="xv-info"><div class="xv-name">' + esc(name) + '</div>' +
      '<div class="xv-status' + (forceStatus === "Muted" ? " muted" : speaking ? " speak" : "") + '">' + status + "</div></div>" +
      '<div class="xv-wave' + (speaking ? " on" : "") + '"><i></i><i></i><i></i><i></i><i></i></div>' +
      '<div class="xv-mic">' + (forceStatus === "Muted"
        ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V6a3 3 0 0 0-5.94-.6"/><path d="M17 16.95A7 7 0 0 1 5 12v-2m14 0v2a7 7 0 0 1-.11 1.23M12 19v3M8 23h8M3 3l18 18"/></svg>'
        : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2M12 19v3M8 23h8"/></svg>') + "</div>";
    if (isMe) c.classList.add("me");
    return c;
  }

  function refreshStatuses() {
    ["me"].concat(Object.keys(roster)).forEach(function (id) {
      var old = document.getElementById("xvc-" + id);
      if (!old) return;
      var name = id === "me" ? myHandle + " (You)" : roster[id].handle;
      var isMuted = id === "me" ? muted : !!roster[id].muted;
      var fresh = personCard(id, name, isMuted ? "Muted" : null, id === "me");
      old.replaceWith(fresh);
    });
  }

  /* ---------- speaking meters ---------- */
  function meterFor(stream, id) {
    try {
      actx = actx || new (window.AudioContext || window.webkitAudioContext)({latencyHint:"interactive"});
      var src = actx.createMediaStreamSource(stream);
      var an = actx.createAnalyser();
      an.fftSize = 256;
      src.connect(an);
      var buf = new Uint8Array(an.frequencyBinCount);
      var tick = function () {
        if (!joined) return;
        an.getByteTimeDomainData(buf);
        var peak = 0;
        for (var i = 0; i < buf.length; i++) { var v = Math.abs(buf[i] - 128); if (v > peak) peak = v; }
        var speak = peak > 14 && !(id === "me" && muted);
        if (speak !== !!levels[id]) { levels[id] = speak; refreshStatuses(); }
        setTimeout(tick, 250);
      };
      tick();
    } catch (e) { /* meter is cosmetic - skip silently */ }
  }

  /* ---------- WebRTC mesh ---------- */
  function makePeer(id, handle) {
    var pc = new RTCPeerConnection(RTC_CFG);
    var entry = { pc: pc, handle: handle, audio: null, pendingIce:[],signalQueue:Promise.resolve() };
    peers[id] = entry;
    localStream.getTracks().forEach(function (t) { pc.addTrack(t, localStream); });
    pc.onicecandidate = function (ev) {
      if (ev.candidate) sig("ice", id, ev.candidate.toJSON());
    };
    pc.ontrack = function (ev) {
      if (entry.audio) return;
      var a = document.createElement("audio");
      // A hint, not a promise. Browser/network may require a larger buffer.
      if(ev.receiver&&'jitterBufferTarget'in ev.receiver){try{ev.receiver.jitterBufferTarget=40;}catch(ignore){}}
      a.autoplay = true; a.playsInline=true;a.srcObject = ev.streams[0]||new MediaStream([ev.track]);
      a.muted = deafened;
      document.body.appendChild(a);
      entry.audio = a;
      a.play().catch(function(){toast("Tap the page to allow voice playback.");});
      meterFor(a.srcObject, id);
    };
    pc.onconnectionstatechange = function () {
      if (pc.connectionState === "failed" || pc.connectionState === "closed") dropPeer(id);
    };
    return entry;
  }
  function dropPeer(id) {
    var e = peers[id];
    if (e) { try { e.pc.close(); } catch (x) {} if (e.audio) e.audio.remove(); delete peers[id]; }
    delete levels[id];
  }
  function sig(kind, to, data) {
    chan.send({ type: "broadcast", event: "sig", payload: { kind: kind, from: myId, to: to, data: data, handle: myHandle } });
  }

  function tuneSender(pc){
    pc.getSenders().forEach(function(sender){if(!sender.track||sender.track.kind!=='audio')return;
      try{var params=sender.getParameters();if(params.encodings&&params.encodings.length){params.encodings[0].maxBitrate=32000;sender.setParameters(params).catch(function(){});}}catch(ignore){}
    });
  }
  function flushIce(entry){var pending=entry.pendingIce.splice(0);return pending.reduce(function(chain,c){return chain.then(function(){return entry.pc.addIceCandidate(c).catch(function(){});});},Promise.resolve());}
  function onSig(ev) {
    var p=ev.payload;if(!p||p.to!==myId||p.from===myId||!localStream)return;
    var entry=peers[p.from]||makePeer(p.from,p.handle),pc=entry.pc;
    // ICE can arrive before offer/answer. Keep it until remote description exists.
    entry.signalQueue=entry.signalQueue.then(function(){
      if(p.kind==='ice'){
        var candidate=new RTCIceCandidate(p.data);if(!pc.remoteDescription){entry.pendingIce.push(candidate);return;}
        return pc.addIceCandidate(candidate);
      }
      if(p.kind==='offer')return pc.setRemoteDescription(p.data).then(function(){return flushIce(entry);}).then(function(){return pc.createAnswer();}).then(function(ans){return pc.setLocalDescription(ans);}).then(function(){sig('answer',p.from,pc.localDescription.toJSON());tuneSender(pc);});
      if(p.kind==='answer')return pc.setRemoteDescription(p.data).then(function(){return flushIce(entry);}).then(function(){tuneSender(pc);});
    }).catch(function(){/* keep the queue alive for later valid signaling */});
  }

  // Read-only transport diagnostics: these are NOT microphone-to-ear measurements.
  function updateDiagnostics(){
    var box=document.getElementById('xv-network');if(!box||statsBusy)return;
    statsBusy=true;var rtts=[],buffers=[],loss=0,received=0;
    Promise.all(Object.keys(peers).map(function(id){return peers[id].pc.getStats().then(function(report){report.forEach(function(r){
      if(r.type==='candidate-pair'&&r.state==='succeeded'&&r.nominated&&Number.isFinite(r.currentRoundTripTime))rtts.push(r.currentRoundTripTime*1000);
      if(r.type==='inbound-rtp'&&(r.kind==='audio'||r.mediaType==='audio')){if(r.jitterBufferEmittedCount>0)buffers.push(r.jitterBufferDelay/r.jitterBufferEmittedCount*1000);loss+=Math.max(0,r.packetsLost||0);received+=r.packetsReceived||0;}
    });}).catch(function(){});})).then(function(){
      if(!joined)return;
      box.textContent=rtts.length?'Peer RTT '+Math.round(Math.max.apply(null,rtts))+' ms'+(buffers.length?' | Audio buffer avg '+Math.round(Math.max.apply(null,buffers))+' ms':'')+(received?' | Packet loss '+(100*loss/(received+loss)).toFixed(1)+'%':'')+' (not total voice delay)':'Waiting for a connected peer. Zero delay is not physically possible.';
    }).finally(function(){statsBusy=false;});
  }

  function reconcile() {
    /* deterministic: the lexicographically smaller id offers */
    Object.keys(roster).forEach(function (id) {
      if (!peers[id] && myId < id) {
        var e = makePeer(id, roster[id].handle);
        e.pc.createOffer().then(function (off) {
          return e.pc.setLocalDescription(off);
        }).then(function () {
          sig("offer", id, e.pc.localDescription.toJSON());
        }).catch(function () {});
      }
    });
    Object.keys(peers).forEach(function (id) { if (!roster[id]) dropPeer(id); });
  }

  /* ---------- join / leave ---------- */
  function join() {
    var btn = $("#xv-join-btn");
    btn.disabled = true; btn.textContent = "Connecting mic…";
    resolveRoom().then(function(){
      return navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true,channelCount:{ideal:1},latency:{ideal:0.02}},video:false});
    })
      .then(function (stream) {
        localStream = stream;
        sb = sb || supabase.createClient(SUPA_URL, SUPA_KEY);
        myId = (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + "-" + Math.random().toString(36).slice(2, 8));
        myHandle = pickHandle();
        chan = sb.channel(vroom, { config: { presence: { key: myId } } });
        chan.on("presence", { event: "sync" }, function () {
          var state = chan.presenceState();
          var next = {};
          Object.keys(state).forEach(function (id) {
            var pres = state[id][0];
            if (id !== myId && pres) next[id] = { handle: pres.handle || "Player", muted: !!pres.muted };
          });
          roster = next;
          renderRoster();
          reconcile();
        });
        chan.on("broadcast", { event: "sig" }, onSig);
        chan.subscribe(function (status) {
          if (status !== "SUBSCRIBED") return;
          chan.track({ handle: myHandle, muted: false, at: Date.now() });
          joined = true; joinAt = Date.now();
          var title=roomBox.querySelector('h2');if(title)title.textContent=clanMode?clanName+' VOICE':'ARENA LOBBY';
          if(!document.getElementById('xv-network')){var net=document.createElement('p');net.id='xv-network';net.className='xv-live';net.setAttribute('role','status');roomBox.appendChild(net);}
          clearInterval(statsIv);statsIv=setInterval(updateDiagnostics,5000);updateDiagnostics();
          joinBox.style.display = "none";
          roomBox.style.display = "";
          renderRoster();
          meterFor(localStream, "me");
          timerIv = setInterval(function () {
            $("#xv-timer").textContent = fmtClock(Date.now() - joinAt);
          }, 1000);
        });
      })
      .catch(function (err) {
        btn.disabled = false; btn.textContent = "Join Voice";
        var why = (err && err.message && /Log in|Join a squad/.test(err.message)) ? err.message : (err && err.name === "NotAllowedError") ? "Microphone permission was denied - allow mic access and try again." :
                  (err && err.name === "NotFoundError") ? "No microphone found on this device." :
                  "Could not open the microphone on this device.";
        $("#xv-err").textContent = why;
      });
  }

  function leave() {
    joined = false;
    clearInterval(timerIv);clearInterval(statsIv);
    Object.keys(peers).forEach(dropPeer);
    roster = {}; levels = {};
    if (chan) { try { chan.unsubscribe(); } catch (e) {} chan = null; }
    if (localStream) { localStream.getTracks().forEach(function (t) { t.stop(); }); localStream = null; }
    muted = false; deafened = false;
    $("#xv-mute").classList.remove("on"); $("#xv-deaf").classList.remove("on");
    roomBox.style.display = "none";
    joinBox.style.display = "";
    var btn = $("#xv-join-btn");
    btn.disabled = false; btn.textContent = "Join Voice";
  }

  if (clanMode) {
    var intro=joinBox.querySelector('h3'), description=joinBox.querySelector('p');
    if(intro)intro.textContent='Join your clan voice room';
    if(description)description.textContent='Your current clan has a separate voice channel. Voice channels are not access-restricted: anyone who knows the channel can join. You will be audible while joined. Mic permission is required.';
  }

  /* ---------- controls ---------- */
  $("#xv-join-btn").addEventListener("click", join);
  $("#xv-leave").addEventListener("click", function () { leave(); toast("Left the voice lobby."); });
  $("#xv-mute").addEventListener("click", function () {
    if (!localStream) return;
    muted = !muted;
    localStream.getAudioTracks().forEach(function (t) { t.enabled = !muted; });
    this.classList.toggle("on", muted);
    this.querySelector("span").textContent = muted ? "Unmute" : "Mute Mic";
    if (chan) chan.track({ handle: myHandle, muted: muted, at: Date.now() });
    refreshStatuses();
  });
  $("#xv-deaf").addEventListener("click", function () {
    deafened = !deafened;
    Object.keys(peers).forEach(function (id) { if (peers[id].audio) peers[id].audio.muted = deafened; });
    this.classList.toggle("on", deafened);
    toast(deafened ? "Deafened - you can't hear the room." : "You can hear the room again.");
  });
  $("#xv-invite").addEventListener("click", function () {
    var link = location.origin + location.pathname + (clanMode ? '?clan=1' : '');
    if (navigator.share) {
      navigator.share({ title: "XARENA Voice Lobby", text: "Join my voice lobby on XARENA.", url: link }).catch(function () {});
    } else if (navigator.clipboard) {
      navigator.clipboard.writeText(link).then(function () { toast("Lobby link copied."); }, function () { toast(link); });
    } else { toast(link); }
  });
  window.addEventListener("beforeunload", function () { if (joined) leave(); });

  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia || !window.RTCPeerConnection) {
    $("#xv-join-btn").disabled = true;
    $("#xv-err").textContent = "This browser does not support voice calls. Open XARENA in Chrome.";
  }
})();
