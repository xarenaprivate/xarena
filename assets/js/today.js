/* XARENA home "Today" section. Real data only; every empty state is honest. Additive: reads public data and calls existing RPCs. */
(function () {
  "use strict";
  var root = document.getElementById("today"); if (!root) return;
  var KEY = "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7", URLB = "https://oskccuajgbyabogbvedj.supabase.co";
  var AVB = "https://xarenaprivate.github.io/xarena/assets/avatars/";
  var MONTHS = { january: 0, february: 1, march: 2, april: 3, may: 4, june: 5, july: 6, august: 7, september: 8, october: 9, november: 10, december: 11 };
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function get(path) { return fetch(URLB + "/rest/v1/" + path, { headers: { apikey: KEY } }).then(function (r) { return r.json(); }); }
  function rpc(n, a) { return window.xrApi ? window.xrApi.uRpc(n, a || {}) : Promise.resolve(null); }
  function el(id) { return document.getElementById(id); }
  function safeLink(u) { u = String(u || ""); return /^https:\/\//.test(u) || /^[a-z0-9-]+\.html(#[a-z0-9-]*)?$/i.test(u) ? u : ""; }
  /* start time: start_at if set, else parse "Saturday, October 24, 2026 at 8:00 PM IST" (IST = UTC+5:30). null if it cannot be read. */
  function startOf(t) {
    if (t.start_at) { var d = new Date(t.start_at); return isNaN(d) ? null : d; }
    var m = /([A-Za-z]+)\s+(\d{1,2}),\s*(\d{4})\s+at\s+(\d{1,2}):(\d{2})\s*(AM|PM)\s*IST/i.exec(t.date_text || "");
    if (!m || MONTHS[m[1].toLowerCase()] == null) return null;
    var h = (+m[4]) % 12 + (/pm/i.test(m[6]) ? 12 : 0);
    return new Date(Date.UTC(+m[3], MONTHS[m[1].toLowerCase()], +m[2], h, +m[5]) - 330 * 60000);
  }
  function fmtIST(d) { try { return d.toLocaleString("en-IN", { timeZone: "Asia/Kolkata", weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit" }) + " IST"; } catch (e) { return ""; } }
  function card(id, title, body) { return '<div class="td-c" id="' + id + '"><div class="td-t">' + title + "</div>" + body + "</div>"; }
  root.innerHTML = '<div class="wrap"><div id="td-pin"></div><div class="overline">Today</div><h2>Aaj kya hai</h2><div class="td-lite"><button type="button" id="td-lite" class="td-btn td-ghost"></button><button type="button" id="td-theme" class="td-btn td-ghost"></button><button type="button" id="td-lang" class="td-btn td-ghost"></button><span class="td-sub">Data saver turns off 3D, animations and the big hero image on all pages (good for slow phones).</span></div><div class="td-grid">' +
    card("td-tip", "Aaj ka tip", '<div class="td-empty">Loading…</div>') +
    card("td-cr", "Creator spotlight", '<div class="td-empty">Loading…</div>') +
    card("td-cd", "Countdown", '<div class="td-empty">Loading…</div>') +
    card("td-reg", "Squads registered", '<div class="td-empty">Loading…</div>') +
    card("td-match", "Next match", '<div class="td-empty">Loading…</div>') +
    card("td-streak", "Daily check-in", '<div class="td-empty">Loading…</div>') +
    card("td-daily", "Daily unlocks", '<div class="td-empty">Loading…</div>') +
    card("td-win", "Latest winners", '<div class="td-empty">Loading…</div>') +
    card("td-top", "Top 3 squads", '<div class="td-empty">Loading…</div>') + "</div></div>";

  /* pinned announcement (site_settings key "pinned", set in Admin > Pinned) */
  get("site_settings?key=eq.pinned&select=value").then(function (r) {
    var v = r && r[0] && r[0].value; if (!v || !v.active || !v.title) return;
    if (v.expires_at) { var x = new Date(v.expires_at); if (!isNaN(x) && x < new Date()) return; }
    var l = safeLink(v.link);
    el("td-pin").innerHTML = '<div class="td-pin"><b>' + esc(v.title) + "</b>" + (v.text ? "<span>" + esc(v.text) + "</span>" : "") + (l ? '<a href="' + esc(l) + '"' + (/^https/.test(l) ? ' target="_blank" rel="noopener noreferrer"' : "") + ">Open</a>" : "") + "</div>";
  }).catch(function () {});

  var T = null, tick = null, mine = null;
  function setCountdown() {
    var box = el("td-cd"); if (!T) return;
    function paint() {
      var ms = T.start - Date.now();
      if (ms <= 0) { box.querySelector(".td-big").textContent = "Started"; if (tick) clearInterval(tick); return; }
      var s = Math.floor(ms / 1000), d = Math.floor(s / 86400), h = Math.floor(s % 86400 / 3600), m = Math.floor(s % 3600 / 60), ss = s % 60;
      box.querySelector(".td-big").textContent = (d ? d + "d " : "") + ("0" + h).slice(-2) + "h " + ("0" + m).slice(-2) + "m " + ("0" + ss).slice(-2) + "s";
    }
    box.innerHTML = '<div class="td-t">Countdown</div><div class="td-name">' + esc(T.t.name) + '</div><div class="td-big"></div><div class="td-sub">' + esc(fmtIST(new Date(T.start))) + "</div>";
    paint(); tick = setInterval(paint, 1000);
  }
  function setRegister(count) {
    var box = el("td-reg"), t = T.t, slots = +t.slots || 0, n = count == null ? null : +count;
    var pct = slots && n != null ? Math.min(100, Math.round(n / slots * 100)) : 0;
    var full = slots && n != null && n >= slots;
    var cta;
    if (mine) cta = '<a class="td-btn td-ok" href="tournaments.html#register">You are registered &#10003; (' + esc(mine.status) + ")</a>";
    else if (full) cta = '<div class="td-sub">Registrations are full.</div>';
    else if (t.reg_open) cta = '<a class="td-btn" href="tournaments.html#register">Register your squad</a>';
    else cta = '<div class="td-sub">Registration is not open yet.</div>';
    box.innerHTML = '<div class="td-t">Squads registered</div><div class="td-big">' + (n == null ? "-" : n) + " / " + (slots || "-") + '</div><div class="td-bar"><i style="width:' + pct + '%"></i></div><div class="td-sub">' + esc(t.name) + " &middot; free entry</div>" + cta;
  }
  function setMatch(matches) {
    var box = el("td-match"), m = (matches || [])[0];
    if (!m) { box.innerHTML = '<div class="td-t">Next match</div><div class="td-empty">The match schedule is announced soon. Room ID shows here for registered players.</div>'; return; }
    var when = m.starts_at ? fmtIST(new Date(m.starts_at)) : "Time to be announced";
    box.innerHTML = '<div class="td-t">Next match</div><div class="td-name">' + esc(m.title) + '</div><div class="td-sub">' + esc(when) + " &middot; Room ID unlocks " + esc(m.reveal_minutes) + ' min before</div><div id="td-room"></div>';
    if (!mine) { el("td-room").innerHTML = '<div class="td-sub">Room ID is for registered players only.</div>'; return; }
    el("td-room").innerHTML = '<button type="button" class="td-btn" id="td-room-b">Show Room ID</button><div class="td-sub" id="td-room-o"></div>';
    el("td-room-b").addEventListener("click", function () {
      rpc("match_room", { p_match: m.id }).then(function (r) {
        el("td-room-o").innerHTML = r && r.ok ? "Room ID <b>" + esc(r.room_id) + "</b> &middot; Password <b>" + esc(r.room_pass) + "</b>" : esc((r && r.error) || "Not available yet.");
      });
    });
  }
  function setStreak() {
    var box = el("td-streak");
    function login() { box.innerHTML = '<div class="td-t">Daily check-in</div><div class="td-empty">Log in to start your streak.</div><a class="td-btn" href="account.html">Log in</a>'; }
    if (!window.xrApi) { login(); return; }
    window.xrApi.user().then(function (u) {
      if (!u) { login(); return; }
      function paint(s) {
        if (!s || !s.ok) { login(); return; }
        box.innerHTML = '<div class="td-t">Daily check-in</div><div class="td-big">' + esc(s.streak) + ' day streak</div><div class="td-sub">XP ' + esc(s.xp) + " &middot; level " + esc(s.level) + "</div>" +
          (s.checked_in_today ? '<div class="td-sub td-ok">Checked in today &#10003;</div>' : '<button type="button" class="td-btn" id="td-ci">Check in</button>') + '<div class="td-sub" id="td-ci-m"></div>';
        var b = el("td-ci"); if (b) b.addEventListener("click", function () { b.disabled = true; rpc("daily_checkin").then(function () { return rpc("my_game_stats"); }).then(paint); });
      }
      rpc("my_game_stats").then(paint);
    }).catch(login);
  }
  function setDaily() {
    var box = el("td-daily");
    rpc("daily_avatar").then(function (r) {
      var a = r && r.avatar, h = '<div class="td-t">Daily unlocks</div>';
      if (a) {
        h += '<div class="td-av"><img src="' + AVB + esc(a.asset) + '.webp" alt="" width="56" height="56" loading="lazy"><div><b>' + esc(a.title) + '</b><div class="td-sub">Today\'s free avatar (cosmetic)</div></div></div>';
        h += r.owned ? '<div class="td-sub td-ok">Unlocked &#10003;</div>' : (r.owned === false ? '<button type="button" class="td-btn" id="td-av-b">Unlock free</button>' : '<a class="td-btn" href="account.html">Log in to unlock</a>');
      }
      h += '<div class="td-links"><a href="play.html">Daily quiz</a><a href="play.html">Daily mission</a></div><div class="td-sub" id="td-av-m"></div>';
      box.innerHTML = h;
      var b = el("td-av-b"); if (b) b.addEventListener("click", function () { b.disabled = true; rpc("daily_avatar_claim").then(function (x) { el("td-av-m").textContent = x && x.ok ? "Unlocked. Equip it in the Store." : ((x && x.error) || "Failed."); if (x && x.ok) b.remove(); }); });
    }).catch(function () { box.innerHTML = '<div class="td-t">Daily unlocks</div><div class="td-links"><a href="play.html">Daily quiz</a><a href="play.html">Daily mission</a></div>'; });
  }
  function setWinners() {
    get("hall_of_fame?select=category,name,detail,season,logo_url&order=created_at.desc&limit=3").then(function (rows) {
      var box = el("td-win");
      if (!Array.isArray(rows) || !rows.length) { box.innerHTML = '<div class="td-t">Latest winners</div><div class="td-empty">Results appear here after the first match.</div>'; return; }
      box.innerHTML = '<div class="td-t">Latest winners</div>' + rows.map(function (r) {
        return '<div class="td-row">' + (/^https:\/\//.test(r.logo_url || "") ? '<img src="' + esc(r.logo_url) + '" alt="" width="28" height="28" loading="lazy">' : "") + "<div><b>" + esc(r.name) + '</b><div class="td-sub">' + esc(r.category) + (r.detail ? " &middot; " + esc(r.detail) : "") + "</div></div></div>";
      }).join("") + '<a class="td-more" href="winners.html">All winners</a>';
    }).catch(function () {});
  }
  function setTop() {
    rpc("squad_board", { p_scope: "global" }).then(function (r) {
      var box = el("td-top"), rows = r && r.ok && r.rows || [];
      if (!rows.length) { box.innerHTML = '<div class="td-t">Top 3 squads</div><div class="td-empty">No squads ranked yet.</div>'; return; }
      box.innerHTML = '<div class="td-t">Top 3 squads</div>' + rows.slice(0, 3).map(function (s) {
        return '<div class="td-row"><span class="td-rk">#' + esc(s.rank) + "</span><div><b>" + esc(s.name) + '</b><div class="td-sub">' + esc(s.xp) + " XP &middot; " + esc(s.members) + " members</div></div></div>";
      }).join("") + '<a class="td-more" href="leaderboard.html">Full leaderboard</a>';
    }).catch(function () {});
  }

  function setLite() {
    var b = el("td-lite"); function on() { try { return localStorage.getItem("xr_lite") === "1"; } catch (e) { return false; } }
    function paint() { b.textContent = "Data saver: " + (on() ? "ON" : "OFF"); }
    paint(); b.addEventListener("click", function () { try { localStorage.setItem("xr_lite", on() ? "0" : "1"); } catch (e) {} location.reload(); });
  }
  function setTheme() {
    var b = el("td-theme"); if (!window.xrLooks) { if (!setTheme.t) { setTheme.t = 1; setTimeout(setTheme, 700); } else b.remove(); return; }
    var cur = window.xrLooks.current();
    b.textContent = cur === "gold" ? "Theme: Dark Gold (tap for previous)" : "Theme: try Dark Gold";
    b.addEventListener("click", function () {
      if (cur === "gold") { var pv = localStorage.getItem("xr_prev_look") || "liquid"; window.xrLooks.pick(pv === "gold" ? "liquid" : pv); }
      else { try { localStorage.setItem("xr_prev_look", cur); } catch (e) {} window.xrLooks.pick("gold"); }
    });
  }
  function setLang() {
    var b = el("td-lang"); if (!window.xrLang) { if (!setLang.t) { setLang.t = 1; setTimeout(setLang, 700); } else b.remove(); return; }
    function paint() { b.textContent = "Language: " + (window.xrLang.get() === "hi" ? "Hinglish" : "English"); }
    paint(); b.addEventListener("click", function () { window.xrLang.set(window.xrLang.get() === "hi" ? "en" : "hi"); location.reload(); });
  }
  function setTip() {
    get("site_settings?key=eq.tips&select=value").then(function (r) {
      var items = r && r[0] && r[0].value && r[0].value.items, box = el("td-tip");
      if (!Array.isArray(items) || !items.length) { box.remove(); return; }
      var day = Math.floor((Date.now() + 330 * 60000) / 86400000);
      box.innerHTML = '<div class="td-t">Aaj ka tip</div><div class="td-name" style="font-weight:700">' + esc(items[day % items.length]) + "</div>";
    }).catch(function () { var b = el("td-tip"); if (b) b.remove(); });
  }
  function setCreator() {
    get("creators_public?select=username,ign,avatar_url,tier,self_followers,channel_url,verified_creator").then(function (rows) {
      var box = el("td-cr");
      if (!Array.isArray(rows) || !rows.length) { box.remove(); return; }
      var day = Math.floor((Date.now() + 330 * 60000) / 86400000), c = rows[day % rows.length];
      var link = /^https:\/\/(www\.)?(youtube\.com|youtu\.be|instagram\.com)\//.test(c.channel_url || "") ? c.channel_url : "creator.html";
      box.innerHTML = '<div class="td-t">Creator spotlight</div><div class="td-name">' + esc(c.ign || c.username) + ' <span class="td-ok">&#10003; verified</span></div><div class="td-sub">' + esc(c.tier) + ' creator &middot; self-reported followers: ' + esc(c.self_followers) + '</div><a class="td-more" href="' + esc(link) + '"' + (link === "creator.html" ? "" : ' target="_blank" rel="noopener noreferrer"') + ">Visit channel</a>";
    }).catch(function () { var b = el("td-cr"); if (b) b.remove(); });
  }

  function boot() {
    setLite(); setTheme(); setLang(); setTip(); setCreator(); setStreak(); setDaily(); setWinners(); setTop();
    get("tournaments?select=id,name,date_text,start_at,slots,reg_open,status&order=sort.asc,created_at.asc").then(function (list) {
      var now = Date.now(), best = null;
      (list || []).forEach(function (t) { var d = startOf(t); if (d && d.getTime() > now - 3 * 3600000 && (!best || d < best.start)) best = { t: t, start: d }; });
      if (!best) { ["td-cd", "td-reg", "td-match"].forEach(function (id) { el(id).innerHTML = '<div class="td-t">' + (id === "td-cd" ? "Countdown" : id === "td-reg" ? "Squads registered" : "Next match") + '</div><div class="td-empty">No upcoming tournament is announced yet.</div>'; }); return; }
      T = best; best.start = best.start.getTime(); setCountdown();
      var needMine = window.xrApi ? window.xrApi.user().then(function (u) { return u ? rpc("my_registrations") : null; }).catch(function () { return null; }) : Promise.resolve(null);
      Promise.all([rpc("tournament_reg_counts").catch(function () { return []; }), needMine, get("matches_public?tournament_id=eq." + encodeURIComponent(best.t.id) + "&select=id,title,starts_at,status,reveal_minutes&order=starts_at.asc.nullslast&limit=5").catch(function () { return []; })]).then(function (x) {
        var c = (x[0] || []).filter(function (r) { return r.tournament_id === best.t.id; })[0];
        var regs = x[1] && x[1].ok ? x[1].registrations : [];
        mine = (regs || []).filter(function (r) { return r.tournament_id === best.t.id && r.status !== "rejected"; })[0] || null;
        setRegister(c ? c.registered : null);
        var up = (x[2] || []).filter(function (m) { return !m.starts_at || new Date(m.starts_at).getTime() > Date.now() - 3 * 3600000; });
        setMatch(up);
      });
    }).catch(function () {});
  }
  if (window.xrApi) boot(); else window.addEventListener("load", boot);
})();
