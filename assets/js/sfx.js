/* XARENA sound + haptics. Sounds are synthesized with WebAudio (zero download weight).
   Toggles live in localStorage (xr_sfx / xr_haptics, default ON) and in Account settings. */
(function () {
  "use strict";
  var ctx = null;
  function sfxOn() { try { return localStorage.getItem("xr_sfx") !== "off"; } catch (e) { return true; } }
  function hapOn() { try { return localStorage.getItem("xr_haptics") !== "off"; } catch (e) { return true; } }
  function ac() {
    if (!ctx) {
      var AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return null;
      try { ctx = new AC(); } catch (e) { return null; }
    }
    if (ctx.state === "suspended") ctx.resume().catch(function () {});
    return ctx;
  }
  function tone(freq, dur, type, gain, when, slide) {
    var c = ac(); if (!c) return;
    try {
      var o = c.createOscillator(), g = c.createGain();
      o.type = type || "square";
      o.frequency.setValueAtTime(freq, c.currentTime + (when || 0));
      if (slide) o.frequency.exponentialRampToValueAtTime(slide, c.currentTime + (when || 0) + dur);
      g.gain.setValueAtTime(gain || 0.05, c.currentTime + (when || 0));
      g.gain.exponentialRampToValueAtTime(0.0001, c.currentTime + (when || 0) + dur);
      o.connect(g); g.connect(c.destination);
      o.start(c.currentTime + (when || 0));
      o.stop(c.currentTime + (when || 0) + dur + 0.02);
    } catch (e) {}
  }
  var SOUNDS = {
    click:   function () { tone(880, 0.05, "square", 0.025); },
    open:    function () { tone(520, 0.07, "triangle", 0.04, 0, 760); },
    success: function () { tone(660, 0.09, "triangle", 0.05); tone(990, 0.12, "triangle", 0.05, 0.09); },
    error:   function () { tone(220, 0.15, "sawtooth", 0.05, 0, 140); },
    coin:    function () { tone(1240, 0.06, "square", 0.04); tone(1560, 0.1, "square", 0.04, 0.06); },
    spin:    function () { for (var i = 0; i < 8; i++) tone(600 + i * 90, 0.04, "square", 0.028, i * 0.09); },
    win:     function () { tone(523, 0.1, "triangle", 0.05); tone(659, 0.1, "triangle", 0.05, 0.1); tone(784, 0.1, "triangle", 0.05, 0.2); tone(1047, 0.22, "triangle", 0.06, 0.3); },
    levelup: function () { tone(440, 0.09, "square", 0.05); tone(554, 0.09, "square", 0.05, 0.09); tone(659, 0.09, "square", 0.05, 0.18); tone(880, 0.25, "square", 0.055, 0.27); },
    pop:     function () { tone(980, 0.05, "sine", 0.05, 0, 420); },
    whoosh:  function () { tone(180, 0.18, "sawtooth", 0.035, 0, 640); }
  };
  function play(name) {
    if (!sfxOn()) return;
    var f = SOUNDS[name]; if (f) f();
  }
  function buzz(pat) {
    if (!hapOn()) return;
    try { if (navigator.vibrate) navigator.vibrate(pat); } catch (e) {}
  }
  function setSfx(on) { try { localStorage.setItem("xr_sfx", on ? "on" : "off"); } catch (e) {} }
  function setHaptics(on) { try { localStorage.setItem("xr_haptics", on ? "on" : "off"); } catch (e) {} }
  window.xrSfx = { play: play, buzz: buzz, sfxOn: sfxOn, hapOn: hapOn, setSfx: setSfx, setHaptics: setHaptics };
  /* subtle global click blip on buttons and nav (delegated, light) */
  document.addEventListener("click", function (e) {
    if (!sfxOn()) return;
    var t = e.target.closest(".btn, .nav-links a, .xr-look-opt, button[type=submit]");
    if (t) { play("click"); buzz(8); }
  }, { passive: true });
})();
