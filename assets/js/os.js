/* Boot splash hide (os.html has no app.js) */
(function () {
  var sp = document.getElementById("xr-splash"); if (!sp) return;
  var reduced = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
  var t0 = Date.now();
  function hide() {
    if (!sp.parentNode) return;
    sp.classList.add("xs-done");
    setTimeout(function () { if (sp.parentNode) sp.parentNode.removeChild(sp); }, reduced ? 0 : 320);
  }
  function ready() { setTimeout(hide, reduced ? 0 : Math.max(0, 650 - (Date.now() - t0))); }
  if (document.readyState === "complete") ready(); else window.addEventListener("load", ready);
  setTimeout(hide, 2400);
})();
/* XARENA HYBRID OS — window manager + live widgets. Real data only. */
(function () {
  "use strict";
  var URLB = "https://oskccuajgbyabogbvedj.supabase.co";
  var KEY = "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7";
  var wins = {}; var zTop = 310; var openCount = 0;
  var root = document.getElementById("os-windows");
  var dockTasks = document.getElementById("os-dock-tasks");

  function isMobile() { return window.innerWidth <= 760; }
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }

  /* ---------- windows ---------- */
  function focusWin(w) {
    zTop += 1; w.style.zIndex = zTop;
    document.querySelectorAll(".os-win").forEach(function (x) { x.classList.remove("focus"); });
    w.classList.add("focus");
  }
  function openApp(id, title, icon, url) {
    if (wins[id]) { var w0 = wins[id]; w0.el.classList.remove("min"); focusWin(w0.el); syncDock(); return; }
    openCount += 1;
    var el = document.createElement("div");
    el.className = "os-win focus";
    if (!isMobile()) {
      var W = Math.min(880, window.innerWidth - 60), H = Math.min(600, window.innerHeight - 150);
      el.style.width = W + "px"; el.style.height = H + "px";
      el.style.left = Math.max(14, (window.innerWidth - W) / 2 + (openCount % 5) * 26 - 52) + "px";
      el.style.top = Math.max(10, 26 + (openCount % 5) * 22) + "px";
    }
    el.innerHTML =
      '<div class="os-win-head"><span class="wi">' + icon + '</span><span class="wt">' + esc(title) + "</span>" +
      '<button type="button" class="wh" title="Home (minimize)">–</button>' +
      '<button type="button" class="wc" title="Close">✕</button></div>' +
      '<div class="os-win-body"><iframe src="' + url + (url.indexOf("?") >= 0 ? "&" : "?") + 'os=1" loading="lazy"></iframe></div>';
    root.appendChild(el);
    var rec = { el: el, icon: icon, title: title };
    wins[id] = rec;
    el.addEventListener("pointerdown", function () { focusWin(el); });
    el.querySelector(".wc").addEventListener("click", function () {
      el.remove(); delete wins[id]; syncDock();
    });
    el.querySelector(".wh").addEventListener("click", function () { el.classList.add("min"); syncDock(); });
    if (!isMobile()) makeDraggable(el, el.querySelector(".os-win-head"));
    focusWin(el); syncDock();
  }
  function makeDraggable(el, handle) {
    var sx, sy, ox, oy, dragging = false;
    handle.addEventListener("pointerdown", function (e) {
      if (e.target.closest("button")) return;
      dragging = true; sx = e.clientX; sy = e.clientY;
      ox = el.offsetLeft; oy = el.offsetTop;
      handle.setPointerCapture(e.pointerId);
    });
    handle.addEventListener("pointermove", function (e) {
      if (!dragging) return;
      el.style.left = Math.max(-el.offsetWidth + 90, Math.min(window.innerWidth - 90, ox + e.clientX - sx)) + "px";
      el.style.top = Math.max(0, Math.min(window.innerHeight - 60, oy + e.clientY - sy)) + "px";
    });
    handle.addEventListener("pointerup", function () { dragging = false; });
    handle.addEventListener("pointercancel", function () { dragging = false; });
  }
  function syncDock() {
    dockTasks.innerHTML = "";
    Object.keys(wins).forEach(function (id) {
      var w = wins[id];
      var b = document.createElement("button");
      b.type = "button"; b.className = "task" + (w.el.classList.contains("min") ? "" : " on");
      b.textContent = w.icon; b.title = w.title;
      b.addEventListener("click", function () {
        if (w.el.classList.contains("min")) { w.el.classList.remove("min"); focusWin(w.el); }
        else w.el.classList.add("min");
        syncDock();
      });
      dockTasks.appendChild(b);
    });
    var has = Object.keys(wins).length > 0;
    document.getElementById("os-dock-sep").style.display = has ? "" : "none";
  }

  /* ---------- apps ---------- */
  var APPS = {
    tournaments: ["🏆", "Tournaments", "tournaments.html"],
    squads: ["🛡️", "Squads", "squads.html"],
    tasks: ["✅", "Tasks", "tasks.html"],
    chat: ["💬", "World Chat", "chat.html"],
    luna: ["✨", "Luna AI", "luna.html"],
    leaderboard: ["📊", "Leaderboard", "leaderboard.html"],
    winners: ["🥇", "Winners", "winners.html"],
    players: ["🎮", "Players", "players.html"],
    scrims: ["🎯", "Scrims", "scrims.html"],
    news: ["📰", "News", "news.html"],
    rules: ["📜", "Rules", "rules.html"],
    profile: ["👤", "Profile", "profile.html"],
    search: ["🔍", "Search", "search.html"],
    contact: ["✉️", "Contact", "contact.html"]
  };
  function launch(id) { var a = APPS[id]; if (a) openApp(id, a[1], a[0], a[2]); }

  var grid = document.getElementById("os-grid");
  Object.keys(APPS).forEach(function (id) {
    var a = APPS[id];
    var b = document.createElement("button");
    b.type = "button"; b.className = "os-app";
    b.innerHTML = '<span class="ic">' + a[0] + '</span><span class="nm">' + a[1] + "</span>";
    b.addEventListener("click", function () { launch(id); });
    grid.appendChild(b);
  });
  document.querySelectorAll("[data-os-app]").forEach(function (el) {
    el.addEventListener("click", function () { launch(el.getAttribute("data-os-app")); });
  });
  document.getElementById("os-exit").addEventListener("click", function () {
    localStorage.setItem("xr_shell", "web");
    location.href = "index.html";
  });
  document.getElementById("os-home").addEventListener("click", function () {
    Object.keys(wins).forEach(function (id) { wins[id].el.classList.add("min"); });
    syncDock();
    document.querySelector(".os-desktop").scrollTo({ top: 0, behavior: "smooth" });
  });

  /* ---------- clock ---------- */
  function tick() {
    var n = new Date();
    document.getElementById("os-time").textContent =
      String(n.getHours()).padStart(2, "0") + ":" + String(n.getMinutes()).padStart(2, "0");
    document.getElementById("os-date").textContent =
      n.toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long" });
  }
  tick(); setInterval(tick, 15000);

  /* ---------- live widgets (real data, honest fallbacks) ---------- */
  function get(path) {
    return fetch(URLB + "/rest/v1/" + path, { headers: { apikey: KEY } })
      .then(function (r) { return r.json(); }).catch(function () { return null; });
  }
  /* next tournament */
  get("tournaments?select=name,date_text,status&status=in.(live,upcoming)&order=sort.asc&limit=1").then(function (rows) {
    var b = document.getElementById("os-w-tourn");
    if (rows && rows.length) {
      var t = rows[0];
      b.innerHTML = '<div class="big">' + esc(t.name) + '</div><div class="ws">' + esc(t.date_text || "dates to be announced") + " · " + esc(t.status) + "</div>";
    } else b.innerHTML = '<div class="wb">No live or upcoming tournament right now.</div><div class="ws">Season 1 details land here first.</div>';
  });
  /* open tasks */
  get("tasks?select=id&active=eq.true").then(function (rows) {
    var b = document.getElementById("os-w-tasks");
    var n = rows && rows.length ? rows.length : 0;
    b.innerHTML = n
      ? '<div class="big">' + n + '</div><div class="ws">open task' + (n === 1 ? "" : "s") + " — finish them for points and badges</div>"
      : '<div class="wb">No open tasks right now.</div><div class="ws">New tasks from the owner appear here.</div>';
  });
  /* task score leaders */
  get("v_task_scores?select=ign,points&order=points.desc&limit=3").then(function (rows) {
    var b = document.getElementById("os-w-score");
    if (rows && rows.length) {
      b.innerHTML = rows.map(function (r, i) {
        return '<div style="display:flex;justify-content:space-between;padding:2px 0"><span>' + ["🥇", "🥈", "🥉"][i] + " " + esc(r.ign) + '</span><b style="color:var(--osd-cyan)">' + r.points + "</b></div>";
      }).join("");
    } else b.innerHTML = '<div class="wb">No task scores yet.</div><div class="ws">Finish tasks to take the top spot.</div>';
  });
  /* chat: online now + unread dot */
  get("chat_messages?select=created_at&order=id.desc&limit=1").then(function (rows) {
    if (rows && rows.length) {
      var last = localStorage.getItem("xr_chat_read") || "";
      if (rows[0].created_at > last) document.getElementById("os-chat-dot").classList.add("on");
    }
  });

  /* auto-enter OS mode when shell flag says so */
  localStorage.setItem("xr_shell", "os");
})();
