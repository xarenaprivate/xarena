/* Selected tournament IDs are opaque server strings. Do not normalize their case or spaces. */
function xrValidTournamentId(id) { return typeof id === "string" && id.length > 0 && id.length <= 200 && !/[\u0000-\u001f\u007f]/.test(id); }
function xrReadableSchedule(value) { var text = String(value || ""); return /^\d{1,2}(?:st|nd|rd|th)?\s*,\s*[A-Za-z]+\s*,\s*\d{4}$/.test(text) ? text.replace(/(\d)(?:st|nd|rd|th)\b/, "$1").replace(/\s*,\s*/g, " ").trim() : text; }
function xrDetailsUrl(id) { return "tournaments.html?tournament=" + encodeURIComponent(id) + "#event-details"; }
function xrNormalizePhone(value) { var n = String(value || "").replace(/[\s()+-]/g, ""); if (/^91[0-9]{10}$/.test(n)) n = n.slice(2); if (/^0[0-9]{10}$/.test(n)) n = n.slice(1); return n; }
function xrRegistrationUrl(id) { return "tournaments.html?tournament=" + encodeURIComponent(id) + "#register"; }
window.__xrRegT = null;
function xrRegT() { return window.__xrRegT; }
window.xrRegReady = (function () {
  var selected = new URLSearchParams(location.search).get("tournament");
  if (selected !== null) {
    if (xrValidTournamentId(selected)) window.__xrRegT = selected;
    return Promise.resolve(window.__xrRegT);
  }
  return fetch("https://oskccuajgbyabogbvedj.supabase.co/rest/v1/site_settings?key=eq.reg_tournament&select=value", { headers: { apikey: "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7" } })
    .then(function (r) { if (!r.ok) throw new Error("Could not load tournament selection."); return r.json(); })
    .then(function (r) { var id = r && r[0] && r[0].value && r[0].value.id; if (xrValidTournamentId(id)) window.__xrRegT = id; return window.__xrRegT; })
    .catch(function () { return null; });
})();
/* Load one exact event before any registration action or cost routing. Read-only. */
window.xrRegQuote = window.xrRegReady.then(function (id) {
  if (!id) throw new Error("Choose a tournament from its Register button.");
  return fetch("https://oskccuajgbyabogbvedj.supabase.co/rest/v1/tournaments?select=id,name,entry_cost_ap,reg_open,status,slots,date_text,prize,mode,format,description&id=eq." + encodeURIComponent(id), { headers: { apikey: "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7" } })
    .then(function (r) { if (!r.ok) throw new Error("Could not check tournament details."); return r.json(); })
    .then(function (rows) {
      var event = rows && rows[0];
      if (!event || event.id !== id) throw new Error("Selected tournament was not found. Choose it again from Tournaments.");
      var cost = event.entry_cost_ap == null ? 0 : Number(event.entry_cost_ap);
      if (!Number.isInteger(cost) || cost < 0) throw new Error("Could not confirm the entry cost.");
      return Object.freeze({id:id,name:event.name || id,cost:cost,open:event.reg_open === true && (event.status === "upcoming" || event.status === "live"),slots:event.slots,dateText:event.date_text,prize:event.prize,mode:event.mode,format:event.format,description:event.description});
    });
});
/* Attach a rejection handler immediately; the form shows the same error once mounted. */
window.xrRegQuote.catch(function () {});
/* Hybrid OS embed mode: pages opened inside Hybrid OS windows hide the website chrome. */
(function () {
  try {
    if (window.self !== window.top && /[?&]os=1/.test(location.search)) {
      window.XR_OS_EMBED = true;
      var mark = function () { document.body.classList.add("os-embed"); };
      if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", mark);
      else mark();
    }
  } catch (e) {}
})();

/* XARENA ESPORTS — renderers + interactions.
   Renders instantly from data.js, re-renders with live backend data
   when api.js connects. Forms submit to the server when it is live,
   with an honest on-device fallback. */
