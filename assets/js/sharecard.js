/* XARENA share cards - generates branded 1080x1350 images client-side (own assets, no server).
   draw({kicker, title, lines, sub}) -> canvas; download(canvas, name); share(canvas, title, text) */
(function () {
  const W = 1080, H = 1350;

  function drawShield(c, cx, cy, s) {
    // same shield mark as the site icon, drawn with canvas paths
    const p = new Path2D('M20 2 36 10v14c0 8-7 12.7-16 14C11 36.7 4 32 4 24V10Z');
    c.save(); c.translate(cx - s / 2, cy - s / 2); c.scale(s / 40, s / 40);
    const g = c.createLinearGradient(0, 0, 40, 40);
    g.addColorStop(0, '#ff2d55'); g.addColorStop(1, '#ff8a3d');
    c.fillStyle = g; c.fill(p);
    c.restore();
    c.save(); c.translate(cx - s / 2, cy - s / 2); c.scale(s / 40, s / 40);
    c.strokeStyle = '#fff'; c.lineWidth = 4; c.lineCap = 'round';
    c.beginPath(); c.moveTo(13, 13); c.lineTo(27, 27); c.moveTo(27, 13); c.lineTo(13, 27); c.stroke();
    c.restore();
  }

  function draw(o) {
    const cv = document.createElement('canvas'); cv.width = W; cv.height = H;
    const c = cv.getContext('2d');
    // bg
    const bg = c.createLinearGradient(0, 0, 0, H);
    bg.addColorStop(0, '#0d0208'); bg.addColorStop(0.55, '#05010c'); bg.addColorStop(1, '#0d0208');
    c.fillStyle = bg; c.fillRect(0, 0, W, H);
    // glow
    let g = c.createRadialGradient(W / 2, 380, 40, W / 2, 380, 620);
    g.addColorStop(0, 'rgba(255,45,85,.34)'); g.addColorStop(1, 'rgba(255,45,85,0)');
    c.fillStyle = g; c.fillRect(0, 0, W, H);
    g = c.createRadialGradient(W / 2, H - 200, 30, W / 2, H - 200, 560);
    g.addColorStop(0, 'rgba(0,217,255,.14)'); g.addColorStop(1, 'rgba(0,217,255,0)');
    c.fillStyle = g; c.fillRect(0, 0, W, H);
    // border
    c.strokeStyle = 'rgba(255,255,255,.16)'; c.lineWidth = 3;
    c.strokeRect(28, 28, W - 56, H - 56);
    // brand
    drawShield(c, W / 2, 210, 190);
    c.textAlign = 'center';
    c.fillStyle = '#fff'; c.font = '800 64px Rajdhani, Orbitron, Arial, sans-serif';
    c.fillText('XARENA ESPORTS', W / 2, 350);
    c.fillStyle = 'rgba(255,255,255,.55)'; c.font = '600 30px Rajdhani, Arial, sans-serif';
    c.fillText('FREE FIRE TOURNAMENTS - INDIA', W / 2, 396);
    // kicker
    if (o.kicker) {
      c.fillStyle = '#ff2d55'; c.font = '800 40px Rajdhani, Arial, sans-serif';
      c.fillText(String(o.kicker).toUpperCase(), W / 2, 520);
    }
    // title
    c.fillStyle = '#fff'; c.font = '800 ' + (o.titleSize || 88) + 'px Rajdhani, Arial, sans-serif';
    const title = String(o.title || '');
    if (title.length > 22) c.font = '800 64px Rajdhani, Arial, sans-serif';
    c.fillText(title.toUpperCase(), W / 2, o.kicker ? 610 : 560);
    // stat lines
    c.font = '600 44px Rajdhani, Arial, sans-serif';
    let y = (o.kicker ? 610 : 560) + 96;
    (o.lines || []).forEach(function (ln) {
      c.fillStyle = 'rgba(255,255,255,.82)';
      c.fillText(String(ln), W / 2, y); y += 64;
    });
    // sub
    if (o.sub) {
      c.fillStyle = 'rgba(255,255,255,.5)'; c.font = '500 32px Rajdhani, Arial, sans-serif';
      c.fillText(String(o.sub), W / 2, y + 20);
    }
    // optional QR
    if (o.qrText) {
      var qy = H - 470;
      if (drawQr(c, String(o.qrText), W / 2, qy, 250)) {
        if (o.qrCaption) {
          c.fillStyle = 'rgba(255,255,255,.65)'; c.font = '600 30px Rajdhani, Arial, sans-serif';
          c.fillText(String(o.qrCaption), W / 2, qy + 250 / 2 + 52);
        }
      }
    }
    // footer link
    c.fillStyle = '#00d9ff'; c.font = '700 40px Rajdhani, Arial, sans-serif';
    c.fillText('xarena.surge.sh', W / 2, H - 120);
    c.fillStyle = 'rgba(255,255,255,.4)'; c.font = '500 26px Rajdhani, Arial, sans-serif';
    c.fillText('Join free - founded & owned by Shivhari Rajput', W / 2, H - 72);
    return cv;
  }

  function drawQr(c, text, cx, cy, size) {
    if (typeof qrcode !== "function") return false;
    try {
      var q = qrcode(0, "M"); q.addData(text); q.make();
      var n = q.getModuleCount(), quiet = 4, cell = size / (n + quiet * 2);
      c.save();
      c.fillStyle = "#ffffff";
      c.beginPath();
      if (c.roundRect) c.roundRect(cx - size / 2 - 14, cy - size / 2 - 14, size + 28, size + 28, 18); else c.rect(cx - size / 2 - 14, cy - size / 2 - 14, size + 28, size + 28);
      c.fill();
      c.fillStyle = "#ffffff"; c.fillRect(cx - size / 2, cy - size / 2, size, size);
      c.fillStyle = "#0b0614";
      for (var r = 0; r < n; r++) for (var cc = 0; cc < n; cc++) if (q.isDark(cc, r)) c.fillRect(cx - size / 2 + (cc + quiet) * cell, cy - size / 2 + (r + quiet) * cell, Math.ceil(cell), Math.ceil(cell));
      c.restore();
      return true;
    } catch (e) { return false; }
  }

  function download(cv, name) {
    cv.toBlob(function (b) {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(b);
      a.download = name || 'xarena-card.png';
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(function () { URL.revokeObjectURL(a.href); }, 4000);
    }, 'image/png');
  }

  async function share(cv, title, text) {
    const blob = await new Promise(function (r) { cv.toBlob(r, 'image/png'); });
    const file = new File([blob], 'xarena-card.png', { type: 'image/png' });
    if (navigator.canShare && navigator.canShare({ files: [file] })) {
      try { await navigator.share({ files: [file], title: title, text: text }); return true; } catch (e) { return false; }
    }
    return false;
  }

  function qrCanvas(text, size) {
    var cv = document.createElement("canvas"); cv.width = size; cv.height = size;
    drawQr(cv.getContext("2d"), text, size / 2, size / 2, size - 6);
    return cv;
  }

  window.xshare = { draw: draw, download: download, share: share, qrCanvas: qrCanvas };
})();
