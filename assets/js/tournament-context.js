/* Tournament UI context. IDs remain opaque; this module makes no account/event writes. */
(function () {
  'use strict';
  var selected = new URLSearchParams(location.search).get('tournament');
  window.xrEventRows = function (rows) {
    if (!Array.isArray(rows)) return [];
    return selected === null ? rows : rows.filter(function (r) { return r.tournament_id === selected; });
  };
  function txt(el, value) { if (el) el.textContent = value; }
  function ready() {
    document.addEventListener('focusin', function (e) {
      if (e.target.closest('form')) document.body.classList.add('xr-form-focus');
    });
    document.addEventListener('focusout', function () {
      setTimeout(function () { if (!document.activeElement.closest('form')) document.body.classList.remove('xr-form-focus'); }, 0);
    });
    if (selected === null || !window.xrRegQuote) return;
    var section = document.getElementById('register');
    if (!section) return;
    var summary = document.createElement('div');
    summary.id = 'event-details'; summary.className = 'card xr-event-summary';
    summary.style.cssText = 'margin:0 0 24px;scroll-margin-top:84px';
    summary.innerHTML = '<h1>Selected tournament</h1><p>Loading current event details...</p>';
    section.querySelector('.section-head').before(summary);
    section.querySelector('.section-head').hidden = true;
    var oldRules = document.getElementById('xr-s1');
    if (oldRules && selected !== 'xr-s1') oldRules.hidden = true;
    window.xrRegQuote.then(function (event) {
      summary.replaceChildren();
      var h = document.createElement('h1'); h.textContent = event.name; summary.appendChild(h);
      var items = [['Schedule', xrReadableSchedule(event.dateText) || 'Not announced'], ['Slots', event.slots == null ? 'Not announced' : event.slots + ' squads'], ['Mode', event.mode || 'Not announced'], ['Prize', event.prize && !/^x+\s*$/i.test(event.prize) ? event.prize : 'Not confirmed'], ['Format', event.format || 'Not published for this tournament']];
      items.forEach(function (item) { var p = document.createElement('p'), b = document.createElement('b'); b.textContent = item[0] + ': '; p.append(b, document.createTextNode(item[1])); summary.appendChild(p); });
      if (event.description) { var d = document.createElement('p'); d.textContent = event.description; summary.appendChild(d); }
      var rules = document.createElement('a'); rules.href = 'rules.html'; rules.className = 'btn btn-ghost btn-sm'; rules.textContent = 'General fair-play rules'; summary.appendChild(rules);
      var shareUrl = new URL('tournaments.html?tournament=' + encodeURIComponent(event.id) + '#event-details', location.href).href;
      document.querySelectorAll('[data-xr-share]').forEach(function (button) {
        if (button.closest('#reg-success')) return;
        button.dataset.title = event.name;
        button.dataset.text = event.name + '. ' + (event.dateText ? 'Published schedule: ' + event.dateText + '. ' : '') + 'See current event details and registration:';
        button.dataset.url = shareUrl;
      });
      ['description','og:description','twitter:description'].forEach(function (name) { var meta = document.querySelector('meta[name="' + name + '"],meta[property="' + name + '"]'); if (meta) meta.content = event.name + ' - tournament details, registration and published results.'; });
      ['og:title','twitter:title'].forEach(function (name) { var meta = document.querySelector('meta[name="' + name + '"],meta[property="' + name + '"]'); if (meta) meta.content = event.name + ' | XARENA ESPORTS'; });
      if (location.hash === '#event-details') summary.scrollIntoView({block:'start'});
    }).catch(function () { summary.replaceChildren(); var p = document.createElement('p'); p.textContent = 'Could not load the selected event. Return to Tournaments and choose it again.'; summary.appendChild(p); });
  }
  if (document.readyState === 'complete') ready(); else window.addEventListener('load', ready);
})();
