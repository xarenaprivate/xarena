/* XARENA Clip Highlights - real approved clips from the community feed.
   No auto-detection claims, no fake stats: caption, uploader, date and the
   video itself are the only things shown, because those are real. */
(function () {
  "use strict";
  var app = document.getElementById("clips-app");
  if (!app) return;

  var SVG = {
    spark: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2l1.9 5.7L19.5 9l-5.6 1.3L12 16l-1.9-5.7L4.5 9l5.6-1.3L12 2z"/><path d="M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8L19 15z"/></svg>',
    share: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 16V4m0 0L7 9m5-5l5 5"/><path d="M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/></svg>',
    down: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4v12m0 0l5-5m-5 5l-5-5"/><path d="M4 19h16"/></svg>',
    play: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M8 5.5v13l11-6.5z"/></svg>'
  };

  function esc(t) { return String(t == null ? "" : t).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function fmtDate(ts) {
    var d = new Date(ts);
    if (isNaN(d)) return "";
    return d.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
  }
  function fmtDur(s) {
    if (!isFinite(s) || s <= 0) return "";
    var m = Math.floor(s / 60), r = Math.round(s % 60);
    return m + ":" + (r < 10 ? "0" : "") + r;
  }
  function toast(msg) {
    var t = document.createElement("div");
    t.className = "xclip-toast";
    t.textContent = msg;
    document.body.appendChild(t);
    requestAnimationFrame(function () { t.classList.add("on"); });
    setTimeout(function () { t.classList.remove("on"); setTimeout(function () { t.remove(); }, 300); }, 2200);
  }

  function mediaUrl(path) {
    if (!path) return Promise.resolve(null);
    if (path.indexOf("https://res.cloudinary.com/") === 0) {
      return Promise.resolve(path.replace("/upload/", "/upload/q_auto/"));
    }
    return fetch(xrApi.url + "/storage/v1/object/feed-media/" + path, { headers: { apikey: xrApi.key } })
      .then(function (r) { return r.ok ? r.blob() : null; })
      .then(function (b) { return b ? URL.createObjectURL(b) : null; })
      .catch(function () { return null; });
  }

  function emptyState() {
    app.innerHTML =
      '<div class="xclip-empty">' +
        '<div class="xclip-empty-ic">' + SVG.spark + "</div>" +
        "<h3>No clips published yet</h3>" +
        "<p>Upload your best moment on the Feed - once it is reviewed and approved, it appears here for everyone.</p>" +
        '<a class="xclip-btn gold" href="feed.html">Open the Feed</a>' +
      "</div>";
  }

  function clipCard(c, uploader) {
    var card = document.createElement("article");
    card.className = "xclip-card";
    card.id = "clip-" + c.id;
    var title = (c.caption && c.caption.trim()) || "Community clip";
    var byline = (uploader ? uploader : "XARENA player") + " · " + fmtDate(c.created_at);
    card.innerHTML =
      '<div class="xclip-media"><p class="xclip-loading">Loading clip…</p></div>' +
      '<div class="xclip-body">' +
        '<h3 class="xclip-title">' + esc(title) + "</h3>" +
        '<p class="xclip-meta">' + esc(byline) + "</p>" +
        '<div class="xclip-btns">' +
          '<button type="button" class="xclip-btn gold" data-share>' + SVG.share + "<span>Share Clip</span></button>" +
          '<button type="button" class="xclip-btn ghost" data-download disabled>' + SVG.down + "<span>Download</span></button>" +
        "</div>" +
      "</div>";

    var mediaBox = card.querySelector(".xclip-media");
    var dlBtn = card.querySelector("[data-download]");
    mediaUrl(c.media_path).then(function (url) {
      if (!url) { mediaBox.innerHTML = '<p class="xclip-loading">Clip media unavailable.</p>'; return; }
      mediaBox.innerHTML = "";
      var v = document.createElement("video");
      v.controls = true; v.preload = "metadata"; v.setAttribute("playsinline", "");
      v.src = url;
      mediaBox.appendChild(v);
      v.addEventListener("loadedmetadata", function () {
        var d = fmtDur(v.duration);
        if (d) {
          var chip = document.createElement("span");
          chip.className = "xclip-chip";
          chip.textContent = d;
          mediaBox.appendChild(chip);
        }
      });
      dlBtn.disabled = false;
      dlBtn.addEventListener("click", function () {
        var a = document.createElement("a");
        a.href = url;
        a.download = "xarena-clip-" + c.id + ".mp4";
        document.body.appendChild(a); a.click(); a.remove();
        toast("Download started.");
      });
    });

    card.querySelector("[data-share]").addEventListener("click", function () {
      var link = location.origin + location.pathname + "#clip-" + c.id;
      if (navigator.share) {
        navigator.share({ title: "XARENA clip", text: title, url: link }).catch(function () {});
      } else if (navigator.clipboard) {
        navigator.clipboard.writeText(link).then(function () { toast("Clip link copied."); }, function () { toast(link); });
      } else { toast(link); }
    });
    return card;
  }

  app.innerHTML = '<p class="xclip-loading" style="text-align:center;padding:30px 0">Loading clips…</p>';
  if (!window.xrApi) { app.innerHTML = '<p class="xclip-loading">Service unavailable.</p>'; return; }

  xrApi.get("feed_posts", "?kind=eq.clip&status=eq.approved&select=id,user_id,caption,media_path,created_at&order=created_at.desc&limit=30")
    .then(function (rows) {
      if (!rows || !rows.length) { emptyState(); return; }
      var ids = rows.map(function (r) { return r.user_id; }).filter(Boolean);
      var names = {};
      var done = function () {
        app.innerHTML = "";
        rows.forEach(function (c) {
          var prof = names[c.user_id];
          var label = prof ? (prof.ign || prof.username || "XARENA player") : "XARENA player";
          app.appendChild(clipCard(c, label));
        });
      };
      if (!ids.length) { done(); return; }
      xrApi.get("profiles", "?id=in.(" + ids.join(",") + ")&select=id,username,ign")
        .then(function (ps) {
          (ps || []).forEach(function (p) { names[p.id] = p; });
          done();
        })
        .catch(done);
    })
    .catch(function () {
      app.innerHTML = '<div class="xclip-empty"><h3>Could not load clips</h3><p>Check your connection and try again.</p></div>';
    });
})();
