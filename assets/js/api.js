/* XARENA ESPORTS — backend client (Supabase PostgREST + Auth, no library).
   Only the publishable anon key lives here; Row Level Security on the
   server enforces what it can do (read public content, submit forms).
   Admin power comes only from an authenticated admin session. */
(function () {
  "use strict";
  var SUPA_URL = "https://oskccuajgbyabogbvedj.supabase.co";
  var SUPA_KEY = "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7";
  var SES_KEY = "xr_admin_session";

  function baseHeaders() {
    return { "apikey": SUPA_KEY, "Content-Type": "application/json" };
  }
  function authHeaders() {
    var h = baseHeaders();
    var s = session();
    h["Authorization"] = "Bearer " + (s && s.access_token ? s.access_token : SUPA_KEY);
    return h;
  }
  function session() {
    try { return JSON.parse(sessionStorage.getItem(SES_KEY) || "null"); } catch (e) { return null; }
  }
  function saveSession(s) { sessionStorage.setItem(SES_KEY, JSON.stringify(s)); }

  function req(method, path, body, useAuth, extra) {
    if (method !== "GET" && typeof navigator !== "undefined" && navigator.onLine === false) {
      return Promise.reject(new Error("You are offline - this needs internet. It will go through when you are back."));
    }
    var h = useAuth ? authHeaders() : baseHeaders();
    if (extra) for (var k in extra) h[k] = extra[k];
    return fetch(SUPA_URL + path, {
      method: method,
      headers: h,
      body: body == null ? undefined : JSON.stringify(body)
    }).then(function (r) {
      if (r.status === 204) return null;
      return r.text().then(function (t) {
        var d; try { d = t ? JSON.parse(t) : null; } catch (e) { d = null; }
        if (!r.ok) { var err = new Error((d && (d.message || d.msg || d.error_description)) || ("HTTP " + r.status)); err.status = r.status; err.data = d; throw err; }
        return d;
      });
    });
  }

  var api = {
    url: SUPA_URL,
    key: SUPA_KEY,
    connected: false,
    /* public reads */
    get: function (table, query) { return req("GET", "/rest/v1/" + table + (query || "")); },
    rpc: function (name, args, useAuth) { return req("POST", "/rest/v1/rpc/" + name, args || {}, useAuth); },
    /* form submissions (anon) */
    submit: function (table, row) {
      return req("POST", "/rest/v1/" + table, row, false, { "Prefer": "return=minimal" });
    },
    /* admin */
    aGet: function (table, query) { return req("GET", "/rest/v1/" + table + (query || ""), null, true); },
    aPost: function (table, row) { return req("POST", "/rest/v1/" + table, row, true, { "Prefer": "return=representation" }); },
    aUpsert: function (table, row, conflict) { return req("POST", "/rest/v1/" + table + "?on_conflict=" + (conflict || "key"), row, true, { "Prefer": "resolution=merge-duplicates,return=minimal" }); },
    aPatch: function (table, match, row) { return req("PATCH", "/rest/v1/" + table + "?" + match, row, true, { "Prefer": "return=minimal" }); },
    aDel: function (table, match) { return req("DELETE", "/rest/v1/" + table + "?" + match, null, true, { "Prefer": "return=minimal" }); },
    audit: function (action, details) {
      var s = session();
      return req("POST", "/rest/v1/audit_log", { actor: (s && s.email) || "admin", action: action, details: details || {} }, true, { "Prefer": "return=minimal" }).catch(function () { });
    },
    /* auth */
    signIn: function (email, password) {
      return req("POST", "/auth/v1/token?grant_type=password", { email: email, password: password }).then(function (d) {
        d.email = email; saveSession(d); return d;
      });
    },
    signOut: function () {
      var s = session();
      sessionStorage.removeItem(SES_KEY);
      if (s && s.access_token) req("POST", "/auth/v1/logout", null, true).catch(function () { });
    },
    session: session,
    isAdmin: function () {
      return req("GET", "/rest/v1/admins?select=user_id", null, true).then(function (rows) {
        return Array.isArray(rows) && rows.length > 0;
      }).catch(function () { return false; });
    },
    /* player accounts (Instagram-style): supabase-js auth session */
    user: function () {
      if (!window.xrSb) return Promise.resolve(null);
      return window.xrSb.auth.getUser().then(function (r) { return (r && r.data && r.data.user) || null; }).catch(function () { return null; });
    },
    userToken: function () {
      if (!window.xrSb) return Promise.resolve(null);
      return window.xrSb.auth.getSession().then(function (r) { return (r && r.data && r.data.session && r.data.session.access_token) || null; }).catch(function () { return null; });
    },
    uRpc: function (name, args) {
      return api.userToken().then(function (t) {
        return req("POST", "/rest/v1/rpc/" + name, args || {}, false, t ? { "Authorization": "Bearer " + t } : {});
      });
    },
    refresh: function () {
      var s = session();
      if (!s || !s.refresh_token) return Promise.reject(new Error("no session"));
      return req("POST", "/auth/v1/token?grant_type=refresh_token", { refresh_token: s.refresh_token }).then(function (d) {
        d.email = s.email; saveSession(d); return d;
      });
    }
  };

  /* ---------- load live public content into window.XARENA ---------- */
  function mapTournament(t) {
    return {
      id: t.id, name: t.name, game: t.game, mode: t.mode, status: t.status,
      dateText: t.date_text, prize: t.prize, slots: t.slots, registered: 0,
      regOpen: t.reg_open, format: t.format, description: t.description
    };
  }
  function loadPublic() {
    var jobs = {
      tournaments: api.get("tournaments", "?select=*&order=sort.asc,created_at.asc"),
      news: api.get("news", "?select=*&published=eq.true&order=published_at.desc"),
      scrims: api.get("scrims", "?select=*&active=eq.true&order=created_at.asc"),
      leaderboard: api.get("v_leaderboard", "?select=*"),
      teams: api.get("teams", "?select=*&order=created_at.asc"),
      players: api.get("players", "?select=*,teams(name,tag)&active=eq.true&order=created_at.asc"),
      regcounts: api.rpc("tournament_reg_counts", {})
    };
    var cache = {};
    try { cache = JSON.parse(localStorage.getItem("xr_cache_public") || "{}").data || {}; } catch (e) { cache = {}; }
    var keys = Object.keys(jobs);
    return Promise.all(keys.map(function (k) {
      return jobs[k].then(function (v) { return [k, v]; }).catch(function () { return [k, cache[k] || []]; });
    }))
      .then(function (pairs) {
        try {
          var store = {};
          pairs.forEach(function (p) { if (p[1] && p[1].length) store[p[0]] = p[1]; });
          if (Object.keys(store).length) localStorage.setItem("xr_cache_public", JSON.stringify({ at: Date.now(), data: store }));
        } catch (e) { /* storage full or blocked - caching is best effort */ }
        var D = window.XARENA;
        pairs.forEach(function (p) {
          var k = p[0], v = p[1];
          if (k === "tournaments" && v.length) D.tournaments = v.map(mapTournament);
          if (k === "news" && v.length) D.news = v.map(function (n) { return { date: (n.published_at || "").slice(0, 10), tag: n.tag, title: n.title, body: n.body }; });
          if (k === "scrims") D.scrims = v.map(function (s) { return { title: s.title, day: s.day_text, time: s.time_text, note: s.note }; });
          if (k === "leaderboard") D.leaderboard = { season: "SEASON 1", rows: v.map(function (r) { return { name: r.name, squad: r.squad, kills: r.kills, points: r.points, wins: r.wins, status: "OFFICIAL" }; }) };
          if (k === "teams") D.teams = v;
          if (k === "players") D.players = v;
        });
        pairs.forEach(function (p) { if (p[0] === "regcounts" && Array.isArray(p[1])) (D.tournaments || []).forEach(function (t) { var c = p[1].filter(function (r) { return r.tournament_id === t.id; })[0]; if (c) t.registered = c.registered; }); });
        api.connected = true;
        if (window.XR && window.XR.render) window.XR.render();
        document.dispatchEvent(new CustomEvent("xr-backend-live"));
      });
  }

  api.loadPublic = loadPublic;
  if (window.supabase && !window.xrSb) {
    window.xrSb = window.supabase.createClient(SUPA_URL, SUPA_KEY);
  }
  
  /* anti-abuse: install id (random, stays in this browser/app) + coarse fingerprint. Both are hashed server side; nothing personal is stored. */
  api.deviceInfo = function () {
    var id = null;
    try { id = localStorage.getItem("xr_did"); if (!id) { id = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : (Date.now().toString(36) + Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2)); localStorage.setItem("xr_did", id); } } catch (e) {}
    var fp = "";
    try { fp = [navigator.userAgent, screen.width + "x" + screen.height + "x" + screen.colorDepth, navigator.language, new Date().getTimezoneOffset(), navigator.hardwareConcurrency || "", navigator.platform || ""].join("|"); } catch (e) {}
    var ua = ""; try { ua = /XARENA|; wv\)/i.test(navigator.userAgent) ? "android" : "web"; } catch (e) {}
    return { id: id, fp: fp, platform: ua || "web" };
  };
  api.bindDevice = function () {
    try { if (sessionStorage.getItem("xr_bound")) return; var d = api.deviceInfo(); if (!d.id) return;
      api.uRpc("bind_device", { p_device_id: d.id, p_fp: d.fp, p_platform: d.platform }).then(function (r) { if (r && r.ok) sessionStorage.setItem("xr_bound", "1"); }).catch(function () {});
    } catch (e) {}
  };
  window.xrApi = api;

  /* Native app bridge: hand the session to the APK so it can poll alerts
     when the app is closed. No-ops in a normal browser. */
  function syncNativeAuth(sess) {
    if (!window.XarenaApp || !window.XarenaApp.saveAuth) return;
    try {
      if (sess && sess.access_token && sess.refresh_token) {
        window.XarenaApp.saveAuth(sess.access_token, sess.refresh_token);
      } else if (window.XarenaApp.clearAuth) {
        window.XarenaApp.clearAuth();
      }
    } catch (e) { /* bridge missing or refused - page still works */ }
  }
  if (window.xrSb) {
    window.xrSb.auth.getSession().then(function (r) {
      syncNativeAuth(r && r.data && r.data.session);
    }).catch(function () { });
    /* banned accounts are signed out on every page load - owner moderation is final */
    window.xrSb.auth.getSession().then(function (r) {
      var s = r && r.data && r.data.session;
      if (!s || !s.user) return;
      fetch(SUPA_URL + "/rest/v1/profiles?id=eq." + s.user.id + "&select=banned,banned_reason", { headers: { "apikey": SUPA_KEY, "Authorization": "Bearer " + s.access_token } })
        .then(function (x) { return x.json(); })
        .then(function (rows) {
          var b = rows && rows[0];
          if (b && b.banned) {
            window.xrSb.auth.signOut().catch(function () { });
            alert("This account is banned from XARENA ESPORTS." + (b.banned_reason ? "\nReason: " + b.banned_reason : ""));
            location.href = "index.html";
          }
        }).catch(function () { });
    }).catch(function () { });
    window.xrSb.auth.onAuthStateChange(function (event, sess) {
      syncNativeAuth(sess);
    });
  }

  /* try to connect; page already rendered from data.js fallback */
  /* ---------- offline mode ---------- */
  function offlineBar() {
    var bar = document.createElement("div");
    bar.id = "xr-offline-bar";
    bar.setAttribute("style", "position:fixed;left:0;right:0;bottom:0;z-index:9999;background:#8a1c1c;color:#fff;text-align:center;font-size:13.5px;padding:8px 12px;font-family:inherit;display:none");
    bar.textContent = "You are offline - showing the last saved data. Writes will wait for internet.";
    document.body.appendChild(bar);
    function apply() {
      var off = typeof navigator !== "undefined" && navigator.onLine === false;
      bar.style.display = off ? "block" : "none";
    }
    window.addEventListener("offline", apply);
    window.addEventListener("online", function () {
      apply();
      loadPublic().then(function () {
        if (window.XR && window.XR.render) { try { window.XR.render(); } catch (e) { } }
        try { document.dispatchEvent(new CustomEvent("xr:online")); } catch (e) { }
      }).catch(function () { });
    });
    apply();
  }
  function boot() {
    loadPublic().catch(function () { });
    offlineBar();
  }
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
