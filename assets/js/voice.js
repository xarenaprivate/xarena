/* XARENA Voice Lobby - real peer-to-peer voice over WebRTC.
   Signaling: Supabase Realtime broadcast + presence (single open room).
   No fake participants, no fake speaking states: every person in the list
   is a real connected peer; "Speaking" comes from real audio levels. */
(function () {
  "use strict";
  var SUPA_URL = "https://oskccuajgbyabogbvedj.supabase.co";
  var SUPA_KEY = "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7";
  var ROOM = "voice:arena-lobby";
  var vroom = ROOM; /* regional voice lobby: voice:<room> from chat_my_room (coarse state), General if no area */
  try {
    var _h = localStorage.getItem("xr_chat_handle"), _t = localStorage.getItem("xr_chat_token");
    if (_h && _t) {
      fetch(SUPA_URL + "/rest/v1/rpc/chat_my_room", { method: "POST", headers: { "apikey": SUPA_KEY, "Authorization": "Bearer " + SUPA_KEY, "Content-Type": "application/json" }, body: JSON.stringify({ p_handle: _h, p_token: _t }) })
        .then(function (r) { return r.json(); }).then(function (j) { if (j && j.room && j.room !== "general") vroom = "voice:" + j.room; }).catch(function () {});
    }
  } catch (e) {}
  var RTC_CFG = { iceServers: [{ urls: ["stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"] }] };

  var $ = function (s) { return document.querySelector(s); };
  var joinBox = $("#voice-join"), roomBox = $("#voice-room");
  if (!joinBox) return;

  var sb = null, chan = null, myId = null, myHandle = null;
  var localStream = null, joined = false, muted = false, deafened = false;
  var joinAt = 0, timerIv = null;
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
      actx = actx || new (window.AudioContext || window.webkitAudioContext)();
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
    var entry = { pc: pc, handle: handle, audio: null };
    peers[id] = entry;
    localStream.getTracks().forEach(function (t) { pc.addTrack(t, localStream); });
    pc.onicecandidate = function (ev) {
      if (ev.candidate) sig("ice", id, ev.candidate.toJSON());
    };
    pc.ontrack = function (ev) {
      if (entry.audio) return;
      var a = document.createElement("audio");
      a.autoplay = true; a.srcObject = ev.streams[0];
      a.muted = deafened;
      document.body.appendChild(a);
      entry.audio = a;
      meterFor(ev.streams[0], id);
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

  function onSig(ev) {
    var p = ev.payload;
    if (!p || p.to !== myId || p.from === myId) return;
    var e = peers[p.from] || makePeer(p.from, p.handle);
    var pc = e.pc;
    if (p.kind === "offer") {
      pc.setRemoteDescription(p.data).then(function () {
        return pc.createAnswer();
      }).then(function (ans) {
        return pc.setLocalDescription(ans);
      }).then(function () {
        sig("answer", p.from, pc.localDescription.toJSON());
      }).catch(function () {});
    } else if (p.kind === "answer") {
      pc.setRemoteDescription(p.data).catch(function () {});
    } else if (p.kind === "ice") {
      pc.addIceCandidate(new RTCIceCandidate(p.data)).catch(function () {});
    }
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
    navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true }, video: false })
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
        var why = (err && err.name === "NotAllowedError") ? "Microphone permission was denied - allow mic access and try again." :
                  (err && err.name === "NotFoundError") ? "No microphone found on this device." :
                  "Could not open the microphone on this device.";
        $("#xv-err").textContent = why;
      });
  }

  function leave() {
    joined = false;
    clearInterval(timerIv);
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
    var link = location.origin + location.pathname;
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
