(function () {
  "use strict";
  var app = document.getElementById("hub-app");
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function rpc(n, a, auth) { if (auth && xrApi.uRpc) return xrApi.uRpc(n, a || {}); return fetch(xrApi.url + "/rest/v1/rpc/" + n, { method: "POST", headers: { apikey: xrApi.key, "Content-Type": "application/json" }, body: JSON.stringify(a || {}) }).then(function (r) { return r.json(); }); }
  var css = "<style>.hb-c{background:rgba(20,12,40,.7);border:1px solid rgba(255,255,255,.1);border-radius:16px;padding:16px;margin:0 0 14px}.hb-c h3{margin:0 0 10px;font-size:15px;letter-spacing:1.5px;color:#ffd23f}" +
    ".hb-r{display:flex;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid rgba(255,255,255,.06)}.hb-r:last-child{border:0}.hb-r .t{flex:1;min-width:0}.hb-r small{display:block;color:#b9a8d8;font-size:12px}" +
    ".hb-b{border:0;border-radius:10px;padding:8px 12px;font-weight:700;font-size:13px;cursor:pointer;background:linear-gradient(180deg,#ffd23f,#f5a300);color:#241500}.hb-b[disabled]{opacity:.45;cursor:default}.hb-b.l{background:transparent;color:#ffd23f;border:1px solid rgba(255,210,63,.5)}" +
    ".hb-tabs{display:flex;gap:6px;margin:0 0 10px;flex-wrap:wrap}.hb-in{width:100%;padding:9px;border-radius:8px;border:1px solid rgba(255,255,255,.15);background:rgba(0,0,0,.3);color:#fff;margin:4px 0}.hb-e{text-align:center;color:#b9a8d8;padding:14px;font-size:13px}</style>";
  var me = null, sc = "global";
  function card(t, inner) { return '<div class="hb-c"><h3>' + t + "</h3>" + inner + "</div>"; }
  function login() { return '<div class="hb-e">Log in to use this. <a href="account.html" style="color:#ffd23f">Log in</a></div>'; }
  function missions() {
    if (!me) return card("DAILY MISSIONS", login());
    return rpc("daily_missions_v2", {}, true).then(function (r) {
      if (!r || !r.ok) return card("DAILY MISSIONS", '<div class="hb-e">Could not load.</div>');
      var hint = { checkin: "Open the Streak page and check in.", match: "Check in on a tournament match.", friend: "Add a friend from a player profile." };
      return card("DAILY MISSIONS \u00b7 resets 12:00 AM IST", r.missions.map(function (m) {
        return '<div class="hb-r"><div class="t"><b>' + esc(m.title) + "</b><small>+" + m.reward + " EXP and AP \u00b7 " + hint[m.kind] + '</small></div><button class="hb-b" data-m="' + m.kind + '"' + (!m.done || m.claimed ? " disabled" : "") + ">" + (m.claimed ? "Claimed" : m.done ? "Claim" : "Not done") + "</button></div>";
      }).join(""));
    });
  }
  function season() {
    return rpc("season_pass", {}, !!me).then(function (r) {
      if (!r || !r.ok) return "";
      return card("SEASON PASS \u00b7 FREE \u00b7 LEVEL " + r.level + " (" + r.xp + " EXP)", r.tiers.map(function (t) {
        var st = t.claimed ? "Claimed" : t.unlocked ? "Claim" : "Level " + t.level;
        return '<div class="hb-r"><div class="t"><b>' + esc(t.title) + "</b><small>Unlocks at level " + t.level + " \u00b7 " + esc(t.kind) + '</small></div><button class="hb-b" data-s="' + t.level + '"' + (!me || !t.unlocked || t.claimed ? " disabled" : "") + ">" + st + "</button></div>";
      }).join("") + '<div class="hb-e">No payments. Rewards are badges and AP only.</div>');
    });
  }
  function squads() {
    return rpc("squad_board", { p_scope: sc }, !!me).then(function (r) {
      var tabs = '<div class="hb-tabs">' + ["global", "state", "district"].map(function (k) { return '<button class="hb-b' + (k === sc ? "" : " l") + '" data-sc="' + k + '">' + k + "</button>"; }).join("") + "</div>";
      if (r && r.ok === false) return card("SQUAD WARS BOARD", tabs + '<div class="hb-e">' + (r.error === "location_required" ? "Turn on your area on the Leaderboard page to see state and district squads." : esc(r.error)) + "</div>");
      var rows = (r.rows || []);
      return card("SQUAD WARS BOARD \u00b7 combined member EXP", tabs + (rows.length ? rows.map(function (q) { return '<div class="hb-r"><b style="width:30px">#' + q.rank + '</b><div class="t"><b>' + esc(q.name) + (q.tag ? " [" + esc(q.tag) + "]" : "") + "</b><small>" + q.members + " members" + (q.district ? " \u00b7 " + esc(q.district) : "") + "</small></div><b>" + Number(q.xp).toLocaleString("en-IN") + " EXP</b></div>"; }).join("") : '<div class="hb-e">No squads ranked here yet.</div>') + '<p style="font-size:12px;color:#b9a8d8;margin:8px 0 0">Captains can challenge squads to a war from the <a href="squads.html" style="color:#ffd23f">Squads page</a>.</p>');
    });
  }
  function scrims() {
    if (!me) return card("SCRIM FINDER", login());
    return rpc("scrim_find", {}, true).then(function (r) {
      var form = '<div style="margin-top:10px"><b style="font-size:13px">Post "need scrim" (squad captains)</b><input class="hb-in" id="sc-day" placeholder="Day, e.g. Tonight" maxlength="30"><input class="hb-in" id="sc-time" placeholder="Time, e.g. 9 PM" maxlength="30"><input class="hb-in" id="sc-note" placeholder="Note (optional)" maxlength="160"><button class="hb-b" id="sc-post">Post request</button> <span id="sc-msg" style="font-size:12px"></span></div>';
      if (r && r.error === "location_required") return card("SCRIM FINDER", '<div class="hb-e">Turn on your area on the <a href="leaderboard.html" style="color:#ffd23f">Leaderboard</a> page to find squads in your district.</div>');
      if (!r || !r.ok) return card("SCRIM FINDER", '<div class="hb-e">Could not load.</div>');
      return card("SCRIM FINDER \u00b7 " + esc(r.district), (r.rows.length ? r.rows.map(function (x) {
        return '<div class="hb-r"><div class="t"><b>' + esc(x.squad) + "</b><small>" + esc(x.day) + " " + esc(x.time) + (x.note ? " \u00b7 " + esc(x.note) : "") + "</small></div>" + (x.mine ? '<button class="hb-b l" data-sx="' + x.id + '">Close</button>' : '<button class="hb-b" data-sr="' + x.id + '">Scrim?</button>') + "</div>";
      }).join("") : '<div class="hb-e">No open scrim requests in your district yet.</div>') + form);
    });
  }
  function wire() {
    function on(attr, fn) { app.querySelectorAll("[" + attr + "]").forEach(function (b) { b.onclick = function () { fn(b.getAttribute(attr), b); }; }); }
    on("data-m", function (k, b) { b.disabled = true; rpc("claim_mission_v2", { p_kind: k }, true).then(function (r) { if (r && r.ok === false) alert(r.error); draw(); }); });
    on("data-s", function (l, b) { b.disabled = true; rpc("season_claim", { p_level: parseInt(l, 10) }, true).then(function (r) { if (r && r.ok === false) alert(r.error); draw(); }); });
    on("data-sc", function (k) { sc = k; draw(); });
    on("data-sr", function (id) { rpc("scrim_reach", { p_id: id }, true).then(function (r) { alert(r && r.ok ? "Sent. Their captain was notified. You can message them from Messages." : (r && r.error) || "Failed"); }); });
    on("data-sx", function (id) { rpc("scrim_close", { p_id: id }, true).then(draw); });
    var p = document.getElementById("sc-post"); if (p) p.onclick = function () {
      rpc("scrim_post", { p_day: document.getElementById("sc-day").value, p_time: document.getElementById("sc-time").value, p_note: document.getElementById("sc-note").value }, true).then(function (r) { if (r && r.ok) draw(); else document.getElementById("sc-msg").textContent = (r && r.error) || "Failed"; });
    };
  }
  function draw() { Promise.all([missions(), season(), squads(), scrims()]).then(function (a) { app.innerHTML = css + a.join(""); wire(); }); }
  function go() { (xrApi.user ? xrApi.user() : Promise.resolve(null)).then(function (u) { me = u; draw(); }, function () { me = null; draw(); }); }
  if (window.xrApi) go(); else document.addEventListener("xr-backend-live", go, { once: true });
})();
