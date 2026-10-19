/* XARENA gold leaderboard: Global / State / District / Location (10 km). Real data only. */
(function () {
  "use strict";
  var root, state = { scope: "global", me: null, ranks: null, area: null, rows: {} };
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function rpc(name, args, authed) {
    if (authed && xrApi.uRpc) return xrApi.uRpc(name, args || {});
    return fetch(xrApi.url + "/rest/v1/rpc/" + name, { method: "POST", headers: { apikey: xrApi.key, "Content-Type": "application/json" }, body: JSON.stringify(args || {}) }).then(function (r) { return r.json(); });
  }
  var CSS = ".lbg{background:#07070a;color:#f3e6bf;font-family:inherit;max-width:560px;margin:0 auto;padding:14px 14px 90px}" +
    ".lbg *{box-sizing:border-box}.lbg-pill{display:flex;align-items:center;gap:8px;flex-wrap:wrap;border:1px solid rgba(212,175,55,.28);border-radius:14px;padding:12px 14px;background:rgba(255,255,255,.03)}" +
    ".lbg-pill .loc{font-weight:700;font-size:17px;flex:1;min-width:120px}.lbg-chip{border:1px solid rgba(212,175,55,.5);border-radius:6px;padding:4px 8px;font-size:11px;font-weight:700;letter-spacing:.4px;color:#e8cf86}" +
    ".lbg-tabs{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin:14px 0}.lbg-tab{border:0;border-radius:12px;padding:13px 4px;font:700 14px inherit;background:#2a2a2e;color:#8b8b90;cursor:pointer;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}" +
    ".lbg-tab.on{background:linear-gradient(180deg,#f3d27a,#c9992a);color:#1a1405}.lbg h1{font-family:Georgia,serif;font-weight:500;letter-spacing:1px;font-size:38px;margin:10px 0 2px;color:#ecc86b}" +
    ".lbg .sub{color:#a9a08a;font-size:14px;margin:0 0 14px}.lbg-ranks{display:flex;gap:8px;margin:0 0 12px;flex-wrap:wrap}.lbg-ranks span{flex:1;min-width:90px;text-align:center;border:1px solid rgba(212,175,55,.3);border-radius:10px;padding:8px 4px;font-size:12px;color:#a9a08a}.lbg-ranks b{display:block;color:#ecc86b;font-size:19px}" +
    ".lbg-card{position:relative;display:flex;gap:14px;align-items:center;border:1px solid rgba(212,175,55,.35);border-radius:14px;padding:14px;margin:0 0 14px;background:linear-gradient(135deg,rgba(255,255,255,.05),rgba(255,255,255,.01))}" +
    ".lbg-card.r1{border:2px solid #d4af37;box-shadow:0 0 22px rgba(212,175,55,.25)}.lbg-av{width:84px;height:84px;border-radius:50%;border:3px solid #d4af37;object-fit:cover;background:#17171b;display:flex;align-items:center;justify-content:center;font:700 28px inherit;color:#ecc86b;flex:none;overflow:hidden}" +
    ".lbg-card.r1 .lbg-av{border-radius:10px;width:96px;height:96px;border-width:4px}.lbg-card.r2 .lbg-av{border-color:#c9ccd3}.lbg-card.r3 .lbg-av{border-color:#cd7f32}" +
    ".lbg-badge{position:absolute;left:-6px;top:-10px;min-width:48px;padding:6px 10px;text-align:center;font-weight:800;font-size:20px;color:#fff;background:#6b5416;clip-path:polygon(0 0,100% 0,100% 78%,50% 100%,0 78%);border:0}" +
    ".lbg-card.r1 .lbg-badge{background:#8a6a14}.lbg-card.r2 .lbg-badge{background:#6d7078}.lbg-card.r3 .lbg-badge{background:#7a4a1c}" +
    ".lbg-n{font-weight:800;font-size:23px;margin:0 0 6px;word-break:break-word}.lbg-st{display:flex;gap:6px;flex-wrap:wrap}.lbg-st span{border:1px solid rgba(212,175,55,.5);border-radius:6px;padding:3px 8px;font-size:12px;font-weight:700;color:#e8cf86;background:rgba(0,0,0,.35)}" +
    ".lbg-tag{margin-top:6px;font-size:13px;color:#a9a08a}.lbg-mvp{margin-left:auto;text-align:center;font-weight:800;color:#ecc86b}.lbg-mvp i{display:block;width:54px;height:54px;border-radius:50%;background:radial-gradient(circle at 35% 30%,#ffe89a,#c9992a);margin:0 auto 4px;box-shadow:0 0 12px rgba(212,175,55,.5)}" +
    ".lbg-row{display:flex;align-items:center;gap:12px;border:1px solid rgba(255,255,255,.08);border-radius:12px;padding:10px 12px;margin:0 0 8px;background:rgba(255,255,255,.025)}.lbg-row.me{border-color:#d4af37}.lbg-row .rk{width:30px;font-weight:800;color:#ecc86b;text-align:center}" +
    ".lbg-row .lbg-av{width:44px;height:44px;border-width:2px;font-size:16px}.lbg-row .lbg-n{font-size:16px;margin:0 0 4px}.lbg-row .lbg-st span{font-size:11px;padding:2px 6px}" +
    ".lbg-btn{border:0;border-radius:10px;padding:9px 12px;font:700 13px inherit;cursor:pointer;background:linear-gradient(180deg,#f3d27a,#c9992a);color:#1a1405}.lbg-btn.line{background:transparent;color:#ecc86b;border:1px solid rgba(212,175,55,.5)}" +
    ".lbg-rival{margin-top:8px;display:flex;gap:8px;align-items:center;flex-wrap:wrap;font-size:12px;color:#e8cf86}.lbg-empty{border:1px dashed rgba(212,175,55,.35);border-radius:14px;padding:20px;text-align:center;color:#a9a08a}" +
    ".lbg-look{background:none;border:0;color:#a9a08a;text-decoration:underline;font-size:12px;cursor:pointer;margin-top:12px}.lbg-sec{margin-top:18px}.lbg-sec h3{color:#ecc86b;font-size:15px;letter-spacing:1px;margin:0 0 8px}" +
    ".lbg-modal{position:fixed;inset:0;background:rgba(0,0,0,.75);z-index:9999;display:flex;align-items:center;justify-content:center;padding:18px}.lbg-modal>div{background:#101014;border:1px solid rgba(212,175,55,.45);border-radius:16px;max-width:420px;width:100%;padding:18px;color:#f3e6bf}.lbg-modal p{font-size:14px;line-height:1.5;color:#cfc4a4}";
  function avatar(r) {
    if (r.avatar) return '<img class="lbg-av" alt="" src="' + esc(r.avatar) + '" onerror="this.outerHTML=\'<div class=lbg-av>' + esc((r.name || "?")[0]).toUpperCase() + '</div>\'">';
    return '<div class="lbg-av">' + esc((r.name || "?")[0]).toUpperCase() + "</div>";
  }
  function tag(r) { var t = [r.state, r.district].filter(Boolean).join(" \u2022 "); return t ? '<div class="lbg-tag">' + esc(t) + "</div>" : ""; }
  function stats(r) { return '<div class="lbg-st"><span>EXP ' + Number(r.xp || 0).toLocaleString("en-IN") + "</span><span>K/D " + (r.kd != null ? esc(r.kd) : "\u2014") + "</span><span>WINS " + (r.wins || 0) + "</span></div>"; }
  function rivalBox(r) {
    if (!r.rival) return "";
    var gap = r.xp - (state.ranks ? state.ranks.xp : 0);
    return '<div class="lbg-rival">\u2694 Nearby rival \u00b7 ' + gap + ' EXP above you <button class="lbg-btn" data-ch="' + esc(r.id) + '" data-nm="' + esc(r.name) + '">Challenge \u00b7 AP stake</button></div>';
  }
  function bigCard(r, i) {
    return '<div class="lbg-card r' + (i + 1) + '"><div class="lbg-badge">#' + (i + 1) + "</div>" + avatar(r) + '<div style="min-width:0"><div class="lbg-n">' + esc(r.name) + "</div>" + stats(r) + tag(r) + rivalBox(r) + "</div>" + (i === 0 ? '<div class="lbg-mvp"><i></i>MVP</div>' : "") + "</div>";
  }
  function smallRow(r) {
    return '<div class="lbg-row' + (r.is_me ? " me" : "") + '"><div class="rk">' + r.rank + "</div>" + avatar(r) + '<div style="min-width:0;flex:1"><div class="lbg-n">' + esc(r.name) + (r.is_me ? " (you)" : "") + "</div>" + stats(r) + tag(r) + rivalBox(r) + "</div></div>";
  }
  function head() {
    var a = state.area || {};
    var loc = a.district ? a.district + ", " + a.state : (a.state || "India");
    var chips = a.state ? '<span class="lbg-chip">STATE: ' + esc(a.state.toUpperCase()) + "</span>" + (a.district ? '<span class="lbg-chip">DISTRICT: ' + esc(a.district.toUpperCase()) + "</span>" : "") : "";
    var tabs = [["global", "Global"], ["state", a.state || "State"], ["district", a.district || "District"], ["near", "Location"]];
    return '<div class="lbg-pill"><span style="font-size:20px">\ud83d\udccd</span><span class="loc">' + esc(loc) + "</span>" + chips + "</div>" +
      '<div class="lbg-tabs">' + tabs.map(function (t) { return '<button class="lbg-tab' + (state.scope === t[0] ? " on" : "") + '" data-sc="' + t[0] + '">' + esc(t[1]) + "</button>"; }).join("") + "</div>";
  }
  function ranksBox() {
    var r = state.ranks; if (!r || !r.ok) return "";
    function c(l, v) { return "<span>" + l + "<b>" + (v ? "#" + v : "\u2014") + "</b></span>"; }
    return '<div class="lbg-ranks">' + c("GLOBAL", r.global) + c("STATE", r.state) + c("DISTRICT", r.district) + "</div>";
  }
  function scopeTitle() {
    var a = state.area || {};
    return { global: "Top Players \u2013 All India", state: "Top Players \u2013 " + (a.state || ""), district: "Top Players \u2013 " + (a.district || "") + " District", near: "Players within about 10 km" }[state.scope];
  }
  function locationPrompt(kind) {
    return '<div class="lbg-empty"><p style="margin:0 0 10px">' + (kind === "login" ? "Log in to see State, District and Location boards." :
      "To place you on the State and District leaderboards, XARENA can detect your area once. Only your state, district and a rough grid (about 5 km, only if you turn on the 10 km tab) are saved. Never your exact position.") + "</p>" +
      (kind === "login" ? '<a class="lbg-btn" href="account.html" style="text-decoration:none;display:inline-block">Log in</a>' : '<button class="lbg-btn" id="lbg-loc">Detect my area</button>') + "</div>";
  }
  function render(body) {
    var h = '<div class="lbg"><style>' + CSS + "</style>" + head() + "<h1>LEADERBOARD</h1>";
    h += '<p class="sub">' + esc(scopeTitle()) + " \u2022 live from real XP</p>" + ranksBox() + body + '<div id="lbg-ch"></div><div><button class="lbg-look" id="lbg-prev">Use previous look</button></div></div>';
    root.innerHTML = h;
    root.querySelectorAll("[data-sc]").forEach(function (b) { b.onclick = function () { state.scope = b.getAttribute("data-sc"); load(); }; });
    root.querySelectorAll("[data-ch]").forEach(function (b) { b.onclick = function () { challengeDialog(b.getAttribute("data-ch"), b.getAttribute("data-nm")); }; });
    var l = document.getElementById("lbg-loc"); if (l) l.onclick = consent;
    var p = document.getElementById("lbg-prev"); if (p) p.onclick = function () { try { localStorage.setItem("xr_lb_look", "nova"); } catch (e) {} location.reload(); };
    renderChallenges();
  }
  function rowsHtml(rows) {
    if (!rows.length) return '<div class="lbg-empty">No ranked players here yet. Earn EXP on the Play page to appear.</div>';
    var top = rows.slice(0, 3).map(bigCard).join(""), rest = rows.slice(3).map(smallRow).join("");
    return top + rest;
  }
  function load() {
    var sc = state.scope;
    if ((sc !== "global") && !state.me) { render(locationPrompt("login")); return; }
    rpc("leaderboard_scope", { p_scope: sc, p_limit: 50 }, !!state.me).then(function (r) {
      if (r && r.ok === false && r.error === "location_required") {
        render(sc === "near" && state.area && state.area.state ? '<div class="lbg-empty"><p>The 10 km board needs the optional nearby setting. Turn it on once, only a rough grid is stored.</p><button class="lbg-btn" id="lbg-loc">Enable nearby</button></div>' : locationPrompt("loc")); return;
      }
      if (!r || !r.ok) { render('<div class="lbg-empty">Could not load the board. Try again in a moment.</div>'); return; }
      if (r.state) state.area = { state: r.state, district: r.district };
      render(rowsHtml(r.rows || []));
    }).catch(function () { render('<div class="lbg-empty">Could not load the board. Check your connection.</div>'); });
  }
  function modal(html) { var m = document.createElement("div"); m.className = "lbg-modal"; m.innerHTML = "<div>" + html + "</div>"; document.body.appendChild(m); m.addEventListener("click", function (e) { if (e.target === m) m.remove(); }); return m; }
  function consent() {
    var m = modal("<h3 style='margin:0 0 8px;color:#ecc86b'>Place me on my area boards</h3><p>Your browser will ask for location once. XARENA uses it only to find your state and district for the State and District leaderboards. Your exact position is never saved.</p>" +
      "<label style='display:flex;gap:8px;font-size:13px;color:#cfc4a4;margin:8px 0'><input type='checkbox' id='lbg-near'> Also turn on the 10 km Location board (saves a rough 5 km grid cell, never shown to others)</label>" +
      "<div style='display:flex;gap:8px;margin-top:12px'><button class='lbg-btn' id='lbg-go'>Continue</button><button class='lbg-btn line' id='lbg-no'>Not now</button></div><p id='lbg-msg' style='font-size:12px'></p>");
    m.querySelector("#lbg-no").onclick = function () { m.remove(); };
    m.querySelector("#lbg-go").onclick = function () {
      var msg = m.querySelector("#lbg-msg"), near = m.querySelector("#lbg-near").checked;
      if (!navigator.geolocation) { msg.textContent = "This browser has no location support. You stay on the Global board."; return; }
      msg.textContent = "Detecting\u2026";
      navigator.geolocation.getCurrentPosition(function (pos) {
        var la = pos.coords.latitude, lo = pos.coords.longitude;
        fetch("https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&addressdetails=1&accept-language=en&lat=" + la + "&lon=" + lo).then(function (r) { return r.json(); }).then(function (j) {
          var a = (j && j.address) || {}; var st = a.state || a.region, di = a.state_district || a.county || a.city_district || a.city || a.town;
          if (!st) throw new Error("no state");
          return rpc("set_my_area", { p_state: st, p_district: di || null, p_lat: near ? la : null, p_lon: near ? lo : null }, true);
        }).then(function (r) { if (!r || !r.ok) throw new Error((r && r.error) || "failed"); m.remove(); state.scope = near ? "near" : (r.district ? "district" : "state"); init(true); })
          .catch(function () { msg.textContent = "Could not work out your area. You stay on the Global board; try again later."; });
      }, function () { msg.textContent = "Location permission was not given. You stay on the Global board and can enable it later."; }, { enableHighAccuracy: false, timeout: 15000, maximumAge: 600000 });
    };
  }
  function challengeDialog(id, name) {
    var bal = (state.hdr && state.hdr.ap) || 0, max = Math.min(100, bal);
    if (max < 10) { modal("<p>You need at least 10 AP to stake on a challenge. You have " + bal + " AP. Earn AP from the Play page.</p>"); return; }
    var opts = [10, 25, 50, 100].filter(function (v) { return v <= max; }).map(function (v) { return "<option>" + v + "</option>"; }).join("");
    var m = modal("<h3 style='margin:0 0 8px;color:#ecc86b'>Challenge " + esc(name) + "</h3><p>Both of you put the same AP stake in. They have 24 hours to accept. Then whoever earns more EXP in the next 24 hours wins both stakes. A tie returns both. Declined or expired returns yours. AP has no cash value.</p>" +
      "<label style='font-size:13px'>Stake (AP) <select id='lbg-stake' style='margin-left:8px;padding:6px'>" + opts + "</select></label><div style='display:flex;gap:8px;margin-top:12px'><button class='lbg-btn' id='lbg-send'>Send challenge</button><button class='lbg-btn line' id='lbg-x'>Cancel</button></div><p id='lbg-cm' style='font-size:12px'></p>");
    m.querySelector("#lbg-x").onclick = function () { m.remove(); };
    m.querySelector("#lbg-send").onclick = function () {
      rpc("challenge_create", { p_target: id, p_stake: parseInt(m.querySelector("#lbg-stake").value, 10) }, true).then(function (r) {
        if (r && r.ok) { m.remove(); init(true); } else m.querySelector("#lbg-cm").textContent = (r && r.error) || "Could not send.";
      });
    };
  }
  function renderChallenges() {
    var box = document.getElementById("lbg-ch"); if (!box || !state.me) return;
    rpc("my_challenges", {}, true).then(function (r) {
      if (!r || !r.ok || !r.rows || !r.rows.length) return;
      var h = '<div class="lbg-sec"><h3>MY CHALLENGES</h3>' + r.rows.map(function (c) {
        var line = c.incoming ? esc(c.other_name) + " challenged you" : "You challenged " + esc(c.other_name);
        var st = { pending: "waiting", active: "live until " + (c.ends_at ? new Date(c.ends_at).toLocaleString() : ""), declined: "declined", cancelled: "cancelled", expired: "expired, stake returned", settled: c.won ? "you won" : "you lost", void: "tie, stakes returned" }[c.status] || c.status;
        var act = "";
        if (c.status === "pending" && c.incoming) act = '<button class="lbg-btn" data-ac="' + c.id + '">Accept</button> <button class="lbg-btn line" data-dc="' + c.id + '">Decline</button>';
        if (c.status === "pending" && !c.incoming) act = '<button class="lbg-btn line" data-cc="' + c.id + '">Cancel</button>';
        return '<div class="lbg-row"><div style="flex:1;min-width:0"><div class="lbg-n" style="font-size:14px">' + line + " \u00b7 " + c.stake + " AP</div><div class=lbg-tag>" + esc(st) + "</div></div>" + act + "</div>";
      }).join("") + "</div>";
      box.innerHTML = h;
      function wire(attr, fn) { box.querySelectorAll("[" + attr + "]").forEach(function (b) { b.onclick = function () { b.disabled = true; fn(b.getAttribute(attr)).then(function (x) { if (x && x.ok === false) alert(x.error); init(true); }); }; }); }
      wire("data-ac", function (id) { return rpc("challenge_respond", { p_id: id, p_accept: true }, true); });
      wire("data-dc", function (id) { return rpc("challenge_respond", { p_id: id, p_accept: false }, true); });
      wire("data-cc", function (id) { return rpc("challenge_cancel", { p_id: id }, true); });
    });
  }
  function init(keep) {
    var done = function (u) {
      state.me = u || null;
      if (!state.me) { state.scope = "global"; load(); return; }
      Promise.all([rpc("my_ranks", {}, true), rpc("my_header", {}, true)]).then(function (a) {
        state.ranks = a[0]; state.hdr = a[1];
        if (a[0] && a[0].state_name) state.area = { state: a[0].state_name, district: a[0].district_name };
        if (!keep) state.scope = state.area && state.area.district ? "district" : "global";
        load();
      }).catch(function () { load(); });
    };
    if (xrApi.user) xrApi.user().then(done, function () { done(null); }); else done(null);
  }
  window.xrLbGold = { mount: function (el) { root = el; init(false); } };
})();