(function () {
  "use strict";
  function D() { return window.XARENA; }
  function el(sel, root) { return (root || document).querySelector(sel); }
  function els(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }

  /* ---------- nav ---------- */
  var burger = el(".burger");
  if (burger) {
    burger.addEventListener("click", function () { el(".nav-links").classList.toggle("open"); });
    els(".nav-links a").forEach(function (a) {
      a.addEventListener("click", function () { el(".nav-links").classList.remove("open"); });
    });
  }

  /* ---------- reveal on scroll ---------- */
  function observeReveals() {
    if ("IntersectionObserver" in window) {
      var io = new IntersectionObserver(function (entries) {
        entries.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } });
      }, { threshold: 0.12 });
      els(".reveal:not(.in)").forEach(function (n) { io.observe(n); });
    } else {
      els(".reveal").forEach(function (n) { n.classList.add("in"); });
    }
  }

  /* ---------- toast ---------- */
  var toastTimer;
  window.xrToast = function (msg) {
    var t = el(".toast");
    if (!t) { t = document.createElement("div"); t.className = "toast"; document.body.appendChild(t); }
    t.textContent = msg;
    t.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.classList.remove("show"); }, 3200);
  };

  /* ---------- tournaments ---------- */
  function statusBadge(t) {
    if (t.status === "live") return '<span class="badge live"><i></i>LIVE NOW</span>';
    if (t.status === "done") return '<span class="badge done">COMPLETED</span>';
    return '<span class="badge up"><i></i>UPCOMING</span>';
  }
  function tCard(t) {
    var regBtn = t.regOpen
      ? '<a class="btn btn-red btn-sm" href="' + esc(xrRegistrationUrl(t.id)) + '">Register Squad</a>'
      : '<span class="badge info">REGISTRATION CLOSED</span>';
    return '<article class="card tcard reveal">' +
      '<div class="tcard-top"><h3>' + esc(t.name) + '</h3>' + statusBadge(t) + '</div>' +
      '<p>' + esc(t.description) + '</p>' +
      '<div class="tmeta">' +
      '<div><b>' + esc(t.mode) + '</b><span>Mode</span></div>' +
      '<div><b>' + esc(xrReadableSchedule(t.dateText)) + '</b><span>Schedule</span></div>' +
      '<div><b>' + esc(t.prize && !/^x+\s*$/i.test(t.prize) ? t.prize : 'Not confirmed') + '</b><span>Prize Pool</span></div>' +
      '<div><b>' + t.slots + ' squads</b><span>Slots</span></div>' +
      '</div>' +
      '<div class="tcard-foot">' + regBtn + '<a class="btn btn-ghost btn-sm" href="' + esc(xrDetailsUrl(t.id)) + '">Details</a></div>' +
      '</article>';
  }

  /* ---------- news ---------- */
  function newsItem(n) {
    var dt = new Date(n.date + "T00:00:00");
    var ds = isNaN(dt) ? n.date : dt.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
    return '<div class="news-item reveal"><span class="ntag">' + esc(n.tag) + '</span> · <span class="nd">' + esc(ds) + '</span><h3>' + esc(n.title) + '</h3><p>' + esc(n.body) + '</p></div>';
  }

  /* ---------- leaderboard ---------- */
  function renderLeaderboard() {
    var lbMount = el("#leaderboard");
    if (!lbMount) return;
    var tabs = [["points", "Points"], ["kills", "Kills"], ["wins", "Wins"]];
    var html = '<div class="lb-tabs">' + tabs.map(function (t, i) {
      return '<button class="lb-tab' + (i === 0 ? " active" : "") + '" data-tab="' + t[0] + '">' + t[1] + '</button>';
    }).join("") + "</div>";
    if (!D().leaderboard.rows.length) {
      /* task score board: real points from approved solo/team tasks */
      function loadTaskBoard() {
        fetch("https://oskccuajgbyabogbvedj.supabase.co/rest/v1/v_task_scores?select=*&limit=50", { headers: { apikey: "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7" } })
          .then(function (r) { return r.json(); })
          .then(function (rows) {
            if (!rows || !rows.length) return;
            var div = document.createElement("div");
            div.innerHTML = '<h3 style="margin:26px 0 12px;font-family:var(--font-d);letter-spacing:2px">TASK <em style="color:var(--gold2)">SCORE</em></h3>' +
              '<table class="lb-table"><thead><tr><th>Rank</th><th>Player</th><th class="lb-hide-s">Tasks done</th><th>Task pts</th></tr></thead><tbody>' +
              rows.map(function (r, i) {
                var rc = i === 0 ? " r1" : i === 1 ? " r2" : i === 2 ? " r3" : "";
                var name = r.ign || r.username;
                var linked = r.username ? '<a href="player.html?u=' + encodeURIComponent(r.username) + '" style="color:inherit">' + esc(name) + "</a>" : esc(name);
                return '<tr><td class="lb-rank' + rc + '">' + String(i + 1).padStart(2, "0") + "</td><td>" + linked + '</td><td class="lb-hide-s">' + r.tasks_done + '</td><td class="lb-score">' + (r.points || 0).toLocaleString("en-IN") + "</td></tr>";
              }).join("") + "</tbody></table>" +
              '<p style="color:var(--muted);font-size:12.5px;margin-top:8px">Points from owner-approved solo and team tasks. Open the Tasks page to earn yours.</p>';
            lbMount.appendChild(div);
          })
          .catch(function () {});
      }
      /* live points board from real awards; honest empty state until then */
      fetch("https://oskccuajgbyabogbvedj.supabase.co/rest/v1/leaderboard_points?select=*", { headers: { apikey: "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7" } })
        .then(function (r) { return r.json(); })
        .then(function (rows) {
          if (rows && rows.length) {
            html += '<table class="lb-table"><thead><tr><th>Rank</th><th>Squad</th><th>Points</th><th class="lb-hide-s">Honors</th></tr></thead><tbody>' +
              rows.map(function (r, i) {
                var rc = i === 0 ? " r1" : i === 1 ? " r2" : i === 2 ? " r3" : "";
                var honors = (r.champion ? "🏆 " : "") + (r.mvp ? "⭐ " : "") + (r.titles || "");
                return '<tr><td class="lb-rank' + rc + '">' + String(i + 1).padStart(2, "0") + "</td><td>" + esc(r.squad) + '</td><td class="lb-score">' + (r.points || 0).toLocaleString("en-IN") + '</td><td class="lb-hide-s" style="font-size:12.5px">' + esc(honors) + "</td></tr>";
              }).join("") + "</tbody></table>" +
              '<p style="color:var(--muted);font-size:12.5px;margin-top:8px">Points from real match results, granted by the owner. 🏆 Champion · ⭐ MVP</p>';
          } else {
            html += '<div class="empty"><div class="big">Rankings unlock with Season 1</div>' +
              "<p>The leaderboard goes live the moment the first official XARENA match ends. Kills, points and wins are tracked for every registered squad — no manual entries, straight from match results.</p>" +
              '<a class="btn btn-red btn-sm" href="tournaments.html#register">Register for Season 1</a></div>';
          }
          lbMount.innerHTML = html;
          loadTaskBoard();
        })
        .catch(function () {
          lbMount.innerHTML = html + '<div class="empty"><div class="big">Rankings unlock with Season 1</div><p>Could not reach the server — the board appears here once Season 1 results land.</p></div>';
          loadTaskBoard();
        });
      return;
    }
    html += '<table class="lb-table"><thead><tr><th>Rank</th><th>Player</th><th class="lb-hide-s">Squad</th><th id="lb-metric-h">Points</th><th class="lb-hide-s">Wins</th></tr></thead><tbody id="lb-body"></tbody></table>';
    lbMount.innerHTML = html;
    function renderRows(metric) {
      var rows = D().leaderboard.rows.slice().sort(function (a, b) { return (b[metric] || 0) - (a[metric] || 0); });
      el("#lb-metric-h").textContent = metric.charAt(0).toUpperCase() + metric.slice(1);
      el("#lb-body").innerHTML = rows.map(function (r, i) {
        var rc = i === 0 ? " r1" : i === 1 ? " r2" : i === 2 ? " r3" : "";
        return '<tr><td class="lb-rank' + rc + '">' + String(i + 1).padStart(2, "0") + '</td><td>' + esc(r.name) + '</td><td class="lb-hide-s">' + esc(r.squad || "-") + '</td><td class="lb-score">' + (r[metric] || 0).toLocaleString("en-IN") + '</td><td class="lb-hide-s">' + (r.wins || 0) + "</td></tr>";
      }).join("");
    }
    els(".lb-tab", lbMount).forEach(function (b) {
      b.addEventListener("click", function () {
        els(".lb-tab", lbMount).forEach(function (x) { x.classList.remove("active"); });
        b.classList.add("active");
        renderRows(b.dataset.tab);
      });
    });
    renderRows("points");
  }

  /* ---------- teams / players ---------- */
  function renderTeams() {
    var m = el("#teams-mount");
    if (!m) return;
    if (!D().teams.length) {
      m.innerHTML = '<div class="empty"><div class="big">Founding roster recruiting now</div>' +
        "<p>XARENA's official squad is being built right now. Selected players get a verified player profile on this page with roles, stats and achievements tracked across every XARENA event.</p>" +
        '<a class="btn btn-red btn-sm" href="#tryout">Apply for Tryout</a></div>';
      return;
    }
    m.innerHTML = '<div class="grid c3">' + D().teams.map(function (t) {
      return '<article class="card reveal"><h3>' + esc(t.name) + (t.tag ? ' <span style="color:var(--gold2)">[' + esc(t.tag) + "]</span>" : "") + "</h3><p>" + esc(t.bio || "") + "</p></article>";
    }).join("") + "</div>";
  }
  function renderPlayers() {
    var m = el("#players-mount");
    if (!m) return;
    if (!D().players.length) {
      m.innerHTML = '<div class="empty"><div class="big">Player profiles coming with the roster</div>' +
        "<p>Every signed XARENA player gets a public profile here — role, K/D, headshot rate, match history and titles. The first profiles go live when the founding roster is announced.</p>" +
        '<a class="btn btn-red btn-sm" href="teams.html#tryout">Join the Roster</a></div>';
      return;
    }
    m.innerHTML = '<div class="grid c3">' + D().players.map(function (p) {
      var ach = (p.achievements || []).map(function (a) { return '<span class="badge info" style="margin:2px 4px 2px 0">' + esc(a) + "</span>"; }).join("");
      return '<article class="card reveal"><div class="overline">' + esc(p.role || "PLAYER") + '</div><h3>' + esc(p.ign) + "</h3>" +
        "<p>" + esc(p.bio || "") + "</p>" +
        '<div class="tmeta" style="margin-top:12px">' +
        (p.kd != null ? '<div><b>' + esc(p.kd) + "</b><span>K/D</span></div>" : "") +
        (p.teams && p.teams.name ? '<div><b>' + esc(p.teams.name) + "</b><span>Squad</span></div>" : "") +
        "</div>" + (ach ? '<div style="margin-top:10px">' + ach + "</div>" : "") + "</article>";
    }).join("") + "</div>";
  }

  /* ---------- scrims ---------- */
  function renderScrims() {
    var m = el("#scrims-mount");
    if (!m) return;
    if (!D().scrims.length) {
      m.innerHTML = '<div class="empty"><div class="big">Scrim schedule drops after registrations close</div>' +
        "<p>Daily practice scrims for registered squads start once Season 1 registration fills. Registered captains get scrim slots first. The full schedule will be published on this page.</p>" +
        '<a class="btn btn-red btn-sm" href="tournaments.html#register">Register Your Squad</a></div>';
      return;
    }
    m.innerHTML = D().scrims.map(function (s) {
      return '<div class="scrim reveal"><div class="when"><b>' + esc(s.day) + '</b><span>' + esc(s.time) + '</span></div><div><h4>' + esc(s.title) + '</h4><p>' + esc(s.note || "") + "</p></div></div>";
    }).join("");
  }

  /* ---------- master render ---------- */
  window.XR = {
    render: function () {
      var feat = el("#featured-tournaments");
      if (feat) feat.innerHTML = D().tournaments.map(tCard).join("");
      var tlist = el("#tournament-list");
      if (tlist) tlist.innerHTML = D().tournaments.map(tCard).join("");
      if (window.xrApi && window.xrApi.publicState === "loading") [feat, tlist].forEach(function (list) { if (list) list.innerHTML = '<div class="xr-skeleton" aria-hidden="true"></div><div class="xr-skeleton" aria-hidden="true"></div><div class="xr-skeleton" aria-hidden="true"></div><span class="xr-sr-only">Loading tournaments...</span>'; });
      var newsHome = el("#news-home");
      if (newsHome) newsHome.innerHTML = D().news.slice(0, 3).map(newsItem).join("");
      var newsAll = el("#news-all");
      if (newsAll) newsAll.innerHTML = D().news.map(newsItem).join("");
      renderLeaderboard();
      renderTeams();
      renderPlayers();
      renderScrims();
      observeReveals();
    }
  };
  window.XR.render();

  /* ---------- forms ---------- */
  function saveLocal(key, rec) {
    try {
      var arr = JSON.parse(localStorage.getItem(key) || "[]");
      arr.push(rec);
      localStorage.setItem(key, JSON.stringify(arr));
      return true;
    } catch (e) { return false; }
  }
  function genId(prefix) { return prefix + "-" + Math.floor(1000 + Math.random() * 9000); }

  function bindForm(formId, opts) {
    var form = el("#" + formId);
    if (!form) return;
    var pKey = "xr_form_" + formId;
    try {
      var saved = JSON.parse(sessionStorage.getItem(pKey) || "null");
      if (saved) {
        els("[data-req],[data-opt]", form).forEach(function (inp) {
          if (inp.type !== "file" && saved[inp.name] != null && !inp.value) inp.value = saved[inp.name];
        });
      }
      form.addEventListener("input", function () {
        var data = {};
        els("[data-req],[data-opt]", form).forEach(function (inp) {
          if (inp.type !== "file") data[inp.name] = inp.value || "";
        });
        try { sessionStorage.setItem(pKey, JSON.stringify(data)); } catch (e) {}
      });
    } catch (e) {}
    form.addEventListener("submit", function (ev) {
      ev.preventDefault();
      var ok = true;
      els("[data-req]", form).forEach(function (inp) {
        var f = inp.closest(".field");
        if (opts.type === "tournament_registration" && inp.name === "phone") inp.value = xrNormalizePhone(inp.value);
        var val = (inp.value || "").trim();
        var valid = !!val;
        if (valid && inp.dataset.pattern) valid = new RegExp(inp.dataset.pattern).test(val);
        if (f) f.classList.toggle("invalid", !valid);
        if (!valid) ok = false;
      });
      var agree = el("[data-agree]", form);
      if (agree && !agree.checked) { ok = false; xrToast("Please accept the fair-play rules first."); }
      if (!ok) { xrToast("Fix the highlighted fields and try again."); return; }

      var rec = { id: opts.type === "tournament_registration" ? "XR-" + crypto.randomUUID() : genId(opts.prefix), type: opts.type };
      els("[data-req],[data-opt]", form).forEach(function (inp) { rec[inp.name] = (inp.value || "").trim(); });

      var btn = el("button[type=submit]", form);
      if (btn) { btn.disabled = true; btn.textContent = "SUBMITTING..."; }
      function done(serverSide, res) {
        if (serverSide && opts.type === "tournament_registration") saveLocal(opts.store, Object.assign({ at: new Date().toISOString(), tournament_id: xrRegT() }, rec));
        try { sessionStorage.removeItem(pKey); } catch (e) {}
        if (btn) { btn.disabled = false; btn.textContent = opts.btn; }
        var eb = el(".server-err", form); if (eb) eb.style.display = "none";
        form.reset();
        var sc = el("#" + opts.successId);
        if (sc) {
          el(".sid", sc).textContent = rec.id;
          var hp = el(".hold-note", sc);
          if (hp) {
            if (res && res.waitlist) { hp.textContent = "You are on the waitlist. All 48 slots are taken. If a slot opens, the owner moves squads up in the order they joined."; hp.style.display = "block"; }
            else hp.style.display = (res && res.hold) ? "block" : "none";
          }
          sc.classList.add("show");
          sc.scrollIntoView({ behavior: "smooth", block: "center" });
        }
        showMyRegs();
        xrToast(serverSide ? opts.toast + " Saved on the XARENA server." : opts.toast);
      }
      function fail() {
        if (btn) { btn.disabled = false; btn.textContent = opts.btn; }
        xrToast("Could not submit — check your connection and try again.");
      }
      /* server first when backend is live; local copy always kept */
      var row = opts.row(rec);
      if (opts.type !== "tournament_registration") saveLocal(opts.store, Object.assign({ at: new Date().toISOString() }, rec));
      function failMsg(msg) {
        if (btn) { btn.disabled = false; btn.textContent = opts.btn; }
        var eb = el(".server-err", form);
        if (msg && eb) { eb.textContent = msg; eb.style.display = "block"; }
        xrToast(msg || "Could not submit — check your connection and try again.");
      }
      if (opts.rpcSubmit && window.xrApi && xrApi.connected) {
        opts.rpcSubmit(rec, row).then(function (res) {
          if (res && res.ok && res.status_token) {
            try {
              var tk = JSON.parse(localStorage.getItem("xarena_reg_tokens") || "{}");
              tk[rec.id] = res.status_token;
              localStorage.setItem("xarena_reg_tokens", JSON.stringify(tk));
            } catch (e) {}
          }
          if (res && res.ok) { done(true, res); }
          else { saveLocal("xarena_regs_failed", Object.assign({ at: new Date().toISOString() }, rec)); failMsg((res && res.error) || null); }
        }).catch(function (e) { failMsg(e && e.message); });
      } else if (opts.rpcSubmit) {
        failMsg("Registration needs a live connection. Nothing was registered. Try again online.");
      } else if (window.xrApi && xrApi.connected) {
        xrApi.submit(opts.table, row).then(function () { done(true); }).catch(function () { done(false); });
      } else {
        done(false);
      }
    });
    els("[data-req]", form).forEach(function (inp) {
      inp.addEventListener("input", function () {
        var f = inp.closest(".field");
        if (f) f.classList.remove("invalid");
      });
    });
  }

  function deviceId() {
    var d = localStorage.getItem("xr_device_id");
    if (!d) { d = "dev-" + Math.random().toString(36).slice(2) + Date.now().toString(36); localStorage.setItem("xr_device_id", d); }
    return d;
  }
  function uploadProof(regId, file) {
    if (!file) return Promise.reject(new Error("Attach your UID screenshot first."));
    if (!/^image\//.test(file.type || "")) return Promise.reject(new Error("Choose an image screenshot."));
    if (file.size > 8 * 1024 * 1024) return Promise.reject(new Error("Image too large (max 8 MB)."));
    var ext = (file.name.split(".").pop() || "jpg").toLowerCase().replace(/[^a-z0-9]/g, "") || "jpg";
    var path = regId + "." + ext;
    return fetch(xrApi.url + "/storage/v1/object/proofs/" + path, {
      method: "POST",
      headers: { "apikey": "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7", "Authorization": "Bearer sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7", "Content-Type": file.type || "image/jpeg" },
      body: file
    }).then(function (r) { if (!r.ok) throw new Error("proof upload failed"); return path; });
  }
  /* account-required notice on the squad form (server enforces it too) */
  window.addEventListener("load", function () {
    var rf = document.getElementById("reg-form");
    if (!rf || !window.xrApi || !xrApi.user) return;
    xrApi.user().then(function (u) {
      rf.dataset.authenticated = u ? "true" : "false";
      if (u) return;
      rf.querySelectorAll("input,button,select,textarea").forEach(function (control) { control.disabled = true; });
      var note = document.createElement("div");
      note.id = "xr-reg-login-notice";
      note.style.cssText = "background:rgba(255,60,60,.08);border:1px solid rgba(255,60,60,.35);border-radius:8px;padding:10px 14px;margin-bottom:14px;font-size:13px";
      note.innerHTML = "To register a squad, first <a href='account.html' style='color:var(--gold2)'>create a free account or log in</a> — it takes a minute. Then come right back here.";
      rf.parentNode.insertBefore(note, rf);
    });
  });


  /* One registration per account: show a "Registered" state instead of the register form/buttons */
  window.addEventListener("load", function () {
    if (!window.xrApi || !xrApi.uRpc) return;
    window.xrRegQuote.then(function (event) { return xrApi.uRpc("my_registrations", {}).then(function (r) { return {event:event,result:r}; }); }).then(function (loaded) {
      var event = loaded.event, r = loaded.result;
      if (!r || !r.ok) return;
      var mine = (r.registrations || []).filter(function (x) { return x.tournament_id === xrRegT(); })[0];
      if (!mine) return;
      var st = mine.verified ? "VERIFIED" : (mine.status === "confirmed" ? "Confirmed" : (mine.status || "pending"));
      var rf = document.getElementById("reg-form");
      if (rf) {
        var card = document.createElement("div");
        card.style.cssText = "background:rgba(60,200,120,.08);border:1px solid rgba(60,200,120,.4);border-radius:8px;padding:16px;margin:8px 0";
        var h = document.createElement("p"); h.style.cssText = "color:var(--gold2);font-weight:700;letter-spacing:2px;text-transform:uppercase;font-size:13px"; h.textContent = "Registered";
        var d = document.createElement("p"); d.textContent = "Your squad \u201C" + mine.squad + "\u201D is registered for " + event.name + ". Status: " + st + ". Registration ID: " + mine.id + ". One registration per account.";
        card.appendChild(h); card.appendChild(d);
        rf.style.display = "none"; rf.parentNode.insertBefore(card, rf);
      }
      var markBtns = function () { els('a[href]').filter(function (a) { try { return new URL(a.href, location.href).searchParams.get('tournament') === event.id; } catch (e) { return false; } }).forEach(function (a) {
        a.textContent = "Registered \u2713"; a.classList.add("disabled"); a.setAttribute("aria-disabled", "true");
      }); };
      markBtns(); setTimeout(markBtns, 1500); setTimeout(markBtns, 4000);
    }).catch(function () {});
  });

  /* An explicit card selection opens the form first, above async cards/room widgets.
     A hash-only jump before those widgets load can land on the old room section. */
  (function () {
    if (!new URLSearchParams(location.search).has("tournament")) return;
    var section = document.getElementById("register"), main = document.querySelector("main");
    if (!section || !main) return;
    main.insertBefore(section, main.firstChild);
    section.style.scrollMarginTop = "84px";
    var back = document.createElement("a"); back.href = "tournaments.html";
    back.textContent = "Back to tournaments"; back.className = "btn btn-ghost btn-sm";
    back.style.marginBottom = "18px";
    section.querySelector(".wrap").insertBefore(back,section.querySelector(".wrap").firstChild);
    Array.prototype.forEach.call(document.querySelectorAll('a[href="tournaments.html#register"]'),function(a){a.href=xrRegistrationUrl(xrRegT());});
    document.title = "Tournament registration | XARENA ESPORTS";
  })();

  /* Show selected event and exact AP quote. A failed quote never becomes a free entry. */
  window.addEventListener("load", function () {
    var rf = document.getElementById("reg-form");
    if (!rf) return;
    var btn = rf.querySelector('button[type="submit"]');
    btn.disabled = true;
    window.xrRegQuote.then(function (event) {
      var title = document.getElementById("xr-reg-title"), info = document.getElementById("xr-reg-info");
      if (title) title.textContent = event.name + " - Squad Registration";
      if (new URLSearchParams(location.search).has("tournament")) document.title = event.name + " - Registration | XARENA ESPORTS";
      if (info) info.textContent = event.open ? "Registration is open for this tournament. One registration per account per tournament." : "Registration is closed for this tournament.";
      var share = document.querySelector("#reg-success [data-xr-share]");
      if (share) { share.dataset.title = "My XARENA squad registration"; share.dataset.text = "My squad registration for " + event.name + ". Check the tournament page for current details:"; share.dataset.url = new URL(xrRegistrationUrl(event.id),location.href).href; }
      var n = document.createElement("div"); n.className = "xr-entry-quote";
      n.style.cssText = "padding:10px 14px;margin-bottom:14px;font-size:13px;border:1px solid #8885;border-radius:8px";
      n.textContent = event.cost > 0 ? "Entry cost: " + event.cost + " AP (Arena Points). It is deducted when you register, and refunded if the registration is rejected or cancelled." : "Free entry. No AP is charged.";
      rf.parentNode.insertBefore(n,rf);
      return xrApi.user().then(function (u) { btn.disabled = !event.open || !u; });
    }).catch(function (e) { var eb = rf.querySelector(".server-err");if(eb){eb.textContent=e.message;eb.style.display="block";}btn.disabled=true; });
  });

  bindForm("reg-form", {
    store: "xarena_regs", table: "registrations", prefix: "XR-S1", type: "tournament_registration",
    successId: "reg-success", toast: "Squad registered — save your Registration ID.", btn: "REGISTER SQUAD",
    row: function (r) { return { id: r.id, tournament_id: xrRegT(), squad: r.squad, captain: r.captain, uid: r.uid, phone: r.phone }; },
    rpcSubmit: function (rec) {
      var fileInput = el("#proof");
      var file = fileInput && fileInput.files && fileInput.files[0];
      return window.xrRegQuote.then(function (event) {
        if (!event.open) throw new Error("Registration is closed for this tournament.");
        return xrApi.user().then(function (u) { if (!u) throw new Error("Log in before registering your squad."); return uploadProof(rec.id, file); }).then(function (proofPath) {
        return xrApi.uRpc(event.cost > 0 ? "enter_tournament_with_ap" : "register_squad", {
          p_id: rec.id, p_tournament: event.id, p_squad: rec.squad, p_captain: rec.captain,
          p_uid: rec.uid, p_ign: rec.captain, p_phone: rec.phone, p_proof: proofPath,
          p_client: deviceId(), p_ip: null
        });
        });
      });
    }
  });
  /* Registration draft: survives an app restart/reload while the UID proof picker is open.
     Text fields + checkbox are saved on every input and restored on load; cleared only
     after a successful submit (the success card shows). The file itself cannot be
     restored (browser security) - on a full process kill only the file must be re-picked. */
  (function () {
    var form = document.getElementById("reg-form");
    if (!form) return;
    var KEY = "xarena_reg_draft";
    var fields = ["squad", "captain", "uid", "phone"];
    function save() {
      var d = {};
      fields.forEach(function (f) { var i = form.querySelector("#" + f); if (i) d[f] = i.value; });
      var c = form.querySelector("[data-agree]"); if (c) d.agree = c.checked;
      try { localStorage.setItem(KEY, JSON.stringify(d)); } catch (e) {}
    }
    function restore() {
      var d;
      try { d = JSON.parse(localStorage.getItem(KEY) || "null"); } catch (e) { return; }
      if (!d) return;
      fields.forEach(function (f) { var i = form.querySelector("#" + f); if (i && d[f]) i.value = d[f]; });
      var c = form.querySelector("[data-agree]"); if (c && d.agree) c.checked = true;
    }
    restore();
    form.addEventListener("input", save);
    form.addEventListener("change", save);
    form.addEventListener("submit", function () {
      setTimeout(function () {
        var sc = document.getElementById("reg-success");
        if (sc && sc.classList.contains("show")) { try { localStorage.removeItem(KEY); } catch (e) {} }
      }, 1200);
    });
  })();

  bindForm("tryout-form", {
    store: "xarena_tryouts", table: "tryouts", prefix: "XR-TRY", type: "roster_tryout",
    successId: "tryout-success", toast: "Tryout application received.", btn: "SUBMIT APPLICATION",
    row: function (r) { return { id: r.id, name: r.name, ign: r.ign, uid: r.uid, role: r.role, kd: r.kd || null, phone: r.phone }; }
  });
  bindForm("contact-form", {
    store: "xarena_msgs", table: "messages", prefix: "XR-MSG", type: "contact_message",
    successId: "contact-success", toast: "Message received.", btn: "SEND MESSAGE",
    row: function (r) { return { id: r.id, name: r.name, contact: r.contact, message: r.message }; }
  });


/* ---------- team logo helpers (shared by squads page + my registrations) ---------- */
window.xrCompressImage = function (file, maxPx, quality) {
  maxPx = maxPx || 512; quality = quality || 0.8;
  return new Promise(function (resolve, reject) {
    var img = new Image();
    var url = URL.createObjectURL(file);
    img.onload = function () {
      var w = img.naturalWidth, h = img.naturalHeight;
      var scale = Math.min(1, maxPx / Math.max(w, h));
      var cw = Math.max(1, Math.round(w * scale)), ch = Math.max(1, Math.round(h * scale));
      var cv = document.createElement("canvas"); cv.width = cw; cv.height = ch;
      cv.getContext("2d").drawImage(img, 0, 0, cw, ch);
      URL.revokeObjectURL(url);
      cv.toBlob(function (b) { b ? resolve(b) : reject(new Error("Could not process that image.")); }, "image/jpeg", quality);
    };
    img.onerror = function () { URL.revokeObjectURL(url); reject(new Error("Could not read that image.")); };
    img.src = url;
  });
};
window.xrUploadTeamLogo = function (file) {
  if (!window.xrApi) return Promise.reject(new Error("Backend not ready - try again in a moment."));
  if (!file || !/^image\//.test(file.type || "")) return Promise.reject(new Error("Pick an image file (PNG or JPG)."));
  if (file.size > 8 * 1024 * 1024) return Promise.reject(new Error("Image too large (max 8 MB)."));
  return window.xrCompressImage(file, 512, 0.8).then(function (blob) {
    return xrApi.userToken().then(function (tok) {
      if (!tok) throw new Error("Log in first, then add your team logo.");
      return fetch(xrApi.url + "/functions/v1/cloudinary-sign", {
        method: "POST", headers: { apikey: xrApi.key, Authorization: "Bearer " + tok, "Content-Type": "application/json" }, body: "{}"
      }).then(function (r) { return r.json(); }).then(function (sig) {
        if (!sig || !sig.cloud) throw new Error("Upload unavailable right now - try again shortly.");
        var fd = new FormData();
        fd.append("file", blob, "logo.jpg"); fd.append("api_key", sig.key); fd.append("timestamp", sig.timestamp);
        fd.append("folder", sig.folder); fd.append("signature", sig.signature);
        return fetch("https://api.cloudinary.com/v1_1/" + sig.cloud + "/image/upload", { method: "POST", body: fd })
          .then(function (r) { return r.json(); })
          .then(function (up) { if (!up || !up.secure_url) throw new Error("Upload failed - try again."); return up.secure_url; });
      });
    });
  });
};

  function showMyRegs() {
    var myRegs = el("#my-regs");
    if (!myRegs) return;
    try {
      var regs = JSON.parse(localStorage.getItem("xarena_regs") || "[]");
      if (regs.length) {
        myRegs.innerHTML = '<h3 style="font-family:var(--font-d);letter-spacing:2px;font-size:16px;margin-bottom:12px">YOUR REGISTRATIONS ON THIS DEVICE</h3>' +
          regs.map(function (r) {
            return '<div class="news-item"><span class="ntag">' + esc(r.id) + '</span><h3>' + esc(r.squad || "Squad") + '</h3><p>Captain: ' + esc(r.captain || "-") + " · FF UID: " + esc(r.uid || "-") + '</p>' +
              '<div style="margin-top:8px;display:flex;gap:8px;align-items:center;flex-wrap:wrap"><label class="btn btn-ghost btn-sm" style="cursor:pointer">Add / change team logo<input type="file" accept="image/*" data-logo-reg="' + esc(r.id) + '" style="display:none"></label><span class="logo-msg" style="font-size:12px;color:var(--muted)"></span></div></div>';
          }).join("");
          Array.prototype.forEach.call(myRegs.querySelectorAll("input[data-logo-reg]"), function (inp) {
            inp.addEventListener("change", function () {
              var f = inp.files && inp.files[0];
              if (!f) return;
              var card = inp.closest(".news-item");
              var lmsg = card ? card.querySelector(".logo-msg") : null;
              if (lmsg) lmsg.textContent = "Uploading…";
              xrUploadTeamLogo(f).then(function (url) {
                return xrApi.uRpc("set_reg_logo", { p_reg: inp.getAttribute("data-logo-reg"), p_logo: url });
              }).then(function () {
                if (lmsg) lmsg.textContent = "Logo saved - it now shows next to your squad in the list below.";
                loadSquads();
              }).catch(function (e) {
                if (lmsg) lmsg.textContent = (e && e.message && e.message !== "Failed to fetch") ? e.message : "Could not save - log in with the account that registered this squad, then try again.";
              });
            });
          });
      }
    } catch (e) { }
  }
  showMyRegs();

  /* public registered squads list (view exposes only safe columns) */
  function loadSquads() {
    var sl = el("#squad-list");
    if (!sl || !window.xrApi) return;
    xrApi.get("public_squads", "?select=squad,ign,verified,created_at,logo_url&order=created_at.asc").then(function (rows) {
      if (!rows || !rows.length) {
        sl.innerHTML = '<p style="color:var(--muted)">No squads registered yet — the first slot could be yours.</p>';
        return;
      }
      sl.innerHTML = rows.map(function (r, i) {
        var badge = r.verified
          ? '<span style="color:var(--gold2);border:1px solid var(--gold2);padding:2px 8px;font-size:11px;letter-spacing:1px;margin-left:8px;vertical-align:middle">✓ VERIFIED</span>'
          : '<span style="color:var(--muted);font-size:11px;margin-left:8px;vertical-align:middle">verification pending</span>';
        var logo = r.logo_url ? '<img src="' + esc(r.logo_url) + '" alt="" loading="lazy" style="width:40px;height:40px;border-radius:8px;object-fit:cover;margin-right:10px;flex:none;border:1px solid rgba(0,0,0,.08)">' : "";
        return '<div class="news-item"><span class="ntag">SLOT ' + (i + 1) + '</span><h3 style="display:flex;align-items:center;flex-wrap:wrap">' + logo + '<span>' + esc(r.squad) + badge + '</span></h3><p>Captain IGN: ' + esc(r.ign || "-") + '</p>' +
          '<div style="margin-top:6px"><button class="btn btn-ghost btn-sm" data-roast="' + esc(r.squad) + '">🔥 Roast this squad</button> <span class="roast-out" style="font-size:13px"></span></div></div>';
      }).join("");
      Array.prototype.forEach.call(sl.querySelectorAll("[data-roast]"), function (b) {
        var out = b.parentNode.querySelector(".roast-out");
        if (window.xrFunline) xrFunline.wire(b, "roast", b.getAttribute("data-roast"), out, {});
      });
    }).catch(function () {
      sl.innerHTML = '<p style="color:var(--muted)">Could not load the squad list — try refreshing.</p>';
    });
  }

  /* when backend connects, upgrade the honesty notes */
  document.addEventListener("xr-backend-live", function () {
    els(".form-note").forEach(function (n) {
      n.innerHTML = "<b>Live:</b> connected to the XARENA server — your submission goes straight to the management panel, and a copy stays on this device.";
    });
    loadSquads();
  });

  var yr = el("#yr");
  if (yr) yr.textContent = new Date().getFullYear();
})();

