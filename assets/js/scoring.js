/* Published Season 2 rules. This does not calculate or award match results. */
(function () {
  'use strict';
  function start() {
    var mount = document.getElementById('xr-scoring-rules');
    if (!mount || !window.xrApi) return;
    var requested = new URLSearchParams(location.search).get('tournament') || 'XR - S2';
    window.xrApi.rpc('xr_scoring_rules', {p_tournament: requested}).then(function (r) {
      if (!r || !r.published) { mount.hidden = true; return; }
      function text(tag, value, parent) { var e = document.createElement(tag); e.textContent = value; parent.appendChild(e); return e; }
      mount.textContent = '';
      text('h3', 'Season 2 scoring rules', mount);
      text('p', 'Six-match series. Total score = placement points + 1 point per kill.', mount);
      var table = document.createElement('table'); table.style.cssText = 'width:100%;border-collapse:collapse;text-align:left';
      table.setAttribute('aria-label', 'Season 2 placement points');
      var head = table.createTHead().insertRow(); ['Place','Points'].forEach(function (s) { var th = document.createElement('th'); th.textContent=s; th.style.padding='8px'; head.appendChild(th); });
      var body = table.createTBody();
      r.placement_points.forEach(function (points, index) { var row=body.insertRow(); [String(index+1),String(points)].forEach(function(s){var c=row.insertCell();c.textContent=s;c.style.cssText='padding:6px 8px;border-top:1px solid rgba(127,127,127,.18)';}); });
      mount.appendChild(table);
      text('p', 'Ties: most Booyahs, then most total kills, then better placement in Match 6.', mount);
      text('p', 'Match results are reviewed by match control. Publishing these rules does not automatically calculate or award points.', mount);
    }).catch(function () { mount.textContent='Scoring rules could not load. Reload to check the published rules.'; });
  }
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',start); else start();
})();
