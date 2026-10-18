/* XARENA — Luna chat widget (Phase 3)
   Talks to the luna-chat Edge Function. Auto-activates only when the backend
   is live (404 = not deployed, 503 = Gemini key not configured). No fake UI. */
(function () {
  "use strict";
  var LUNA_URL = "https://oskccuajgbyabogbvedj.supabase.co/functions/v1/luna-chat";
  var ANON_KEY = "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7";

  function lsGet(k, d) { try { return JSON.parse(localStorage.getItem(k)) || d; } catch (e) { return d; } }
  function lsSet(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} }
  function esc(s) { return String(s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }

  var sid = lsGet("xr_luna_sid", null);
  if (!sid) { sid = "web-" + Math.random().toString(36).slice(2) + Date.now().toString(36); lsSet("xr_luna_sid", sid); }
  var hist = lsGet("xr_luna_hist", []);

  // Probe the function once per page load; only show Luna when she can actually reply.
  function probe() {
    return fetch(LUNA_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json", "apikey": ANON_KEY, "Authorization": "Bearer " + ANON_KEY },
      body: JSON.stringify({ session_id: sid, ping: true })
    }).then(function (r) {
      if (r.status === 404 || r.status === 503) return false;
      return true;
    }).catch(function () { return false; });
  }

  function buildUI() {
    var css = document.createElement("style");
    css.textContent = [
      "#luna-fab{position:fixed;right:18px;bottom:18px;z-index:900;width:58px;height:58px;border-radius:50%;border:2px solid rgba(191,106,255,.55);background:radial-gradient(circle at 30% 30%,#3a1150,#14010b 75%);color:#e8c8ff;font-size:26px;cursor:pointer;box-shadow:0 6px 24px rgba(160,32,240,.45);transition:transform .2s}",
      "#luna-fab:hover{transform:scale(1.07)}",
      "#luna-panel{position:fixed;right:14px;bottom:86px;z-index:901;width:min(360px,calc(100vw - 28px));height:min(500px,calc(100vh - 110px));background:rgba(10,1,4,.97);border:1px solid rgba(191,106,255,.35);border-radius:16px;display:none;flex-direction:column;overflow:hidden;box-shadow:0 12px 44px rgba(0,0,0,.7);backdrop-filter:blur(8px)}",
      "#luna-panel.open{display:flex}",
      "#luna-head{display:flex;align-items:center;gap:10px;padding:12px 14px;border-bottom:1px solid rgba(191,106,255,.25);background:linear-gradient(90deg,rgba(160,32,240,.18),rgba(255,30,60,.12))}",
      "#luna-head .dot{width:8px;height:8px;border-radius:50%;background:#4ade80;box-shadow:0 0 6px #4ade80}",
      "#luna-head b{color:#f3e8ff;font-size:15px;letter-spacing:.5px}",
      "#luna-head span{color:#b98fd9;font-size:11.5px;display:block}",
      "#luna-close{margin-left:auto;background:none;border:none;color:#b98fd9;font-size:20px;cursor:pointer}",
      "#luna-msgs{flex:1;overflow-y:auto;padding:14px;display:flex;flex-direction:column;gap:10px}",
      ".luna-msg{max-width:82%;padding:9px 13px;border-radius:14px;font-size:14.5px;line-height:1.45;white-space:pre-wrap;word-break:break-word}",
      ".luna-msg.luna{align-self:flex-start;background:rgba(160,32,240,.16);border:1px solid rgba(191,106,255,.3);color:#f3e8ff;border-bottom-left-radius:4px}",
      ".luna-msg.me{align-self:flex-end;background:rgba(255,30,60,.14);border:1px solid rgba(255,30,60,.3);color:#ffe3e8;border-bottom-right-radius:4px}",
      ".luna-typing{color:#b98fd9;font-size:13px;padding:4px 13px;align-self:flex-start}",
      "#luna-chips{display:flex;flex-wrap:wrap;gap:6px;padding:0 14px 8px}",
      "#luna-chips button{background:rgba(160,32,240,.12);border:1px solid rgba(191,106,255,.35);color:#dcb8ff;border-radius:999px;padding:6px 12px;font-size:12.5px;cursor:pointer}",
      "#luna-form{display:flex;gap:8px;padding:10px 12px;border-top:1px solid rgba(191,106,255,.25)}",
      "#luna-input{flex:1;background:rgba(255,255,255,.05);border:1px solid rgba(191,106,255,.3);border-radius:10px;color:#fff;padding:9px 12px;font-size:14.5px;outline:none;font-family:inherit}",
      "#luna-send{background:linear-gradient(135deg,#a020f0,#ff1e3c);border:none;border-radius:10px;color:#fff;padding:0 16px;font-size:16px;cursor:pointer}",
      "#luna-send:disabled{opacity:.5;cursor:default}"
    ].join("\n");
    document.head.appendChild(css);

    var fab = document.createElement("button");
    fab.id = "luna-fab"; fab.textContent = "💜"; fab.setAttribute("aria-label", "Chat with Luna");
    var panel = document.createElement("div");
    panel.id = "luna-panel";
    panel.innerHTML =
      '<div id="luna-head"><div class="dot"></div><div><b>Luna</b><span>XARENA assistant · online</span></div><button id="luna-close" aria-label="Close">×</button></div>' +
      '<div id="luna-msgs"></div>' +
      '<div id="luna-chips"></div>' +
      '<form id="luna-form"><input id="luna-input" placeholder="Ask Luna anything…" maxlength="500" autocomplete="off"><button id="luna-send" type="submit">➤</button></form>';
    document.body.appendChild(fab);
    document.body.appendChild(panel);

    var msgs = panel.querySelector("#luna-msgs");
    var chips = panel.querySelector("#luna-chips");
    var form = panel.querySelector("#luna-form");
    var input = panel.querySelector("#luna-input");
    var sendBtn = panel.querySelector("#luna-send");

    function addMsg(role, text) {
      var d = document.createElement("div");
      d.className = "luna-msg " + (role === "user" ? "me" : "luna");
      d.textContent = role === "user" ? text : text.replace(/\*\*/g, "");
      msgs.appendChild(d);
      msgs.scrollTop = msgs.scrollHeight;
    }
    function setChips() {
      chips.innerHTML = "";
      if (hist.length > 1) return;
      ["When is Season 1?", "How do I register?", "Where is the leaderboard?", "What are scrims?"].forEach(function (q) {
        var b = document.createElement("button");
        b.type = "button"; b.textContent = q;
        b.addEventListener("click", function () { input.value = q; form.requestSubmit(); });
        chips.appendChild(b);
      });
    }
    // Greet once
    if (!hist.length) {
      var hi = "Hi! I am Luna — the XARENA assistant 💜 Tournaments, registration, leaderboard, scrims… ask me anything!";
      hist.push({ role: "assistant", content: hi });
      lsSet("xr_luna_hist", hist);
    }
    hist.slice(-30).forEach(function (m) { addMsg(m.role, m.content); });
    setChips();

    fab.addEventListener("click", function () { panel.classList.toggle("open"); if (panel.classList.contains("open")) input.focus(); });
    panel.querySelector("#luna-close").addEventListener("click", function () { panel.classList.remove("open"); });

    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var text = input.value.trim();
      if (!text) return;
      input.value = "";
      addMsg("user", text);
      hist.push({ role: "user", content: text });
      lsSet("xr_luna_hist", hist);
      setChips();
      sendBtn.disabled = true;
      var typing = document.createElement("div");
      typing.className = "luna-typing"; typing.textContent = "Luna is thinking…";
      msgs.appendChild(typing); msgs.scrollTop = msgs.scrollHeight;
      fetch(LUNA_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json", "apikey": ANON_KEY, "Authorization": "Bearer " + ANON_KEY },
        body: JSON.stringify({ session_id: sid, message: text, history: hist.slice(-12) })
      }).then(function (r) { return r.json().then(function (j) { return { ok: r.ok, j: j }; }); })
        .then(function (res) {
          typing.remove();
          sendBtn.disabled = false;
          var reply = (res.ok && res.j && res.j.reply) ? res.j.reply : "Oops, something went wrong on my side. Please try again in a bit! 🥺";
          addMsg("assistant", reply);
          hist.push({ role: "assistant", content: reply });
          if (hist.length > 40) hist = hist.slice(-40);
          lsSet("xr_luna_hist", hist);
        }).catch(function () {
          typing.remove();
          sendBtn.disabled = false;
          addMsg("assistant", "Looks like a network issue — check your connection and try again! 🥺");
        });
    });
  }

  function init() {
    probe().then(function (live) {
      if (live) buildUI();
    });
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
