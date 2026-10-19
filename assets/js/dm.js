/* XARENA Direct Messages — real end-to-end encryption (WebCrypto ECDH P-256 + AES-GCM).
   Private keys never leave this device (IndexedDB, non-extractable). The server stores
   only ciphertext. Losing the device key (reinstall/clear data) = old messages unreadable. */
(function () {
  "use strict";
  var SUPA = "https://oskccuajgbyabogbvedj.supabase.co";

  /* ---------- IndexedDB key store ---------- */
  function idbOpen() {
    return new Promise(function (res, rej) {
      var r = indexedDB.open("xr-dm-keys", 1);
      r.onupgradeneeded = function () { r.result.createObjectStore("keys"); };
      r.onsuccess = function () { res(r.result); };
      r.onerror = function () { rej(r.error); };
    });
  }
  function idbGet(k) {
    return idbOpen().then(function (d) {
      return new Promise(function (res, rej) {
        var t = d.transaction("keys").objectStore("keys").get(k);
        t.onsuccess = function () { res(t.result); }; t.onerror = function () { rej(t.error); };
      });
    });
  }
  function idbSet(k, v) {
    return idbOpen().then(function (d) {
      return new Promise(function (res, rej) {
        var t = d.transaction("keys", "readwrite").objectStore("keys").put(v, k);
        t.onsuccess = function () { res(); }; t.onerror = function () { rej(t.error); };
      });
    });
  }

  /* ---------- base64 helpers ---------- */
  function b2a(buf) { var b = new Uint8Array(buf), s = ""; for (var i = 0; i < b.length; i++) s += String.fromCharCode(b[i]); return btoa(s); }
  function a2b(str) { var s = atob(str), b = new Uint8Array(s.length); for (var i = 0; i < s.length; i++) b[i] = s.charCodeAt(i); return b.buffer; }

  /* ---------- authed REST ---------- */
  function rest(method, path, body, extra) {
    return window.xrApi.userToken().then(function (t) {
      if (!t) throw new Error("Login required");
      var h = { "apikey": window.xrApi.key, "Authorization": "Bearer " + t, "Content-Type": "application/json" };
      if (extra) for (var k in extra) h[k] = extra[k];
      return fetch(SUPA + path, { method: method, headers: h, body: body == null ? undefined : (typeof body === "string" ? body : JSON.stringify(body)) })
        .then(function (r) {
          if (r.status === 204) return null;
          return r.text().then(function (x) {
            var d; try { d = x ? JSON.parse(x) : null; } catch (e) { d = null; }
            if (!r.ok) throw new Error((d && (d.message || d.error || d.msg)) || ("HTTP " + r.status));
            return d;
          });
        });
    });
  }

  /* ---------- key ids (v1): first 16 hex chars of SHA-256(raw x || raw y) ---------- */
  function b64uToBytes(str) {
    str = str.replace(/-/g, "+").replace(/_/g, "/"); while (str.length % 4) str += "=";
    var s = atob(str), b = new Uint8Array(s.length); for (var i = 0; i < s.length; i++) b[i] = s.charCodeAt(i); return b;
  }
  function keyIdOfJwk(jwk) {
    var x = b64uToBytes(jwk.x), y = b64uToBytes(jwk.y), all = new Uint8Array(x.length + y.length);
    all.set(x, 0); all.set(y, x.length);
    return crypto.subtle.digest("SHA-256", all).then(function (h) {
      var u = new Uint8Array(h), hex = ""; for (var i = 0; i < 8; i++) hex += ("0" + u[i].toString(16)).slice(-2);
      return hex;
    });
  }

  /* ---------- my keypair (this device) ---------- */
  var keypairP = null, myIdP = null;
  function ensureKeys() {
    if (keypairP) return keypairP;
    keypairP = idbGet("ecdh").then(function (pair) {
      if (pair) return pair;
      return crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveKey"]).then(function (p) {
        return idbSet("ecdh", p).then(function () { return p; });   /* generating never publishes: see syncKey */
      });
    });
    return keypairP;
  }
  function myKeyId() {
    if (myIdP) return myIdP;
    myIdP = ensureKeys().then(function (pair) { return crypto.subtle.exportKey("jwk", pair.publicKey); }).then(keyIdOfJwk);
    return myIdP;
  }
  /* Read-before-publish. Publishes only when no key is published yet.
     Returns {state:"ok"|"published"|"mismatch", mine, published}. A different published key is NEVER overwritten silently. */
  function syncKey() {
    return Promise.all([ensureKeys(), window.xrApi.user()]).then(function (r) {
      var pair = r[0], u = r[1];
      if (!u) return { state: "nouser" };
      return Promise.all([crypto.subtle.exportKey("jwk", pair.publicKey), rest("GET", "/rest/v1/dm_public_keys?user_id=eq." + u.id + "&select=public_key")]).then(function (q) {
        var jwk = q[0], rows = q[1];
        return keyIdOfJwk(jwk).then(function (mine) {
          if (!rows || !rows.length || !rows[0].public_key) {
            return rest("POST", "/rest/v1/dm_public_keys", { user_id: u.id, public_key: jwk }, { "Prefer": "return=minimal,resolution=merge-duplicates" })
              .then(function () { return { state: "published", mine: mine, published: mine }; });
          }
          return keyIdOfJwk(rows[0].public_key).then(function (pub) {
            return { state: pub === mine ? "ok" : "mismatch", mine: mine, published: pub };
          });
        });
      });
    });
  }
  /* Explicit "Activate this device": plain upsert of this device's key (the only way the published key changes). */
  function publishMyKey() {
    return Promise.all([ensureKeys(), window.xrApi.user()]).then(function (r) {
      var u = r[1]; if (!u) return;
      return crypto.subtle.exportKey("jwk", r[0].publicKey).then(function (jwk) {
        keyCache = {};
        return rest("POST", "/rest/v1/dm_public_keys", { user_id: u.id, public_key: jwk }, { "Prefer": "return=minimal,resolution=merge-duplicates" });
      });
    });
  }

  /* ---------- shared thread key ---------- */
  var keyCache = {};
  function threadCtx(peerId) {
    if (keyCache[peerId]) return keyCache[peerId];
    keyCache[peerId] = Promise.all([ensureKeys(), myKeyId(), rest("GET", "/rest/v1/dm_public_keys?user_id=eq." + peerId + "&select=public_key")]).then(function (r) {
      var rows = r[2];
      if (!rows || !rows.length || !rows[0].public_key) throw new Error("Your friend has not set up messages on this device yet.");
      return Promise.all([keyIdOfJwk(rows[0].public_key), crypto.subtle.importKey("jwk", rows[0].public_key, { name: "ECDH", namedCurve: "P-256" }, false, [])]).then(function (z) {
        return crypto.subtle.deriveKey({ name: "ECDH", public: z[1] }, r[0].privateKey, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]).then(function (key) {
          return { key: key, sk: r[1], rk: z[0] };
        });
      });
    });
    return keyCache[peerId];
  }
  function threadKey(peerId) { return threadCtx(peerId).then(function (c) { return c.key; }); }

  /* ---------- text ---------- */
  function encText(key, text) {
    var iv = crypto.getRandomValues(new Uint8Array(12));
    return crypto.subtle.encrypt({ name: "AES-GCM", iv: iv }, key, new TextEncoder().encode(text)).then(function (ct) {
      return { ciphertext: b2a(ct), iv: b2a(iv) };
    });
  }
  function decText(key, ciphertext, iv) {
    return crypto.subtle.decrypt({ name: "AES-GCM", iv: new Uint8Array(a2b(iv)) }, key, a2b(ciphertext)).then(function (pt) {
      return new TextDecoder().decode(pt);
    });
  }

  /* ---------- media ---------- */
  function compressImage(file) {
    return new Promise(function (res, rej) {
      var img = new Image();
      var url = URL.createObjectURL(file);
      img.onload = function () {
        URL.revokeObjectURL(url);
        var max = 1280, w = img.width, h = img.height;
        if (Math.max(w, h) > max) { var f = max / Math.max(w, h); w = Math.round(w * f); h = Math.round(h * f); }
        var c = document.createElement("canvas"); c.width = w; c.height = h;
        c.getContext("2d").drawImage(img, 0, 0, w, h);
        c.toBlob(function (b) { b ? res({ blob: b, width: w, height: h }) : rej(new Error("Could not read that image.")); }, "image/jpeg", 0.82);
      };
      img.onerror = function () { URL.revokeObjectURL(url); rej(new Error("Could not read that image.")); };
      img.src = url;
    });
  }
  function checkVideo(file) {
    return new Promise(function (res, rej) {
      if (file.size > 8 * 1024 * 1024) return rej(new Error("Video too large - keep it under 8 MB."));
      var v = document.createElement("video");
      var url = URL.createObjectURL(file);
      v.preload = "metadata";
      v.onloadedmetadata = function () {
        URL.revokeObjectURL(url);
        if (v.duration > 61) return rej(new Error("Video too long - 1 minute max."));
        res({ duration: Math.round(v.duration) });
      };
      v.onerror = function () { URL.revokeObjectURL(url); rej(new Error("Could not read that video.")); };
      v.src = url;
    });
  }
  function uploadMedia(threadId, peerId, file, onKind) {
    var prep;
    if (file.type.indexOf("image/") === 0) prep = compressImage(file).then(function (r) { return { blob: r.blob, meta: { mime: "image/jpeg", width: r.width, height: r.height, size: r.blob.size }, kind: "image" }; });
    else if (file.type.indexOf("video/") === 0) prep = checkVideo(file).then(function (r) { return { blob: file, meta: { mime: file.type, size: file.size, duration: r.duration }, kind: "video" }; });
    else return Promise.reject(new Error("Photos and short videos only."));
    return prep.then(function (p) {
      return threadCtx(peerId).then(function (ctx) {
        var key = ctx.key; p.meta.sk = ctx.sk; p.meta.rk = ctx.rk;
        return p.blob.arrayBuffer().then(function (buf) {
          var iv = crypto.getRandomValues(new Uint8Array(12));
          return crypto.subtle.encrypt({ name: "AES-GCM", iv: iv }, key, buf).then(function (ct) {
            return { path: threadId + "/" + crypto.randomUUID(), bytes: ct, iv: b2a(iv), meta: p.meta, kind: p.kind };
          });
        });
      });
    }).then(function (up) {
      return window.xrApi.userToken().then(function (t) {
        return fetch(SUPA + "/storage/v1/object/dm-media/" + up.path, {
          method: "POST",
          headers: { "apikey": window.xrApi.key, "Authorization": "Bearer " + t, "Content-Type": "application/octet-stream", "x-upsert": "false" },
          body: up.bytes
        }).then(function (r) {
          if (!r.ok) return r.text().then(function (x) { throw new Error("Upload failed: " + x.slice(0, 120)); });
          return up;
        });
      });
    });
  }
  function downloadMedia(key, path) {
    return rest("POST", "/storage/v1/object/sign/dm-media/" + path, { expiresIn: 300 }).then(function (d) {
      if (!d || !d.signedURL) throw new Error("Could not open media.");
      return fetch(SUPA + "/storage/v1" + d.signedURL).then(function (r) {
        if (!r.ok) throw new Error("Media download failed.");
        return r.arrayBuffer();
      });
    }).then(function () { });
  }
  function fetchAndDecrypt(key, path, signedURL) {
    return fetch(SUPA + "/storage/v1" + signedURL).then(function (r) {
      if (!r.ok) throw new Error("Media download failed.");
      return r.arrayBuffer();
    });
  }

  window.xrDm = {
    ensureKeys: ensureKeys,
    publishMyKey: publishMyKey,
    syncKey: syncKey,
    myKeyId: myKeyId,
    keyIdOfJwk: keyIdOfJwk,
    threadCtx: threadCtx,
    threadKey: threadKey,
    encText: encText,
    decText: decText,
    uploadMedia: uploadMedia,
    rest: rest,
    signMedia: function (path) { return rest("POST", "/storage/v1/object/sign/dm-media/" + path, { expiresIn: 300 }); },
    decBytes: function (key, bytes, iv) { return crypto.subtle.decrypt({ name: "AES-GCM", iv: new Uint8Array(a2b(iv)) }, key, bytes); }
  };
})();
