/* Share / Invite: Web Share API with a WhatsApp link fallback. No tracking, no personal data, no rewards. */
(function () {
  function go(btn) {
    var url = btn.getAttribute("data-url") || location.href.split("#")[0];
    var text = btn.getAttribute("data-text") || "Join XARENA ESPORTS - free Free Fire tournaments.";
    var title = btn.getAttribute("data-title") || "XARENA ESPORTS";
    if (navigator.share) {
      navigator.share({ title: title, text: text, url: url }).catch(function () {});
      return;
    }
    window.open("https://wa.me/?text=" + encodeURIComponent(text + " " + url), "_blank", "noopener,noreferrer");
  }
  document.addEventListener("click", function (e) {
    var b = e.target.closest && e.target.closest("[data-xr-share]");
    if (b) { e.preventDefault(); go(b); }
  });
})();
