/* Public stage navigation. Match controls and registration retain their existing handlers. */
(function () {
  'use strict';
  var selected = new URLSearchParams(location.search).get('tournament');
  var resolve;
  var ready = new Promise(function (r) { resolve = r; });
  function load() {
    fetch(xrApi.url + '/rest/v1/tournament_stages?select=tournament_id,stage,label,ord&order=ord.asc', {headers:{apikey:xrApi.key}})
      .then(function (r) { if (!r.ok) throw new Error('Stages unavailable'); return r.json(); })
      .then(function (rows) { resolve(Array.isArray(rows) ? rows : []); })
      .catch(function () { resolve([]); }); // Legacy room controls remain available on a read failure.
  }
  function decorate(box, matches) {
    return ready.then(function (all) {
      var stages = all.filter(function (s) { return selected === null || s.tournament_id === selected; });
      if (!stages.length) return;
      var ids = [], cards = {};
      matches.forEach(function (m) {
        var button = box.querySelector('[data-room="' + m.id + '"]');
        if (button) cards[m.id] = button.closest('.card');
      });
      stages.forEach(function (s) { if (ids.indexOf(s.tournament_id) < 0) ids.push(s.tournament_id); });
      var frag = document.createDocumentFragment();
      ids.forEach(function (id) {
        var section = document.createElement('section'); section.className = 'xr-stage-section';
        var h = document.createElement('h2'); h.textContent = selected === null ? id + ' - Stages' : 'Tournament stages'; section.appendChild(h);
        stages.filter(function (s) { return s.tournament_id === id; }).forEach(function (s) {
          var rows = matches.filter(function (m) { return m.tournament_id === id && m.stage === s.stage; });
          var detail = document.createElement('details'); detail.className = 'card xr-stage'; detail.dataset.stage = s.stage;
          var summary = document.createElement('summary');
          var label = document.createElement('strong'); label.textContent = s.label; summary.appendChild(label);
          var state = document.createElement('span');
          state.textContent = rows.length ? rows.length + ' matches - ' + (rows.some(function (m) { return m.status === 'live'; }) ? 'Live' : rows.every(function (m) { return m.status === 'done'; }) ? 'Completed' : 'Scheduled') : 'Coming soon';
          summary.appendChild(state); detail.appendChild(summary);
          if (!rows.length) { var p = document.createElement('p'); p.textContent = 'Upcoming. Matches and dates have not been announced.'; detail.appendChild(p); }
          rows.forEach(function (m) { if (cards[m.id]) { detail.appendChild(cards[m.id]); delete cards[m.id]; } });
          section.appendChild(detail);
        });
        frag.appendChild(section);
      });
      // Keep unstaged historical matches accessible, without assigning them a stage.
      matches.forEach(function (m) { if (cards[m.id]) frag.appendChild(cards[m.id]); });
      box.replaceChildren(frag); box.style.display = '';
      if (selected !== null) {
        var summary = document.getElementById('event-details');
        if (summary && summary.nextElementSibling !== box) summary.after(box);
      }
    });
  }
  window.xrTournamentStages = {ready:ready, decorate:decorate, enabled:function (rows) { return rows.some(function (s) { return selected === null || s.tournament_id === selected; }); }};
  if (document.readyState === 'complete') load(); else window.addEventListener('load',load);
})();
