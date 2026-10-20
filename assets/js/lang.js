/* XARENA language toggle: English (default) or Hinglish labels. Exact-match label swap only; user content is never touched. */
(function () {
  "use strict";
  var KEY = "xr_lang";
  function get() { try { return localStorage.getItem(KEY) === "hi" ? "hi" : "en"; } catch (e) { return "en"; } }
  function set(v) { try { localStorage.setItem(KEY, v); } catch (e) {} }
  window.xrLang = { get: get, set: set };
  if (get() !== "hi") return;
  var D = {
    "Home": "Ghar", "Tournaments": "Tournaments", "Leaderboard": "Leaderboard", "Winners": "Vijeta", "Squads": "Squads", "Players": "Players", "News": "Khabar", "Rules & Regulations": "Rules aur Niyam",
    "How to Play": "Kaise khelein", "Calendar": "Calendar", "Account": "Mera account", "Contact": "Sampark", "Contact & Support": "Sampark aur Support", "Messages": "Messages", "Streak Badges": "Streak Badges",
    "Get the App": "App download karo", "Support": "Support", "Arena Hub": "Arena Hub", "Tasks": "Kaam", "Play": "Khelo", "Live": "Live", "Clips": "Clips", "Scrims": "Practice match", "Coaches": "Coach",
    "Today": "Aaj", "Aaj kya hai": "Aaj kya hai", "Countdown": "Ginti (countdown)", "Squads registered": "Squads register hue", "Next match": "Agla match", "Daily check-in": "Roz ka check-in", "Daily unlocks": "Aaj ke free unlock",
    "Latest winners": "Naye vijeta", "Top 3 squads": "Top 3 squads", "Register your squad": "Apna squad register karo", "Check in": "Check-in karo", "Show Room ID": "Room ID dikhao", "All winners": "Sab vijeta",
    "Full leaderboard": "Poora leaderboard", "Daily quiz": "Roz ka quiz", "Daily mission": "Roz ka mission", "Unlock free": "Free unlock karo", "Checked in today ✓": "Aaj check-in ho gaya ✓", "Aaj ka tip": "Aaj ka tip",
    "Creator spotlight": "Creator spotlight", "How to join in 3 steps": "3 step me join karo", "Register for Ignition Cup": "Ignition Cup ke liye register karo", "Share": "Share karo", "Invite friends": "Doston ko bulao",
    "Points table": "Points table", "History": "Itihaas", "Spotlights": "Spotlight", "Notice board": "Notice board", "Head to head": "Aamne-saamne", "Squad profile": "Squad profile", "Feedback": "Feedback", "Send": "Bhejo",
    "Points, history and spotlights": "Points, itihaas aur spotlight", "No notices right now.": "Abhi koi notice nahi.", "No results yet. The table fills in after the first match is played.": "Abhi koi result nahi. Pehla match khelne ke baad table bharegi.",
    "The match schedule is announced soon.": "Match schedule jaldi announce hoga.", "Login": "Login", "Log in": "Login", "Sign up": "Sign up", "Register": "Register", "Download": "Download"
  };
  var SKIP = { SCRIPT: 1, STYLE: 1, TEXTAREA: 1, INPUT: 1, CODE: 1, PRE: 1, NOSCRIPT: 1 };
  function walk(n) {
    if (n.nodeType === 3) { var t = n.nodeValue, k = t.trim(); if (k && D[k] && D[k] !== k) n.nodeValue = t.replace(k, D[k]); return; }
    if (n.nodeType !== 1 || SKIP[n.tagName] || n.isContentEditable) return;
    for (var c = n.firstChild; c; c = c.nextSibling) walk(c);
  }
  function run() { walk(document.body); new MutationObserver(function (ms) { ms.forEach(function (m) { m.addedNodes.forEach(walk); if (m.type === "characterData") walk(m.target); }); }).observe(document.body, { childList: true, subtree: true, characterData: true }); }
  if (document.body) run(); else document.addEventListener("DOMContentLoaded", run);
})();
