/* XARENA game helpers: level curve (mirrors server), badge meta, XP UI. No fake data. */
(function () {
  "use strict";
  function levelFromXp(xp) {
    var x = Math.max(0, xp | 0), lvl = 1, need;
    for (;;) {
      need = 50 + 25 * (lvl - 1);
      if (x < need) return { level: lvl, into: x, need: need };
      x -= need; lvl++;
    }
  }
  var BADGES = {
    streak_3:  { name: "Warming Up",   icon: "🔥", desc: "3-day login streak" },
    streak_7:  { name: "On Fire",      icon: "🔥", desc: "7-day login streak" },
    streak_30: { name: "Unstoppable",  icon: "💎", desc: "30-day login streak" },
    lucky_spinner: { name: "Lucky Spinner", icon: "🍀", desc: "Hit the rare spin segment" },
    quiz_first_win: { name: "Quiz Debut", icon: "🧠", desc: "First correct quiz answer" },
    quiz_sharp_10:  { name: "Sharp Mind", icon: "🏆", desc: "10 correct quiz answers" },
    clean_cup:      { name: "Clean Cup", icon: "🧼", desc: "Kept World Chat clean all week" },
    top_fan:        { name: "Top Fan", icon: "💜", desc: "Most active supporter of the week" }
  };
  function badgeMeta(key) {
    return BADGES[key] || { name: key.replace(/_/g, " "), icon: "🎖️", desc: "XARENA badge" };
  }
  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function xpBarHtml(stats, big) {
    var lv = stats.level || levelFromXp(stats.xp || 0).level;
    var into = stats.into_level != null ? stats.into_level : levelFromXp(stats.xp || 0).into;
    var need = stats.level_needed != null ? stats.level_needed : levelFromXp(stats.xp || 0).need;
    var pct = Math.max(0, Math.min(100, Math.round((into / need) * 100)));
    return '<div class="xg-level' + (big ? " big" : "") + '">' +
      '<div class="xg-lv-badge">LV ' + lv + '</div>' +
      '<div class="xg-lv-info"><div class="xg-lv-nums"><b>' + (stats.xp || 0) + ' XP</b><span>' + into + ' / ' + need + ' to LV ' + (lv + 1) + '</span></div>' +
      '<div class="xg-bar"><i style="width:' + pct + '%"></i></div></div></div>';
  }
  window.xrGame = { levelFromXp: levelFromXp, badgeMeta: badgeMeta, esc: esc, xpBarHtml: xpBarHtml };
})();
