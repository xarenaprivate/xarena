/* AURA look behavior: kit top bar (title + bell + avatar), bottom nav,
   profile stats row, Luna quick actions, Messages Luna row. Real data only. */
(function () {
  "use strict";
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  var page = (location.pathname.split("/").pop() || "index.html");

  /* ---------- top bar ---------- */
  var navIn = document.querySelector("header.nav .nav-in");
  if (navIn && !document.querySelector(".xa-title")) {
    var title = (document.title.split("·")[0] || "XARENA").trim();
    var t = document.createElement("div");
    t.className = "xa-title";
    t.textContent = title;
    navIn.insertBefore(t, navIn.firstChild);
    var bell = document.createElement("a");
    bell.className = "xa-topbtn";
    bell.href = "notifications.html";
    bell.setAttribute("aria-label", "Notifications");
    bell.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/></svg>';
    var ava = document.createElement("a");
    ava.className = "xa-ava";
    ava.href = "profile.html";
    ava.setAttribute("aria-label", "Profile");
    ava.textContent = "X";
    var burger = navIn.querySelector(".burger");
    navIn.insertBefore(bell, burger);
    navIn.insertBefore(ava, burger);
    if (window.xrApi && xrApi.user) {
      xrApi.user().then(function (u) {
        if (!u) return;
        return xrApi.userToken().then(function (tk) {
          return fetch(xrApi.url + "/rest/v1/profiles?id=eq." + u.id + "&select=username,ign", { headers: { "apikey": xrApi.key, "Authorization": "Bearer " + (tk || xrApi.key) } });
        }).then(function (r) { return r.json(); }).then(function (rows) {
          var p = rows && rows[0];
          var n = (p && (p.ign || p.username)) || (u.email || "X");
          ava.textContent = (n[0] || "X").toUpperCase();
        });
      }).catch(function () {});
    }
  }

  /* ---------- bottom nav ---------- */
  if (!document.querySelector(".xa-bnav")) {
    var tabs = [
      ["feed.html", "Home", '<svg viewBox="0 0 24 24"><path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/><path d="M9 21v-6h6v6"/></svg>', ["feed.html", "index.html"]],
      ["tournaments.html#register", "Registration", '<svg viewBox="0 0 24 24"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>', ["tournaments.html"]],
      ["messages.html", "Message", '<svg viewBox="0 0 24 24"><path d="M22 2 11 13"/><path d="M22 2 15 22l-4-9-9-4z"/></svg>', ["messages.html"]],
      ["luna.html", "Luna", '<svg viewBox="0 0 24 24"><path d="M12 3l1.9 5.6L19.5 10l-5.6 1.9L12 17.5l-1.9-5.6L4.5 10l5.6-1.4Z"/><path d="M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8Z"/></svg>', ["luna.html", "chat.html"]]
    ];
    var bar = document.createElement("nav");
    bar.className = "xa-bnav";
    bar.setAttribute("aria-label", "Main");
    bar.innerHTML = tabs.map(function (tb) {
      var on = tb[3].indexOf(page) >= 0;
      return '<a href="' + tb[0] + '" class="' + (on ? "on" : "") + '">' + tb[2] + "<span>" + tb[1] + "</span></a>";
    }).join("");
    document.body.appendChild(bar);
  }

  /* ---------- Luna quick actions ---------- */
  if (page === "luna.html" && !document.querySelector(".xa-quick")) {
    var main = document.querySelector("main");
    if (main) {
      var q = document.createElement("div");
      q.className = "xa-quick";
      q.innerHTML =
        '<button data-prompt="Give me 3 content ideas I can post on the XARENA highlights feed as a Free Fire player."><span class="ic">💡</span>Generate content ideas</button>' +
        '<button data-prompt="Summarize the latest XARENA news and what matters for players this week."><span class="ic">✨</span>Summarize text</button>' +
        '<button data-prompt="Draft a short, hype caption for my Free Fire booyah screenshot on the XARENA feed."><span class="ic">✍️</span>Draft a caption</button>';
      main.insertBefore(q, main.children[1] || null);
      q.addEventListener("click", function (e) {
        var b = e.target.closest("[data-prompt]");
        if (!b) return;
        var inp = main.querySelector("textarea, input[type=text], input:not([type])");
        if (inp) {
          inp.value = b.getAttribute("data-prompt");
          inp.focus();
          inp.dispatchEvent(new Event("input", { bubbles: true }));
        }
      });
    }
  }

  /* ---------- Messages: pinned Luna AI row ---------- */
  if (page === "messages.html" && !document.querySelector(".xa-luna-row")) {
    var mm = document.querySelector("main .wrap") || document.querySelector("main");
    if (mm) {
      var row = document.createElement("a");
      row.className = "xa-luna-row";
      row.href = "luna.html";
      row.innerHTML = '<div class="ic">✨</div><div><b>Luna AI</b><span>Your assistant - ask anything</span></div><span class="ai">AI</span>';
      mm.insertBefore(row, mm.children[1] || null);
    }
  }

  /* ---------- Profile kit stats row ---------- */
  if (page === "profile.html") {
    var tries = 0;
    var iv = setInterval(function () {
      tries++;
      var out = document.getElementById("prof-out");
      if (!out || tries > 20) { clearInterval(iv); return; }
      var head = out.querySelector("div[style*='text-align:center']");
      if (!head || document.querySelector(".xa-pstats")) return;
      if (!window.xrApi || !xrApi.user) { clearInterval(iv); return; }
      xrApi.user().then(function (u) {
        if (!u) { clearInterval(iv); return; }
        xrApi.userToken().then(function (tk) {
          var h = { "apikey": xrApi.key, "Authorization": "Bearer " + (tk || xrApi.key) };
          var posts = fetch(xrApi.url + "/rest/v1/feed_posts?user_id=eq." + u.id + "&select=id", { headers: h }).then(function (r) { return r.json(); }).catch(function () { return []; });
          var friends = fetch(xrApi.url + "/rest/v1/friends?or=(user_id.eq." + u.id + ",friend_id.eq." + u.id + ")&select=user_id", { headers: h }).then(function (r) { return r.json(); }).catch(function () { return []; });
          var badges = fetch(xrApi.url + "/rest/v1/badges?user_id=eq." + u.id + "&select=badge", { headers: h }).then(function (r) { return r.json(); }).catch(function () { return []; });
          Promise.all([posts, friends, badges]).then(function (rs) {
            var n = function (x) { return Array.isArray(x) ? x.length : 0; };
            var stats = document.createElement("div");
            stats.className = "xa-pstats";
            stats.innerHTML =
              "<div><b>" + n(rs[0]) + "</b><span>Posts</span></div>" +
              "<div><b>" + n(rs[1]) + "</b><span>Friends</span></div>" +
              "<div><b>" + n(rs[2]) + "</b><span>Badges</span></div>";
            head.parentNode.insertBefore(stats, head.nextSibling);
            clearInterval(iv);
          });
        });
      }).catch(function () { clearInterval(iv); });
    }, 600);
  }
})();
