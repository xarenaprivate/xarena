/* XARENA "For You" — personal picks from the recommend edge function.
   Server-side AI, cached 6h per user. Honest label: AI-ranked vs rule-based. */
(function () {
  "use strict";
  var FN = "https://oskccuajgbyabogbvedj.supabase.co/functions/v1/recommend";
  function el(s, r) { return (r || document).querySelector(s); }
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }

  function card(link, title, sub, reason) {
    return '<a class="news-item fy-card" href="' + link + '" style="display:block;text-decoration:none;border-left-color:var(--gold)">' +
      '<h3 style="font-size:15px">' + esc(title) + '</h3>' +
      (sub ? '<p style="color:var(--muted)">' + esc(sub) + "</p>" : "") +
      '<p style="color:var(--gold2);font-size:12.5px;margin-top:4px">▸ ' + esc(reason) + "</p></a>";
  }

  function render(sec, d, names) {
    var fy = d.for_you || {};
    var html = "";
    (fy.tournaments || []).forEach(function (t) {
      var n = names.tournaments[t.id] || {};
      html += card("tournaments.html", n.name || "Tournament", [n.date_text, n.prize].filter(Boolean).join(" · "), t.reason);
    });
    (fy.tasks || []).forEach(function (t) {
      var n = names.tasks[t.id] || {};
      html += card("tasks.html", n.title || "Task", n.points ? n.points + " pts" : "", t.reason);
    });
    (fy.squads || []).forEach(function (s) {
      var n = names.squads[s.id] || {};
      html += card("squads.html", (n.name || "Squad") + (n.tag ? " [" + n.tag + "]" : ""), "", s.reason);
    });
    (fy.players || []).forEach(function (p) {
      html += card("players.html", p.ign, "", p.reason);
    });
    (fy.scrims || []).forEach(function (s) {
      html += card("scrims.html", s.title, [s.day_text, s.time_text].filter(Boolean).join(" · "), "Open scrim slot");
    });
    var grid = el(".fy-grid", sec);
    if (!html) {
      grid.innerHTML = '<div class="empty"><div class="big">Nothing to pick yet</div><p>Play, finish tasks and join events — your picks get sharper with activity.</p></div>';
    } else {
      grid.innerHTML = html;
    }
    el(".fy-note", sec).textContent = d.ai
      ? "Picked for you by XARENA AI from your real activity."
      : "Picked for you from live XARENA data (AI ranking temporarily unavailable).";
    sec.style.display = "";
  }

  function boot() {
    var sec = el("#foryou-sec");
    if (!sec || !window.xrApi) return;
    window.xrApi.user().then(function (u) {
      if (!u) {
        el(".fy-grid", sec).innerHTML = '<div class="empty"><div class="big">Your personal arena</div><p>Log in and XARENA AI picks tournaments, tasks, squads and teammates for you.</p><a class="btn btn-red btn-sm" href="account.html">Log in / Sign up</a></div>';
        el(".fy-note", sec).textContent = "";
        sec.style.display = "";
        return;
      }
      return window.xrApi.userToken().then(function (t) {
        if (!t) return;
        return fetch(FN, { method: "POST", headers: { "Authorization": "Bearer " + t, "Content-Type": "application/json" }, body: "{}" })
          .then(function (r) { return r.json(); })
          .then(function (d) {
            if (!d || !d.ok) return;
            var fy = d.for_you || {};
            var tIds = (fy.tournaments || []).map(function (x) { return x.id; });
            var kIds = (fy.tasks || []).map(function (x) { return x.id; });
            var sIds = (fy.squads || []).map(function (x) { return x.id; });
            var jobs = [];
            if (tIds.length) jobs.push(window.xrApi.get("tournaments", "?select=id,name,date_text,prize&id=in.(" + tIds.join(",") + ")").then(function (r) { return ["tournaments", r]; }));
            if (kIds.length) jobs.push(window.xrApi.get("tasks", "?select=id,title,points&id=in.(" + kIds.join(",") + ")").then(function (r) { return ["tasks", r]; }));
            if (sIds.length) jobs.push(window.xrApi.get("squads", "?select=id,name,tag&id=in.(" + sIds.join(",") + ")").then(function (r) { return ["squads", r]; }));
            return Promise.all(jobs).then(function (pairs) {
              var names = { tournaments: {}, tasks: {}, squads: {} };
              pairs.forEach(function (p) { (p[1] || []).forEach(function (r) { names[p[0]][r.id] = r; }); });
              render(sec, d, names);
            });
          }).catch(function () { });
      });
    }).catch(function () { });
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
})();
