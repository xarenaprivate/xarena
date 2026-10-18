/* VOID look: turn earned task rewards into quest cards with progress bars. Earned = complete. Real data only. */
(function () {
  function transform(box) {
    if (!box) return;
    var rows = box.querySelectorAll(".reward-row:not([data-vq-done])");
    if (!rows.length) return;
    var pts = 0, badges = 0;
    rows.forEach(function (r) {
      r.dataset.vqDone = "1";
      var p = r.querySelector(".task-pts");
      if (p) { var n = parseInt(p.textContent.replace(/[^\d]/g, ""), 10); if (!isNaN(n)) pts += n; }
      if (r.querySelector(".reward-badge")) badges++;
      var bar = document.createElement("div"); bar.className = "vq-bar";
      bar.innerHTML = '<i style="width:100%"></i>';
      var st = document.createElement("span"); st.className = "vq-status"; st.textContent = "Completed";
      r.appendChild(bar); r.appendChild(st);
    });
    if (!box.querySelector(".vq-stats")) {
      var all = box.querySelectorAll(".reward-row");
      var totalPts = 0, totalBadges = 0;
      all.forEach(function (r) {
        var p = r.querySelector(".task-pts");
        if (p) { var n = parseInt(p.textContent.replace(/[^\d]/g, ""), 10); if (!isNaN(n)) totalPts += n; }
        if (r.querySelector(".reward-badge")) totalBadges++;
      });
      var stats = document.createElement("div");
      stats.className = "vq-stats";
      stats.innerHTML =
        '<div class="vq-stat"><b>' + totalPts + '</b><span>Task Points</span></div>' +
        '<div class="vq-stat"><b>' + all.length + '</b><span>Quests Done</span></div>' +
        '<div class="vq-stat"><b>' + totalBadges + '</b><span>Badges</span></div>';
      box.insertBefore(stats, box.firstChild);
    }
  }
  function scan() {
    document.querySelectorAll("#prof-rewards, #pc-rewards, .prof-rewards").forEach(transform);
  }
  scan();
  new MutationObserver(function () { scan(); }).observe(document.body, { childList: true, subtree: true });
})();
