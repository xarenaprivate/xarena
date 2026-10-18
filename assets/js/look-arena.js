/* ARENA look: tap a tournament row to pin it and open the detail panel. Real data only. */
(function () {
  if (document.body.classList.contains("os-embed")) return;
  var panel = null, selRow = null;
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; }); }
  function closePanel() {
    if (panel) { panel.remove(); panel = null; }
    if (selRow) { selRow.classList.remove("sel"); selRow = null; }
    document.body.classList.remove("arena-has-detail");
  }
  function findT(name) {
    var list = (window.XARENA && window.XARENA.tournaments) || [];
    for (var i = 0; i < list.length; i++) if (list[i].name === name) return list[i];
    return null;
  }
  function row(label, val) { return '<div class="ad-row"><span>' + esc(label) + '</span><b>' + esc(val) + '</b></div>'; }
  function openPanel(rowEl) {
    var name = (rowEl.querySelector(".tcard-top h3") || {}).textContent || "";
    var t = findT(name.trim());
    if (!t) return;
    closePanel();
    selRow = rowEl; rowEl.classList.add("sel");
    panel = document.createElement("div");
    panel.id = "arena-detail";
    panel.innerHTML =
      '<div class="ad-head"><b>Tournament Detail</b><button type="button" class="ad-close" aria-label="Close">&times;</button></div>' +
      '<div class="ad-body"><h3>' + esc(t.name) + '</h3>' +
      '<span class="ad-status">' + esc(t.status || "upcoming") + '</span>' +
      row("Mode", t.mode) + row("Schedule", t.dateText) + row("Prize Pool", t.prize) +
      row("Slots", t.slots + " squads") + row("Registered", String(t.registered || 0)) +
      (t.format ? row("Format", t.format) : "") +
      '<p class="ad-desc">' + esc(t.description) + '</p>' +
      (t.regOpen ? '<a class="ad-cta" href="tournaments.html#register">Register Squad</a>' : "") +
      '</div>';
    panel.querySelector(".ad-close").addEventListener("click", closePanel);
    document.body.appendChild(panel);
    document.body.classList.add("arena-has-detail");
  }
  document.addEventListener("click", function (e) {
    var rowEl = e.target.closest("#tournament-list .tcard");
    if (!rowEl) { if (panel && !e.target.closest("#arena-detail")) closePanel(); return; }
    if (e.target.closest("a,button")) return;
    if (rowEl === selRow) { closePanel(); return; }
    openPanel(rowEl);
  });
})();
