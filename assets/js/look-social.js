/* SOCIAL look behavior: IG-style top bar (wordmark + notifications + paper-plane DMs
   with unread-request badge), bottom tab bar, feed card reorder (actions above caption,
   caption prefixed with author), player profile IG header + posts grid + WhatsApp-style
   about line. All icons are our own SVGs. Real data only. */
(function () {
  "use strict";
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  var page = (location.pathname.split("/").pop() || "index.html");
  var SVG = {
    heart: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 20.7C6.4 17.1 3 13.9 3 10.2 3 7.6 5 5.5 7.6 5.5c1.7 0 3.3.9 4.4 2.4 1.1-1.5 2.7-2.4 4.4-2.4 2.6 0 4.6 2.1 4.6 4.7 0 3.7-3.4 6.9-9 10.5z"/></svg>',
    plane: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M22 2 11 13"/><path d="M22 2 15 22l-4-9-9-4z"/></svg>',
    home: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/></svg>',
    search: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="m20.5 20.5-4.6-4.6"/></svg>',
    feed: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="18" height="18" rx="5"/><circle cx="12" cy="12" r="4"/><circle cx="17.2" cy="6.8" r="1" fill="currentColor" stroke="none"/></svg>',
    person: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="8" r="4"/><path d="M4.5 20.5c1.6-3.4 4.3-5 7.5-5s5.9 1.6 7.5 5"/></svg>'
  };

  /* ---------- top bar icons ---------- */
  var navIn = document.querySelector("header.nav .nav-in");
  if (navIn && !document.querySelector(".xs-topic")) {
    var burger = navIn.querySelector(".burger");
    var heart = document.createElement("a");
    heart.className = "xs-topic"; heart.href = "notifications.html"; heart.setAttribute("aria-label", "Notifications");
    heart.innerHTML = SVG.heart;
    var plane = document.createElement("a");
    plane.className = "xs-topic xs-plane"; plane.href = "messages.html"; plane.setAttribute("aria-label", "Messages");
    plane.innerHTML = SVG.plane + '<b class="xs-badge"></b>';
    var ava = document.createElement("a");
    ava.className = "xs-topava"; ava.href = "profile.html"; ava.setAttribute("aria-label", "Profile");
    ava.textContent = "X";
    navIn.insertBefore(heart, burger); navIn.insertBefore(plane, burger); navIn.insertBefore(ava, burger);
    if (window.xrApi && xrApi.user) {
      xrApi.user().then(function (u) {
        if (!u) return;
        xrApi.userToken().then(function (tk) {
          var h = { "apikey": xrApi.key, "Authorization": "Bearer " + (tk || xrApi.key) };
          fetch(xrApi.url + "/rest/v1/profiles?id=eq." + u.id + "&select=username,ign", { headers: h })
            .then(function (r) { return r.json(); }).then(function (rows) {
              var p = rows && rows[0]; var n = (p && (p.ign || p.username)) || (u.email || "X");
              ava.textContent = (n[0] || "X").toUpperCase();
            }).catch(function () {});
          fetch(xrApi.url + "/rest/v1/dm_threads?or=(user_a.eq." + u.id + ",user_b.eq." + u.id + ")&status=eq.requested&requested_by=neq." + u.id + "&select=id", { headers: h })
            .then(function (r) { return r.json(); }).then(function (rows) {
              var n = (rows && rows.length) || 0;
              if (n > 0) {
                var label = n > 9 ? "9+" : String(n);
                document.querySelectorAll(".xs-badge").forEach(function (b) { b.textContent = label; b.style.display = "flex"; });
              }
            }).catch(function () {});
        });
      }).catch(function () {});
    }
  }

  /* ---------- bottom tab bar ---------- */
  if (!document.querySelector(".xs-bnav")) {
    var tabs = [
      ["index.html", "Home", SVG.home, ["index.html", ""]],
      ["search.html", "Search", SVG.search, ["search.html", "players.html"]],
      ["feed.html", "Feed", SVG.feed, ["feed.html"]],
      ["messages.html", "Msgs", SVG.plane + '<b class="xs-badge"></b>', ["messages.html", "chat.html"]],
      ["profile.html", "Profile", SVG.person, ["profile.html", "player.html", "account.html"]]
    ];
    var bar = document.createElement("nav");
    bar.className = "xs-bnav"; bar.setAttribute("aria-label", "Main");
    bar.innerHTML = tabs.map(function (t) {
      return '<a href="' + t[0] + '" class="' + (t[3].indexOf(page) >= 0 ? "on" : "") + '">' + t[2] + "<span>" + t[1] + "</span></a>";
    }).join("");
    document.body.appendChild(bar);
  }

  /* ---------- feed: actions above caption, author inside caption ---------- */
  if (page === "feed.html") {
    var fl = document.getElementById("feed-list");
    if (fl) {
      var fix = function (card) {
        if (card.dataset.xsDone) return; card.dataset.xsDone = "1";
        if (card.querySelector(".xf-likes")) return; /* new IG card: already actions-first with author caption */
        var cap = card.querySelector(".xf-cap"), acts = card.querySelector(".xf-actions");
        if (cap && acts && acts.nextElementSibling !== cap) card.insertBefore(acts, cap);
        if (cap && !cap.querySelector(".xs-capauthor")) {
          var author = card.querySelector(".xf-head a");
          if (author) {
            var a = document.createElement("a");
            a.className = "xs-capauthor"; a.href = author.getAttribute("href"); a.textContent = author.textContent;
            cap.insertBefore(a, cap.firstChild);
          }
        }
      };
      var scan = function () { fl.querySelectorAll(".xf-post").forEach(fix); };
      new MutationObserver(scan).observe(fl, { childList: true, subtree: true });
      scan();
    }
  }

  /* ---------- player profile: IG header + about + posts grid ---------- */
  if (page === "player.html") {
    var uname = "";
    try { uname = new URLSearchParams(location.search).get("u") || ""; } catch (e) {}
    var tries = 0;
    var iv = setInterval(function () {
      tries++;
      var stats = document.getElementById("pc-stats");
      if (!stats) { if (tries > 40) clearInterval(iv); return; }
      clearInterval(iv);
      var card = stats.closest(".card");
      if (!card || card.dataset.xsDone) return;
      card.dataset.xsDone = "1";
      card.classList.add("xs-pcard");
      var avaOld = card.querySelector("div[style*='border-radius:50%']");
      var h2 = card.querySelector("h2");
      var sub = null;
      card.querySelectorAll("p").forEach(function (p) { if (!sub && p.textContent.indexOf("@") === 0) sub = p; });
      var btnRow = null;
      card.querySelectorAll("div").forEach(function (d) { if (!btnRow && d.querySelector("#pc-follow")) btnRow = d; });
      /* row: avatar + stats */
      var av = document.createElement("div");
      av.className = "xs-pava"; av.textContent = avaOld ? avaOld.textContent : "X";
      if (avaOld) avaOld.remove();
      var row = document.createElement("div"); row.className = "xs-pheadrow";
      row.appendChild(av); row.appendChild(stats);
      card.insertBefore(row, card.firstChild);
      /* bio: name + @handle/squad + about (WhatsApp-style) */
      var bio = document.createElement("div"); bio.className = "xs-pbio";
      if (h2) bio.appendChild(h2);
      if (sub) bio.appendChild(sub);
      var about = document.createElement("div"); about.className = "xs-about";
      var idc = null;
      document.querySelectorAll(".card").forEach(function (c) { if (!idc && /Free Fire identity/.test(c.textContent)) idc = c; });
      if (idc) {
        var bits = [];
        idc.querySelectorAll("div[style*='justify-content:space-between']").forEach(function (r) {
          var k = r.querySelector("span"), v = r.querySelector("b");
          if (k && v && v.textContent !== "—") bits.push(k.textContent + ": " + v.textContent);
        });
        if (bits.length) about.textContent = bits.join(" · ") + "  (entered by the player)";
      }
      bio.appendChild(about);
      card.insertBefore(bio, row.nextSibling);
      if (btnRow) {
        btnRow.classList.add("xs-pbtns"); btnRow.removeAttribute("style"); card.insertBefore(btnRow, bio.nextSibling);
        var mb = document.getElementById("pc-msg"); if (mb) mb.textContent = "Message";
        var ab = document.getElementById("pc-add"); if (ab) ab.textContent = "Friend";
      }
      /* posts grid */
      if (!uname || !window.xrApi || !xrApi.uRpc) return;
      var wrap = document.createElement("div");
      wrap.className = "xs-pgridwrap";
      wrap.innerHTML = '<div class="xs-pgtabs"><span>Posts</span></div><div class="xs-pgrid"><div class="xs-pempty">Loading posts…</div></div>';
      card.parentNode.insertBefore(wrap, card.nextSibling);
      var grid = wrap.querySelector(".xs-pgrid");
      xrApi.uRpc("feed_user", { p_name: uname }).then(function (r) {
        var posts = (r && r.posts) || [];
        if (!posts.length) { grid.innerHTML = '<div class="xs-pempty">No posts yet.</div>'; return; }
        grid.innerHTML = "";
        posts.forEach(function (p) {
          var a = document.createElement("a");
          a.href = "feed.html";
          a.title = p.caption || "Post";
          var put = function (el) { a.appendChild(el); if (p.kind === "clip") { var k = document.createElement("span"); k.className = "xs-pkind"; k.textContent = "▶"; a.appendChild(k); } grid.appendChild(a); };
          if (p.media_path && p.media_path.indexOf("https://res.cloudinary.com/") === 0) {
            var img = new Image(); img.loading = "lazy"; img.alt = p.caption || "Post";
            img.src = p.media_path.replace("/upload/", "/upload/w_400,h_400,c_fill,q_auto,f_auto/");
            put(img);
          } else if (p.media_path) {
            fetch(xrApi.url + "/storage/v1/object/feed-media/" + p.media_path, { headers: { "apikey": xrApi.key } })
              .then(function (r2) { return r2.ok ? r2.blob() : null; })
              .then(function (b) {
                if (b) { var im = new Image(); im.loading = "lazy"; im.alt = p.caption || "Post"; im.src = URL.createObjectURL(b); put(im); }
                else { var d = document.createElement("div"); d.className = "xs-ptext"; d.textContent = (p.caption || "Post").slice(0, 80); put(d); }
              }).catch(function () { var d = document.createElement("div"); d.className = "xs-ptext"; d.textContent = (p.caption || "Post").slice(0, 80); put(d); });
          } else {
            var d2 = document.createElement("div"); d2.className = "xs-ptext"; d2.textContent = (p.caption || "Post").slice(0, 80);
            put(d2);
          }
        });
      }).catch(function () { grid.innerHTML = '<div class="xs-pempty">Could not load posts.</div>'; });
    }, 400);
  }
})();
