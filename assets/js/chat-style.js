/* Messages page skin (the one and only chat style, every look): glossy bubbles
   with tails, grouping, pop-in, frosted bars, honest "Sent" marker. Smooth mode
   toggle lives in the Messages header (shared xr_fx preference). */
(function () {
  "use strict";
  if ((location.pathname.split("/").pop() || "") !== "messages.html") return;
  var l = document.createElement("link");
  l.rel = "stylesheet"; l.href = "assets/css/imsg-bubbles.css";
  document.head.appendChild(l);
  document.body.classList.add("imsg-on");
  try { if (localStorage.getItem("xr_fx") === "calm") document.body.classList.add("imsg-calm"); } catch (e) {}

  /* bubble grouping + Sent marker */
  function group(box) {
    if (!document.body.classList.contains("imsg-on")) return;
    var bubs = box.querySelectorAll(".dm-bub");
    var prev = null;
    bubs.forEach(function (b) {
      var me = b.classList.contains("me");
      b.classList.remove("grp", "grpof", "tail");
      if (prev && prev._me === me) { b.classList.add("grp"); prev.classList.add("grpof"); }
      prev = b; b._me = me;
    });
    if (prev) prev.classList.add("tail");
    var lastMe = null;
    bubs.forEach(function (b) { if (b.classList.contains("me")) lastMe = b; });
    box.querySelectorAll(".imsg-status").forEach(function (s) { s.remove(); });
    if (lastMe) {
      var s = document.createElement("div");
      s.className = "imsg-status"; s.textContent = "Sent";
      lastMe.parentNode.insertBefore(s, lastMe.nextSibling);
    }
  }
  function arm() {
    var box = document.getElementById("dm-msgs");
    if (!box || box._csWatch) return;
    box._csWatch = 1;
    group(box);
    new MutationObserver(function () { group(box); }).observe(box, { childList: true });
  }

  /* Smooth mode toggle in the Messages header */
  function mount() {
    var head = document.querySelector(".dm-apphead");
    if (!head || head.querySelector(".cs-fx")) return false;
    var b = document.createElement("button");
    b.type = "button"; b.className = "cs-fx";
    function paint() { b.textContent = document.body.classList.contains("imsg-calm") ? "Smooth: on" : "Smooth: off"; b.classList.toggle("on", document.body.classList.contains("imsg-calm")); }
    b.addEventListener("click", function () {
      document.body.classList.toggle("imsg-calm");
      try { localStorage.setItem("xr_fx", document.body.classList.contains("imsg-calm") ? "calm" : "full"); } catch (err) {}
      paint();
    });
    var note = head.querySelector(".dm-lock-note");
    head.insertBefore(b, note || null);
    paint();
    return true;
  }

  /* full-screen: hide the list header while a conversation is open */
  var dmApp = document.getElementById("dm-app");
  function syncConv() { document.body.classList.toggle("dm-inconv", !!document.querySelector(".dm-conv")); }
  if (dmApp) { new MutationObserver(syncConv).observe(dmApp, { childList: true }); syncConv(); }

  var tries = 0;
  var iv = setInterval(function () {
    if (mount()) { clearInterval(iv); }
    else if (++tries > 40) clearInterval(iv);
  }, 300);
  document.addEventListener("click", function () { setTimeout(arm, 600); });
})();
