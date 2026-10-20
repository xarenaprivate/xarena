/* XARENA ULTIMATE 3D - real WebGL scenes (Three.js) + interaction layer.
   Loaded ONLY when the 3D look is selected. All geometry is procedural
   (no external 3D model assets exist for this brand - trophy is lathe-built). */
(function () {
  "use strict";
  var REDUCED = (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches) || (function () { try { return localStorage.getItem("xr_lite") === "1"; } catch (e) { return false; } })();
  var MOBILE = window.innerWidth < 700;

  function webglOK() {
    try { var c = document.createElement("canvas"); return !!(c.getContext("webgl") || c.getContext("experimental-webgl")); }
    catch (e) { return false; }
  }

  /* ---- procedural 3D trophy (lathe geometry - genuinely 3D) ---- */
  function makeTrophy() {
    var pts = [];
    var profile = [ // r, y - cup silhouette
      [0.0, 0.0], [0.42, 0.0], [0.46, 0.06], [0.30, 0.12], [0.16, 0.22],
      [0.15, 0.34], [0.30, 0.46], [0.52, 0.62], [0.62, 0.86], [0.60, 1.06],
      [0.50, 1.16], [0.52, 1.20], [0.60, 1.24], [0.62, 1.28]
    ];
    profile.forEach(function (p) { pts.push(new THREE.Vector2(p[0], p[1])); });
    var geo = new THREE.LatheGeometry(pts, 40);
    var mat = new THREE.MeshPhongMaterial({ color: 0xf0b429, specular: 0xfff2c0, shininess: 90, emissive: 0x241500 });
    return new THREE.Mesh(geo, mat);
  }

  function makeEmbers(count) {
    var geo = new THREE.BufferGeometry();
    var pos = new Float32Array(count * 3);
    var seed = new Float32Array(count);
    for (var i = 0; i < count; i++) {
      pos[i * 3] = (Math.random() - 0.5) * 10;
      pos[i * 3 + 1] = Math.random() * 6 - 1;
      pos[i * 3 + 2] = (Math.random() - 0.5) * 6;
      seed[i] = Math.random() * 100;
    }
    geo.setAttribute("position", new THREE.BufferAttribute(pos, 3));
    geo.userData.seed = seed;
    var mat = new THREE.PointsMaterial({ color: 0xff5a3c, size: 0.045, transparent: true, opacity: 0.85, blending: THREE.AdditiveBlending, depthWrite: false });
    return new THREE.Points(geo, mat);
  }

  function mountScene(el, opts) {
    var scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(0x050103, 0.16);
    var w = el.clientWidth, h = el.clientHeight;
    var cam = new THREE.PerspectiveCamera(50, w / h, 0.1, 60);
    cam.position.set(0, 1.15, 4.6);
    var ren = new THREE.WebGLRenderer({ antialias: !MOBILE, alpha: true, powerPreference: "low-power" });
    ren.setPixelRatio(Math.min(window.devicePixelRatio || 1, MOBILE ? 1.2 : 1.6));
    ren.setSize(w, h);
    ren.shadowMap.enabled = !MOBILE;
    el.appendChild(ren.domElement);

    scene.add(new THREE.AmbientLight(0x40304a, 1.7));
    var key = new THREE.PointLight(0xffe9b0, 1.8, 22); key.position.set((opts.trophyX || 0) + 1.5, 2.8, 5.5); scene.add(key);
    var red = new THREE.PointLight(0xff1e3c, 2.2, 18); red.position.set(-3.4, 2.4, 2.4); scene.add(red);
    var pur = new THREE.PointLight(0xa020f0, 2.4, 18); pur.position.set(3.4, 1.6, 2.2); scene.add(pur);
    var gold = new THREE.PointLight(0xf5b301, 1.6, 14); gold.position.set(0, 3.4, -1.5); scene.add(gold);

    var trophy = makeTrophy();
    trophy.position.set(opts.trophyX || 1.6, -0.9, 0);
    trophy.rotation.z = opts.tilt || 0;
    scene.add(trophy);

    // pedestal ring
    var ring = new THREE.Mesh(
      new THREE.TorusGeometry(1.15, 0.02, 12, 80),
      new THREE.MeshBasicMaterial({ color: 0xa020f0, transparent: true, opacity: 0.7 }));
    ring.rotation.x = Math.PI / 2;
    ring.position.set(trophy.position.x, -0.9, 0);
    scene.add(ring);

    var embers = makeEmbers(MOBILE ? 120 : 260);
    scene.add(embers);

    var mx = 0, my = 0;
    if (!REDUCED) {
      window.addEventListener("pointermove", function (e) {
        mx = (e.clientX / window.innerWidth - 0.5) * 2;
        my = (e.clientY / window.innerHeight - 0.5) * 2;
      }, { passive: true });
    }

    var t0 = performance.now();
    var running = true;
    document.addEventListener("visibilitychange", function () { running = !document.hidden; });
    (function loop() {
      requestAnimationFrame(loop);
      if (!running) return;
      var t = (performance.now() - t0) / 1000;
      if (!REDUCED) {
        trophy.rotation.y = t * 0.5;
        ring.rotation.z = t * 0.35;
        var p = embers.geometry.attributes.position.array;
        var seed = embers.geometry.userData.seed;
        for (var i = 0; i < seed.length; i++) {
          p[i * 3 + 1] += 0.008 + (seed[i] % 7) * 0.0012;
          p[i * 3] += Math.sin(t * 1.6 + seed[i]) * 0.0016;
          if (p[i * 3 + 1] > 5.2) p[i * 3 + 1] = -1;
        }
        embers.geometry.attributes.position.needsUpdate = true;
        cam.position.x += ((mx * 0.55) - cam.position.x) * 0.045;
        cam.position.y += ((1.15 - my * 0.35) - cam.position.y) * 0.045;
        cam.lookAt(trophy.position.x * 0.45, 0.3, 0);
        red.intensity = 2.0 + Math.sin(t * 2.2) * 0.35;
        pur.intensity = 2.2 + Math.cos(t * 1.7) * 0.35;
      }
      ren.render(scene, cam);
    })();

    window.addEventListener("resize", function () {
      var w2 = el.clientWidth, h2 = el.clientHeight;
      cam.aspect = w2 / h2; cam.updateProjectionMatrix(); ren.setSize(w2, h2);
    });
  }

  function init() {
    // real WebGL scenes
    if (!REDUCED && webglOK() && window.THREE) {
      var hero = document.getElementById("hero3d");
      if (hero) { try { mountScene(hero, { trophyX: MOBILE ? 0.9 : 2.3, tilt: 0 }); } catch (e) { hero.remove(); } }
      var tr = document.getElementById("trophy3d");
      if (tr) { try { mountScene(tr, { trophyX: 0, tilt: 0 }); } catch (e) { tr.remove(); } }
    } else {
      var h2 = document.getElementById("hero3d"); if (h2) h2.remove();
    }

    // reveal staggering: the site's own observer (app.js) adds .in; we only stagger the delay.
    function tagReveals() {
      var i = 0;
      document.querySelectorAll(".card,.news-item,.lb-row,.stat,.tcard,.scrim,.section-head,.form-panel").forEach(function (n) {
        if (n.dataset.xrDelay) return;
        n.dataset.xrDelay = "1";
        n.style.transitionDelay = (i++ % 4) * 70 + "ms";
      });
    }
    tagReveals();
    setTimeout(tagReveals, 1200);
    setTimeout(tagReveals, 3000);
    setTimeout(tagReveals, 6000);

    // perspective tilt on cards (fine pointers only)
    if (!MOBILE && !REDUCED && window.matchMedia("(pointer: fine)").matches) {
      document.addEventListener("pointermove", function (e) {
        var card = e.target.closest && e.target.closest(".card");
        if (!card) return;
        var r = card.getBoundingClientRect();
        var rx = ((e.clientY - r.top) / r.height - 0.5) * -7;
        var ry = ((e.clientX - r.left) / r.width - 0.5) * 9;
        card.style.transform = "perspective(700px) rotateX(" + rx + "deg) rotateY(" + ry + "deg) translateZ(4px)";
      }, { passive: true });
      document.addEventListener("pointerout", function (e) {
        var card = e.target.closest && e.target.closest(".card");
        if (card) card.style.transform = "";
      }, { passive: true });
    }

    // animated counters
    document.querySelectorAll("[data-count]").forEach(function (n) {
      var target = parseInt(n.getAttribute("data-count"), 10) || 0;
      var io2 = new IntersectionObserver(function (ents) {
        ents.forEach(function (en) {
          if (!en.isIntersecting) return;
          io2.unobserve(n);
          var t0 = performance.now(), dur = REDUCED ? 1 : 1300;
          (function tick() {
            var k = Math.min(1, (performance.now() - t0) / dur);
            n.textContent = Math.round(target * (1 - Math.pow(1 - k, 3)));
            if (k < 1) requestAnimationFrame(tick);
          })();
        });
      }, { threshold: 0.4 });
      io2.observe(n);
    });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
