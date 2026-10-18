/* XARENA fun lines - playful one-shot AI lines from Luna (roast, win caption, Bhojpuri caster).
   Calls the same luna-chat Edge Function in "funline" mode. Free tier, rate-limited server-side. */
(function () {
  "use strict";
  var LUNA_URL = "https://oskccuajgbyabogbvedj.supabase.co/functions/v1/luna-chat";
  function key() { return (window.xrApi && xrApi.key) || ""; }
  function ask(flavor, subject) {
    return fetch(LUNA_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json", apikey: key(), Authorization: "Bearer " + key() },
      body: JSON.stringify({ session_id: "web-" + (localStorage.getItem("xr_fun_sid") || (localStorage.setItem("xr_fun_sid", Math.random().toString(36).slice(2, 12)), localStorage.getItem("xr_fun_sid"))), message: "go", mode: "funline", flavor: flavor, subject: subject })
    }).then(function (r) {
      return r.json().then(function (d) {
        if (r.status === 429) throw new Error((d && d.error) || "Wait a few seconds, then try again.");
        if (!r.ok || !d || !d.reply) throw new Error((d && d.error) || "Luna is busy - try again in a bit.");
        return d.reply;
      });
    });
  }
  /* speaks a line with the best local Hindi voice the phone has (free, on-device).
     There is no real Bhojpuri voice on phones - this uses the closest Hindi voice and we say so. */
  function speak(text, onEnd) {
    try {
      if (!("speechSynthesis" in window)) { onEnd && onEnd("This phone has no voice engine - read the line instead."); return false; }
      speechSynthesis.cancel();
      var u = new SpeechSynthesisUtterance(text);
      var vs = speechSynthesis.getVoices() || [];
      var hi = null;
      for (var i = 0; i < vs.length; i++) { if (/^hi([-_]|$)/i.test(vs[i].lang)) { hi = vs[i]; break; } }
      if (hi) { u.voice = hi; u.lang = hi.lang; } else { u.lang = "hi-IN"; }
      u.rate = 1.05; u.pitch = 1.1;
      u.onend = function () { onEnd && onEnd(null); };
      u.onerror = function () { onEnd && onEnd("Voice playback failed on this phone."); };
      speechSynthesis.speak(u);
      return true;
    } catch (e) { onEnd && onEnd("Voice playback failed on this phone."); return false; }
  }
  /* wires a button: on click, fetch a line into an output span; optional Listen button appears after. */
  function wire(btn, flavor, subject, out, opts) {
    opts = opts || {};
    btn.addEventListener("click", function () {
      btn.disabled = true;
      var old = btn.textContent;
      btn.textContent = "Thinking…";
      out.textContent = "";
      ask(flavor, subject).then(function (line) {
        btn.disabled = false; btn.textContent = old;
        out.textContent = "";
        var span = document.createElement("span");
        span.textContent = "“" + line + "” ";
        out.appendChild(span);
        var cp = document.createElement("button");
        cp.className = "btn btn-ghost btn-sm"; cp.textContent = "Copy"; cp.style.marginLeft = "4px";
        cp.addEventListener("click", function () {
          var t = line;
          if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(t).then(function () { cp.textContent = "Copied"; setTimeout(function(){cp.textContent="Copy";},1200); });
          else { var ta = document.createElement("textarea"); ta.value = t; document.body.appendChild(ta); ta.select(); try { document.execCommand("copy"); cp.textContent = "Copied"; setTimeout(function(){cp.textContent="Copy";},1200); } catch (e) {} ta.remove(); }
        });
        out.appendChild(cp);
        if (opts.voice) {
          var lb = document.createElement("button");
          lb.className = "btn btn-ghost btn-sm"; lb.textContent = "🔊 Listen"; lb.style.marginLeft = "4px";
          lb.title = "Reads in the closest Hindi voice this phone has (no real Bhojpuri voice exists on phones yet)";
          lb.addEventListener("click", function () {
            lb.disabled = true;
            speak(line, function (err) { lb.disabled = false; if (err && window.xrToast) xrToast(err); });
          });
          out.appendChild(lb);
          var note = document.createElement("div");
          note.style.cssText = "font-size:11.5px;color:var(--muted);margin-top:4px";
          note.textContent = "Voice: closest Hindi voice on your phone - a true Bhojpuri voice is not available on phones yet.";
          out.appendChild(note);
        }
      }).catch(function (e) {
        btn.disabled = false; btn.textContent = old;
        out.textContent = (e && e.message) || "Try again in a bit.";
      });
    });
  }
  window.xrFunline = { ask: ask, speak: speak, wire: wire };
})();