/* XARENA Android app integration + hidden owner entry */
(function () {
  var upd = document.getElementById("app-update");
  if (upd && window.XarenaApp) {
    upd.style.display = "";
    upd.textContent = "App update check · v" + (window.XarenaApp.getVersionName ? window.XarenaApp.getVersionName() : "1.0");
    upd.addEventListener("click", function () { window.XarenaApp.checkUpdate(); });
  }
})();

/* PWA install prompt (Chrome/Edge on Android + Windows). Not shown in the XARENA app or when already installed. */
(function () {
  var deferred = null;
  window.addEventListener("beforeinstallprompt", function (e) {
    if (window.XarenaApp) return;
    if (window.XR_OS_EMBED) return;
    if (window.matchMedia && window.matchMedia("(display-mode: standalone)").matches) return;
    e.preventDefault();
    deferred = e;
    var pill = document.createElement("button");
    pill.id = "xr-install-pill";
    pill.textContent = "📲 Install XARENA app";
    pill.style.cssText = "position:fixed;left:14px;bottom:14px;z-index:899;background:linear-gradient(135deg,#a020f0,#ff1e3c);color:#fff;border:none;border-radius:999px;padding:11px 18px;font-size:13.5px;font-weight:700;cursor:pointer;box-shadow:0 6px 20px rgba(160,32,240,.45)";
    pill.addEventListener("click", function () {
      if (!deferred) return;
      deferred.prompt();
      deferred.userChoice.finally(function () { pill.remove(); deferred = null; });
    });
    document.body.appendChild(pill);
    setTimeout(function () { if (pill.parentNode) pill.remove(); }, 30000);
  });
})();

