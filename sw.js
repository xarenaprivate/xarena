/* XARENA PWA service worker - static shell cache only; API/DB calls always live */
var CACHE = "xarena-shell-v30";
var SHELL = [
  "./", "./index.html", "./manifest.webmanifest",
  "./assets/icon-192.png", "./assets/icon-512.png", "./assets/apple-touch-icon.png"
];
self.addEventListener("install", function (e) {
  e.waitUntil(caches.open(CACHE).then(function (c) { return c.addAll(SHELL); }).then(function () { return self.skipWaiting(); }));
});
self.addEventListener("activate", function (e) {
  e.waitUntil(caches.keys().then(function (keys) {
    return Promise.all(keys.map(function (k) { if (k !== CACHE) return caches.delete(k); }));
  }).then(function () { return self.clients.claim(); }));
});
self.addEventListener("fetch", function (e) {
  var url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== location.origin) return; // live: supabase, luna fn
  // network-first for everything same-origin (updates always reach users); cache = offline fallback
  if (/\.(js|css|png|webp|jpg|svg|woff2?|webmanifest)$/.test(url.pathname)) {
    e.respondWith(fetch(e.request).then(function (res) {
      var copy = res.clone();
      caches.open(CACHE).then(function (c) { c.put(e.request, copy); });
      return res;
    }).catch(function () { return caches.match(e.request); }));
  } else {
    // network-first for pages
    e.respondWith(fetch(e.request).then(function (res) {
      var copy = res.clone();
      caches.open(CACHE).then(function (c) { c.put(e.request, copy); });
      return res;
    }).catch(function () { return caches.match(e.request); }));
  }
});
