/* XARENA Arena Hub: points table, history, spotlights, notices, head-to-head, squad profile, feedback. Real data only. */
(function () {
  "use strict";
  var KEY = "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7", URLB = "https://oskccuajgbyabogbvedj.supabase.co";
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function get(p) { return fetch(URLB + "/rest/v1/" + p, { headers: { apikey: KEY } }).then(function (r) { return r.json(); }); }
  function prpc(n, a) { return fetch(URLB + "/rest/v1/rpc/" + n, { method: "POST", headers: { apikey: KEY, "Content-Type": "application/json" }, body: JSON.stringify(a || {}) }).then(function (r) { return r.json(); }); }
  function safe(u) { u = String(u || ""); return /^https:\/\//.test(u) ? u : ""; }
  var body = document.getElementById("hub-body"), tabs = document.getElementById("hub-tabs");
  var T = [["points", "Points table"], ["history", "History"], ["spot", "Spotlights"], ["notices", "Notice board"], ["h2h", "Head to head"], ["squad", "Squad profile"], ["creators", "Creators"], ["guide", "Guide & checklist"], ["faq", "FAQ"], ["feedback", "Feedback"]];
  function empty(t) { return '<div class="hub-c hub-e">' + t + "</div>"; }
  var V = {
    points: function () {
      get("tournaments?select=id,name&order=sort.asc").then(function (ts) {
        body.innerHTML = '<div class="hub-c"><select id="p-t"><option value="">All tournaments</option>' + (ts || []).map(function (t) { return '<option value="' + esc(t.id) + '">' + esc(t.name) + "</option>"; }).join("") + '</select></div><div id="p-r">Loading…</div>';
        var sel = document.getElementById("p-t");
        function load() { prpc("points_table", { p_tournament: sel.value || null }).then(function (r) {
          var box = document.getElementById("p-r");
          if (!Array.isArray(r) || !r.length) { box.innerHTML = empty("No results yet. The table fills in after the first match is played."); return; }
          box.innerHTML = '<div class="hub-c"><table class="hub-t"><tr><th>#</th><th>Squad</th><th>Matches</th><th>Kills</th><th>Points</th></tr>' + r.map(function (x, i) { return "<tr><td>" + (i + 1) + "</td><td>" + esc(x.squad) + "</td><td>" + x.matches + "</td><td>" + x.kills + "</td><td><b>" + x.points + "</b></td></tr>"; }).join("") + "</table></div>"; }); }
        sel.addEventListener("change", load); load();
      });
    },
    history: function () {
      Promise.all([get("tournaments?select=id,name,status,date_text,mode&order=sort.asc"), get("hall_of_fame?select=season,category,name,detail,logo_url&order=created_at.desc")]).then(function (a) {
        var ts = (a[0] || []).filter(function (t) { return /complete|finish|ended|past/i.test(t.status || ""); }), hof = a[1] || [];
        var h = ts.length ? ts.map(function (t) { return '<div class="hub-c"><b>' + esc(t.name) + "</b><br>" + esc(t.date_text) + "</div>"; }).join("") : empty("No finished tournaments yet. The Ignition Cup is the first.");
        h += "<h3>Hall of fame</h3>" + (hof.length ? hof.map(function (x) { return '<div class="hub-c">' + (safe(x.logo_url) ? '<img src="' + esc(x.logo_url) + '" alt="" width="36" height="36" style="border-radius:8px;vertical-align:middle;margin-right:8px">' : "") + "<b>" + esc(x.name) + "</b> - " + esc(x.category) + (x.detail ? " · " + esc(x.detail) : "") + (x.season ? " · " + esc(x.season) : "") + "</div>"; }).join("") : empty("No winners yet."));
        body.innerHTML = h;
      });
    },
    spot: function () {
      var L = { mvp_match: "MVP of the match", mvp_week: "MVP of the week", squad_week: "Squad of the Week", player_day: "Player of the Day", clip_week: "Clip of the week" };
      get("spotlights?select=kind,name,detail,logo_url,ref_url,created_at&order=created_at.desc&limit=40").then(function (r) {
        if (!Array.isArray(r) || !r.length) { body.innerHTML = empty("Nothing picked yet. Admin picks MVPs, Squad of the Week and Player of the Day."); return; }
        var seen = {}, out = "";
        r.forEach(function (x) { if (seen[x.kind]) return; seen[x.kind] = 1; out += '<div class="hub-c"><small>' + esc(L[x.kind] || x.kind) + "</small><br><b style='font-size:18px'>" + esc(x.name) + "</b>" + (x.detail ? "<div>" + esc(x.detail) + "</div>" : "") + (safe(x.ref_url) ? '<a href="' + esc(x.ref_url) + '" target="_blank" rel="noopener noreferrer">Watch</a>' : "") + "</div>"; });
        body.innerHTML = out + "<h3>Earlier</h3>" + r.filter(function (x, i) { return r.findIndex(function (y) { return y.kind === x.kind; }) !== i; }).map(function (x) { return '<div class="hub-c"><small>' + esc(L[x.kind]) + "</small> " + esc(x.name) + "</div>"; }).join("");
      });
    },
    notices: function () {
      get("notices?select=title,body,link,pinned,created_at&order=pinned.desc,created_at.desc&limit=30").then(function (r) {
        if (!Array.isArray(r) || !r.length) { body.innerHTML = empty("No notices right now."); return; }
        body.innerHTML = r.map(function (n) { var l = safe(n.link) || (/^[a-z0-9-]+\.html$/i.test(n.link || "") ? n.link : ""); return '<div class="hub-c">' + (n.pinned ? "📌 " : "") + "<b>" + esc(n.title) + "</b>" + (n.body ? "<div>" + esc(n.body) + "</div>" : "") + (l ? '<a href="' + esc(l) + '">Open</a>' : "") + "</div>"; }).join("");
      });
    },
    h2h: function () {
      get("squads_public?select=name&order=name.asc&limit=200").then(function (sq) {
        var o = (sq || []).map(function (s) { return "<option>" + esc(s.name) + "</option>"; }).join("");
        body.innerHTML = '<div class="hub-c"><select id="h-a">' + o + '</select><select id="h-b">' + o + '</select><button class="hub-b" id="h-go">Compare</button><div id="h-r"></div></div>';
        document.getElementById("h-go").addEventListener("click", function () {
          var a = document.getElementById("h-a").value, b = document.getElementById("h-b").value, out = document.getElementById("h-r");
          if (!a || !b || a === b) { out.textContent = "Pick two different squads."; return; }
          prpc("head_to_head", { p_a: a, p_b: b }).then(function (r) { out.innerHTML = !r || !r.played ? empty("These two squads have not played the same match yet.") : "<p>Played together: <b>" + r.played + "</b></p><p>" + esc(a) + " finished ahead: <b>" + r.a_wins + "</b> | " + esc(b) + ": <b>" + r.b_wins + "</b></p><p>Kills: " + r.a_kills + " vs " + r.b_kills + "</p>"; });
        });
      });
    },
    squad: function () {
      var id = new URLSearchParams(location.search).get("squad");
      function show(sid) { prpc("squad_profile", { p_squad: sid }).then(function (s) {
        if (!s || !s.ok) { body.innerHTML = empty("Squad not found."); return; }
        var r = s.record || {};
        body.innerHTML = '<div class="hub-c">' + (safe(s.logo_url) ? '<img src="' + esc(s.logo_url) + '" alt="" width="64" height="64" style="border-radius:12px">' : "") + "<h2>" + esc(s.name) + (s.tag ? " [" + esc(s.tag) + "]" : "") + "</h2>" + (s.bio ? "<p>" + esc(s.bio) + "</p>" : "") +
          "<p>Record: " + (r.matches ? r.matches + " matches · " + r.kills + " kills · " + r.points + " points" : "no matches played yet") + "</p><h3>Members</h3>" + (s.members || []).map(function (m) { return "<div>" + esc(m.ign) + " <small>(" + esc(m.role) + ")</small></div>"; }).join("") +
          '<p><button class="hub-b" id="sq-sh">Share this squad</button></p></div>';
        document.getElementById("sq-sh").addEventListener("click", function () { var u = location.origin + location.pathname + "?squad=" + sid + "#squad", t = s.name + " on XARENA"; if (navigator.share) navigator.share({ title: t, url: u }).catch(function () {}); else if (navigator.clipboard) navigator.clipboard.writeText(u).then(function () { alert("Link copied"); }); });
      }); }
      if (id) { show(id); return; }
      get("squads_public?select=id,name&order=name.asc&limit=200").then(function (sq) {
        body.innerHTML = !sq || !sq.length ? empty("No squads yet.") : sq.map(function (s) { return '<div class="hub-c"><a href="more.html?squad=' + esc(s.id) + '#squad">' + esc(s.name) + "</a></div>"; }).join("");
      });
    },
    creators: function () {
      prpc("creator_board", {}).then(function (r) {
        body.innerHTML = !Array.isArray(r) || !r.length ? empty("No verified creators yet. Creators can apply from the Creator page. Ranking = players who joined with the creator's invite code. No rewards.") +
          '<p><a href="creator.html">Become a creator</a></p>' : '<div class="hub-c"><table class="hub-t"><tr><th>#</th><th>Creator</th><th>Tier</th><th>Joined via invite</th></tr>' + r.map(function (x, i) { return "<tr><td>" + (i + 1) + "</td><td>" + esc(x.name) + (x.verified ? " ✓" : "") + "</td><td>" + esc(x.tier) + "</td><td><b>" + x.joined + "</b></td></tr>"; }).join("") + "</table></div>";
      });
    },
    guide: function () {
      var steps = ["Create your account and log in.", "Add your Free Fire IGN in your profile. Never share your UID, phone or password with anyone.", "Verify your phone through the Instagram / in-app code (manual, by the owner).", "Open Tournaments, pick the Ignition Cup and tap Register your squad. Entry is free.", "Make sure all 4 squad members use their real IGN.", "On match day, Room ID and password unlock 15 minutes before start, only for registered players. Never share them."];
      var chk = ["Free Fire is updated to the latest version", "Stable internet (Wi-Fi or good 4G)", "Phone charged above 50% or on charger", "Squad members are all online", "Check-in done on the match page", "Screenshot ready for result proof (end of match screen)", "No hacks or third-party tools. Fair play only"];
      body.innerHTML = '<div class="hub-c"><h3>Beginner guide (Hinglish)</h3><p>Pehli baar aaye ho? Ye 6 steps follow karo:</p><ol>' + steps.map(function (x) { return "<li>" + esc(x) + "</li>"; }).join("") + '</ol><p>Koi sawal ho to FAQ tab dekho ya Feedback me likho. Entry free hai, koi paisa nahi lagta.</p></div><div class="hub-c"><h3>Pre-match checklist</h3>' + chk.map(function (x, i) { return '<label style="display:block;padding:4px 0"><input type="checkbox" data-c="' + i + '" style="width:auto"> ' + esc(x) + "</label>"; }).join("") + "</div>";
      body.querySelectorAll("[data-c]").forEach(function (c) { try { c.checked = localStorage.getItem("xr_chk" + c.dataset.c) === "1"; } catch (e) {} c.addEventListener("change", function () { try { localStorage.setItem("xr_chk" + c.dataset.c, c.checked ? "1" : "0"); } catch (e) {} }); });
    },
    faq: function () {
      get("faq?select=question,answer&order=id.asc").then(function (r) {
        body.innerHTML = Array.isArray(r) && r.length ? r.map(function (f) { return '<details class="hub-c"><summary><b>' + esc(f.question) + "</b></summary><p>" + esc(f.answer) + "</p></details>"; }).join("") + '<p><a href="rules.html">Full rules and fair play</a></p>' : empty("No FAQ yet.");
      });
    },
    feedback: function () {
      body.innerHTML = '<div class="hub-c"><p>Send feedback, a bug or an idea. Log in first. Do not write phone numbers or other private info. Only admins read it.</p><select id="f-k"><option value="feedback">Feedback</option><option value="bug">Bug</option><option value="idea">Idea</option></select><textarea id="f-b" rows="5" maxlength="600"></textarea><button class="hub-b" id="f-s">Send</button> <span id="f-m"></span></div>';
      document.getElementById("f-s").addEventListener("click", function () {
        var m = document.getElementById("f-m"); if (!window.xrApi) { m.textContent = "Log in first."; return; }
        window.xrApi.uRpc("feedback_submit", { p_kind: document.getElementById("f-k").value, p_body: document.getElementById("f-b").value }).then(function (r) { m.textContent = r && r.ok ? "Thanks, sent." : (r && r.error) || "Log in first."; document.getElementById("f-b").value = r && r.ok ? "" : document.getElementById("f-b").value; }).catch(function () { m.textContent = "Log in first."; });
      });
    }
  };
  function go(k) { if (!V[k]) k = "points"; tabs.innerHTML = T.map(function (t) { return '<button data-k="' + t[0] + '"' + (t[0] === k ? ' class="on"' : "") + ">" + t[1] + "</button>"; }).join(""); tabs.querySelectorAll("button").forEach(function (b) { b.addEventListener("click", function () { history.replaceState(null, "", "#" + b.dataset.k); go(b.dataset.k); }); }); body.innerHTML = "Loading…"; V[k](); }
  go(location.hash.replace("#", "") || "points");
})();