/* XARENA boot splash hide + branded busy indicator for real async loads. */
(function () {
  var sp = document.getElementById("xr-splash");
  var reduced = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (sp) {
    var t0 = Date.now(), inFrame = (window.self !== window.top);
    function hide() {
      if (!sp.parentNode) return;
      sp.classList.add("xs-done");
      setTimeout(function () { if (sp.parentNode) sp.parentNode.removeChild(sp); }, reduced ? 0 : 320);
    }
    function ready() {
      var min = inFrame ? 0 : 120;
      setTimeout(hide, reduced ? 0 : Math.max(0, min - (Date.now() - t0)));
    }
    if (document.readyState !== "loading") ready(); else document.addEventListener("DOMContentLoaded", ready, { once: true });
    setTimeout(hide, 1000);
  }
  /* busy overlay: shows only while real backend fetches are in flight */
  if (!window.fetch) return;
  var orig = window.fetch, n = 0, showTimer = null, shownAt = 0, el = null;
  function build() {
    var st = document.getElementById("xr-busy-style") || document.createElement("style");
    st.id = "xr-busy-style";
    st.textContent =
      "#xr-busy{position:fixed;inset:0;z-index:99998;display:flex;align-items:center;justify-content:center;pointer-events:none;opacity:0;transition:opacity .18s}" +
      "#xr-busy.on{opacity:1}" +
      "#xr-busy .xb-card{background:rgba(7,7,15,.82);border:1px solid rgba(255,255,255,.14);border-radius:16px;padding:16px 22px;display:flex;flex-direction:column;align-items:center;gap:10px;backdrop-filter:blur(6px)}" +
      "#xr-busy svg{width:34px;height:34px;animation:xsPulse 1.1s ease-in-out infinite}" +
      "#xr-busy svg path{stroke:var(--xs-acc,#e10600)}#xr-busy svg .xs-x{stroke:#f5b301}" +
      "#xr-busy .xb-txt{font:700 8.5px Orbitron,system-ui,sans-serif;letter-spacing:3px;color:var(--xs-acc,#e10600)}" +
      "#xr-busy .xb-bar{width:110px;height:3px;border-radius:3px;background:rgba(255,255,255,.12);overflow:hidden}" +
      "#xr-busy .xb-bar i{display:block;height:100%;width:40%;border-radius:3px;background:var(--xs-acc,#e10600);box-shadow:0 0 10px var(--xs-acc,#e10600);animation:xbSlide .9s ease-in-out infinite}" +
      "@keyframes xbSlide{0%{transform:translateX(-110%)}100%{transform:translateX(280%)}}" +
      "@media (prefers-reduced-motion:reduce){#xr-busy svg,#xr-busy .xb-bar i{animation:none!important}}";
    document.head.appendChild(st);
    el = document.createElement("div");
    el.id = "xr-busy"; el.setAttribute("aria-hidden", "true");
    el.innerHTML = '<div class="xb-card"><svg viewBox="0 0 40 40"><path d="M20 2 36 10v14c0 8-7 12.7-16 14C11 36.7 4 32 4 24V10Z" fill="#0b0b14" stroke-width="2.5"/><path class="xs-x" d="M13 13l14 14M27 13L13 27" stroke-width="3.2" fill="none"/></svg><div class="xb-bar"><i></i></div><div class="xb-txt">LOADING</div></div>';
    document.body.appendChild(el);
  }
  function scheduleShow() {
    if (el || showTimer) return;
    showTimer = setTimeout(function () {
      showTimer = null;
      if (n > 0 && !el) { build(); requestAnimationFrame(function () { if (el) el.classList.add("on"); }); shownAt = Date.now(); }
    }, 180);
  }
  function scheduleHide() {
    if (showTimer) { clearTimeout(showTimer); showTimer = null; }
    if (!el) return;
    var wait = Math.max(0, 320 - (Date.now() - shownAt));
    setTimeout(function () {
      if (n > 0 || !el) return;
      el.classList.remove("on");
      var x = el; el = null;
      setTimeout(function () { x.remove(); }, 200);
    }, reduced ? 0 : wait);
  }
  window.fetch = function () {
    var a0 = arguments[0];
    var u = String((a0 && a0.url) || a0 || "");
    var init = arguments[1] || {}, method = String(init.method || (a0 && a0.method) || "GET").toUpperCase();
    /* Background reads already have local loading/empty states. Never cover them with a page-wide busy card. */
    var readRpc = /\/rpc\/(tournament_reg_counts|live_board|bracket_prediction_stats|my_bracket_predictions|my_registrations|my_game_stats|my_header|daily_avatar|squad_board|banner_list_public|weekly_top_clip|active_polls)(?:[?#]|$)/.test(u);
    if (u.indexOf("supabase.co") < 0 || method === "GET" || readRpc || /\/functions\/v1\/(luna-chat|recommend)(?:[?#]|$)/.test(u)) return orig.apply(this, arguments);
    n++; scheduleShow();
    return orig.apply(this, arguments).then(function (r) { n = Math.max(0, n - 1); if (n === 0) scheduleHide(); return r; }, function (e) { n = Math.max(0, n - 1); if (n === 0) scheduleHide(); throw e; });
  };
})();

/* XARENA look manager: 7 selectable looks. Choice persists per device; each look's CSS loads only when selected. */
(function () {
  var KEY = "xr_theme";
  var LOOKS = [
    ["liquid", "Liquid", "#0a84ff"],
    ["classic", "Classic Red", "#e10600"],
    ["3d", "Ultimate 3D", "#8b3dff"],
    ["nexus", "Nexus Purple", "#7c5cff"],
    ["arena", "Arena Orange", "#ff7a1a"],
    ["void", "Void Legends", "#a970ff"],
    ["ascend", "Ascend Teal", "#19e3c2"],
    ["hybrid", "Hybrid OS", "#22d3ee"],
    ["pulse", "Neon Pulse", "#ff2e88"],
    ["aura", "Aura Light", "#8b5cf6"],
    ["social", "Social", "#0095f6"],
    ["gold", "Dark Gold", "#d4a017"]
  ];
  var LOOK_JS = ["arena", "void", "ascend", "aura", "social"];
  try {
    var lt = localStorage.getItem(KEY) || "liquid";
    for (var li = 0; li < LOOKS.length; li++) if (LOOKS[li][0] === lt) { document.documentElement.style.setProperty("--xs-acc", LOOKS[li][2]); break; }
  } catch (e) {}
  function current() {
    var v = localStorage.getItem(KEY) || "liquid";
    for (var i = 0; i < LOOKS.length; i++) if (LOOKS[i][0] === v) return v;
    return "liquid";
  }
  function nameOf(v) {
    for (var i = 0; i < LOOKS.length; i++) if (LOOKS[i][0] === v) return LOOKS[i][1];
    return "Classic Red";
  }
  function load3d() {
    document.body.classList.add("theme-3d");
    var l = document.createElement("link");
    l.rel = "stylesheet"; l.href = "assets/css/theme3d.css";
    document.head.appendChild(l);
    var s = document.createElement("script");
    s.src = "assets/js/vendor/three.min.js";
    s.onload = function () {
      var t = document.createElement("script");
      t.src = "assets/js/theme3d.js?v=20261108-audit1";
      document.body.appendChild(t);
    };
    document.body.appendChild(s);
  }
  function applyLook(v) {
    if (v === "3d") { load3d(); return; }
    if (v === "classic") return;
    document.body.classList.add("look-" + v);
    var l = document.createElement("link");
    l.rel = "stylesheet"; l.href = "assets/css/look-" + v + ".css";
    document.head.appendChild(l);
    if (LOOK_JS.indexOf(v) >= 0) {
      var j = document.createElement("script");
      j.src = "assets/js/look-" + v + ".js";
      document.body.appendChild(j);
    }
  }
  function pick(v) { localStorage.setItem(KEY, v); location.reload(); }
  function openPanel() {
    var existing = document.getElementById("xr-look-panel");
    if (existing) { existing.remove(); return; }
    var cur = current();
    var p = document.createElement("div");
    p.id = "xr-look-panel";
    p.setAttribute("role", "dialog");
    p.setAttribute("aria-label", "Choose your look");
    var html = '<div class="xr-look-head">Choose your look</div>';
    LOOKS.forEach(function (L) {
      html += '<button type="button" class="xr-look-opt' + (L[0] === cur ? " on" : "") + '" data-look="' + L[0] + '">' +
        '<span class="xr-look-dot" style="background:' + L[2] + '"></span><span class="xr-look-name">' + L[1] + "</span>" +
        (L[0] === cur ? '<span class="xr-look-cur">current</span>' : "") + "</button>";
    });
    html += '<div class="xr-look-div"></div><button type="button" class="xr-look-opt xr-look-os" data-osmode="1">' +
      '<span class="xr-look-dot" style="background:linear-gradient(135deg,#8b5cf6,#22d3ee)"></span><span class="xr-look-name">Hybrid OS Mode</span>' +
      '<span class="xr-look-cur">apps + dock</span></button>';
    var calmOn = false;
    try { calmOn = localStorage.getItem("xr_fx") === "calm"; } catch (e) {}
    html += '<div class="xr-look-div"></div><button type="button" class="xr-look-opt xr-look-smooth" data-smooth="1">' +
      '<span class="xr-look-dot" style="background:#8e8e93"></span><span class="xr-look-name">Smooth mode</span>' +
      '<span class="xr-look-cur">' + (calmOn ? "on" : "off") + "</span></button>";
    p.innerHTML = html;
    p.addEventListener("click", function (e) {
      var smb = e.target.closest("[data-smooth]");
      if (smb) {
        var on = true;
        try { on = localStorage.getItem("xr_fx") !== "calm"; } catch (err) {}
        try { localStorage.setItem("xr_fx", on ? "calm" : "full"); } catch (err) {}
        document.body.classList.toggle("imsg-calm", on);
        var c = smb.querySelector(".xr-look-cur"); if (c) c.textContent = on ? "on" : "off";
        return;
      }
      var osb = e.target.closest("[data-osmode]");
      if (osb) {
        localStorage.setItem("xr_theme", "hybrid");
        localStorage.setItem("xr_shell", "os");
        location.href = "os.html";
        return;
      }
      var b = e.target.closest("[data-look]");
      if (b) pick(b.getAttribute("data-look"));
    });
    document.body.appendChild(p);
    setTimeout(function () {
      document.addEventListener("click", function off(e) {
        if (p.parentNode && !p.contains(e.target) && !e.target.closest("#xr-theme-btn") && !e.target.closest("#xr-mini-theme")) {
          p.remove();
          document.removeEventListener("click", off);
        }
      });
    }, 40);
  }
  document.addEventListener("click", function (e) {
    var t = e.target.closest("#xr-theme-btn, #xr-mini-theme");
    if (t) { e.preventDefault(); openPanel(); }
  });
  /* profile page Look card renders its own full list */
  window.xrLooks = { list: LOOKS, current: current, nameOf: nameOf, pick: pick };
  var v = current();
  if (v !== "classic") applyLook(v);
  try { if (localStorage.getItem("xr_fx") === "calm") document.body.classList.add("imsg-calm"); } catch (e) {}
})();

/* ---------- World Chat unread badge ---------- */
(function () {
  "use strict";
  var SUPA_URL = "https://oskccuajgbyabogbvedj.supabase.co";
  var SUPA_KEY = "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7";
  function refresh() {
    var el = document.getElementById("xr-chat-badge");
    if (!el) return;
    if (location.pathname.indexOf("chat.html") >= 0) return; // chat page marks read itself
    var last = localStorage.getItem("xr_chat_read");
    if (!last) return; // never entered the lobby yet
    fetch(SUPA_URL + "/rest/v1/chat_messages?select=id&created_at=gt." + encodeURIComponent(last) + "&limit=99", {
      headers: { "apikey": SUPA_KEY, "Authorization": "Bearer " + SUPA_KEY, "Prefer": "count=exact", "Range-Unit": "items", "Range": "0-0" }
    }).then(function (r) {
      if (!r.ok) return;
      var cr = r.headers.get("content-range") || "";
      var n = parseInt((cr.split("/")[1] || "0"), 10) || 0;
      if (n > 0) { el.textContent = n > 99 ? "99+" : n; el.classList.add("on"); }
      else el.classList.remove("on");
    }).catch(function () {});
  }
  document.addEventListener("DOMContentLoaded", function () {
    refresh();
    setInterval(refresh, 45000);
  });
})();

/* in-app error log (APK only): keeps the last page errors for the crash report; nothing leaves the device */
(function () {
  if (!window.XarenaApp || !window.XarenaApp.logJsError) return;
  window.addEventListener("error", function (e) {
    try { window.XarenaApp.logJsError((e.message || "error") + " @" + String(e.filename || "").split("/").pop() + ":" + (e.lineno || 0)); } catch (x) {}
  });
  window.addEventListener("unhandledrejection", function (e) {
    try { window.XarenaApp.logJsError("promise: " + String((e.reason && e.reason.message) || e.reason || "").slice(0, 120)); } catch (x) {}
  });
})();

/* Optional floating shortcuts must not cover a focused phone form. */
(function(){
 document.addEventListener("focusin",function(e){if(e.target.closest("form"))document.body.classList.add("xr-form-focus");});
 document.addEventListener("focusout",function(){setTimeout(function(){if(!document.activeElement.closest("form"))document.body.classList.remove("xr-form-focus");},0);});
})();
/* Keep core desktop destinations visible; preserve every other route under More. */
(function(){
 var nav=document.querySelector('.nav-links');if(!nav||!window.matchMedia)return;
 var original=Array.prototype.slice.call(nav.children),mq=matchMedia('(min-width:901px)'),more=null;
 var primary=['index.html','tournaments.html','squads.html','leaderboard.html','live.html','account.html'];
 function arrange(){
  if(more){original.forEach(function(li){nav.appendChild(li);});more.remove();more=null;}
  if(!mq.matches)return;
  more=document.createElement('li');more.className='xr-nav-more';
  var details=document.createElement('details'),summary=document.createElement('summary'),list=document.createElement('ul');
  summary.textContent='More';list.className='xr-nav-more-list';details.append(summary,list);more.append(details);
  original.forEach(function(li){var a=li.querySelector('a'),path=a&&new URL(a.href,location.href).pathname.split('/').pop();if(!primary.includes(path)||li.classList.contains('nav-cta'))list.appendChild(li);});
  nav.appendChild(more);
  list.addEventListener('click',function(e){if(e.target.closest('a'))details.open=false;});
 }
 arrange();if(mq.addEventListener)mq.addEventListener('change',arrange);else mq.addListener(arrange);
 document.addEventListener('click',function(e){if(more&&!more.contains(e.target))more.querySelector('details').open=false;});
 document.addEventListener('keydown',function(e){if(e.key==='Escape'&&more)more.querySelector('details').open=false;});
})();
