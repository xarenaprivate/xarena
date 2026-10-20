(function(){try{if(localStorage.getItem("xr_lite")==="1")document.documentElement.classList.add("xr-lite")}catch(e){}})();
/* XARENA app shell — bottom tab bar + profile circle in the installed app.
   Activates in the Android app (XarenaApp bridge), installed PWA, or ?app=1 preview. */
(function () {
  "use strict";
  var isApp = !!window.XarenaApp ||
    (window.matchMedia && window.matchMedia("(display-mode: standalone)").matches) ||
    /[?&]app=1/.test(location.search);
  if (!isApp) return;
  document.documentElement.classList.add("app-mode");

  function esc(s) { return String(s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  var page = (location.pathname.split("/").pop() || "index.html");
  var tabs = [
    ["index.html", "🏠", "FEED", ""],
    ["search.html", "🔍", "SEARCH", ""],
    ["tournaments.html#register", "📝", "REGISTER", "big"],
    ["chat.html", "💬", "CHAT", ""],
    ["messages.html", '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" style="width:21px;height:21px"><path d="M22 2 11 13"/><path d="M22 2 15 22l-4-9-9-4z"/></svg>', "MSGS", ""],
    ["luna.html", "💜", "LUNA", ""]
  ];
  function current(href) {
    var p = href.split("#")[0];
    if (p === "tournaments.html" && page === "tournaments.html") return true;
    return p === page;
  }

  document.addEventListener("DOMContentLoaded", function () {
    /* bottom tab bar */
    var bar = document.createElement("nav");
    bar.id = "xr-tabs";
    bar.innerHTML = tabs.map(function (t) {
      var cls = (current(t[0]) ? "on " : "") + t[3];
      return '<a href="' + t[0] + '" class="' + cls.trim() + '"><span class="ti">' + t[1] + "</span>" + t[2] + "</a>";
    }).join("");
    document.body.appendChild(bar);

    /* header: mini theme toggle + profile circle */
    var navIn = document.querySelector(".nav-in");
    var right = document.createElement("div");
    right.style.cssText = "display:flex;align-items:center;margin-left:auto";
    var themeBtn = document.createElement("button");
    themeBtn.id = "xr-mini-theme";
    themeBtn.type = "button";
    themeBtn.textContent = "Look";
    themeBtn.setAttribute("aria-label", "Choose your look");
    var handle = localStorage.getItem("xr_chat_handle") || "";
    var dm = document.createElement("a");
    dm.href = "messages.html";
    dm.id = "xr-dm";
    dm.setAttribute("aria-label", "Messages");
    dm.style.cssText = "color:inherit;display:flex;padding:4px";
    dm.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" style="width:21px;height:21px"><path d="M22 2 11 13"/><path d="M22 2 15 22l-4-9-9-4z"/></svg>';
    var bell = document.createElement("a");
    bell.href = "notifications.html";
    bell.id = "xr-bell";
    bell.setAttribute("aria-label", "Notifications");
    bell.textContent = "🔔";
    var avatar = document.createElement("a");
    avatar.href = "profile.html";
    avatar.id = "xr-avatar";
    avatar.setAttribute("aria-label", "Profile");
    avatar.textContent = (handle || "X")[0].toUpperCase();
    right.appendChild(themeBtn);
    right.appendChild(dm);
    right.appendChild(bell);
    right.appendChild(avatar);
    if (navIn) navIn.appendChild(right);
    (function chips() {
      var tries = 0;
      (function go() {
        if (!window.xrApi || !xrApi.user) { if (++tries < 40) setTimeout(go, 500); return; }
        xrApi.user().then(function (u) {
          if (!u) return;
          if (xrApi.bindDevice) xrApi.bindDevice();
          xrApi.uRpc("my_header", {}).then(function (h) {
            if (!h || !h.ok || document.getElementById("xr-chips")) return;
            var c = document.createElement("a"); c.id = "xr-chips"; c.href = "leaderboard.html";
            c.setAttribute("aria-label", "Your EXP and AP");
            c.style.cssText = "display:flex;gap:8px;align-items:center;color:inherit;text-decoration:none;font-size:12px;font-weight:800;margin-right:8px";
            var ex = '<svg viewBox="0 0 24 24" width="16" height="16" fill="#f5b301"><path d="M12 2l2.9 6.6 7.1.6-5.4 4.7 1.7 7-6.3-3.8-6.3 3.8 1.7-7L2 9.2l7.1-.6z"/></svg>';
            var ap = '<svg viewBox="0 0 24 24" width="16" height="16"><circle cx="12" cy="12" r="10" fill="#2fd1a5"/><text x="12" y="16" text-anchor="middle" font-size="10" font-weight="800" fill="#05261d">AP</text></svg>';
            c.innerHTML = '<span style="display:flex;gap:3px;align-items:center" title="EXP">' + ex + h.exp + '</span><span style="display:flex;gap:3px;align-items:center" title="AP">' + ap + h.ap + "</span>";
            right.insertBefore(c, right.firstChild);
          }).catch(function () {});
        }).catch(function () {});
      })();
    })();

  });
})();
