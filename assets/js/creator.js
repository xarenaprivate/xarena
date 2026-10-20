/* XARENA Creator Mode (web). Free-entry events only. No money, no wallet. */
(function () {
  "use strict";
  var KEY = "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7", URLB = "https://oskccuajgbyabogbvedj.supabase.co";
  var root = document.getElementById("cr-app"); if (!root) return;
  var CHECK = '<svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 1l3 2.2 3.7-.2 1.2 3.5 3 2.2-1.2 3.5 1.2 3.5-3 2.2-1.2 3.5-3.7-.2L12 23l-3-2.2-3.7.2-1.2-3.5-3-2.2L2.3 12 1.1 8.5l3-2.2 1.2-3.5L9 3z"/><path fill="#1a0a00" d="M10.5 15.5l-3-3 1.4-1.4 1.6 1.6 4.6-4.6 1.4 1.4z"/></svg>';
  var NOTE = '<div class="cr-note">Free entry only. No cash, no wallet. Prizes, if any, are arranged by the creator, not XARENA. Follower counts are self-reported by the creator.</div>';
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function rpc(n, a) { return window.xrApi.uRpc(n, a || {}); }
  function fmt(t) { try { return new Date(t).toLocaleString([], { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }); } catch (e) { return t; } }
  function num(n) { n = +n || 0; return n >= 1e6 ? (n / 1e6).toFixed(1) + "M" : n >= 1e3 ? (n / 1e3).toFixed(1).replace(/\.0$/, "") + "K" : String(n); }
  function get(path) { return fetch(URLB + "/rest/v1/" + path, { headers: { apikey: KEY } }).then(function (r) { return r.json(); }); }
  var me = null, st = null, dash = null;

  function events(list, mine) {
    if (!Array.isArray(list) || !list.length) return '<div class="cr-e">No creator tournaments yet. They appear here when an approved creator schedules one.</div>';
    return list.map(function (e) {
      var live = e.stream_live ? ' <span class="cr-pill" style="color:#ff6b5e;border-color:#ff6b5e">LIVE</span>' : "";
      var host = e.host_username ? '<p class="cr-badge">' + CHECK + " " + esc(e.host_ign || e.host_username) + " &middot; " + esc(e.host_tier) + " creator</p>" : "";
      return '<div class="cr-ev" data-id="' + e.id + '"><h4>' + esc(e.title) + live + ' <span class="cr-pill">' + esc(e.status) + "</span></h4>" + host +
        "<p>" + esc(e.mode || "") + " &middot; " + esc(e.map || "") + " &middot; " + fmt(e.starts_at) + " &middot; " + (e.joined == null ? "" : e.joined + "/") + e.slots + " players &middot; FREE entry</p>" +
        (e.description ? "<p>" + esc(e.description) + "</p>" : "") +
        '<div class="cr-row">' + (e.stream_url && e.stream_live ? '<a class="cr-go g" target="_blank" rel="noopener noreferrer" href="' + esc(e.stream_url) + '">Watch live</a>' : "") +
        (mine ? hostBtns(e) : playerBtns(e)) + '</div><div class="cr-msg" data-m></div></div>';
    }).join("");
  }
  function playerBtns(e) {
    if (e.status === "ended" || e.status === "cancelled") return "";
    return (e.status === "scheduled" ? '<button class="cr-go" data-a="join">Join (free)</button><button class="cr-go g" data-a="leave">Leave</button>' : "") +
      '<button class="cr-go g" data-a="room">Room ID / pass</button><button class="cr-go g" data-a="report">Report</button>';
  }
  function hostBtns(e) {
    if (e.status === "ended" || e.status === "cancelled") return "";
    return (e.status === "scheduled" ? '<button class="cr-go" data-a="start">Start</button>' : '<button class="cr-go" data-a="end">End</button>') +
      '<button class="cr-go g" data-a="players">Players</button><button class="cr-go g" data-a="setroom">Room</button><button class="cr-go g" data-a="stream">Live link</button>' +
      '<button class="cr-go g" data-a="delay">Delay</button><button class="cr-go r" data-a="cancel">Cancel</button>';
  }
  function wire(scope, reload) {
    scope.querySelectorAll(".cr-ev [data-a]").forEach(function (b) {
      b.addEventListener("click", function () {
        var card = b.closest(".cr-ev"), id = card.getAttribute("data-id"), m = card.querySelector("[data-m]"), a = b.getAttribute("data-a");
        function out(r) { m.textContent = r && r.ok ? "Done." : ((r && r.error) || "Failed."); if (r && r.ok && reload) reload(); }
        if (!me && a !== "x") { m.textContent = "Log in first (Account page)."; return; }
        if (a === "join") rpc("creator_event_join", { p_event: id }).then(out);
        else if (a === "leave") rpc("creator_event_leave", { p_event: id }).then(out);
        else if (a === "room") rpc("creator_event_room", { p_event: id }).then(function (r) { m.textContent = r && r.ok ? "Room ID: " + r.room_id + "  Pass: " + (r.room_pass || "-") : ((r && r.error) || "Not available."); });
        else if (a === "report") { var why = prompt("What is wrong with this event?"); if (why) rpc("creator_event_report", { p_event: id, p_reason: why }).then(out); }
        else if (a === "start" || a === "end") rpc("creator_event_manage", { p_event: id, p_action: a }).then(out);
        else if (a === "cancel") { if (confirm("Cancel this event?")) rpc("creator_event_manage", { p_event: id, p_action: "cancel" }).then(out); }
        else if (a === "delay") { var t = prompt("New start (YYYY-MM-DD HH:MM, your time)"); if (t) { var d = new Date(t.replace(" ", "T")); if (isNaN(d)) { m.textContent = "Invalid time."; return; } rpc("creator_event_manage", { p_event: id, p_action: "delay", p_starts: d.toISOString() }).then(out); } }
        else if (a === "setroom") { var rid = prompt("Room ID"); if (rid === null) return; var rp = prompt("Room password") || ""; rpc("creator_event_set_room", { p_event: id, p_room_id: rid, p_room_pass: rp }).then(out); }
        else if (a === "stream") { var u = prompt("YouTube live link (empty to clear)"); if (u === null) return; rpc("creator_event_set_stream", { p_event: id, p_url: u, p_live: !!u }).then(out); }
        else if (a === "players") rpc("creator_event_players", { p_event: id }).then(function (r) { if (!r || !r.ok) { m.textContent = (r && r.error) || "Failed."; return; }
          m.innerHTML = r.players.length ? r.players.map(function (p) { return esc(p.name) + " (" + p.status + ")" + (p.status === "joined" ? ' <a href="#" data-k="' + p.user_id + '">kick</a> <a href="#" data-b="' + p.user_id + '">ban</a>' : ""); }).join("<br>") : "No players yet.";
          m.querySelectorAll("[data-k],[data-b]").forEach(function (x) { x.addEventListener("click", function (ev) { ev.preventDefault(); rpc("creator_event_kick", { p_event: id, p_user: x.getAttribute("data-k") || x.getAttribute("data-b"), p_ban: !!x.getAttribute("data-b") }).then(function () { b.click(); }); }); }); });
      });
    });
  }

  function publicList(host) {
    get("creator_events_public?select=*&status=in.(scheduled,live)&order=starts_at.asc&limit=30").then(function (l) { host.innerHTML = events(l, false); wire(host); }).catch(function () { host.innerHTML = '<div class="cr-e">Could not load events.</div>'; });
  }

  function hostForm(box, reload) {
    var d = new Date(Date.now() + 3600e3 * 3); d.setMinutes(0, 0, 0);
    var v = new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    box.innerHTML = '<div class="cr-card"><div class="cr-t" style="margin-top:0">HOST A FREE TOURNAMENT</div><input class="cr-in" id="h-t" maxlength="80" placeholder="Title"><select class="cr-in" id="h-m"><option>Squad</option><option>Duo</option><option>Solo</option></select><input class="cr-in" id="h-map" value="Bermuda" maxlength="30"><input class="cr-in" id="h-s" type="datetime-local" value="' + v + '"><input class="cr-in" id="h-n" type="number" min="2" max="100" value="48" placeholder="Slots"><input class="cr-in" id="h-d" maxlength="400" placeholder="Short description (optional)"><input class="cr-in" id="h-r" maxlength="20" placeholder="Room ID (can add later)"><input class="cr-in" id="h-p" maxlength="20" placeholder="Room password (can add later)"><button class="cr-go" id="h-go">Create free event</button><div class="cr-msg" id="h-msg"></div>' + NOTE + "</div>";
    box.querySelector("#h-go").addEventListener("click", function () {
      var m = box.querySelector("#h-msg"); m.textContent = "Saving…";
      rpc("creator_event_create", { p_title: box.querySelector("#h-t").value, p_mode: box.querySelector("#h-m").value, p_map: box.querySelector("#h-map").value, p_starts: new Date(box.querySelector("#h-s").value).toISOString(), p_slots: parseInt(box.querySelector("#h-n").value, 10) || 48, p_desc: box.querySelector("#h-d").value, p_room_id: box.querySelector("#h-r").value, p_room_pass: box.querySelector("#h-p").value })
        .then(function (r) { m.textContent = r && r.ok ? "Event created." : ((r && r.error) || "Failed."); if (r && r.ok) reload(); });
    });
  }

  function render() {
    var h = '<div class="cr-h">CREATOR MODE</div>';
    if (!me) { root.innerHTML = h + '<div class="cr-card">Log in to apply as a creator or join creator tournaments. <br><br><a class="cr-go" href="account.html">Log in / Sign up</a></div>' + NOTE + '<div class="cr-t">CREATOR TOURNAMENTS</div><div id="cr-pub"></div>'; publicList(root.querySelector("#cr-pub")); return; }
    if (st && st.is_creator && dash && dash.ok) {
      var d = dash, nm = d.name || d.username;
      h += '<div class="cr-card"><div class="cr-prof"><div class="cr-av">' + (d.avatar_url ? '<img src="' + esc(d.avatar_url) + '" alt="">' : esc(String(nm).charAt(0).toUpperCase())) + '</div><div><div class="cr-nm">' + esc(nm) + '</div><div class="cr-sub"><span class="cr-badge">' + CHECK + " VERIFIED CREATOR</span> &middot; " + esc(d.tier) + '</div></div></div><div class="cr-stats"><div><b>' + num(d.self_reported_followers) + '</b><span>Followers (self-reported)</span></div><div><b>' + d.tournaments_hosted + '</b><span>Tournaments</span></div><div><b>' + d.wins + '</b><span>Wins</span></div></div></div>' +
        '<div class="cr-live"><i class="cr-dot"></i> LIVE NOW: ' + d.live_now + " creator tournament" + (d.live_now === 1 ? "" : "s") + " running</div><div class=\"cr-t\">QUICK ACTIONS</div>" +
        '<button class="cr-btn" id="q-host">Host Free Tournament<small>Create and manage your own free-entry event</small></button>' +
        '<button class="cr-btn" disabled>Find Sponsors<small>Coming soon</small></button><button class="cr-btn" disabled>Build My City Team<small>Coming soon</small></button><div id="cr-form"></div>' +
        '<div class="cr-t">MY TOURNAMENTS</div><div id="cr-mine"></div>' + NOTE + '<div class="cr-t">ALL CREATOR TOURNAMENTS</div><div id="cr-pub"></div>';
      root.innerHTML = h;
      root.querySelector("#q-host").addEventListener("click", function () { hostForm(root.querySelector("#cr-form"), load); });
      var mine = root.querySelector("#cr-mine");
      get("creator_events_public?select=*&host=eq." + me.id + "&order=starts_at.desc&limit=30").then(function (l) { mine.innerHTML = events(l, true); wire(mine, load); });
      publicList(root.querySelector("#cr-pub")); return;
    }
    var app = st && st.application_status;
    if (app === "pending") h += '<div class="cr-card"><b>Application pending.</b><br>The admin will review your channel. You will be able to host as soon as it is approved.</div>';
    else {
      h += '<div class="cr-card"><div class="cr-t" style="margin-top:0">BECOME A VERIFIED CREATOR</div>' + (app === "rejected" || app === "revoked" ? '<div class="cr-msg">Your last application was ' + esc(app) + ". You can apply again.</div>" : "") +
        '<input class="cr-in" id="a-c" placeholder="YouTube or Instagram channel link (https://...)"><input class="cr-in" id="a-f" type="number" min="0" placeholder="Followers (self-reported)"><input class="cr-in" id="a-n" maxlength="300" placeholder="Why do you want to host? (optional)"><button class="cr-go" id="a-go">Apply</button><div class="cr-msg" id="a-msg"></div>' + NOTE + "</div>";
    }
    h += '<div class="cr-t">CREATOR TOURNAMENTS</div><div id="cr-pub"></div>';
    root.innerHTML = h;
    var go = root.querySelector("#a-go");
    if (go) go.addEventListener("click", function () {
      var m = root.querySelector("#a-msg"); m.textContent = "Sending…";
      rpc("creator_apply", { p_channel: root.querySelector("#a-c").value, p_followers: parseInt(root.querySelector("#a-f").value, 10), p_note: root.querySelector("#a-n").value }).then(function (r) { if (r && r.ok) load(); else m.textContent = (r && r.error) || "Failed."; });
    });
    publicList(root.querySelector("#cr-pub"));
  }
  function load() {
    window.xrApi.user().then(function (u) { me = u;
      if (!u) return render();
      return rpc("creator_status").then(function (s) { st = s; if (s && s.is_creator) return rpc("creator_dashboard").then(function (d) { dash = d; render(); }); render(); });
    });
  }
  document.addEventListener("DOMContentLoaded", function () {
    if (!window.xrApi) { root.innerHTML = '<div class="cr-e">Could not load.</div>'; return; }
    setTimeout(load, 200);
  });
})();
