/* XARENA helper desk: staff-only RPCs (set room, enter results, view registrations without phone/UID). Admin adds helpers. */
(function () {
  "use strict";
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  var body = document.getElementById("hub-body"), tabs = document.getElementById("hub-tabs");
  tabs.innerHTML = "";
  function rpc(n, a) { return window.xrApi ? window.xrApi.uRpc(n, a || {}) : Promise.resolve(null); }
  setTimeout(function () {
    rpc("helper_matches").then(function (r) {
      if (!r || !r.ok) { body.innerHTML = '<div class="hub-c">This page is for XARENA helpers. Log in with a helper account.</div>'; return; }
      var ms = r.matches || [];
      var opts = ms.map(function (m) { return '<option value="' + esc(m.id) + '">' + esc(m.title) + (m.room_set ? " (room set)" : "") + "</option>"; }).join("");
      body.innerHTML = '<div class="hub-c"><h3>Room ID and password</h3><select id="h-m">' + (opts || "<option value=''>No matches yet</option>") + '</select><input id="h-r" placeholder="Room ID" maxlength="30"><input id="h-p" placeholder="Password" maxlength="30"><button class="hub-b" id="h-rs">Save room</button> <span id="h-rm"></span></div>' +
        '<div class="hub-c"><h3>Enter result</h3><input id="r-pl" placeholder="Player IGN" maxlength="40"><input id="r-sq" placeholder="Squad" maxlength="40"><input id="r-k" type="number" placeholder="Kills" min="0" max="60"><input id="r-pc" type="number" placeholder="Placement (1-48)" min="1" max="48"><input id="r-pt" type="number" placeholder="Points" min="0" max="500"><button class="hub-b" id="r-go">Add result</button> <span id="r-m"></span></div>' +
        '<div class="hub-c"><h3>Registrations (no phone or UID)</h3><button class="hub-b" id="g-go">Load Ignition Cup</button><div id="g-l"></div></div>';
      document.getElementById("h-rs").addEventListener("click", function () { rpc("helper_set_room", { p_match: document.getElementById("h-m").value, p_room_id: document.getElementById("h-r").value, p_room_pass: document.getElementById("h-p").value }).then(function (x) { document.getElementById("h-rm").textContent = x && x.ok ? "Saved." : (x && x.error) || "Failed"; }); });
      document.getElementById("r-go").addEventListener("click", function () { var v = function (i) { return document.getElementById(i).value; }; rpc("helper_add_result", { p_match: v("h-m"), p_player: v("r-pl"), p_squad: v("r-sq"), p_kills: +v("r-k") || 0, p_placement: +v("r-pc") || 0, p_points: +v("r-pt") || 0 }).then(function (x) { document.getElementById("r-m").textContent = x && x.ok ? "Added." : (x && x.error) || "Failed"; }); });
      document.getElementById("g-go").addEventListener("click", function () { rpc("helper_registrations", { p_tournament: "xr-5oar" }).then(function (x) { var l = (x && x.registrations) || []; document.getElementById("g-l").innerHTML = l.length ? l.map(function (q) { return "<div>" + esc(q.squad) + " · " + esc(q.ign) + " · " + esc(q.status) + (q.hold ? " · HOLD" : "") + "</div>"; }).join("") : "<p>No registrations yet.</p>"; }); });
    });
  }, 800);
})();
