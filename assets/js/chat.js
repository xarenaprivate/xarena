/* XARENA WORLD CHAT — real-time lobby chat.
   Transport: Supabase Postgres (history + RPC) + Supabase Realtime
   (new messages, presence, typing). Text + emoji only; the server
   rejects HTML and links. Rate limit and mutes enforced server-side. */
(function () {
  "use strict";
  var SUPA_URL = "https://oskccuajgbyabogbvedj.supabase.co";
  var SUPA_KEY = "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7";
  var HANDLE_RE = /^[A-Za-z0-9_]{3,20}$/;
  var LS_HANDLE = "xr_chat_handle", LS_TOKEN = "xr_chat_token",
      LS_READ = "xr_chat_read", LS_BLOCK = "xr_chat_blocked";
  var EMOJIS = "😀 😂 🤣 😊 😍 😎 🤩 😜 🫡 😤 😭 😅 😉 🙃 😇 🥳 😴 🤯 🥵 😡 👍 👎 🙏 👏 🔥 💯 ⚡ 🎮 🏆 🎯 💜 ❤️ 🖤 💛 ✨ ⭐ 👑 🦁 🐺 🦅 🇮🇳 ☠️ 🤝 💪 🎉".split(" ");
  var AV_COLORS = ["#e10600","#8b3dff","#f5b301","#0aa5ff","#3ddc84","#ff4fa3","#ff7a1a","#00c2b8"];

  var sb = null, channel = null, handle = null, token = null, room = "general", roomLabel = "General";
  var logEl, inputEl, sendBtn, typingEl, onlineEl;
  var lastId = 0, sending = false, typingSentAt = 0, emojiOpen = false;
  var typingTimers = {};

  function $(s) { return document.querySelector(s); }
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; }); }
  function toast(msg) {
    var t = document.createElement("div");
    t.className = "chat-toast"; t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(function () { t.remove(); }, 3400);
  }
  function blocked() { try { return JSON.parse(localStorage.getItem(LS_BLOCK) || "[]"); } catch (e) { return []; } }
  function isBlocked(h) { return blocked().indexOf(h) >= 0; }
  function colorFor(h) { var n = 0; for (var i = 0; i < h.length; i++) n = (n * 31 + h.charCodeAt(i)) >>> 0; return AV_COLORS[n % AV_COLORS.length]; }
  var AV = {}, avFetchedAt = 0;
  function fetchAvatars(force) {
    var now = Date.now();
    if (!force && now - avFetchedAt < 60000) return Promise.resolve(AV);
    avFetchedAt = now;
    return rest("/rest/v1/chat_avatars?select=handle,avatar_url").then(function (r) { return r.json(); }).then(function (rows) {
      (rows || []).forEach(function (x) { if (x.avatar_url) AV[x.handle] = x.avatar_url; });
      return AV;
    }).catch(function () { return AV; });
  }
  function avaHtml(h, cls) {
    if (AV[h]) return '<div class="cavatar ' + (cls || "") + '" style="padding:0"><img src="' + esc(AV[h]) + '" alt="" loading="lazy"></div>';
    return '<div class="cavatar ' + (cls || "") + '" style="background:' + colorFor(h) + '">' + esc(h.charAt(0).toUpperCase()) + "</div>";
  }
  function fmtTime(ts) { var d = new Date(ts); var h = d.getHours(), m = d.getMinutes(); return (h % 12 || 12) + ":" + (m < 10 ? "0" : "") + m + (h < 12 ? " AM" : " PM"); }

  /* ---------- PostgREST helpers ---------- */
  function rest(path, opts) {
    opts = opts || {};
    opts.headers = Object.assign({ "apikey": SUPA_KEY, "Authorization": "Bearer " + SUPA_KEY, "Content-Type": "application/json" }, opts.headers || {});
    return fetch(SUPA_URL + path, opts);
  }
  function rpc(name, body) {
    return rest("/rest/v1/rpc/" + name, { method: "POST", body: JSON.stringify(body || {}) })
      .then(function (r) { return r.json().catch(function () { return null; }).then(function (j) { return { ok: r.ok, status: r.status, data: j }; }); });
  }

  /* ---------- handle gate ---------- */
  function showGate(err) {
    $("#chat-gate").style.display = "";
    $("#chat-main").style.display = "none";
    if (err) $(".chat-err").textContent = err;
  }
  function claimHandle() {
    var v = $("#gate-handle").value.trim();
    if (!HANDLE_RE.test(v)) { $(".chat-err").textContent = "3-20 characters: letters, numbers, underscore only."; return; }
    $("#gate-btn").disabled = true;
    $(".chat-err").textContent = "";
    /* new handles need a free account (old handles + tokens keep working) */
    function claimErr(msg) {
      $("#gate-btn").disabled = false;
      $(".chat-err").innerHTML = msg;
    }
    var done = function (r) {
      if (r && r.ok && r.token) {
        handle = r.handle || v; token = r.token;
        localStorage.setItem(LS_HANDLE, handle); localStorage.setItem(LS_TOKEN, token);
        boot();
      } else {
        claimErr((r && r.error) ? r.error : "Could not claim the name right now. Try again.");
      }
    };
    if (window.xrApi && xrApi.uRpc) {
      xrApi.user().then(function (u) {
        if (!u) { done(null); return; }
        xrApi.uRpc("chat_claim_handle_account", { p_handle: v }).then(done).catch(function () { done(null); });
      });
    } else {
      done(null);
    }
  }

  /* ---------- rendering ---------- */
  function addMsg(m, prepend) {
    if (isBlocked(m.handle)) return;
    var mine = m.handle === handle;
    var luna = m.handle === "Luna";
    var row = document.createElement("div");
    row.className = "cmsg" + (mine ? " mine" : "") + (luna ? " luna" : "");
    row.dataset.id = m.id;
    row.innerHTML =
      (luna
        ? '<div class="cavatar luna-avatar">&#10022;</div>'
        : avaHtml(m.handle)) +
      '<div class="cbody"><div class="cmeta"><b>' + (mine ? "You (" + esc(m.handle) + ")" : luna ? "Luna AI" : '<a href="player.html?u=' + encodeURIComponent(m.handle) + '" style="color:inherit;text-decoration:none">' + esc(m.handle) + "</a>") + (luna ? "<span class='luna-tag'>AI</span>" : "") + "</b><span class='ctime'>" + fmtTime(m.created_at) + (mine ? " · Delivered" : "") + "</span></div>" +
      "<span class='ctext'>" + esc(m.body) + "</span>" +
      (mine || luna ? "" : "<span class='cacts'><button data-act='report'>Report</button><button data-act='block'>Block</button></span>") +
      "</div>";
    row.querySelectorAll(".cacts button").forEach(function (b) {
      b.addEventListener("click", function () { onAction(b.dataset.act, m); });
    });
    if (prepend) logEl.insertBefore(row, logEl.firstChild); else logEl.appendChild(row);
    if (m.id > lastId) lastId = m.id;
  }
  function sysMsg(text) {
    var d = document.createElement("div");
    d.className = "chat-sys"; d.textContent = text;
    logEl.appendChild(d);
  }
  function nearBottom() { return logEl.scrollHeight - logEl.scrollTop - logEl.clientHeight < 90; }
  function scrollBottom() { logEl.scrollTop = logEl.scrollHeight; }
  function markRead() { localStorage.setItem(LS_READ, new Date().toISOString()); }

  function onAction(act, m) {
    if (act === "block") {
      var b = blocked(); b.push(m.handle);
      localStorage.setItem(LS_BLOCK, JSON.stringify(b));
      Array.prototype.forEach.call(logEl.querySelectorAll(".cmsg"), function (n) {
        var h = n.querySelector(".cmeta b");
        if (h && h.textContent === m.handle) n.remove();
      });
      toast(m.handle + " blocked. Unblock: clear site data.");
    } else if (act === "report") {
      rpc("chat_report", { p_message_id: m.id, p_reporter: handle || "anon", p_reason: "user report" }).then(function (r) {
        toast(r.ok ? "Reported. The owner reviews every report." : "Could not send the report right now.");
      });
    }
  }

  /* ---------- history + realtime ---------- */
  function loadHistory() {
    return rest("/rest/v1/chat_messages?select=id,handle,body,created_at&room=eq." + encodeURIComponent(room) + "&order=id.desc&limit=80")
      .then(function (r) {
        if (!r.ok) throw new Error("no table");
        return r.json();
      })
      .then(function (rows) {
        rows.reverse().forEach(function (m) { addMsg(m); });
        scrollBottom(); markRead();
      });
  }

  function connectRealtime() {
    channel = sb.channel("world-chat:" + room, { config: { presence: { key: handle } } });
    channel.on("postgres_changes", { event: "INSERT", schema: "public", table: "chat_messages", filter: "room=eq." + room }, function (p) {
      var m = p.new;
      if (!m || m.id <= lastId || (m.room && m.room !== room)) return;
      var stick = nearBottom();
      addMsg(m);
      clearTyping(m.handle);
      if (m.handle === handle || stick) { scrollBottom(); markRead(); }
    });
    channel.on("presence", { event: "sync" }, function () {
      var keys = Object.keys(channel.presenceState());
      var n = keys.length;
      if (onlineEl) onlineEl.textContent = n + " online";
      var oc = document.getElementById("chat-online-classic");
      if (oc) oc.textContent = "● " + n + " online";
      renderStrip(keys);
    });
    channel.on("broadcast", { event: "typing" }, function (p) {
      var h = p.payload && p.payload.handle;
      if (!h || h === handle) return;
      typingEl.textContent = h + " is typing…";
      clearTimeout(typingTimers[h]);
      typingTimers[h] = setTimeout(function () { typingEl.textContent = ""; }, 3500);
    });
    channel.subscribe(function (status) {
      if (status === "SUBSCRIBED") {
        channel.track({ handle: handle, at: Date.now() });
      }
    });
  }
  function clearTyping(h) {
    if (typingEl.textContent.indexOf(h + " is typing") === 0) typingEl.textContent = "";
  }

  /* ---------- online strip (real presence) ---------- */
  function renderStrip(keys) {
    var strip = document.getElementById("chat-strip");
    if (!strip) return;
    var handles = keys.filter(function (k) { return k && k !== "undefined"; });
    if (!handles.length) { strip.style.display = "none"; return; }
    strip.style.display = "";
    document.getElementById("chat-strip-t").innerHTML = "Online now: <b>" + handles.length + "</b> in the lobby";
    var avas = document.getElementById("chat-strip-avas");
    var show = handles.slice(0, 4);
    var html = show.map(function (h) {
      var isLuna = h === "Luna";
      var av = isLuna
        ? '<div class="av" style="background:linear-gradient(135deg,#2a1a52,#4b2f8f)">✦<span class="dot"></span></div>'
        : (AV[h]
          ? '<div class="av"><img src="' + esc(AV[h]) + '" alt="" loading="lazy"><span class="dot"></span></div>'
          : '<div class="av" style="background:' + colorFor(h) + '">' + esc(h.charAt(0).toUpperCase()) + '<span class="dot"></span></div>');
      return '<div class="cn-ava">' + av + '<div class="nm">' + esc(h) + (isLuna ? '<span class="ai">AI</span>' : "") + "</div></div>";
    }).join("");
    if (handles.length > 4) html += '<div class="cn-ava more"><div class="av">+' + (handles.length - 4) + '<span class="dot"></span></div><div class="nm">+' + (handles.length - 4) + " more</div></div>";
    avas.innerHTML = html;
  }

  /* ---------- real chips: next match + real pages ---------- */
  function loadChips() {
    var box = document.getElementById("chat-chips");
    if (!box) return;
    var chips = [];
    rest("/rest/v1/matches_public?select=title,starts_at,status&order=starts_at.asc&limit=10").then(function (r) { return r.json(); }).then(function (rows) {
      var now = Date.now();
      var next = (rows || []).filter(function (m) { return m.starts_at && new Date(m.starts_at).getTime() > now - 15 * 60000 && m.status !== "done" && m.status !== "cancelled"; })[0];
      if (next) {
        var d = new Date(next.starts_at);
        var when = d.toLocaleDateString("en-IN", { day: "numeric", month: "short" }) + " " + fmtTime(next.starts_at);
        chips.push('<a class="cn-chip" href="tournaments.html">🎮 ' + esc(next.title) + " · " + esc(when) + "</a>");
      }
      chips.push('<a class="cn-chip" href="leaderboard.html">🏆 Leaderboard</a>');
      chips.push('<a class="cn-chip" href="tournaments.html#squads">👥 Squads</a>');
      box.innerHTML = chips.join("");
      box.style.display = "";
    }).catch(function () {
      box.innerHTML = '<a class="cn-chip" href="leaderboard.html">🏆 Leaderboard</a><a class="cn-chip" href="tournaments.html#squads">👥 Squads</a>';
      box.style.display = "";
    });
  }

  /* ---------- look toggle (nova <-> classic) ---------- */
  function wireLookToggle() {
    function setLook(v) {
      try { localStorage.setItem("xr_chat_look", v); } catch (e) {}
      document.body.classList.toggle("chat-classic", v === "classic");
      document.body.classList.toggle("chat-nova-page", v !== "classic");
    }
    var b1 = document.getElementById("cn-look"), b2 = document.getElementById("cn-look2");
    if (b1) b1.addEventListener("click", function () { setLook("classic"); });
    if (b2) b2.addEventListener("click", function () { setLook("nova"); });
  }

  /* ---------- Luna mentions ---------- */
  var LUNA_URL = "https://oskccuajgbyabogbvedj.supabase.co/functions/v1/luna-chat";
  var lunaAskedAt = 0;
  function askLuna(body) {
    typingEl.textContent = "Luna is thinking\u2026";
    clearTimeout(typingTimers.__luna);
    typingTimers.__luna = setTimeout(function () { typingEl.textContent = ""; }, 12000);
    fetch(LUNA_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json", apikey: SUPA_KEY },
      body: JSON.stringify({ session_id: "wc-" + handle, message: body, mode: "worldchat", handle: handle }),
    }).then(function (r) { return r.json(); }).then(function (d) {
      if (d && d.skipped) { typingEl.textContent = ""; toast("Luna needs a breather - mention her again in a bit."); }
    }).catch(function () { typingEl.textContent = ""; });
  }

  /* ---------- sending ---------- */
  function send() {
    var body = inputEl.value.trim();
    if (!body || sending) return;
    if (body.length > 400) { toast("Max 400 characters."); return; }
    if (/[<>]/i.test(body) || /(https?:\/\/|www\.)/i.test(body)) { toast("Text and emojis only - no links or HTML."); return; }
    sending = true; sendBtn.disabled = true;
    rpc("chat_send", { p_handle: handle, p_token: token, p_body: body }).then(function (r) {
      sending = false; sendBtn.disabled = false;
      if (r.ok && r.data && r.data.ok) {
        inputEl.value = "";
        inputEl.focus();
        if (/@luna\b/i.test(body)) askLuna(body);
      } else {
        var e = r.data && r.data.error;
        if (e === "unauthorized") {
          localStorage.removeItem(LS_HANDLE); localStorage.removeItem(LS_TOKEN);
          handle = null; token = null;
          if (channel) { try { channel.unsubscribe(); } catch (x) {} channel = null; }
          showGate("That chat name is no longer active. Pick a name to re-enter.");
          return;
        }
        if (e === "muted") toast("You are muted by the owner for now.");
        else if (e === "rate_limited") toast("Slow down a little - too many messages.");
        else if (e === "invalid") toast("Text and emojis only - no links or HTML.");
        else if (e === "blocked_language") toast("Keep it clean - abusive words are not allowed in XARENA chat.");
        else toast("Message not sent. Try again.");
      }
    });
  }
  function sendTyping() {
    var now = Date.now();
    if (now - typingSentAt < 2800 || !channel) return;
    typingSentAt = now;
    channel.send({ type: "broadcast", event: "typing", payload: { handle: handle } });
  }

  /* ---------- emoji panel ---------- */
  function buildEmojiPanel() {
    var p = $("#emoji-panel");
    EMOJIS.forEach(function (e) {
      var b = document.createElement("button");
      b.type = "button"; b.textContent = e;
      b.addEventListener("click", function () {
        var s = inputEl.selectionStart || inputEl.value.length;
        inputEl.value = inputEl.value.slice(0, s) + e + inputEl.value.slice(inputEl.selectionEnd || s);
        inputEl.focus();
      });
      p.appendChild(b);
    });
    $("#emoji-btn").addEventListener("click", function () {
      emojiOpen = !emojiOpen;
      p.classList.toggle("open", emojiOpen);
    });
    var moreBtn = $("#chat-more");
    if (moreBtn) moreBtn.addEventListener("click", function () {
      var r = $(".chat-rules");
      if (r) r.classList.toggle("collapsed");
    });
  }

  /* ---------- boot ---------- */
  function boot() {
    $("#chat-gate").style.display = "none";
    $("#chat-main").style.display = "";
    if (!sb) sb = supabase.createClient(SUPA_URL, SUPA_KEY);
    wireLookToggle(); loadChips();
    fetchAvatars(true).then(function () {
      /* regional room: the server picks it from the account's coarse state (no exact location); no area = General */
      return rpc("chat_my_room", { p_handle: handle, p_token: token }).then(function (r) {
        if (r && r.ok && r.data && r.data.room) { room = String(r.data.room); roomLabel = r.data.label || "General"; }
      }).catch(function () {});
    }).then(loadHistory).then(function () {
      sysMsg(room === "general" ? "Room: General. Set your area in Account to join your state room. Chat resets every few hours." : "Room: " + roomLabel + ". Chat resets every few hours.");
      connectRealtime();
    }).catch(function () {
      logEl.innerHTML = "";
      sysMsg("World Chat is being set up by the owner. Check back soon.");
      inputEl.disabled = true; sendBtn.disabled = true;
    });
    window.addEventListener("beforeunload", markRead);
    setInterval(markRead, 15000);
  }

  document.addEventListener("DOMContentLoaded", function () {
    logEl = $("#chat-log"); inputEl = $("#chat-input"); sendBtn = $("#send-btn");
    typingEl = $("#chat-typing"); onlineEl = $("#chat-online");
    buildEmojiPanel();
    $("#gate-btn").addEventListener("click", claimHandle);
    $("#gate-handle").addEventListener("keydown", function (e) { if (e.key === "Enter") claimHandle(); });
    sendBtn.addEventListener("click", send);
    inputEl.addEventListener("keydown", function (e) { if (e.key === "Enter") send(); });
    inputEl.addEventListener("input", sendTyping);
    handle = localStorage.getItem(LS_HANDLE);
    token = localStorage.getItem(LS_TOKEN);
    if (handle && token) boot(); else showGate();
  });
})();
