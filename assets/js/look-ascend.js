/* ASCEND look: live ops clock, like a monitoring HUD. Real time only. */
(function () {
  if (document.body.classList.contains("os-embed")) return;
  var d = document.createElement("div");
  d.className = "asc-clock";
  function tick() {
    var n = new Date();
    var p = function (x) { return String(x).padStart(2, "0"); };
    d.innerHTML = p(n.getHours()) + ":" + p(n.getMinutes()) + ":" + p(n.getSeconds()) + "<small>SYSTEM TIME</small>";
  }
  tick(); setInterval(tick, 1000);
  document.body.appendChild(d);
})();
