/* XARENA player profile - black/gold mockup layout. REAL data only (player_profile_v2); empty states are honest. */
(function () {
  "use strict";
  var KEY = "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7", URLB = "https://oskccuajgbyabogbvedj.supabase.co";
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function fmt(n) { return (n || 0).toLocaleString("en-IN"); }
  function tierOf(xp) { xp = xp || 0; return xp >= 5000 ? "GRANDMASTER" : xp >= 2500 ? "HEROIC" : xp >= 1200 ? "DIAMOND" : xp >= 600 ? "PLATINUM" : xp >= 250 ? "GOLD" : xp >= 80 ? "SILVER" : "BRONZE"; }
  function ago(iso) {
    var s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
    if (s < 90) return "just now"; if (s < 3600) return Math.round(s / 60) + "m ago";
    if (s < 86400) return Math.round(s / 3600) + "h ago"; return Math.round(s / 86400) + "d ago";
  }
  var CSS = ".pm{background:#0a0a0e;border:1px solid rgba(245,179,1,.28);border-radius:18px;padding:16px;color:#f3efe3;font-family:Rajdhani,system-ui,sans-serif}" +
    ".pm *{box-sizing:border-box}.pm-top{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}" +
    ".pm-ic{width:36px;height:36px;border-radius:10px;display:inline-flex;align-items:center;justify-content:center;color:#f5b301;background:rgba(245,179,1,.07);border:1px solid rgba(245,179,1,.3);text-decoration:none;cursor:pointer}" +
    ".pm-logo{font:900 22px Orbitron,system-ui,sans-serif;letter-spacing:3px;color:#f5b301}" +
    ".pm-hero{display:flex;gap:16px;align-items:center}.pm-ava{width:96px;height:96px;flex:none;border-radius:50%;border:3px solid #f5b301;box-shadow:0 0 18px rgba(245,179,1,.28);display:flex;align-items:center;justify-content:center;font:900 38px Orbitron,sans-serif;color:#f5b301;background:#15120a;overflow:hidden}" +
    ".pm-ava img{width:100%;height:100%;object-fit:cover}.pm-name{font:700 27px Rajdhani,sans-serif;color:#fff;line-height:1.1;word-break:break-word}.pm-handle{color:#c9a24a;font-size:17px;margin-top:2px}" +
    ".pm-on{margin-top:10px;font-size:14px;color:#d9d2bd}.pm-dot{display:inline-block;width:9px;height:9px;border-radius:50%;background:#35d07f;margin-right:6px;vertical-align:1px}.pm-dot.off{background:#6b6858}" +
    ".pm-act{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:18px 0}.pm-btn{border-radius:10px;padding:13px 8px;text-align:center;font:700 17px Rajdhani,sans-serif;cursor:pointer;text-decoration:none;display:block;border:1px solid #f5b301}" +
    ".pm-btn.gold{background:linear-gradient(180deg,#f9cf5d,#e5a812);color:#1a1405}.pm-btn.line{background:#0e0d0a;color:#fff}.pm-btn:disabled{opacity:.6}" +
    ".pm-card{background:linear-gradient(180deg,#14120c,#0d0c09);border:1px solid rgba(245,179,1,.35);border-radius:14px;padding:16px;margin-bottom:20px}" +
    ".pm-lv{display:flex;justify-content:space-between;align-items:center;gap:8px;font-size:14px;letter-spacing:.5px}.pm-lv b{color:#f5b301;font-size:15px}.pm-lv span{color:#bdb59c}" +
    ".pm-bar{height:9px;border-radius:99px;background:#26231a;margin:12px 0 8px;overflow:hidden}.pm-fill{height:100%;background:linear-gradient(90deg,#b87d06,#ffe08a);border-radius:99px;box-shadow:0 0 10px rgba(245,179,1,.5)}.pm-sub{font-size:13px;color:#9d957d}" +
    ".pm-st{display:grid;grid-template-columns:repeat(3,1fr);margin-top:14px;padding-top:14px;border-top:1px solid rgba(245,179,1,.15);text-align:center}.pm-st>div+div{border-left:1px solid rgba(245,179,1,.15)}" +
    ".pm-st .l{font-size:13px;color:#fff;font-weight:700;letter-spacing:.5px}.pm-st .v{font:900 30px Orbitron,sans-serif;color:#f5b301;margin:4px 0 2px}.pm-st .s{font-size:12px;color:#8fbf8f;min-height:15px}.pm-st .s.n{color:#9d957d}" +
    ".pm-h{display:flex;align-items:center;justify-content:space-between;margin:6px 0 12px}.pm-h h3{font:800 19px Rajdhani,sans-serif;letter-spacing:1px;color:#fff;margin:0}.pm-h a,.pm-h button{background:none;border:0;color:#f5b301;font:700 14px Rajdhani,sans-serif;letter-spacing:.5px;cursor:pointer;text-decoration:none}" +
    ".pm-badges{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-bottom:22px}.pm-b{background:#0e0d0a;border:1px solid rgba(245,179,1,.35);border-radius:12px;padding:10px 4px;text-align:center}.pm-b svg{width:34px;height:34px;stroke:#f5b301;fill:none;stroke-width:1.6;stroke-linecap:round;stroke-linejoin:round}" +
    ".pm-b .n{font-size:11.5px;font-weight:700;color:#f5b301;margin-top:6px;text-transform:uppercase;line-height:1.1}.pm-b .s{font-size:10.5px;color:#9d957d;margin-top:3px}" +
    ".pm-clips{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.pm-c{background:#0e0d0a;border:1px solid rgba(245,179,1,.2);border-radius:10px;overflow:hidden;text-decoration:none;color:#fff;display:block}.pm-c .t{aspect-ratio:16/10;background:#15120a center/cover no-repeat;position:relative}" +
    ".pm-c .t i{position:absolute;inset:0;margin:auto;width:34px;height:34px;border-radius:50%;border:2px solid #f5b301;background:rgba(0,0,0,.45)}.pm-c .t i:after{content:'';position:absolute;left:12px;top:8px;border-left:11px solid #f5b301;border-top:7px solid transparent;border-bottom:7px solid transparent}" +
    ".pm-c .cap{font-size:11.5px;font-weight:700;padding:6px;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}" +
    ".pm-empty{border:1px dashed rgba(245,179,1,.35);border-radius:12px;padding:16px;text-align:center;color:#a79f86;font-size:13.5px;margin-bottom:22px}";
  var IC = {
    cup: '<svg viewBox="0 0 24 24"><path d="M7 3h10v5a5 5 0 0 1-10 0z"/><path d="M7 4H4a3 3 0 0 0 3 5M17 4h3a3 3 0 0 1-3 5"/><path d="M12 13v4"/><path d="M8.5 21h7M10 17h4v4h-4z"/></svg>',
    shield: '<svg viewBox="0 0 24 24"><path d="M12 2l8 3v6c0 5-3.5 9-8 11-4.5-2-8-6-8-11V5z"/><path d="M9 12l2 2 4-4"/></svg>',
    medal: '<svg viewBox="0 0 24 24"><circle cx="12" cy="14" r="6"/><path d="M8 8.5L5 2h5l2 4 2-4h5l-3 6.5"/></svg>',
    flame: '<svg viewBox="0 0 24 24"><path d="M12 2c1 4-4 6-4 11a4.5 4.5 0 0 0 9 0c0-2-1-3.5-2-4.5.5 2-1 3-2 2.5C14.5 8 13 5 12 2z"/></svg>',
    back: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#f5b301" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7"/></svg>',
    share: '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#f5b301" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 15V4"/><path d="M7 8l5-5 5 5"/><path d="M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7"/></svg>'
  };
  function bIcon(n) { n = String(n || "").toLowerCase(); if (/champ|mvp|win|cup|booyah/.test(n)) return IC.cup; if (/streak|fire|clutch/.test(n)) return IC.flame; if (/team|captain|shield|surviv/.test(n)) return IC.shield; return IC.medal; }
  function thumb(p) {
    var m = p.media_path || "";
    if (m.indexOf("https://res.cloudinary.com/") === 0) {
      if (p.kind === "photo") return m.replace("/upload/", "/upload/w_400,h_250,c_fill,q_auto,f_auto/");
      return m.replace("/upload/", "/upload/so_0,w_400,h_250,c_fill,q_auto,f_jpg/").replace(/\.(mp4|mov|webm)(\?.*)?$/i, ".jpg");
    }
    return "";
  }
  function inject() { if (!document.getElementById("pm-css")) { var s = document.createElement("style"); s.id = "pm-css"; s.textContent = CSS; document.head.appendChild(s); } }

  window.xrPM = function (box, uname) {
    inject();
    box.innerHTML = '<div class="pm"><p class="pm-sub" style="text-align:center;padding:30px 0">Loading profile…</p></div>';
    return fetch(URLB + "/rest/v1/rpc/player_profile_v2", { method: "POST", headers: { apikey: KEY, "Content-Type": "application/json" }, body: JSON.stringify({ p_username: uname }) })
      .then(function (r) { return r.json(); }).then(function (d) {
        if (!d || !d.ok) { box.innerHTML = '<div class="pm"><div class="pm-empty">Player not found.</div><a class="pm-btn line" href="players.html">All players</a></div>'; return; }
        var tier = tierOf(d.xp), pct = d.needed > 0 ? Math.min(100, Math.round(d.into_level / d.needed * 100)) : 0;
        var ava = d.avatar ? '<img src="' + esc(d.avatar) + '" alt="">' : esc((d.ign || d.username || "X").charAt(0).toUpperCase());
        var pres = "";
        if (d.presence) pres = d.presence.active ? '<span class="pm-dot"></span>Online now' : '<span class="pm-dot off"></span>Last seen ' + ago(d.presence.last_seen);
        var items = (d.awards || []).map(function (a) { return { n: a.title || a.kind, s: a.points ? "+" + a.points + " pts" : a.kind }; })
          .concat((d.badges || []).map(function (b) { return { n: b, s: "XARENA badge" }; }));
        var bhtml = items.length ? '<div class="pm-badges" id="pm-badges">' + items.map(function (b, i) { return '<div class="pm-b"' + (i > 3 ? ' data-more="1" style="display:none"' : "") + ">" + bIcon(b.n) + '<div class="n">' + esc(b.n) + '</div><div class="s">' + esc(b.s) + "</div></div>"; }).join("") + "</div>"
          : '<div class="pm-empty">No badges yet. Badges are earned from real matches, streaks and awards.</div>';
        var hl = d.highlights || [];
        var hhtml = hl.length ? '<div class="pm-clips">' + hl.slice(0, 6).map(function (p) {
          var t = thumb(p); return '<a class="pm-c" href="feed.html"><div class="t" style="' + (t ? "background-image:url('" + esc(t) + "')" : "") + '">' + (p.kind === "photo" ? "" : "<i></i>") + '</div><div class="cap">' + esc(p.caption || (p.kind === "photo" ? "Photo" : "Clip")) + "</div></a>"; }).join("") + "</div>"
          : '<div class="pm-empty">No highlights yet. Approved clips from the Highlights feed show up here.</div>';
        var kd = d.kd != null && d.matches > 0 ? Number(d.kd).toFixed(2) : (d.kd != null ? Number(d.kd).toFixed(2) : "-");
        box.innerHTML = '<div class="pm">' +
          '<div class="pm-top"><a class="pm-ic" href="players.html" aria-label="Back to players">' + IC.back + '</a><div class="pm-logo">XARENA</div><button class="pm-ic" id="pm-share" aria-label="Share profile">' + IC.share + "</button></div>" +
          '<div class="pm-hero"><div class="pm-ava">' + ava + '</div><div style="min-width:0"><div class="pm-name">' + esc(d.ign || d.username) + (d.verified ? ' <span style="color:#f5b301;font-size:18px">✓</span>' : "") + '</div><div class="pm-handle">@' + esc(d.username) + "</div>" +
          (pres ? '<div class="pm-on">' + pres + "</div>" : "") + (d.role || d.ff_rank ? '<div class="pm-on" style="margin-top:4px;color:#bdb59c">' + esc([d.role, d.ff_rank].filter(Boolean).join(" · ")) + "</div>" : "") + "</div></div>" +
          '<div class="pm-act" id="pm-act"><span></span><span></span></div>' +
          '<div class="pm-card"><div class="pm-lv"><b>LEVEL ' + d.level + " · " + tier + "</b><span>" + fmt(d.into_level) + " / " + fmt(d.needed) + " EXP</span></div>" +
          '<div class="pm-bar"><div class="pm-fill" style="width:' + pct + '%"></div></div><div class="pm-sub">' + fmt(Math.max(0, d.needed - d.into_level)) + " EXP until LEVEL " + (d.level + 1) + " · " + fmt(d.xp) + " EXP total</div>" +
          '<div class="pm-st"><div><div class="l">WINS</div><div class="v">' + fmt(d.wins) + '</div><div class="s n">of ' + fmt(d.matches) + " matches</div></div>" +
          '<div><div class="l">K/D</div><div class="v">' + kd + '</div><div class="s n">' + (d.kd != null ? "profile stat" : "not set yet") + "</div></div>" +
          '<div><div class="l">CHECK-INS</div><div class="v">' + fmt(d.checkins) + '</div><div class="s n">' + (d.week_xp > 0 ? "+" + fmt(d.week_xp) + " EXP this week" : "no EXP this week") + "</div></div></div></div>" +
          '<div class="pm-h"><h3>BADGES</h3>' + (items.length > 4 ? '<button id="pm-more">SEE ALL (' + items.length + ") →</button>" : "") + "</div>" + bhtml +
          '<div class="pm-h"><h3>HIGHLIGHTS</h3>' + (hl.length ? '<a href="feed.html">' + hl.length + " CLIP" + (hl.length > 1 ? "S" : "") + " · SEE ALL →</a>" : "") + "</div>" + hhtml + "</div>";
        document.getElementById("pm-share").addEventListener("click", function () {
          var link = location.origin + location.pathname.replace(/[^/]*$/, "") + "player.html?u=" + encodeURIComponent(d.username);
          (navigator.clipboard ? navigator.clipboard.writeText(link) : Promise.reject()).then(function () { alert("Profile link copied: " + link); }).catch(function () { prompt("Copy the profile link:", link); });
        });
        var more = document.getElementById("pm-more");
        if (more) more.addEventListener("click", function () { document.querySelectorAll("#pm-badges [data-more]").forEach(function (e) { e.style.display = ""; }); more.remove(); });
        wireActions(d);
      }).catch(function () { box.innerHTML = '<div class="pm"><div class="pm-empty">Could not load this profile. Check your connection and try again.</div></div>'; });
  };

  function wireActions(d) {
    var act = document.getElementById("pm-act");
    if (!act || !window.xrApi) return;
    xrApi.user().then(function (me) {
      if (!me) { act.innerHTML = '<a class="pm-btn gold" href="account.html">Log in to add friend</a><a class="pm-btn line" href="account.html">Log in to message</a>'; return; }
      return xrApi.userToken().then(function (tk) {
        return fetch(URLB + "/rest/v1/profiles?id=eq." + me.id + "&select=username", { headers: { apikey: KEY, Authorization: "Bearer " + (tk || KEY) } });
      }).then(function (x) { return x.json(); }).then(function (rows) {
        var mine = rows && rows[0] && rows[0].username;
        if (mine && mine.toLowerCase() === String(d.username).toLowerCase()) { act.innerHTML = '<a class="pm-btn gold" href="profile.html">Edit profile</a><a class="pm-btn line" href="messages.html">My messages</a>'; return; }
        act.innerHTML = '<button class="pm-btn gold" id="pm-add">+ Add Friend</button><button class="pm-btn line" id="pm-msg">Message</button>';
        var ab = document.getElementById("pm-add"), mb = document.getElementById("pm-msg");
        ab.addEventListener("click", function () {
          ab.disabled = true;
          xrApi.uRpc("friend_request_by_name", { p_name: d.username }).then(function (r) {
            if (r && r.ok) { ab.textContent = (r.already || r.accepted) ? "Friends ✓" : "Request sent ✓"; }
            else { ab.disabled = false; alert((r && r.error) || "Could not send the request."); }
          });
        });
        var rb = document.createElement("button"); rb.className = "pm-btn line"; rb.textContent = "Report"; rb.style.opacity = ".8"; act.appendChild(rb);
        rb.addEventListener("click", function () {
          var reason = (prompt("Report " + d.username + ". Type one reason: cheating, abuse, spam, fake_account, other", "cheating") || "").trim().toLowerCase();
          if (!reason) return;
          var det = prompt("Add short details (optional)", "") || "";
          xrApi.uRpc("report_player", { p_username: d.username, p_reason: reason, p_details: det }).then(function (r) { alert(r && r.ok ? "Report sent. Admins will review it." : ((r && r.error) || "Could not send the report.")); });
        });
        mb.addEventListener("click", function () {
          mb.disabled = true;
          xrApi.uRpc("dm_open_by_name", { p_name: d.username }).then(function (r) {
            mb.disabled = false;
            if (r && r.ok) { if (!r.requested) location.href = "messages.html"; else alert("Message request sent - they can accept it in Messages > Requests."); }
            else alert((r && r.error) || "Could not open the chat.");
          });
        });
      });
    }).catch(function () {});
  }
})();
