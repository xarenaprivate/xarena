/* XARENA ESPORTS — Owner Panel (admin-only; server RLS enforces access) */
(function () {
  "use strict";
  var api = window.xrApi;
  function el(s, r) { return (r || document).querySelector(s); }
  function els(s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); }
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }

  /* ---------- auth ---------- */
  function call(fn, retried) {
    return fn().catch(function (e) {
      if (e && e.status === 401 && !retried) {
        return api.refresh().then(function () { return call(fn, true); });
      }
      throw e;
    });
  }
  function showPanel() {
    el("#login-view").style.display = "none";
    el("#panel-view").style.display = "block";
    el("#logout-btn").style.display = "";
    var s = api.session();
    el("#admin-email-badge").textContent = (s && s.email) || "OWNER";
    buildTabs();
    openSection("overview");
  }
  function boot() {
    if (!api.session()) return;
    call(function () { return api.isAdmin(); }).then(function (ok) {
      if (ok) showPanel(); else api.signOut();
    }).catch(function () { });
  }
  el("#login-form").addEventListener("submit", function (ev) {
    ev.preventDefault();
    var email = el("#a-email").value.trim(), pass = el("#a-pass").value;
    var err = el("#login-err");
    err.style.display = "none";
    if (!email || !pass) { err.textContent = "Enter email and password."; err.style.display = "block"; return; }
    var btn = el("button[type=submit]", this);
    btn.disabled = true; btn.textContent = "CHECKING...";
    api.signIn(email, pass).then(function () {
      return api.isAdmin();
    }).then(function (ok) {
      btn.disabled = false; btn.textContent = "ENTER PANEL";
      if (ok) { showPanel(); }
      else {
        api.signOut();
        err.textContent = "This account is not the XARENA owner.";
        err.style.display = "block";
      }
    }).catch(function (e) {
      btn.disabled = false; btn.textContent = "ENTER PANEL";
      err.textContent = (e && e.message) ? ("Login failed: " + e.message) : "Login failed.";
      err.style.display = "block";
    });
  });
  el("#logout-btn").addEventListener("click", function () { api.signOut(); location.reload(); });

  /* ---------- sections ---------- */
  var STATUS_T = [["upcoming", "Upcoming"], ["live", "Live"], ["done", "Completed"]];
  var SECTIONS = {
    overview: { title: "Overview", custom: renderOverview },
    phoneverify: { title: "Phone Verify", custom: renderPhoneVerify },
    awards: { title: "Awards", custom: renderAwards },
    squads: { title: "Squads", custom: renderSquads },
    tasks: { title: "Tasks", custom: renderTasks },
    aiguard: { title: "AI Guard", custom: renderAIGuard },
    polls: { title: "Polls", custom: renderPolls },
    dmreports: { title: "DM Reports", custom: renderDMReports },
    feedqueue: { title: "Feed Queue", custom: renderFeedQueue },
    banners: { title: "Banners", custom: renderBanners },
    referrals: { title: "Referrals", custom: renderReferrals },
    proofs: { title: "Proofs", custom: renderProofs },
    cheats: { title: "Cheat Reports", custom: renderCheats },
    brackets: { title: "Brackets", custom: renderBrackets },
    wars: { title: "Squad Wars", custom: renderWars },
    livestream: { title: "Live Stream", custom: renderLivestream },
    playerreports: { title: "Player Reports", custom: renderPlayerReports },
    suspicious: { title: "Suspicious Accounts", custom: renderSuspicious },
    creators: { title: "Creators", custom: renderCreators },
    supportqr: { title: "Support QR", custom: renderSupportQr },
    pinned: { title: "Pinned notice", custom: renderPinned },
    tips: { title: "Aaj ka tip", custom: renderTips },
    control: { title: "Control", custom: renderControl },
    spotlights: { title: "Spotlights", custom: renderSpot },
    notices: { title: "Notice board", custom: renderNotices },
    feedback: { title: "Feedback", custom: renderFeedback },
    thread: { title: "Match thread", custom: renderThread },
    helpers: { title: "Helpers", custom: renderHelpers },
    sponsors: {
      title: "Sponsors", table: "sponsors", order: "id.asc",
      cols: [
        { k: "name", l: "Sponsor / brand name", t: "text", req: true },
        { k: "tier", l: "Tier", t: "select", opts: ["title", "gold", "partner"] },
        { k: "url", l: "Link (optional, https://...)", t: "text" },
        { k: "active", l: "Visible on the Sponsors page", t: "checkbox" },
        { k: "image_url", l: "Poster / banner image (upload, max 5 MB)", t: "image" },
        { k: "image_approved", l: "Approved - show this image publicly on the Sponsor Wall", t: "checkbox", noDefault: true }
      ],
      rowTitle: function (r) { return r.name; },
      rowSub: function (r) { return (r.tier || "Partner") + " · " + (r.active ? "visible" : "hidden") + (r.image_url ? (r.image_approved ? " · image approved" : " · image PENDING approval") : ""); }
    },
    sponsorapps: { title: "Sponsor Requests", custom: renderSponsorApps },
    chatmod: { title: "Chat & Fans", custom: renderChatMod },
    walkovers: { title: "Walkover Claims", custom: renderWalkovers },
    idverify: { title: "Photo Verify", custom: renderIdVerify },
    fame: {
      title: "Hall of Fame", table: "hall_of_fame", order: "sort.asc,id.asc",
      cols: [
        { k: "season", l: "Season (e.g. Season 1)", t: "text", req: true },
        { k: "category", l: "Category (Champion / MVP / Top Fragger)", t: "text", req: true },
        { k: "name", l: "Squad or player name", t: "text", req: true },
        { k: "detail", l: "Detail (score, prize, note)", t: "text" },
        { k: "logo_url", l: "Logo image link (https, optional)", t: "text" },
        { k: "sort", l: "Sort order", t: "number" }
      ],
      rowTitle: function (r) { return r.name; },
      rowSub: function (r) { return r.season + " · " + r.category + (r.detail ? " · " + r.detail : ""); }
    },
    rewards: { title: "Rewards", custom: renderRewards },
    quizzes: { title: "Quizzes", custom: renderQuizzes },
    tournaments: {
      title: "Tournaments", table: "tournaments", order: "sort.asc,created_at.asc",
      cols: [
        { k: "id", l: "ID (short, e.g. xr-s2)", t: "text", createOnly: true, req: true },
        { k: "name", l: "Name", t: "text", req: true },
        { k: "mode", l: "Mode", t: "text" },
        { k: "status", l: "Status", t: "select", opts: STATUS_T },
        { k: "date_text", l: "Date text (shown publicly)", t: "text" },
        { k: "start_at", l: "Start date/time (optional)", t: "datetime" },
        { k: "prize", l: "Prize pool text", t: "text" },
        { k: "slots", l: "Squad slots", t: "number" },
        { k: "reg_open", l: "Registration open", t: "checkbox" },
        { k: "entry_cost_ap", l: "Entry cost in AP (empty or 0 = free)", t: "number" },
        { k: "format", l: "Format", t: "textarea" },
        { k: "description", l: "Description", t: "textarea" },
        { k: "sort", l: "Sort order", t: "number" }
      ],
      rowTitle: function (r) { return r.name; },
      rowSub: function (r) { return r.status + " · " + (r.date_text || "") + " · " + (r.prize || ""); }
    },
    news: {
      title: "News & Announcements", table: "news", order: "published_at.desc",
      cols: [
        { k: "tag", l: "Tag", t: "select", opts: ["ANNOUNCEMENT", "RECRUITMENT", "RESULT", "SCHEDULE", "GENERAL"] },
        { k: "title", l: "Title", t: "text", req: true },
        { k: "body", l: "Body", t: "textarea", req: true },
        { k: "published", l: "Published (visible)", t: "checkbox" },
        { k: "published_at", l: "Publish date", t: "datetime" }
      ],
      rowTitle: function (r) { return r.title; },
      rowSub: function (r) { return r.tag + " · " + (r.published ? "live" : "hidden") + " · " + String(r.published_at || "").slice(0, 10); }
    },
    players: {
      title: "Players", table: "players", order: "created_at.asc",
      cols: [
        { k: "ign", l: "In-game name", t: "text", req: true },
        { k: "name", l: "Real name", t: "text" },
        { k: "role", l: "Role", t: "select", opts: ["IGL", "Rusher", "Sniper", "Support", "Grenadier"] },
        { k: "ff_uid", l: "Free Fire UID", t: "text" },
        { k: "kd", l: "K/D", t: "number" },
        { k: "team_id", l: "Team", t: "teamselect" },
        { k: "bio", l: "Bio", t: "textarea" },
        { k: "active", l: "Active (shown publicly)", t: "checkbox" }
      ],
      rowTitle: function (r) { return r.ign; },
      rowSub: function (r) { return (r.role || "") + (r.kd != null ? " · K/D " + r.kd : ""); }
    },
    teams: {
      title: "Teams", table: "teams", order: "created_at.asc",
      cols: [
        { k: "name", l: "Team name", t: "text", req: true },
        { k: "tag", l: "Tag (e.g. XR)", t: "text" },
        { k: "bio", l: "About", t: "textarea" }
      ],
      rowTitle: function (r) { return r.name; },
      rowSub: function (r) { return r.tag ? "[" + r.tag + "]" : ""; }
    },
    registrations: { title: "Registrations", custom: renderRegistrations },
    tryouts: {
      title: "Tryouts", table: "tryouts", order: "created_at.desc", inbox: true,
      cols: [{ k: "status", l: "Status", t: "select", opts: ["pending", "shortlisted", "rejected"] }],
      rowTitle: function (r) { return r.ign + "  ·  " + r.id; },
      rowSub: function (r) { return (r.role || "-") + " · UID " + r.uid + " · " + r.phone + (r.kd ? " · K/D " + r.kd : "") + " · " + r.status; }
    },
    messages: {
      title: "Messages", table: "messages", order: "created_at.desc", inbox: true,
      cols: [{ k: "status", l: "Status", t: "select", opts: ["unread", "read"] }],
      rowTitle: function (r) { return r.name + "  ·  " + r.id; },
      rowSub: function (r) { return r.contact + " · " + r.message.slice(0, 90) + " · " + r.status; }
    },
    chat: { title: "World Chat", custom: renderChat },
    results: { title: "Match Results", custom: renderResults },
    scrims: {
      title: "Scrims", table: "scrims", order: "created_at.asc",
      cols: [
        { k: "title", l: "Title", t: "text", req: true },
        { k: "day_text", l: "Day (e.g. SAT)", t: "text", req: true },
        { k: "time_text", l: "Time (e.g. 8 PM)", t: "text", req: true },
        { k: "note", l: "Note", t: "textarea" },
        { k: "active", l: "Active (visible)", t: "checkbox" }
      ],
      rowTitle: function (r) { return r.title; },
      rowSub: function (r) { return r.day_text + " " + r.time_text + (r.active ? "" : " · hidden"); }
    },
    agents: { title: "AI Agents", custom: renderAgents },
    approvals: { title: "Approvals", custom: renderApprovals },
    audit: { title: "Activity Log", custom: renderAudit }
  };

  /* ---------- registrations: verification queue ---------- */
  function proofUrl(path) {
    var s = api.session();
    return fetch(api.url + "/storage/v1/object/sign/proofs/" + path, {
      method: "POST",
      headers: { "apikey": api.key, "Authorization": "Bearer " + (s && s.access_token), "Content-Type": "application/json" },
      body: JSON.stringify({ expiresIn: 3600 })
    }).then(function (r) { return r.json(); }).then(function (d) {
      if (!d || !d.signedURL) throw new Error("sign failed");
      return api.url + "/storage/v1" + d.signedURL;
    });
  }
  function renderTasks() {
    var body = el("#admin-body");
    body.innerHTML = '<p style="color:var(--muted)">Loading…</p>';
    Promise.all([
      call(function () { return api.aGet("tasks", "?select=*&order=created_at.desc"); }).catch(function () { return []; }),
      call(function () { return api.aGet("task_completions", "?select=*&status=eq.pending&order=created_at.asc"); }).catch(function () { return []; }),
      call(function () { return api.aGet("profiles", "?select=id,username,ign"); }).catch(function () { return []; }),
      call(function () { return api.aGet("squads_public", "?select=id,name"); }).catch(function () { return []; })
    ]).then(function (res) {
      var tasks = res[0], pending = res[1], players = res[2], squads = res[3];
      var pname = {}; players.forEach(function (p) { pname[p.id] = p.ign ? p.username + " (" + p.ign + ")" : p.username; });
      var sname = {}; squads.forEach(function (s) { sname[s.id] = s.name; });
      var tname = {}; tasks.forEach(function (t) { tname[t.id] = t; });

      var html = '<div class="card" style="margin-bottom:16px"><h3>New task</h3>' +
        '<p style="font-size:13px">Solo tasks: each player submits their own. Team tasks: the squad captain submits once, every squad member gets the reward. Approving a submission grants the points and badge instantly.</p>' +
        '<div class="field"><label>Title</label><input id="tk-title" maxlength="60" placeholder="e.g. Win 3 scrims this week"></div>' +
        '<div class="field"><label>Description (optional)</label><textarea id="tk-desc" maxlength="400" rows="2" placeholder="What counts as done?"></textarea></div>' +
        '<div class="frow"><div class="field"><label>Kind</label><select id="tk-kind"><option value="solo">Solo</option><option value="team">Team</option></select></div>' +
        '<div class="field"><label>Points</label><input id="tk-points" type="number" min="0" max="1000" value="10"></div></div>' +
        '<div class="field"><label>Badge / title reward (optional)</label><input id="tk-badge" maxlength="40" placeholder="e.g. Grind Master"></div>' +
        '<button class="btn btn-gold btn-sm" id="tk-create">Create task</button><p id="tk-msg" style="font-size:13px;margin-top:6px"></p></div>';

      html += '<div class="card" style="margin-bottom:16px"><h3>Pending approvals (' + pending.length + ")</h3>" +
        (pending.length ? pending.map(function (c) {
          var t = tname[c.task_id] || {};
          return '<div class="news-item"><h3 style="font-size:15px">' + esc(t.title || "task #" + c.task_id) + ' <span class="task-kind ' + esc(t.kind || "solo") + '">' + esc(t.kind || "") + "</span></h3>" +
            '<p style="font-size:12.5px">by <b>' + esc(pname[c.user_id] || c.user_id) + "</b>" + (c.squad_id ? " · squad: " + esc(sname[c.squad_id] || "?") : "") +
            (c.note ? "<br>note: " + esc(c.note) : "") + " · " + new Date(c.created_at).toLocaleDateString() + "</p>" +
            '<div style="display:flex;gap:8px;margin-top:6px"><button class="btn btn-gold btn-sm" data-tk-approve="' + c.id + '">Approve (+' + (t.points || 0) + ' pts' + (t.badge ? " + badge" : "") + ')</button>' +
            '<button class="btn btn-ghost btn-sm" data-tk-reject="' + c.id + '" style="color:#ff6b6b">Reject</button></div></div>';
        }).join("") : '<p style="color:var(--muted);font-size:13px">Nothing waiting.</p>') + "</div>";

      html += '<div class="card"><h3>All tasks (' + tasks.length + ")</h3>" +
        (tasks.length ? tasks.map(function (t) {
          return '<div class="news-item" style="display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap"><span>' + esc(t.title) +
            ' <span class="task-kind ' + esc(t.kind) + '">' + esc(t.kind) + "</span> <b class='task-pts'>+" + t.points + "</b>" + (t.badge ? ' 🎖 ' + esc(t.badge) : "") +
            (t.active ? "" : ' <span style="color:var(--muted);font-size:11px">(hidden)</span>') + "</span>" +
            '<span style="display:flex;gap:8px"><button class="btn btn-ghost btn-sm" data-tk-toggle="' + t.id + '" data-active="' + (t.active ? "1" : "0") + '">' + (t.active ? "Hide" : "Show") + "</button>" +
            '<button class="btn btn-ghost btn-sm" data-tk-del="' + t.id + '" style="color:#ff6b6b">Delete</button></span></div>';
        }).join("") : '<p style="color:var(--muted);font-size:13px">No tasks yet. Create the first one above.</p>') + "</div>";
      body.innerHTML = html;

      el("#tk-create").addEventListener("click", function () {
        var msg = el("#tk-msg");
        var title = el("#tk-title").value.trim();
        if (title.length < 3) { msg.textContent = "Title needs at least 3 characters."; return; }
        this.disabled = true;
        call(function () { return api.rpc("admin_save_task", {
          p_id: null, p_title: title, p_description: el("#tk-desc").value.trim() || null,
          p_kind: el("#tk-kind").value, p_points: parseInt(el("#tk-points").value, 10) || 0,
          p_badge: el("#tk-badge").value.trim() || null, p_active: true
        }, true); }).then(function (r) {
          return api.audit("create_task", { title: title }).then(function () {
            if (r && r.ok === false) { msg.textContent = r.error || "Could not create."; el("#tk-create").disabled = false; }
            else renderTasks();
          });
        });
      });
      els("[data-tk-approve]", body).forEach(function (b) { b.addEventListener("click", function () { b.disabled = true;
        call(function () { return api.rpc("review_task", { p_completion_id: parseInt(b.dataset.tkApprove, 10), p_approve: true }, true); })
          .then(function (r) { return api.audit("approve_task", { id: b.dataset.tkApprove, rewarded: r && r.rewarded_players }); }).then(renderTasks); }); });
      els("[data-tk-reject]", body).forEach(function (b) { b.addEventListener("click", function () { b.disabled = true;
        call(function () { return api.rpc("review_task", { p_completion_id: parseInt(b.dataset.tkReject, 10), p_approve: false }, true); })
          .then(function () { return api.audit("reject_task", { id: b.dataset.tkReject }); }).then(renderTasks); }); });
      els("[data-tk-toggle]", body).forEach(function (b) { b.addEventListener("click", function () { b.disabled = true;
        call(function () { return api.aPatch("tasks", "id=eq." + b.dataset.tkToggle, { active: b.dataset.active !== "1" }); }).then(renderTasks); }); });
      els("[data-tk-del]", body).forEach(function (b) { b.addEventListener("click", function () {
        if (!confirm("Delete this task? Its submissions go too (granted rewards stay on player accounts).")) return; b.disabled = true;
        call(function () { return api.aDel("tasks", "id=eq." + b.dataset.tkDel); })
          .then(function () { return api.audit("delete_task", { id: b.dataset.tkDel }); }).then(renderTasks); }); });
    });
  }

  function renderSquads() {
    var body = el("#admin-body");
    body.innerHTML = '<p style="color:var(--muted)">Loading…</p>';
    Promise.all([
      call(function () { return api.aGet("squad_creator_requests", "?select=id,user_id,note,status,created_at,profiles(username,ign)&status=eq.pending&order=created_at.asc"); }).catch(function () { return []; }),
      call(function () { return api.aGet("squad_creators", "?select=user_id,created_at,profiles(username,ign)"); }).catch(function () { return []; }),
      call(function () { return api.aGet("squads_public", "?select=*"); }).catch(function () { return []; }),
      call(function () { return api.aGet("profiles", "?select=id,username,ign&order=created_at.desc"); }).catch(function () { return []; })
    ]).then(function (res) {
      var reqs = res[0], creators = res[1], squads = res[2], players = res[3];
      var html = '<div class="card" style="margin-bottom:16px"><h3>Creator requests (' + reqs.length + ")</h3>" +
        (reqs.length ? reqs.map(function (r) {
          var p = r.profiles || {};
          return '<div class="news-item"><h3 style="font-size:15px">' + esc(p.username || r.user_id) + (p.ign ? " (" + esc(p.ign) + ")" : "") + '</h3>' +
            '<p style="font-size:12.5px">' + esc(r.note || "(no note)") + " · " + new Date(r.created_at).toLocaleDateString() + "</p>" +
            '<div style="display:flex;gap:8px;margin-top:6px"><button class="btn btn-gold btn-sm" data-approve="' + r.id + '">Approve</button>' +
            '<button class="btn btn-ghost btn-sm" data-reject="' + r.id + '" style="color:#ff6b6b">Reject</button></div></div>';
        }).join("") : '<p style="color:var(--muted);font-size:13px">No pending requests.</p>') + "</div>";
      html += '<div class="card" style="margin-bottom:16px"><h3>Approved creators (' + creators.length + ")</h3>" +
        (creators.length ? creators.map(function (c) {
          var p = c.profiles || {};
          return '<div class="news-item" style="display:flex;justify-content:space-between;align-items:center"><span>' + esc(p.username || c.user_id) + (p.ign ? " (" + esc(p.ign) + ")" : "") + '</span>' +
            '<button class="btn btn-ghost btn-sm" data-revoke-creator="' + c.user_id + '" style="color:#ff6b6b">Revoke</button></div>';
        }).join("") : '<p style="color:var(--muted);font-size:13px">None yet.</p>') +
        '<div style="margin-top:10px"><label style="font-size:12px;color:var(--muted)">Grant creator directly</label>' +
        '<div style="display:flex;gap:8px"><select id="cr-grant" style="flex:1">' + players.map(function (p) { return '<option value="' + esc(p.id) + '">' + esc(p.username) + (p.ign ? " (" + esc(p.ign) + ")" : "") + "</option>"; }).join("") + "</select>" +
        '<button class="btn btn-gold btn-sm" id="cr-grant-btn">Grant</button></div><p id="cr-msg" style="font-size:13px;margin-top:6px"></p></div></div>';
      html += '<div class="card"><h3>All squads (' + squads.length + ")</h3>" +
        (squads.length ? squads.map(function (sq) {
          return '<div class="news-item"><h3 style="font-size:15px">' + esc(sq.name) + (sq.tag ? " [" + esc(sq.tag) + "]" : "") + '</h3>' +
            '<p style="font-size:12.5px">captain: ' + esc(sq.captain_username || "?") + " · " + sq.member_count + "/6 players · " + new Date(sq.created_at).toLocaleDateString() + "</p>" +
            '<button class="btn btn-ghost btn-sm" data-disband="' + sq.id + '" style="color:#ff6b6b;margin-top:6px">Disband</button></div>';
        }).join("") : '<p style="color:var(--muted);font-size:13px">No squads yet.</p>') + "</div>";
      body.innerHTML = html;
      els("[data-approve]", body).forEach(function (b) { b.addEventListener("click", function () { b.disabled = true;
        call(function () { return api.rpc("admin_review_creator", { p_request_id: parseInt(b.dataset.approve, 10), p_approve: true }, true); })
          .then(function () { return api.audit("approve_creator", { id: b.dataset.approve }); }).then(renderSquads); }); });
      els("[data-reject]", body).forEach(function (b) { b.addEventListener("click", function () { b.disabled = true;
        call(function () { return api.rpc("admin_review_creator", { p_request_id: parseInt(b.dataset.reject, 10), p_approve: false }, true); })
          .then(function () { return api.audit("reject_creator", { id: b.dataset.reject }); }).then(renderSquads); }); });
      els("[data-revoke-creator]", body).forEach(function (b) { b.addEventListener("click", function () { b.disabled = true;
        call(function () { return api.rpc("admin_revoke_creator", { p_user: b.dataset.revokeCreator }, true); })
          .then(function () { return api.audit("revoke_creator", {}); }).then(renderSquads); }); });
      els("[data-disband]", body).forEach(function (b) { b.addEventListener("click", function () {
        if (!confirm("Disband this squad?")) return; b.disabled = true;
        call(function () { return api.rpc("disband_squad", { p_squad: b.dataset.disband }, true); })
          .then(function () { return api.audit("admin_disband_squad", {}); }).then(renderSquads); }); });
      var gb = el("#cr-grant-btn");
      if (gb) gb.addEventListener("click", function () { gb.disabled = true;
        call(function () { return api.rpc("admin_grant_creator", { p_user: el("#cr-grant").value }, true); })
          .then(function () { return api.audit("grant_creator", {}); }).then(renderSquads); });
    });
  }

  function renderAwards() {
    var body = el("#admin-body");
    body.innerHTML = '<p style="color:var(--muted)">Loading…</p>';
    Promise.all([
      call(function () { return api.aGet("awards", "?select=*&order=created_at.desc"); }).catch(function () { return []; }),
      call(function () { return api.aGet("registrations", "?select=id,squad,user_id&user_id=not.is.null&order=created_at.desc"); }).catch(function () { return []; }),
      call(function () { return api.aGet("profiles", "?select=id,username,ign,matches,wins&order=created_at.desc"); }).catch(function () { return []; })
    ]).then(function (res) {
      var awards = res[0], regs = res[1], players = res[2];
      var KINDS = [["champion", "🏆 Champion (100 pts)"], ["runner_up", "🥈 Runner-up (60 pts)"], ["third", "🥉 3rd Place (40 pts)"], ["mvp", "⭐ MVP (50 pts)"], ["title", "🎖 Custom title"]];
      var DEF_PTS = { champion: 100, runner_up: 60, third: 40, mvp: 50, title: 0 };
      var html = '<div class="card" style="margin-bottom:16px"><h3>Grant award / title</h3>' +
        '<p style="font-size:13px">Real honors from real results — granted awards are public (Winners page, leaderboard points, player cards).</p>' +
        '<div class="field"><label>Tournament</label><input id="aw-tourn" value="xr-s1" maxlength="30"></div>' +
        '<div class="field"><label>Squad (registered squads with accounts)</label><input id="aw-squad" list="aw-squads" maxlength="40"><datalist id="aw-squads">' +
          regs.map(function (r) { return '<option value="' + esc(r.squad) + '" data-reg="' + esc(r.id) + '">'; }).join("") + "</datalist></div>" +
        '<div class="field"><label>Link to player account (optional — puts it on their player card)</label><select id="aw-reg"><option value="">— not linked —</option>' +
          regs.map(function (r) { return '<option value="' + esc(r.id) + '">' + esc(r.squad) + " (" + esc(r.id) + ")</option>"; }).join("") + "</select></div>" +
        '<div class="field"><label>Kind</label><select id="aw-kind">' + KINDS.map(function (k) { return '<option value="' + k[0] + '">' + k[1] + "</option>"; }).join("") + "</select></div>" +
        '<div class="field"><label>Title text (required for custom title, else auto)</label><input id="aw-title" maxlength="60" placeholder="e.g. Season 1 MVP"></div>' +
        '<div class="field"><label>Points</label><input id="aw-points" type="number" min="0" value="100"></div>' +
        '<div class="field"><label>Note (optional, public)</label><input id="aw-note" maxlength="120"></div>' +
        '<button class="btn btn-gold btn-sm" id="aw-grant">Grant award</button><p id="aw-msg" style="font-size:13px;margin-top:8px"></p></div>';
      html += '<div class="card" style="margin-bottom:16px"><h3>Player stats (XARENA-earned)</h3>' +
        '<p style="font-size:13px">Bump matches/wins after real XARENA matches — players cannot edit these themselves.</p>' +
        '<div class="field"><label>Player</label><select id="st-user">' + players.map(function (p) { return '<option value="' + esc(p.id) + '">' + esc(p.username) + (p.ign ? " (" + esc(p.ign) + ")" : "") + " — " + (p.matches || 0) + "M/" + (p.wins || 0) + "W</option>"; }).join("") + "</select></div>" +
        '<div style="display:flex;gap:10px;flex-wrap:wrap"><div class="field" style="flex:1"><label>Add matches</label><input id="st-matches" type="number" min="0" value="1"></div>' +
        '<div class="field" style="flex:1"><label>Add wins</label><input id="st-wins" type="number" min="0" value="0"></div></div>' +
        '<button class="btn btn-gold btn-sm" id="st-save">Update stats</button><p id="st-msg" style="font-size:13px;margin-top:8px"></p></div>';
      html += '<div class="card"><h3>Granted awards (' + awards.length + ")</h3>" +
        (awards.length ? awards.map(function (a) {
          return '<div class="news-item"><h3 style="font-size:15px">' + esc(a.squad) + " — " + esc(a.title) + '</h3><p style="font-size:12.5px">' + esc(a.kind) + " · " + esc(a.tournament_id) + " · +" + a.points + " pts · " + new Date(a.created_at).toLocaleDateString() + '</p>' +
            '<button class="btn btn-ghost btn-sm" data-revoke="' + a.id + '" style="color:#ff6b6b;margin-top:6px">Revoke</button></div>';
        }).join("") : '<p style="color:var(--muted);font-size:13px">None yet.</p>') + "</div>";
      body.innerHTML = html;
      var kindSel = el("#aw-kind"), ptsInp = el("#aw-points");
      kindSel.addEventListener("change", function () { ptsInp.value = DEF_PTS[kindSel.value]; });
      el("#aw-squad").addEventListener("input", function () {
        var v = el("#aw-squad").value;
        var hit = regs.filter(function (r) { return r.squad === v; })[0];
        if (hit) el("#aw-reg").value = hit.id;
      });
      el("#aw-grant").addEventListener("click", function () {
        var msg = el("#aw-msg"); var btn = el("#aw-grant"); btn.disabled = true;
        call(function () {
          return api.rpc("grant_award", {
            p_tournament: el("#aw-tourn").value.trim(), p_squad: el("#aw-squad").value.trim(),
            p_registration_id: el("#aw-reg").value || null, p_kind: kindSel.value,
            p_title: el("#aw-title").value.trim() || null, p_points: parseInt(ptsInp.value || "0", 10),
            p_note: el("#aw-note").value.trim() || null
          }, true);
        }).then(function (r) {
          btn.disabled = false;
          msg.style.color = r && r.ok ? "var(--gold2)" : "#ff6b6b";
          msg.textContent = r && r.ok ? "Granted: " + (r.title || "") : ((r && r.error) || "Failed.");
          if (r && r.ok) { api.audit("grant_award", { squad: el("#aw-squad").value, kind: kindSel.value }); setTimeout(renderAwards, 900); }
        });
      });
      el("#st-save").addEventListener("click", function () {
        var msg = el("#st-msg"); var btn = el("#st-save"); btn.disabled = true;
        call(function () { return api.rpc("bump_player_stats", { p_user: el("#st-user").value, p_matches: parseInt(el("#st-matches").value || "0", 10), p_wins: parseInt(el("#st-wins").value || "0", 10) }, true); })
          .then(function (r) {
            btn.disabled = false;
            msg.style.color = r && r.ok ? "var(--gold2)" : "#ff6b6b";
            msg.textContent = r && r.ok ? "Stats updated." : ((r && r.error) || "Failed.");
            if (r && r.ok) { api.audit("bump_player_stats", {}); setTimeout(renderAwards, 900); }
          });
      });
      els("[data-revoke]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          b.disabled = true;
          call(function () { return api.rpc("revoke_award", { p_id: parseInt(b.dataset.revoke, 10) }, true); })
            .then(function () { return api.audit("revoke_award", { id: b.dataset.revoke }); })
            .then(renderAwards);
        });
      });
    });
  }

  function renderPhoneVerify() {
    var body = el("#admin-body");
    body.innerHTML = '<p style="color:var(--muted)">Loading…</p>';
    Promise.all([
      call(function () { return api.aGet("profiles", "?select=id,username,ign,phone,phone_code,phone_verified,created_at&phone=not.is.null&order=created_at.desc"); }),
      call(function () { return api.aGet("verify_messages", "?select=user_id,code,note,created_at&order=created_at.desc&limit=100"); }).catch(function () { return []; })
    ]).then(function (res) {
      var rows = res[0], vmsgs = res[1];
      var lastMsg = {};
      vmsgs.forEach(function (m) { if (!lastMsg[m.user_id]) lastMsg[m.user_id] = m; });
        var pend = rows.filter(function (r) { return !r.phone_verified; });
        var done = rows.filter(function (r) { return r.phone_verified; });
        var head = '<div class="card" style="margin-bottom:16px"><h3>Phone verification queue</h3><p>The player DMs their code to you on Instagram (@ud_speed). If the code and username match, tap VERIFY — that opens squad registration for them. It is manual, so check this queue often.</p></div>';
        var html = head;
        if (!pend.length) html += '<div class="empty"><div class="big">No pending verifications</div><p>New signups will appear here.</p></div>';
        else html += pend.map(function (r) {
          return '<div class="news-item" style="border-left-color:var(--gold)">' +
            '<h3 style="font-size:16px">' + esc(r.username) + (r.ign ? " · " + esc(r.ign) : "") + "</h3>" +
            "<p>Phone <b>" + esc(r.phone) + "</b> · Code <b style=\'font-size:18px;letter-spacing:3px\'>" + esc(r.phone_code || "-") + "</b></p>" +
            (lastMsg[r.id] ? '<p style="color:var(--gold2);font-size:13px;margin-top:4px">In-app request' + (lastMsg[r.id].note ? ": " + esc(lastMsg[r.id].note) : "") + " · " + new Date(lastMsg[r.id].created_at).toLocaleString() + "</p>" : "") +
            '<div style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap">' +
            '<button class="btn btn-gold btn-sm" data-pverify="' + esc(r.id) + '">✓ Phone verified</button>' +
            "</div></div>";
        }).join("");
        if (done.length) html += '<div class="card" style="margin-top:16px"><h3>Verified (' + done.length + ")</h3><p style=\'font-size:13px\'>" + done.map(function (r) { return esc(r.username) + " (" + esc(r.phone) + ")"; }).join(" · ") + "</p></div>";
        body.innerHTML = html;
        els("[data-pverify]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            b.disabled = true;
            call(function () { return api.rpc("set_phone_verified", { p_user: b.dataset.pverify, p_verified: true }, true); })
              .then(function () { return api.audit("verify_phone", { user: b.dataset.pverify }); })
              .then(renderPhoneVerify);
          });
        });
      });
  }

  function renderRegistrations() {
    var body = el("#admin-body");
    body.innerHTML = '<p style="color:var(--muted)">Loading…</p>';
    Promise.all([
      call(function () { return api.aGet("registrations", "?select=*&order=created_at.desc"); }),
      call(function () { return api.aGet("reg_reports", "?select=*&order=created_at.desc"); }).catch(function () { return []; })
    ]).then(function (res) {
      var rows = res[0], reports = res[1];
      var head = '<div class="card" style="margin-bottom:16px"><h3>Verification queue</h3><p>One UID/phone = one registration (server-side). Tap VERIFY only after checking the screenshot proof — that is what grants the public VERIFIED badge. Review ON HOLD entries and release or delete them.</p></div>';
      if (!rows.length) { body.innerHTML = head + '<div class="empty"><div class="big">No registrations yet</div><p>Submissions from the website land here.</p></div>'; return; }
      body.innerHTML = head + rows.map(function (r) {
        var reps = reports.filter(function (x) { return x.registration_id === r.id; });
        var badges = [];
        if (r.verified) badges.push('<span style="color:var(--gold2);border:1px solid var(--gold2);padding:2px 8px;font-size:11px;letter-spacing:1px">✓ VERIFIED</span>');
        if (r.hold) badges.push('<span style="color:#ff6b6b;border:1px solid #ff6b6b;padding:2px 8px;font-size:11px;letter-spacing:1px">ON HOLD</span>');
        if (r.flag) badges.push('<span style="color:#ffb4ae;font-size:11px">⚑ ' + esc(r.flag) + '</span>');
        if (reps.length) badges.push('<span style="color:#ff6b6b;font-size:11px">' + reps.length + " fake report" + (reps.length > 1 ? "s" : "") + "</span>");
        return '<div class="news-item" style="border-left-color:' + (r.hold ? "#ff6b6b" : r.verified ? "var(--gold2)" : "var(--purple)") + '">' +
          '<h3 style="font-size:16px">' + esc(r.squad) + "  ·  " + esc(r.id) + "</h3>" +
          "<p>Captain " + esc(r.captain) + " · IGN " + esc(r.ign || "-") + " · UID " + esc(r.uid) + " · " + esc(r.phone) + "</p>" +
          '<p style="margin-top:4px">Status: ' + esc(r.status) + "  " + badges.join(" ") + "</p>" +
          (reps.length ? '<p style="color:#ffb4ae;font-size:12px;margin-top:4px">' + reps.map(function (x) { return "Report: " + esc(x.reason); }).join("<br>") + "</p>" : "") +
          '<div style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap">' +
          (r.verified
            ? '<button class="btn btn-ghost btn-sm" data-unverify="' + esc(r.id) + '">Remove verified</button>'
            : (r.proof_path
              ? '<button class="btn btn-gold btn-sm" data-verify="' + esc(r.id) + '">✓ Verify</button>'
              : '<button class="btn btn-gold btn-sm" data-verifynp="' + esc(r.id) + '">✓ Verify without proof</button>')) +
          (r.proof_path
            ? '<button class="btn btn-ghost btn-sm" data-proof="' + esc(r.proof_path) + '">View proof</button>'
            : '<span style="color:#ff6b6b;font-size:12px;align-self:center">no proof uploaded</span>') +
          '<button class="btn btn-ghost btn-sm" data-holdbtn="' + esc(r.id) + '" data-holdv="' + (r.hold ? "0" : "1") + '">' + (r.hold ? "Release hold" : "Hold") + "</button>" +
          '<button class="btn btn-ghost btn-sm" data-delreg="' + esc(r.id) + '" style="color:#ff6b6b">Delete</button>' +
          "</div></div>";
      }).join("");
      function doVerify(id, auditKind) {
        return call(function () { return api.rpc("admin_verify_registration", { p_id: id, p_verified: true, p_without_proof: auditKind === "verify_registration_override" }, true); })
          .then(function (r) {
            if (r && r.ok === false) throw new Error(r.error || "Verify failed.");
            return r;
          })
          
          .then(renderRegistrations);
      }
      els("[data-verify]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          doVerify(b.dataset.verify).catch(function (e) { alert("Failed: " + e.message); });
        });
      });
      els("[data-verifynp]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          if (!confirm("No proof screenshot was uploaded for this entry. Verify it anyway? Use this only when you know the entry is genuine. This override is audit-logged.")) return;
          doVerify(b.dataset.verifynp, "verify_registration_override").catch(function (e) { alert("Failed: " + e.message); });
        });
      });
      els("[data-unverify]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          call(function () { return api.rpc("set_registration_verified", { p_id: b.dataset.unverify, p_verified: false }, true); })
            .then(function (r) {
              if (r && r.ok === false) throw new Error(r.error || "Remove failed.");
              return api.aPatch("registrations", "id=eq." + encodeURIComponent(b.dataset.unverify), { status: "pending" }); })
            .then(function () { return api.audit("unverify_registration", { id: b.dataset.unverify }); })
            .then(renderRegistrations).catch(function (e) { alert("Failed: " + e.message); });
        });
      });
      els("[data-proof]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          b.disabled = true; b.textContent = "Opening…";
          proofUrl(b.dataset.proof).then(function (u) { window.open(u, "_blank"); })
            .catch(function (e) { alert("Could not open proof: " + e.message); })
            .then(function () { b.disabled = false; b.textContent = "View proof"; });
        });
      });
      els("[data-holdbtn]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          var hold = b.dataset.holdv === "1";
          call(function () { return api.rpc("admin_registration_hold", { p_id: b.dataset.holdbtn, p_hold: hold }, true); })
            .then(function () { return api.audit(hold ? "hold_registration" : "release_registration", { id: b.dataset.holdbtn }); })
            .then(renderRegistrations).catch(function (e) { alert("Failed: " + e.message); });
        });
      });
      els("[data-delreg]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          if (!confirm("Delete this registration permanently?")) return;
          call(function () { return api.aDel("registrations", "id=eq." + encodeURIComponent(b.dataset.delreg)); })
            .then(function () { return api.audit("delete_registration", { id: b.dataset.delreg }); })
            .then(renderRegistrations).catch(function (e) { alert("Delete failed: " + e.message); });
        });
      });
    }).catch(function (e) {
      body.innerHTML = '<div class="empty"><div class="big">Could not load</div><p>' + esc(e.message) + "</p></div>";
    });
  }

  function buildTabs() {
    el("#admin-tabs").innerHTML = Object.keys(SECTIONS).map(function (k) {
      return '<button class="lb-tab" data-sec="' + k + '">' + SECTIONS[k].title + "</button>";
    }).join("");
    els("#admin-tabs .lb-tab").forEach(function (b) {
      b.addEventListener("click", function () { openSection(b.dataset.sec); });
    });
  }
  function openSection(key) {
    els("#admin-tabs .lb-tab").forEach(function (b) { b.classList.toggle("active", b.dataset.sec === key); });
    var sec = SECTIONS[key];
    if (sec.custom) sec.custom();
    else loadList(key);
  }

  /* ---------- generic list + editor ---------- */
  function loadList(key) {
    var sec = SECTIONS[key];
    var body = el("#admin-body");
    body.innerHTML = '<p style="color:var(--muted)">Loading…</p>';
    call(function () { return api.aGet(sec.table, "?select=*&order=" + sec.order); }).then(function (rows) {
      var html = "";
      if (!sec.inbox) html += '<button class="btn btn-gold btn-sm" id="add-btn" style="margin-bottom:16px">+ Add ' + sec.title.replace(/s$/, "") + "</button>";
      if (!rows.length) html += '<div class="empty"><div class="big">Nothing here yet</div><p>' + (sec.inbox ? "Submissions from the website land here." : "Add the first entry above.") + "</p></div>";
      html += rows.map(function (r) {
        return '<div class="news-item" style="border-left-color:var(--purple)">' +
          '<h3 style="font-size:16px">' + esc(sec.rowTitle(r)) + '</h3><p>' + esc(sec.rowSub(r)) + "</p>" +
          '<div style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap">' +
          '<button class="btn btn-ghost btn-sm" data-edit=\'' + esc(JSON.stringify(r)) + "'>Edit</button>" +
          '<button class="btn btn-ghost btn-sm" data-del="' + esc(r.id) + '" style="color:#ff6b6b">Delete</button>' +
          "</div></div>";
      }).join("");
      html += '<div id="editor"></div>';
      body.innerHTML = html;
      var add = el("#add-btn");
      if (add) add.addEventListener("click", function () { showEditor(key, null); });
      els("[data-edit]", body).forEach(function (b) {
        b.addEventListener("click", function () { showEditor(key, JSON.parse(b.dataset.edit)); });
      });
      els("[data-del]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          if (!confirm("Delete this entry permanently?")) return;
          call(function () { return api.aDel(sec.table, "id=eq." + encodeURIComponent(b.dataset.del)); })
            .then(function () { return api.audit("delete", { table: sec.table, id: b.dataset.del }); })
            .then(function () { loadList(key); })
            .catch(function (e) { alert("Delete failed: " + e.message); });
        });
      });
    }).catch(function (e) {
      body.innerHTML = '<div class="empty"><div class="big">Could not load</div><p>' + esc(e.message) + "</p></div>";
    });
  }

  function inputHtml(c, val, teams) {
    var v = val == null ? "" : val;
    if (c.t === "textarea") return '<textarea name="' + c.k + '" rows="3">' + esc(v) + "</textarea>";
    if (c.t === "select") {
      var opts = c.opts.map(function (o) { var ov = Array.isArray(o) ? o : [o, o]; return '<option value="' + esc(ov[0]) + '"' + (String(v) === String(ov[0]) ? " selected" : "") + ">" + esc(ov[1]) + "</option>"; }).join("");
      return '<select name="' + c.k + '">' + opts + "</select>";
    }
    if (c.t === "teamselect") {
      var o2 = '<option value="">— none —</option>' + (teams || []).map(function (t) { return '<option value="' + t.id + '"' + (v === t.id ? " selected" : "") + ">" + esc(t.name) + "</option>"; }).join("");
      return '<select name="' + c.k + '">' + o2 + "</select>";
    }
    if (c.t === "checkbox") return '<input type="checkbox" name="' + c.k + '"' + (v ? " checked" : "") + ' style="width:20px;height:20px">';
    if (c.t === "image") return '<input type="text" name="' + c.k + '" value="' + esc(v) + '" readonly placeholder="No image yet" style="font-size:12px">' +
      '<input type="file" accept="image/*" data-img-file="' + c.k + '" style="margin-top:6px">' +
      '<div data-img-msg style="font-size:12px;margin-top:4px"></div>' +
      '<img data-img-prev="' + c.k + '" alt="" src="' + esc(v) + '" style="display:' + (v ? "block" : "none") + ';max-width:160px;border-radius:8px;margin-top:6px">';
    if (c.t === "number") return '<input type="number" step="any" name="' + c.k + '" value="' + esc(v) + '">';
    if (c.t === "datetime") {
      var dv = "";
      if (v) { var d = new Date(v); if (!isNaN(d)) dv = new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16); }
      return '<input type="datetime-local" name="' + c.k + '" value="' + dv + '">';
    }
    return '<input type="text" name="' + c.k + '" value="' + esc(v) + '">';
  }

  function showEditor(key, row) {
    var sec = SECTIONS[key];
    var isEdit = !!row;
    call(function () { return api.aGet("teams", "?select=id,name&order=name.asc"); }).then(function (teams) {
      var fields = sec.cols.map(function (c) {
        if (isEdit && c.createOnly) return "";
        return '<div class="field"><label>' + esc(c.l) + "</label>" + inputHtml(c, row ? row[c.k] : (c.t === "checkbox" && !c.noDefault ? true : ""), teams) + "</div>";
      }).join("");
      el("#editor").innerHTML = '<div class="form-panel" style="margin-top:20px"><h3>' + (isEdit ? "Edit" : "Add") + " " + esc(sec.title.replace(/s$/, "")) + '</h3><form id="edit-form">' + fields +
        '<div style="display:flex;gap:10px"><button class="btn btn-red" type="submit">Save</button><button class="btn btn-ghost" type="button" id="cancel-edit">Cancel</button></div></form></div>';
      el("#editor").scrollIntoView({ behavior: "smooth" });
      el("#cancel-edit").addEventListener("click", function () { el("#editor").innerHTML = ""; });
      els("[data-img-file]", el("#editor")).forEach(function (fi) {
        fi.addEventListener("change", function () {
          var file = fi.files[0]; if (!file) return;
          var k = fi.getAttribute("data-img-file");
          var msg = fi.parentNode.querySelector("[data-img-msg]");
          if (file.type.indexOf("image/") !== 0) { msg.textContent = "Only images."; return; }
          if (file.size > 5 * 1024 * 1024) { msg.textContent = "Image too large (max 5 MB)."; return; }
          msg.textContent = "Uploading…";
          call(function () { return api.userToken(); }).then(function (tok) {
            return fetch(api.url + "/functions/v1/cloudinary-sign", { method: "POST", headers: { "apikey": api.key, "Authorization": "Bearer " + tok, "Content-Type": "application/json" }, body: "{}" });
          }).then(function (r) { return r.json(); }).then(function (sig) {
            if (!sig || !sig.cloud) throw new Error("Upload unavailable.");
            var fd = new FormData();
            fd.append("file", file); fd.append("api_key", sig.key); fd.append("timestamp", sig.timestamp);
            fd.append("folder", sig.folder); fd.append("signature", sig.signature);
            return fetch("https://api.cloudinary.com/v1_1/" + sig.cloud + "/image/upload", { method: "POST", body: fd }).then(function (r) { return r.json(); });
          }).then(function (up) {
            if (!up || !up.secure_url) throw new Error("Upload failed.");
            el('[name="' + k + '"]', el("#editor")).value = up.secure_url;
            var pv = el('[data-img-prev="' + k + '"]', el("#editor")); pv.src = up.secure_url; pv.style.display = "block";
            var ap = el('[name="image_approved"]', el("#editor")); if (ap) ap.checked = false;
            msg.textContent = "Uploaded. Check the preview, then tick Approved and Save to show it publicly.";
          }).catch(function (e) { msg.textContent = e.message || "Upload failed."; });
        });
      });
      el("#edit-form").addEventListener("submit", function (ev) {
        ev.preventDefault();
        var data = {};
        sec.cols.forEach(function (c) {
          if (isEdit && c.createOnly) return;
          var inp = el('[name="' + c.k + '"]', this);
          if (!inp) return;
          if (c.t === "checkbox") data[c.k] = inp.checked;
          else if (c.t === "number") { if (inp.value !== "") data[c.k] = Number(inp.value); }
          else if (c.t === "datetime") { if (inp.value) data[c.k] = new Date(inp.value).toISOString(); }
          else if (c.t === "teamselect") data[c.k] = inp.value || null;
          else if (inp.value !== "") data[c.k] = inp.value;
        }, ev.target);
        var missing = sec.cols.some(function (c) { return c.req && !data[c.k] && !(isEdit && c.createOnly); });
        if (missing) { alert("Fill the required fields."); return; }
        var op = isEdit
          ? call(function () { return api.aPatch(sec.table, "id=eq." + encodeURIComponent(row.id), data); })
          : call(function () { return api.aPost(sec.table, data); });
        op.then(function () { return api.audit(isEdit ? "update" : "create", { table: sec.table, id: isEdit ? row.id : data.id }); })
          .then(function () { loadList(key); })
          .catch(function (e) { alert("Save failed: " + e.message); });
      });
    });
  }

  /* ---------- custom sections ---------- */
  function renderSecuritySummary() {
    var box = el("#sec-summary");
    if (!box) return;
    Promise.all([
      call(function () { return api.aGet("daily_security_summary", "?select=*"); }).then(function (r) { return r && r[0] ? r[0] : null; }).catch(function () { return null; }),
      call(function () { return api.aGet("audit_log", "?action=eq.security_scan&select=created_at,details&order=created_at.desc&limit=3"); }).catch(function () { return []; })
    ]).then(function (res) {
      var d = res[0], scans = res[1];
      if (!d) { box.innerHTML = ""; return; }
      box.innerHTML = '<div class="card"><h3>🛡️ Security Monitor — daily summary</h3>' +
        '<p>The monitor scans automatically every hour (server-side, nothing for you to do). Suspicious entries go on HOLD automatically; sensitive actions still need your approval.</p>' +
        '<div class="grid c3" style="margin-top:10px">' +
        '<div class="card"><h3>' + d.registrations_today + '</h3><p>Registrations today</p></div>' +
        '<div class="card"><h3>' + d.on_hold_now + '</h3><p>On hold (review needed)</p></div>' +
        '<div class="card"><h3>' + d.verified_total + '</h3><p>Verified squads</p></div>' +
        '<div class="card"><h3>' + d.reports_24h + '</h3><p>Fake reports (24h)</p></div>' +
        "</div>" +
        (scans.length ? '<p style="margin-top:10px;color:var(--muted);font-size:12px">Recent auto-actions: ' + scans.map(function (x) {
          var dt = (x.details || {});
          return new Date(x.created_at).toLocaleString() + " — held " + (dt.auto_held_no_proof || 0) + " no-proof, " + (dt.auto_held_burst || 0) + " burst";
        }).join("<br>") + "</p>" : '<p style="margin-top:10px;color:var(--gold2);font-size:13px">✓ All clean — no suspicious activity found.</p>') +
        "</div>";
    });
  }

  function renderOverview() {
    var body = el("#admin-body");
    body.innerHTML = '<p style="color:var(--muted)">Loading…</p>';
    var tables = ["registrations", "tryouts", "messages", "players", "teams", "tournaments"];
    Promise.all(tables.map(function (t) { return call(function () { return api.aGet(t, "?select=id"); }).then(function (r) { return [t, r.length]; }).catch(function () { return [t, "?"]; }); }))
      .then(function (counts) {
        var c = {}; counts.forEach(function (p) { c[p[0]] = p[1]; });
        body.innerHTML = '<div id="needs-you"></div><div id="auto-log" style="margin-top:16px"></div>' +
          '<div class="grid c3" style="margin-top:20px">' +
          '<div class="card"><h3>' + c.registrations + '</h3><p>Squad registrations</p></div>' +
          '<div class="card"><h3>' + c.tryouts + '</h3><p>Roster tryouts</p></div>' +
          '<div class="card"><h3>' + c.messages + '</h3><p>Inbox messages</p></div>' +
          '<div class="card"><h3>' + c.players + '</h3><p>Players on roster</p></div>' +
          '<div class="card"><h3>' + c.teams + '</h3><p>Teams</p></div>' +
          '<div class="card"><h3>' + c.tournaments + '</h3><p>Tournaments</p></div>' +
          "</div>" +
          '<div id="sec-summary" style="margin-top:20px"><p style="color:var(--muted)">Security summary loading…</p></div>' +
          '<div class="card" style="margin-top:20px"><h3>Quick actions</h3><div style="display:flex;gap:10px;flex-wrap:wrap;margin-top:8px">' +
          '<button class="btn btn-red btn-sm" data-go="news">Post announcement</button>' +
          '<button class="btn btn-ghost btn-sm" data-go="tournaments">Edit Season 1</button>' +
          '<button class="btn btn-ghost btn-sm" data-go="results">Enter match result</button>' +
          '<button class="btn btn-ghost btn-sm" data-go="players">Add player</button></div></div>';
        els("[data-go]", body).forEach(function (b) { b.addEventListener("click", function () { openSection(b.dataset.go); }); });
        renderSecuritySummary();
        renderNeedsYou();
        renderAutoLog();
      });
  }

  /* ---------- Needs you inbox (one-tap queue) ---------- */
  function renderNeedsYou() {
    var host = el("#needs-you");
    if (!host) return;
    call(function () { return api.rpc("admin_needs_you", {}, true); }).then(function (d) {
      if (!d || !d.ok) { host.innerHTML = ""; return; }
      var total = (d.proofs || []).length + (d.reports || []).length + (d.feed || []).length + (d.stories || []).length + (d.creators || []).length + (d.redemptions || []).length + (d.support || []).length;
      if (!total) {
        host.innerHTML = '<div class="card" style="border-color:rgba(74,222,128,.4)"><h3 style="color:#4ade80">All clear</h3><p style="font-size:13px">Nothing needs you right now. Auto-triage handled the rest - see the log below.</p></div>';
        return;
      }
      function escT(s) { return esc(String(s || "")); }
      function when(ts) { return String(ts || "").slice(0, 16).replace("T", " "); }
      var rows = "";
      (d.redemptions || []).forEach(function (r) {
        rows += '<div class="news-item" style="border-left-color:var(--gold)"><h3 style="font-size:14.5px">Reward: ' + escT(r.title) + ' (' + r.cost + ' pts)</h3><p style="font-size:12.5px">' + escT(r.by) + " · " + when(r.created_at) + '</p>' +
          '<div style="display:flex;gap:8px;margin-top:6px"><button class="btn btn-gold btn-sm" data-ny="redeem-ok|' + r.id + '">Fulfilled</button>' +
          '<button class="btn btn-ghost btn-sm" data-ny="redeem-no|' + r.id + '" style="color:#ff6b6b">Reject + refund</button>' +
          '<button class="btn btn-ghost btn-sm" data-ny-go="rewards">Open</button></div></div>';
      });
      (d.proofs || []).forEach(function (r) {
        rows += '<div class="news-item" style="border-left-color:var(--gold)"><h3 style="font-size:14.5px">Proof from ' + escT(r.by) + '</h3><p style="font-size:12.5px">' + escT(r.note || "(no note)") + " · " + when(r.created_at) + '</p>' +
          '<div style="display:flex;gap:8px;margin-top:6px;align-items:center"><input type="number" value="20" min="0" max="500" data-ny-pts="' + r.id + '" style="width:70px;padding:6px">' +
          '<button class="btn btn-gold btn-sm" data-ny="proof-ok|' + r.id + '">Approve + pay</button>' +
          '<button class="btn btn-ghost btn-sm" data-ny="proof-no|' + r.id + '" style="color:#ff6b6b">Reject</button>' +
          '<button class="btn btn-ghost btn-sm" data-ny-go="proofs">Open</button></div></div>';
      });
      (d.reports || []).forEach(function (r) {
        rows += '<div class="news-item" style="border-left-color:#ff6b6b"><h3 style="font-size:14.5px">Cheat report: ' + escT(r.reported_name) + '</h3><p style="font-size:12.5px">"' + escT(r.reason) + '" · by ' + escT(r.by) + " · " + when(r.created_at) + '</p>' +
          '<div style="display:flex;gap:8px;margin-top:6px"><button class="btn btn-red btn-sm" data-ny="rep-act|' + r.id + '">Actioned</button>' +
          '<button class="btn btn-ghost btn-sm" data-ny="rep-dis|' + r.id + '">Dismiss</button>' +
          '<button class="btn btn-ghost btn-sm" data-ny-go="cheats">Open</button></div></div>';
      });
      (d.feed || []).forEach(function (r) {
        rows += '<div class="news-item" style="border-left-color:var(--purple)"><h3 style="font-size:14.5px">Post: ' + escT((r.caption || "(" + r.kind + ")").slice(0, 60)) + '</h3><p style="font-size:12.5px">by ' + escT(r.by) + " · " + when(r.created_at) + '</p>' +
          '<div style="display:flex;gap:8px;margin-top:6px"><button class="btn btn-gold btn-sm" data-ny="feed-ok|' + r.id + '">Approve</button>' +
          '<button class="btn btn-ghost btn-sm" data-ny="feed-no|' + r.id + '" style="color:#ff6b6b">Reject</button>' +
          '<button class="btn btn-ghost btn-sm" data-ny-go="feedqueue">Open</button></div></div>';
      });
      (d.stories || []).forEach(function (r) {
        rows += '<div class="news-item" style="border-left-color:var(--purple)"><h3 style="font-size:14.5px">Story: ' + escT((r.caption || "(" + r.kind + ")").slice(0, 60)) + '</h3><p style="font-size:12.5px">by ' + escT(r.by) + " · " + when(r.created_at) + '</p>' +
          '<div style="display:flex;gap:8px;margin-top:6px"><button class="btn btn-gold btn-sm" data-ny="story-ok|' + r.id + '">Approve</button>' +
          '<button class="btn btn-ghost btn-sm" data-ny="story-no|' + r.id + '" style="color:#ff6b6b">Reject</button>' +
          '<button class="btn btn-ghost btn-sm" data-ny-go="feedqueue">Open</button></div></div>';
      });
      (d.creators || []).forEach(function (r) {
        rows += '<div class="news-item"><h3 style="font-size:14.5px">Squad creator: ' + escT(r.by) + '</h3><p style="font-size:12.5px">' + escT(r.note || "(no note)") + " · " + when(r.created_at) + '</p>' +
          '<div style="display:flex;gap:8px;margin-top:6px"><button class="btn btn-gold btn-sm" data-ny="cr-ok|' + r.id + '">Approve</button>' +
          '<button class="btn btn-ghost btn-sm" data-ny="cr-no|' + r.id + '" style="color:#ff6b6b">Reject</button>' +
          '<button class="btn btn-ghost btn-sm" data-ny-go="squads">Open</button></div></div>';
      });
      (d.support || []).forEach(function (r) {
        rows += '<div class="news-item" style="border-left-color:var(--purple)"><h3 style="font-size:14.5px">Support: ' + escT(r.question).slice(0, 90) + '</h3><p style="font-size:12.5px">by ' + escT(r.by) + " · " + when(r.created_at) + " · the AI could not answer this" + '</p>' +
          '<div style="display:flex;gap:8px;margin-top:6px;flex-wrap:wrap;align-items:center"><input type="text" placeholder="Your answer" data-ny-ans="' + r.id + '" style="flex:1;min-width:180px;padding:6px">' +
          '<button class="btn btn-gold btn-sm" data-ny="sup-ok|' + r.id + '">Send answer</button>' +
          '<button class="btn btn-ghost btn-sm" data-ny="sup-faq|' + r.id + '">Answer + teach FAQ</button></div></div>';
      });
      host.innerHTML = '<div class="card" style="border-color:rgba(245,179,1,.55)"><h3 style="color:var(--gold2)">Needs you (' + total + ")</h3>" +
        '<p style="font-size:13px">Only what auto-triage could not decide. One tap decides it - full queues are one tap away too.</p></div>' + rows;
      els("[data-ny-go]", host).forEach(function (b) { b.addEventListener("click", function () { openSection(b.dataset.nyGo); }); });
      els("[data-ny]", host).forEach(function (b) {
        b.addEventListener("click", function () {
          var parts = b.dataset.ny.split("|"), kind = parts[0], id = parseInt(parts[1], 10);
          b.disabled = true;
          var p;
          if (kind === "sup-ok" || kind === "sup-faq") {
            var sid = parts[1];
            var ans = (el('[data-ny-ans="' + sid + '"]', host) || {}).value || "";
            if (ans.trim().length < 3) { alert("Type an answer first."); b.disabled = false; return; }
            var kws = null;
            if (kind === "sup-faq") {
              var qi = (d.support || []).find(function (x) { return x.id === sid; });
              kws = qi ? qi.question.toLowerCase().split(/[^a-z0-9]+/).filter(function (w) { return w.length > 3; }).slice(0, 4) : null;
            }
            p = call(function () { return api.rpc("admin_support_answer", { p_id: sid, p_answer: ans.trim(), p_add_faq: kind === "sup-faq", p_keywords: kws }, true); });
            p.then(function (r) {
              if (r && r.ok === false) { alert(r.error || "Failed."); b.disabled = false; return; }
              return api.audit("needs_you_" + kind, { id: sid }).then(renderNeedsYou);
            }).catch(function (e) { b.disabled = false; alert("Failed: " + e.message); });
            return;
          }
          if (kind === "proof-ok" || kind === "proof-no") {
            var pts = parseInt((el('[data-ny-pts="' + id + '"]', host) || {}).value || "0", 10);
            p = call(function () { return api.rpc("admin_review_proof", { p_id: id, p_action: kind === "proof-ok" ? "approve" : "reject", p_points: kind === "proof-ok" ? pts : 0, p_note: null }, true); });
          } else if (kind === "rep-act" || kind === "rep-dis") {
            p = call(function () { return api.rpc("admin_handle_cheat_report", { p_id: id, p_action: kind === "rep-act" ? "actioned" : "dismissed", p_note: null }, true); });
          } else if (kind === "feed-ok" || kind === "feed-no") {
            p = call(function () { return api.rpc("admin_feed_review", { p_post: id, p_action: kind === "feed-ok" ? "approve" : "reject" }, true); });
          } else if (kind === "story-ok" || kind === "story-no") {
            p = call(function () { return api.rpc("admin_story_review", { p_id: id, p_action: kind === "story-ok" ? "approve" : "reject" }, true); });
          } else if (kind === "cr-ok" || kind === "cr-no") {
            p = call(function () { return api.rpc("admin_review_creator", { p_request_id: id, p_approve: kind === "cr-ok" }, true); });
          } else {
            p = call(function () { return api.rpc("admin_review_redemption", { p_id: id, p_action: kind === "redeem-ok" ? "fulfilled" : "rejected", p_note: null }, true); });
          }
          p.then(function (r) {
            if (r && r.ok === false) { alert(r.error || "Failed."); b.disabled = false; return; }
            return api.audit("needs_you_" + kind, { id: id }).then(renderNeedsYou);
          }).catch(function (e) { b.disabled = false; alert("Failed: " + e.message); });
        });
      });
    }).catch(function () { host.innerHTML = ""; });
  }

  /* ---------- Auto-handled log with undo ---------- */
  var AUTO_LABEL = { feed_approve: "Post auto-published", story_approve: "Story auto-published", proof_approve: "Proof auto-approved (+15 pts)", report_dismiss: "Spam report auto-dismissed", creator_approve: "Squad creator auto-approved", lfg_close: "Stale scrim lobby auto-closed" };
  function renderAutoLog() {
    var host = el("#auto-log");
    if (!host) return;
    call(function () { return api.aGet("audit_log", "?actor=eq.auto-triage&order=id.desc&limit=15"); }).then(function (rows) {
      rows = rows || [];
      if (!rows.length) { host.innerHTML = '<div class="card"><h3>Auto-handled</h3><p style="font-size:13px;color:var(--muted)">Nothing auto-handled yet. Auto-triage runs every 10 minutes and only touches clear cases.</p></div>'; return; }
      host.innerHTML = '<div class="card"><h3>Auto-handled recently</h3><p style="font-size:13px">Auto-triage did these for you. Tap undo to reverse any of them - it goes back to the queue.</p></div>' +
        rows.map(function (r) {
          var q = (r.details && r.details.queue) || "", id = (r.details && r.details.id) || "";
          return '<div class="news-item" style="border-left-color:#4ade80"><p style="font-size:13.5px"><b style="color:#4ade80">' + esc(AUTO_LABEL[r.action] || r.action) + '</b> · ' + esc((r.details && r.details.why) || "") + '</p>' +
            '<p style="font-size:12px;color:var(--muted)">' + String(r.created_at).slice(0, 16).replace("T", " ") + '</p>' +
            (q && id ? '<button class="btn btn-ghost btn-sm" data-undo="' + q + "|" + id + '" style="margin-top:4px">Undo</button>' : "") + '</div>';
        }).join("");
      els("[data-undo]", host).forEach(function (b) {
        b.addEventListener("click", function () {
          var parts = b.dataset.undo.split("|");
          b.disabled = true;
          call(function () { return api.rpc("admin_undo_auto", { p_queue: parts[0], p_id: parseInt(parts[1], 10) }, true); })
            .then(function (r) {
              if (r && r.ok) { renderAutoLog(); renderNeedsYou(); }
              else { alert((r && r.error) || "Could not undo - it may already be handled."); b.disabled = false; }
            }).catch(function (e) { b.disabled = false; alert("Failed: " + e.message); });
        });
      });
    }).catch(function () { host.innerHTML = ""; });
  }

  function renderResults() {
    var body = el("#admin-body");
    body.innerHTML = '<p style="color:var(--muted)">Loading…</p>';
    call(function () { return api.aGet("matches", "?select=*,tournaments(name)&order=created_at.desc"); }).then(function (matches) {
      call(function () { return api.aGet("tournaments", "?select=id,name&order=sort.asc"); }).then(function (tours) {
        var mopts = matches.map(function (m) { return '<option value="' + m.id + '">' + esc(m.title) + "</option>"; }).join("");
        var topts = tours.map(function (t) { return '<option value="' + esc(t.id) + '">' + esc(t.name) + "</option>"; }).join("");
        var html = '<div class="grid c2">' +
          '<div class="form-panel"><h3>Add match</h3><form id="match-form">' +
          '<div class="field"><label>Tournament</label><select name="tournament_id">' + topts + "</select></div>" +
          '<div class="field"><label>Match title</label><input name="title" required placeholder="e.g. Qualifier Group A · Match 1"></div>' +
          '<div class="field"><label>Stage</label><input name="stage" placeholder="Qualifier / Finals"></div>' +
          '<div class="field"><label>Played at</label><input type="datetime-local" name="played_at"></div>' +
          '<div class="field"><label>Match start (for room reveal)</label><input type="datetime-local" name="starts_at"></div>' +
          '<div class="frow"><div class="field"><label>Room ID</label><input name="room_id" placeholder="posted later ok"></div>' +
          '<div class="field"><label>Room password</label><input name="room_pass"></div></div>' +
          '<div class="field"><label>Reveal minutes before start</label><input type="number" name="reveal_minutes" value="15" min="0" max="180"></div>' +
          '<button class="btn btn-red" type="submit">Add Match</button></form></div>' +
          '<div class="form-panel"><h3>Enter player result</h3><form id="lb-form">' +
          '<div class="field"><label>Match</label><select name="match_id">' + (mopts || '<option value="">— add a match first —</option>') + "</select></div>" +
          '<div class="frow"><div class="field"><label>Player IGN</label><input name="player_name" required></div>' +
          '<div class="field"><label>Squad</label><input name="squad"></div></div>' +
          '<div class="frow"><div class="field"><label>Kills</label><input type="number" name="kills" value="0"></div>' +
          '<div class="field"><label>Placement</label><input type="number" name="placement" min="1" max="48"></div></div>' +
          '<div class="field"><label>Points</label><input type="number" name="points" value="0"></div>' +
          '<button class="btn btn-red" type="submit">Add Result</button></form>' +
          "<p class=\"form-note\">Results instantly update the public leaderboard (kills / points / wins).</p></div></div>" +
          '<div style="margin-top:22px"><h3 style="font-family:var(--font-d);letter-spacing:2px;font-size:16px;margin-bottom:12px">MATCHES</h3>' +
          (matches.length ? matches.map(function (m) {
            return '<div class="news-item" style="border-left-color:var(--purple)"><h3 style="font-size:16px">' + esc(m.title) + '</h3><p>' + esc((m.tournaments && m.tournaments.name) || "") + " · " + esc(m.stage || "") + " · " + esc(m.status || "scheduled") +
              (m.starts_at ? " · starts " + esc(String(m.starts_at).slice(0, 16).replace("T", " ")) : "") +
              (m.room_id ? ' · room <b style="color:#4ade80">set</b>' : ' · room <b style="color:#ff9d9d">not set</b>') + " · reveal " + (m.reveal_minutes || 15) + "m before" + '</p>' +
              '<div class="frow" style="margin-top:8px"><div class="field"><input data-mroom="' + m.id + '" placeholder="Room ID" value="' + esc(m.room_id || "") + '"></div>' +
              '<div class="field"><input data-mpass="' + m.id + '" placeholder="Password" value="' + esc(m.room_pass || "") + '"></div></div>' +
              '<div style="margin-top:8px;display:flex;gap:8px;flex-wrap:wrap">' +
              '<button class="btn btn-gold btn-sm" data-msave="' + m.id + '">Save room</button>' +
              '<button class="btn btn-ghost btn-sm" data-mci="' + m.id + '">Check-ins</button>' +
              '<button class="btn btn-ghost btn-sm" data-mstatus="' + m.id + '">Status: ' + esc(m.status || "scheduled") + '</button>' +
              '<button class="btn btn-ghost btn-sm" data-delmatch="' + m.id + '" style="color:#ff6b6b">Delete match &amp; its results</button></div></div>';
          }).join("") : '<div class="empty"><div class="big">No matches yet</div><p>Add the first match above, then enter player results.</p></div>') +
          "</div>";
        body.innerHTML = html;
        el("#match-form").addEventListener("submit", function (ev) {
          ev.preventDefault();
          var f = ev.target;
          var data = { tournament_id: f.tournament_id.value, title: f.title.value.trim(), stage: f.stage.value.trim() || null, played_at: f.played_at.value ? new Date(f.played_at.value).toISOString() : null,
            starts_at: f.starts_at.value ? new Date(f.starts_at.value).toISOString() : null, room_id: f.room_id.value.trim() || null, room_pass: f.room_pass.value.trim() || null,
            reveal_minutes: parseInt(f.reveal_minutes.value || "15", 10), status: "scheduled" };
          if (!data.title) return;
          call(function () { return api.aPost("matches", data); })
            .then(function () { return api.audit("create", { table: "matches", title: data.title }); })
            .then(renderResults).catch(function (e) { alert("Save failed: " + e.message); });
        });
        el("#lb-form").addEventListener("submit", function (ev) {
          ev.preventDefault();
          var f = ev.target;
          if (!f.match_id.value) { alert("Add a match first."); return; }
          var data = { match_id: f.match_id.value, player_name: f.player_name.value.trim(), squad: f.squad.value.trim() || null, kills: Number(f.kills.value || 0), placement: f.placement.value ? Number(f.placement.value) : null, points: Number(f.points.value || 0) };
          if (!data.player_name) return;
          call(function () { return api.aPost("lb_entries", data); })
            .then(function () { return api.audit("create", { table: "lb_entries", player: data.player_name }); })
            .then(function () { f.reset(); alert("Result added — leaderboard updated."); })
            .catch(function (e) { alert("Save failed: " + e.message); });
        });
        els("[data-delmatch]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            if (!confirm("Delete this match and all its results?")) return;
            call(function () { return api.aDel("matches", "id=eq." + b.dataset.delmatch); })
              .then(function () { return api.audit("delete", { table: "matches", id: b.dataset.delmatch }); })
              .then(renderResults).catch(function (e) { alert("Delete failed: " + e.message); });
          });
        });
        els("[data-msave]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            var id = b.dataset.msave;
            var room = el('[data-mroom="' + id + '"]', body).value.trim();
            var pass = el('[data-mpass="' + id + '"]', body).value.trim();
            b.disabled = true;
            call(function () { return api.aPatch("matches", "id=eq." + id, { room_id: room || null, room_pass: pass || null }); })
              .then(function () { return api.audit("match_room_set", { match: id }); })
              .then(renderResults).catch(function (e) { b.disabled = false; alert("Save failed: " + e.message); });
          });
        });
        els("[data-mci]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            b.disabled = true;
            call(function () { return api.rpc("admin_match_checkins", { p_match: b.dataset.mci }, true); })
              .then(function (r) {
                b.disabled = false;
                if (!r || !r.ok) { alert((r && r.error) || "Failed."); return; }
                var list = r.list || [];
                alert(list.length ? list.map(function (x) { return x.ign + " - " + String(x.at).slice(11, 16) + (x.proof ? " - proof: " + x.proof : " - no proof"); }).join("\n") : "Nobody checked in yet.");
              });
          });
        });
        els("[data-mstatus]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            var order = ["scheduled", "live", "done", "cancelled"];
            var cur = (b.textContent.split(": ")[1] || "scheduled").trim();
            var next = order[(order.indexOf(cur) + 1) % order.length];
            call(function () { return api.aPatch("matches", "id=eq." + b.dataset.mstatus, { status: next }); })
              .then(function () { return api.audit("match_status", { match: b.dataset.mstatus, status: next }); })
              .then(renderResults);
          });
        });
      });
    });
  }

  /* ---------- AI Agent Control Center (Phase 3) ---------- */
  var AGENT_INFO = {
    "tournament-manager": { icon: "🏆", standard: ["draft_tournament", "update_schedule"], sensitive: ["publish_tournament", "modify_prize_pool", "delete_tournament"] },
    "team-manager": { icon: "🛡️", standard: ["draft_roster_change"], sensitive: ["remove_player", "edit_roster"] },
    "statistics-agent": { icon: "📊", standard: ["draft_result_entry"], sensitive: ["modify_results", "recalculate_rankings"] },
    "announcement-agent": { icon: "📣", standard: ["draft_announcement"], sensitive: ["publish_announcement", "post_social"] },
    "security-monitor": { icon: "🔒", standard: ["flag_activity"], sensitive: ["ban_registration"] },
    "executive-agent": { icon: "🧠", standard: ["delegate_tasks"], sensitive: [] }
  };

  function renderAgents() {
    var body = el("#admin-body");
    body.innerHTML = '<p style="color:var(--muted)">Loading…</p>';
    Promise.all([
      call(function () { return api.aGet("agents", "?select=*&order=created_at.asc"); }),
      call(function () { return api.aGet("agent_tasks", "?select=*&order=created_at.desc&limit=20"); }),
      call(function () { return api.aGet("approvals", "?select=*&status=eq.pending&order=created_at.desc"); })
    ]).then(function (r) {
      var agents = r[0], tasks = r[1], pending = r[2];
      var html = "";
      if (pending.length) {
        html += '<div class="card" style="border-color:rgba(245,179,1,.5);margin-bottom:18px"><h3 style="color:var(--gold2)">⏳ ' + pending.length + ' action' + (pending.length > 1 ? "s" : "") + ' waiting for your approval</h3><p>Review them in the Approvals tab. Nothing executes without your yes.</p></div>';
      }
      html += '<div class="grid c2">' + agents.map(function (a) {
        var info = AGENT_INFO[a.id] || { icon: "🤖", standard: [], sensitive: [] };
        var perms = (a.permissions || []);
        var permChips = perms.map(function (p) {
          return '<button class="lb-tab active" style="font-size:10px;padding:6px 10px" data-revoke="' + a.id + "|" + esc(p) + '" title="Tap to revoke">' + esc(p) + " ✕</button>";
        }).join("");
        var catalog = info.standard.concat(info.sensitive);
        var revoked = catalog.filter(function (p) { return perms.indexOf(p) < 0; }).map(function (p) {
          return '<button class="lb-tab" style="font-size:10px;padding:6px 10px" data-grant="' + a.id + "|" + esc(p) + '" title="Tap to grant">' + esc(p) + " +</button>";
        }).join("");
        var myTasks = tasks.filter(function (t) { return t.agent_id === a.id; });
        var taskRows = myTasks.slice(0, 3).map(function (t) {
          var col = t.status === "done" ? "var(--gold2)" : t.status === "failed" ? "#ff6b6b" : "var(--muted)";
          return '<p style="font-size:13.5px;color:' + col + ';margin-top:6px">• [' + esc(t.status.toUpperCase()) + "] " + esc(t.task) + (t.result ? " — " + esc(t.result) : "") + "</p>";
        }).join("") || '<p style="font-size:13.5px;color:var(--dim);margin-top:6px">No tasks yet.</p>';
        return '<article class="card"><div class="tcard-top"><h3>' + info.icon + " " + esc(a.name) + '</h3><span class="badge ' + (a.status === "active" ? "live" : a.status === "paused" ? "done" : "info") + '">' + esc(a.status.toUpperCase()) + "</span></div>" +
          "<p>" + esc(a.description || "") + "</p>" +
          '<div style="margin:10px 0 4px"><span style="font-size:11px;letter-spacing:2px;color:var(--dim);font-weight:700">PERMISSIONS (tap to change)</span></div>' +
          '<div style="display:flex;flex-wrap:wrap;gap:6px">' + (permChips || "") + (revoked || "") + "</div>" +
          '<div style="display:flex;gap:8px;margin-top:12px;flex-wrap:wrap">' +
          '<button class="btn btn-ghost btn-sm" data-assign="' + a.id + '">Assign task</button>' +
          '<button class="btn btn-ghost btn-sm" data-toggle-status="' + a.id + "|" + (a.status === "paused" ? "idle" : "paused") + '">' + (a.status === "paused" ? "Resume" : "Pause") + "</button></div>" +
          '<div style="margin-top:10px;border-top:1px solid var(--line);padding-top:8px"><span style="font-size:11px;letter-spacing:2px;color:var(--dim);font-weight:700">RECENT TASKS</span>' + taskRows + "</div>" +
          "</article>";
      }).join("") + "</div>";
      html += '<div class="card" style="margin-top:18px"><h3>🔒 Security Monitor — run scan</h3><p>Scans registrations and the activity log for duplicate UIDs, duplicate phone numbers and suspicious bursts. Findings are written to the Activity Log.</p>' +
        '<div style="margin-top:10px"><button class="btn btn-red btn-sm" id="sec-scan">Run Security Scan</button></div><div id="sec-scan-out" style="margin-top:12px"></div></div>';
      html += '<div class="card" style="margin-top:18px"><h3>How execution works</h3><p>Agents act through this backend only. Every sensitive action (deleting records, publishing announcements, prize-pool changes) creates an Approval — it executes only after you approve it in the Approvals tab, and the decision is logged. Agents get smarter automatically when the Luna brain (Phase 3 edge function) goes live.</p></div>';
      body.innerHTML = html;

      els("[data-revoke],[data-grant]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          var spec = b.dataset.revoke || b.dataset.grant;
          var parts = spec.split("|"), agentId = parts[0], perm = parts[1];
          var agent = agents.find(function (x) { return x.id === agentId; });
          var perms = (agent.permissions || []).slice();
          if (b.dataset.revoke) perms = perms.filter(function (p) { return p !== perm; });
          else perms.push(perm);
          call(function () { return api.aPatch("agents", "id=eq." + agentId, { permissions: perms }); })
            .then(function () { return api.audit(b.dataset.revoke ? "revoke_permission" : "grant_permission", { agent: agentId, permission: perm }); })
            .then(function () { kickWorker(); }).then(renderAgents).catch(function (e) { alert("Failed: " + e.message); });
        });
      });
      els("[data-toggle-status]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          var parts = b.dataset.toggleStatus.split("|");
          call(function () { return api.aPatch("agents", "id=eq." + parts[0], { status: parts[1] }); })
            .then(function () { return api.audit("agent_status", { agent: parts[0], status: parts[1] }); })
            .then(renderAgents).catch(function (e) { alert("Failed: " + e.message); });
        });
      });
      els("[data-assign]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          var task = prompt("Task for " + b.dataset.assign + ":");
          if (!task || !task.trim()) return;
          var needsApproval = confirm("Does this need your approval before it counts as done?\nOK = approval required (sensitive). Cancel = normal task.");
          call(function () { return api.aPost("agent_tasks", { agent_id: b.dataset.assign, task: task.trim(), status: "queued", needs_approval: needsApproval }); })
            .then(function () { return api.audit("assign_task", { agent: b.dataset.assign, task: task.trim(), needs_approval: needsApproval }); })
            .then(function () {
              if (needsApproval) {
                return call(function () { return api.aPost("approvals", { agent_id: b.dataset.assign, action: "execute_task", details: { task: task.trim() } }); });
              }
            })
            .then(renderAgents).catch(function (e) { alert("Failed: " + e.message); });
        });
      });
      var scanBtn = el("#sec-scan");
      if (scanBtn) scanBtn.addEventListener("click", function () {
        var out = el("#sec-scan-out");
        out.innerHTML = '<p style="color:var(--muted)">Scanning…</p>';
        call(function () { return api.aGet("registrations", "?select=id,squad,uid,phone,created_at"); }).then(function (regs) {
          var flags = [];
          var byUid = {}, byPhone = {};
          regs.forEach(function (r) {
            (byUid[r.uid] = byUid[r.uid] || []).push(r);
            (byPhone[r.phone] = byPhone[r.phone] || []).push(r);
          });
          Object.keys(byUid).forEach(function (k) { if (byUid[k].length > 1) flags.push("Duplicate Free Fire UID " + k + " in squads: " + byUid[k].map(function (r) { return r.squad; }).join(", ")); });
          Object.keys(byPhone).forEach(function (k) { if (byPhone[k].length > 1) flags.push("Phone " + k + " used by " + byPhone[k].length + " registrations: " + byPhone[k].map(function (r) { return r.squad; }).join(", ")); });
          return Promise.all(flags.map(function (f) {
            return call(function () { return api.aPost("audit_log", { actor: "security-monitor", action: "flag", details: { finding: f } }); });
          })).then(function () {
            out.innerHTML = flags.length
              ? flags.map(function (f) { return '<p style="color:#ffb84d;font-size:14.5px">⚠️ ' + esc(f) + "</p>"; }).join("") + '<p style="color:var(--muted);font-size:13.5px;margin-top:6px">Logged to Activity Log.</p>'
              : '<p style="color:var(--gold2)">✓ Clean — no suspicious patterns across ' + regs.length + " registrations.</p>";
          });
        }).catch(function (e) { out.innerHTML = '<p style="color:#ff6b6b">Scan failed: ' + esc(e.message) + "</p>"; });
      });
    });
  }

  /* ---------- Approvals inbox (owner decision required before execution) ---------- */
  function renderApprovals() {
    var body = el("#admin-body");
    body.innerHTML = '<p style="color:var(--muted)">Loading…</p>';
    call(function () { return api.aGet("approvals", "?select=*&order=created_at.desc&limit=50"); }).then(function (rows) {
      if (!rows.length) {
        body.innerHTML = '<div class="empty"><div class="big">No approval requests</div><p>When an AI agent wants to do something sensitive — delete a record, publish an announcement, change a prize pool — it lands here first. Nothing executes without your decision.</p></div>';
        return;
      }
      body.innerHTML = rows.map(function (r) {
        var state = r.status === "pending"
          ? '<div style="display:flex;gap:8px;margin-top:10px"><button class="btn btn-gold btn-sm" data-approve="' + r.id + '">Approve</button><button class="btn btn-ghost btn-sm" data-reject="' + r.id + '" style="color:#ff6b6b">Reject</button></div>'
          : '<span class="badge ' + (r.status === "approved" ? "up" : "done") + '" style="margin-top:10px">' + r.status.toUpperCase() + (r.decided_at ? " · " + String(r.decided_at).slice(0, 16).replace("T", " ") : "") + "</span>";
        return '<div class="news-item" style="border-left-color:var(--gold)"><span class="ntag">' + esc(r.action) + '</span> · <span class="nd">' + esc(String(r.created_at).slice(0, 16).replace("T", " ")) + '</span><h3 style="font-size:16px">' + esc(r.agent_id) + '</h3><p>' + esc(JSON.stringify(r.details)) + "</p>" + state + "</div>";
      }).join("");
      els("[data-approve],[data-reject]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          var id = b.dataset.approve || b.dataset.reject;
          var status = b.dataset.approve ? "approved" : "rejected";
          call(function () { return api.aPatch("approvals", "id=eq." + id, { status: status, decided_at: new Date().toISOString() }); })
            .then(function () { return api.audit("approval_" + status, { approval_id: id }); })
            .then(function () { kickWorker(); }).then(renderApprovals).catch(function (e) { alert("Failed: " + e.message); });
        });
      });
    });
  }

  function kickWorker() {
    try {
      var s = api.session();
      if (!s || !s.access_token) return;
      fetch(api.url + "/functions/v1/agent-worker", {
        method: "POST",
        headers: { "Authorization": "Bearer " + s.access_token, "Content-Type": "application/json" },
        body: "{}"
      }).catch(function () {});
    } catch (e) {}
  }

  function renderChat() {
    var body = el("#admin-body");
    body.innerHTML = '<p style="color:var(--muted)">Loading…</p>';
    call(function () {
      return Promise.all([
        api.aGet("chat_messages", "?select=id,handle,body,created_at&order=id.desc&limit=100"),
        api.aGet("chat_mutes", "?select=*"),
        api.aGet("chat_reports", "?select=*,chat_messages(id,handle,body)&resolved=eq.false&order=id.desc&limit=50")
      ]);
    }).then(function (rs) {
      var msgs = rs[0], mutes = rs[1], reports = rs[2];
      var html = "";
      html += '<h3 style="margin:6px 0 12px">Reports (' + reports.length + ")</h3>";
      html += reports.length ? reports.map(function (r) {
        var m = r.chat_messages || {};
        return '<div class="news-item" style="border-left-color:#e10600"><span class="ntag">REPORT</span> · <span class="nd">' + esc(String(r.created_at).replace("T", " ").slice(0, 16)) + "</span>" +
          "<p><b>" + esc(m.handle || "?") + "</b>: " + esc(m.body || "(message already deleted)") + "</p>" +
          '<div style="margin-top:8px;display:flex;gap:8px;flex-wrap:wrap">' +
          '<button class="btn btn-ghost btn-sm" data-resolve="' + r.id + '">Mark resolved</button>' +
          (m.id ? '<button class="btn btn-ghost btn-sm" style="color:#ff6b6b" data-delmsg="' + m.id + '">Delete message</button>' : "") +
          "</div></div>";
      }).join("") : '<p style="color:var(--muted);font-size:13px">No open reports.</p>';
      html += '<h3 style="margin:22px 0 12px">Mutes &amp; bans (' + mutes.length + ")</h3>";
      html += mutes.length ? mutes.map(function (m) {
        return '<div class="news-item" style="border-left-color:var(--gold)"><b>' + esc(m.handle) + '</b> · <span class="nd">' + (m.until ? "until " + esc(String(m.until).replace("T", " ").slice(0, 16)) : "PERMANENT BAN") + (m.reason ? " · " + esc(m.reason) : "") + "</span>" +
          '<div style="margin-top:8px"><button class="btn btn-ghost btn-sm" data-unmute="' + esc(m.handle) + '">Unmute</button></div></div>';
      }).join("") : '<p style="color:var(--muted);font-size:13px">Nobody is muted.</p>';
      html += '<h3 style="margin:22px 0 12px">Live feed (latest 100)</h3>';
      html += msgs.length ? msgs.map(function (m) {
        return '<div class="news-item" style="border-left-color:var(--purple)"><b>' + esc(m.handle) + '</b> · <span class="nd">' + esc(String(m.created_at).replace("T", " ").slice(0, 16)) + "</span><p>" + esc(m.body) + "</p>" +
          '<div style="margin-top:8px;display:flex;gap:8px;flex-wrap:wrap">' +
          '<button class="btn btn-ghost btn-sm" style="color:#ff6b6b" data-delmsg="' + m.id + '">Delete</button>' +
          '<button class="btn btn-ghost btn-sm" data-mute="' + esc(m.handle) + '" data-dur="1">Mute 1h</button>' +
          '<button class="btn btn-ghost btn-sm" data-mute="' + esc(m.handle) + '" data-dur="24">Mute 24h</button>' +
          '<button class="btn btn-ghost btn-sm" style="color:#ff6b6b" data-mute="' + esc(m.handle) + '" data-dur="0">Ban</button>' +
          "</div></div>";
      }).join("") : '<div class="empty"><div class="big">No messages yet</div><p>World Chat messages appear here once the lobby opens.</p></div>';
      html += '<p style="color:var(--muted);font-size:12px;margin-top:14px">This view refreshes every 15 seconds while it is open.</p>';
      body.innerHTML = html;
      els("[data-delmsg]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          if (!confirm("Delete this message permanently?")) return;
          call(function () { return api.aDel("chat_messages", "id=eq." + b.dataset.delmsg); })
            .then(function () { return api.audit("chat_delete", { id: b.dataset.delmsg }); })
            .then(renderChat)
            .catch(function (e) { alert("Delete failed: " + e.message); });
        });
      });
      els("[data-mute]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          var h = b.dataset.mute, d = parseInt(b.dataset.dur, 10);
          var until = d ? new Date(Date.now() + d * 3600000).toISOString() : null;
          if (!confirm(d ? ("Mute " + h + " for " + d + " hour(s)?") : ("Permanently ban " + h + " from World Chat?"))) return;
          call(function () { return api.aDel("chat_mutes", "handle=eq." + encodeURIComponent(h)).catch(function () {}); })
            .then(function () { return call(function () { return api.aPost("chat_mutes", { handle: h, until: until, reason: d ? ("muted " + d + "h by owner") : "banned by owner" }); }); })
            .then(function () { return api.audit("chat_mute", { handle: h, hours: d }); })
            .then(renderChat)
            .catch(function (e) { alert("Mute failed: " + e.message); });
        });
      });
      els("[data-unmute]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          call(function () { return api.aDel("chat_mutes", "handle=eq." + encodeURIComponent(b.dataset.unmute)); })
            .then(function () { return api.audit("chat_unmute", { handle: b.dataset.unmute }); })
            .then(renderChat);
        });
      });
      els("[data-resolve]", body).forEach(function (b) {
        b.addEventListener("click", function () {
          call(function () { return api.aPatch("chat_reports", "id=eq." + b.dataset.resolve, { resolved: true }); }).then(renderChat);
        });
      });
    }).catch(function () {
      body.innerHTML = '<div class="empty"><div class="big">Chat tables not set up yet</div><p>The World Chat schema is still being prepared on the server. The rest of the panel works normally.</p></div>';
    });
    if (!window.__xrChatTimer) {
      window.__xrChatTimer = setInterval(function () {
        var t = document.querySelector("#admin-tabs .lb-tab.active");
        if (t && t.dataset.sec === "chat") renderChat();
      }, 15000);
    }
  }

  function renderAudit() {
    var body = el("#admin-body");
    body.innerHTML = '<p style="color:var(--muted)">Loading…</p>';
    call(function () { return api.aGet("audit_log", "?select=*&order=created_at.desc&limit=50"); }).then(function (rows) {
      body.innerHTML = rows.length ? rows.map(function (r) {
        return '<div class="news-item" style="border-left-color:var(--gold)"><span class="ntag">' + esc(r.action) + '</span> · <span class="nd">' + esc(String(r.created_at).replace("T", " ").slice(0, 19)) + '</span><p>' + esc(r.actor) + " — " + esc(JSON.stringify(r.details)) + "</p></div>";
      }).join("") : '<div class="empty"><div class="big">No activity yet</div><p>Every change made in this panel is recorded here.</p></div>';
    });
  }


  /* ---------- AI Guard (fake detection) ---------- */
  function renderAIGuard() {
    var body = el("#admin-body");
    body.innerHTML = '<div class="card"><h3>🤖 AI Guard — fake detection</h3>' +
      '<p>Scans registrations, accounts, task submissions and World Chat for fake patterns. AI scores each flag. <b>It never bans on its own</b> — you review and decide.</p>' +
      '<button class="btn btn-red btn-sm" id="ag-scan" style="margin-top:8px">Run AI scan now</button> <span id="ag-scan-msg" style="color:var(--muted);font-size:13px"></span></div>' +
      '<div id="ag-list" style="margin-top:14px"><p style="color:var(--muted)">Loading flags…</p></div>';
    el("#ag-scan").addEventListener("click", function () {
      var b = el("#ag-scan"), m = el("#ag-scan-msg");
      b.disabled = true; m.textContent = "Scanning… this can take up to a minute.";
      call(function () {
        var s = api.session();
        return fetch(api.url + "/functions/v1/ai-guard", {
          method: "POST",
          headers: { "Authorization": "Bearer " + s.access_token, "Content-Type": "application/json" },
          body: "{}"
        }).then(function (r) { return r.json(); });
      }).then(function (d) {
        b.disabled = false;
        m.textContent = d && d.ok ? (d.flags === 0 ? "Scan done: all clear, no suspicious patterns." : "Scan done: " + d.flags + " flag(s)" + (d.ai ? "" : " — AI scoring unavailable, rule-based only")) : ("Scan failed: " + ((d && d.error) || "unknown"));
        loadFlags();
      }).catch(function (e) { b.disabled = false; m.textContent = "Scan failed: " + e.message; });
    });
    function loadFlags() {
      call(function () { return api.aGet("ai_flags", "?select=*&order=created_at.desc&limit=80"); }).then(function (rows) {
        var list = el("#ag-list");
        var pending = rows.filter(function (r) { return r.status === "pending"; });
        var done = rows.filter(function (r) { return r.status !== "pending"; });
        function confBadge(c) {
          if (c == null) return '<span style="color:var(--muted)">unscored</span>';
          var col = c >= 70 ? "#ff6b6b" : c >= 40 ? "#f5b301" : "#4ade80";
          return '<span style="color:' + col + ';font-weight:700">' + c + "% fake</span>";
        }
        function row(r) {
          var det = "";
          try { det = JSON.stringify(r.details).slice(0, 300); } catch (e) {}
          return '<div class="news-item" style="border-left-color:' + (r.status === "pending" ? "var(--purple)" : "var(--muted)") + '">' +
            '<h3 style="font-size:15px">' + esc(r.subject) + '</h3>' +
            '<p><b>' + esc(r.kind) + "</b> · " + confBadge(r.confidence) + " · " + String(r.created_at).slice(0, 16).replace("T", " ") + "</p>" +
            '<p style="color:var(--muted)">' + esc(r.reason || "") + "</p>" +
            '<p style="color:var(--muted);font-size:12px;word-break:break-all">' + esc(det) + "</p>" +
            (r.status === "pending" ?
              '<div style="margin-top:8px;display:flex;gap:8px">' +
              '<button class="btn btn-ghost btn-sm" data-ag-act="actioned" data-ag-id="' + r.id + '">✓ I handled this</button>' +
              '<button class="btn btn-ghost btn-sm" data-ag-act="dismissed" data-ag-id="' + r.id + '" style="color:var(--muted)">Dismiss (false alarm)</button></div>' :
              '<p style="margin-top:6px;color:var(--muted);font-size:12px">' + r.status + (r.reviewed_at ? " · " + String(r.reviewed_at).slice(0, 16).replace("T", " ") : "") + "</p>") +
            "</div>";
        }
        list.innerHTML = "<h3 style='margin:4px 0 10px'>Pending review (" + pending.length + ")</h3>" +
          (pending.length ? pending.map(row).join("") : '<div class="empty"><div class="big">All clear</div><p>No pending flags. Run a scan anytime.</p></div>') +
          (done.length ? "<h3 style='margin:18px 0 10px'>Reviewed (" + done.length + ")</h3>" + done.map(row).join("") : "");
        els("[data-ag-act]", list).forEach(function (b) {
          b.addEventListener("click", function () {
            b.disabled = true;
            call(function () { return api.aPatch("ai_flags", "id=eq." + b.dataset.agId, { status: b.dataset.agAct, reviewed_at: new Date().toISOString() }); })
              .then(function () { return api.audit("ai_flag_review", { id: b.dataset.agId, status: b.dataset.agAct }); })
              .then(loadFlags).catch(function (e) { b.disabled = false; alert("Failed: " + e.message); });
          });
        });
      }).catch(function () { el("#ag-list").innerHTML = '<p style="color:#ffb4ae">Could not load flags.</p>'; });
    }
    loadFlags();
  }

  /* ---------- Polls (voting system) ---------- */
  function renderPolls() {
    var body = el("#admin-body");
    function render() {
      call(function () { return api.aGet("polls", "?select=*&order=created_at.desc&limit=40"); }).then(function (polls) {
        var html = '<div class="card"><h3>🗳️ Create a poll</h3>' +
          '<div class="field"><label>Question</label><input id="pl-title" placeholder="e.g. Who is your Season 1 MVP?"></div>' +
          '<div class="field"><label>Description (optional)</label><input id="pl-desc" placeholder="One line shown under the question"></div>' +
          '<div class="field"><label>Options (one per line)</label><textarea id="pl-opts" rows="4" placeholder="Player A&#10;Squad B&#10;Player C"></textarea></div>' +
          '<label style="display:flex;gap:8px;align-items:center;font-size:14px"><input type="checkbox" id="pl-live" checked> Show live results while voting is open</label>' +
          '<div style="margin-top:10px;display:flex;gap:8px">' +
          '<button class="btn btn-gold btn-sm" id="pl-create-open">Create & open voting</button>' +
          '<button class="btn btn-ghost btn-sm" id="pl-create-draft">Save as draft</button></div>' +
          '<p id="pl-msg" style="margin-top:8px;font-size:13px"></p></div>';
        html += "<h3 style='margin:18px 0 10px'>Your polls</h3>";
        html += polls.length ? polls.map(function (p) {
          var statCol = p.status === "open" ? "#4ade80" : p.status === "closed" ? "var(--muted)" : "#f5b301";
          return '<div class="news-item" style="border-left-color:' + statCol + '">' +
            '<h3 style="font-size:16px">' + esc(p.title) + '</h3>' +
            '<p><b style="color:' + statCol + '">' + p.status.toUpperCase() + "</b> · " + (p.show_live ? "live results" : "results hidden until closed") + " · " + String(p.created_at).slice(0, 10) + "</p>" +
            '<div style="margin-top:8px;display:flex;gap:8px;flex-wrap:wrap">' +
            (p.status !== "open" ? '<button class="btn btn-ghost btn-sm" data-pl-open="' + p.id + '">Open voting</button>' : "") +
            (p.status === "open" ? '<button class="btn btn-ghost btn-sm" data-pl-close="' + p.id + '">Close voting</button>' : "") +
            '<button class="btn btn-ghost btn-sm" data-pl-res="' + p.id + '">Results</button>' +
            '<button class="btn btn-ghost btn-sm" data-pl-del="' + p.id + '" style="color:#ff6b6b">Delete</button></div>' +
            '<div data-pl-resbox="' + p.id + '"></div></div>';
        }).join("") : '<div class="empty"><div class="big">No polls yet</div><p>Create the first one above.</p></div>';
        body.innerHTML = html;

        function create(status) {
          var t = el("#pl-title").value.trim();
          var opts = el("#pl-opts").value.split("\n").map(function (x) { return x.trim(); }).filter(Boolean);
          var m = el("#pl-msg");
          if (!t || opts.length < 2) { m.textContent = "Need a question and at least 2 options."; m.style.color = "#ffb4ae"; return; }
          m.textContent = "Creating…"; m.style.color = "var(--muted)";
          call(function () { return api.aPost("polls", { title: t, description: el("#pl-desc").value.trim() || null, status: status, show_live: el("#pl-live").checked }); })
            .then(function (rows) {
              var pid = rows[0].id;
              var rows2 = opts.map(function (o, i) { return { poll_id: pid, label: o, sort: i }; });
              return call(function () { return api.aPost("poll_options", rows2); });
            })
            .then(function () { return api.audit("create_poll", { title: t, status: status }); })
            .then(render).catch(function (e) { m.textContent = "Failed: " + e.message; m.style.color = "#ffb4ae"; });
        }
        el("#pl-create-open").addEventListener("click", function () { create("open"); });
        el("#pl-create-draft").addEventListener("click", function () { create("draft"); });
        els("[data-pl-open]", body).forEach(function (b) { b.addEventListener("click", function () { b.disabled = true; call(function () { return api.aPatch("polls", "id=eq." + b.dataset.plOpen, { status: "open" }); }).then(render); }); });
        els("[data-pl-close]", body).forEach(function (b) { b.addEventListener("click", function () { b.disabled = true; call(function () { return api.aPatch("polls", "id=eq." + b.dataset.plClose, { status: "closed" }); }).then(render); }); });
        els("[data-pl-del]", body).forEach(function (b) { b.addEventListener("click", function () { if (!confirm("Delete this poll and all its votes?")) return; b.disabled = true; call(function () { return api.aDel("polls", "id=eq." + b.dataset.plDel); }).then(render); }); });
        els("[data-pl-res]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            var box = el('[data-pl-resbox="' + b.dataset.plRes + '"]', body);
            box.innerHTML = '<p style="color:var(--muted)">Loading…</p>';
            call(function () { return api.rpc("poll_results", { p_poll: b.dataset.plRes }, true); }).then(function (d) {
              if (!d || !d.ok) { box.innerHTML = '<p style="color:#ffb4ae">No results.</p>'; return; }
              var total = d.total || 0;
              box.innerHTML = '<div style="margin-top:8px">' + d.options.map(function (o) {
                var pct = total ? Math.round(100 * o.votes / total) : 0;
                return '<p style="margin:4px 0">' + esc(o.label) + " — <b>" + o.votes + "</b> (" + pct + "%)</p>";
              }).join("") + '<p style="color:var(--muted);font-size:12px">Total votes: ' + total + "</p></div>";
            });
          });
        });
      });
    }
    render();
  }


  /* ---------- Feed Queue (AI pre-screened; only the owner approves/rejects) ---------- */
  function renderFeedQueue() {
    var body = el("#admin-body");
    function render() {
      Promise.all([
        call(function () { return api.aGet("feed_posts", "?status=in.(held,pending)&order=created_at.asc&limit=50"); }),
        call(function () { return api.aGet("stories", "?status=in.(held,pending)&order=created_at.asc&limit=50"); }).catch(function () { return []; }),
        call(function () { return api.aGet("profiles", "?select=id,username,ign"); })
      ]).then(function (res) {
        var posts = res[0] || [], stories = res[1] || [], people = res[2] || [];
          var pmap = {};
          (people || []).forEach(function (p) { pmap[p.id] = p.ign || p.username || "player"; });
          var html = '<div class="card"><h3>Feed review queue</h3>' +
            '<p style="font-size:13px">The AI pre-screens every post. Clean ones publish automatically; these are the ones it was unsure about. Only you approve or reject - the AI never removes content.</p>' +
            (posts.length > 1 ? '<div style="display:flex;gap:8px;margin-top:10px;flex-wrap:wrap"><button class="btn btn-gold btn-sm" id="fq-all-ok">Approve all ' + posts.length + '</button><button class="btn btn-ghost btn-sm" id="fq-all-no" style="color:#ff6b6b">Reject all</button></div>' : "") + '</div>';
          html += posts.length ? "" : '<div class="empty"><div class="big">Queue is clear</div><p>Nothing waiting for review.</p></div>';
          html += posts.map(function (p) {
            var scoreCol = (p.ai_score || 0) >= 70 ? "#ff6b6b" : "#f5b301";
            return '<div class="news-item" style="border-left-color:' + scoreCol + '">' +
              '<h3 style="font-size:15px">' + esc(p.caption || "(" + p.kind + " post)") + '</h3>' +
              '<p><b>' + esc(p.kind.toUpperCase()) + '</b> · by <b>' + esc(pmap[p.user_id] || "player") + '</b> · ' + String(p.created_at).slice(0, 16).replace("T", " ") +
              (p.ai_score != null ? ' · AI risk score <b style="color:' + scoreCol + '">' + p.ai_score + "</b>" : "") + '</p>' +
              (p.ai_reason ? '<p style="font-size:12.5px;color:var(--muted)">AI note: ' + esc(p.ai_reason) + "</p>" : "") +
              (p.media_path ? '<div data-fq-media="' + esc(p.media_path) + '" style="margin:8px 0"><button class="btn btn-ghost btn-sm" data-fq-load="' + esc(p.media_path) + '" data-kind="' + p.kind + '">Load media</button></div>' : "") +
              '<div style="margin-top:8px;display:flex;gap:8px;flex-wrap:wrap">' +
              '<button class="btn btn-gold btn-sm" data-fq-approve="' + p.id + '">Approve</button>' +
              '<button class="btn btn-ghost btn-sm" data-fq-reject="' + p.id + '" style="color:#ff6b6b">Reject</button></div></div>';
          }).join("");
          body.innerHTML = html;
          var allOk = el("#fq-all-ok", body), allNo = el("#fq-all-no", body);
          function bulkAll(action) {
            var seq = Promise.resolve();
            posts.forEach(function (p) {
              seq = seq.then(function () { return call(function () { return api.rpc("admin_feed_review", { p_post: p.id, p_action: action }, true); }); });
            });
            seq.then(function () { return api.audit("feed_bulk_" + action, { count: posts.length }); }).then(render);
          }
          if (allOk) allOk.addEventListener("click", function () { allOk.disabled = true; bulkAll("approve"); });
          if (allNo) allNo.addEventListener("click", function () { if (!confirm("Reject ALL " + posts.length + " shown posts?")) return; allNo.disabled = true; bulkAll("reject"); });
          els("[data-fq-approve]", body).forEach(function (b) {
            b.addEventListener("click", function () {
              b.disabled = true;
              call(function () { return api.rpc("admin_feed_review", { p_post: parseInt(b.dataset.fqApprove, 10), p_action: "approve" }, true); })
                .then(function () { return api.audit("feed_approve", { post: b.dataset.fqApprove }); }).then(render);
            });
          });
          els("[data-fq-reject]", body).forEach(function (b) {
            b.addEventListener("click", function () {
              if (!confirm("Reject this post? It stays hidden from the feed.")) return;
              b.disabled = true;
              call(function () { return api.rpc("admin_feed_review", { p_post: parseInt(b.dataset.fqReject, 10), p_action: "reject" }, true); })
                .then(function () { return api.audit("feed_reject", { post: b.dataset.fqReject }); }).then(render);
            });
          });
          if (stories.length) {
            var shost = document.createElement("div");
            shost.innerHTML = '<div class="card" style="margin-top:16px"><h3>Story review queue</h3>' +
              '<p style="font-size:13px">24-hour stories waiting for review - same AI pre-screen as feed posts.</p></div>' +
              stories.map(function (t) {
                var sc = (t.ai_score || 0) >= 70 ? "#ff6b6b" : "#f5b301";
                return '<div class="news-item" style="border-left-color:' + sc + '">' +
                  '<h3 style="font-size:15px">' + esc(t.caption || "(" + t.kind + " story)") + '</h3>' +
                  '<p><b>STORY · ' + esc(t.kind.toUpperCase()) + '</b> · by <b>' + esc(pmap[t.user_id] || "player") + '</b> · ' + String(t.created_at).slice(0, 16).replace("T", " ") +
                  (t.ai_score != null ? ' · AI risk score <b style="color:' + sc + '">' + t.ai_score + "</b>" : "") + '</p>' +
                  (t.ai_reason ? '<p style="font-size:12.5px;color:var(--muted)">AI note: ' + esc(t.ai_reason) + "</p>" : "") +
                  (t.media_path && t.media_path.indexOf("http") === 0 ? '<div style="margin:8px 0">' + (t.kind === "clip" ? '<video controls style="max-width:200px;max-height:280px;background:#000" src="' + esc(t.media_path) + '"></video>' : '<img style="max-width:200px;max-height:280px" src="' + esc(t.media_path) + '">') + '</div>' : "") +
                  '<div style="margin-top:8px;display:flex;gap:8px;flex-wrap:wrap">' +
                  '<button class="btn btn-gold btn-sm" data-sq-approve="' + t.id + '">Approve</button>' +
                  '<button class="btn btn-ghost btn-sm" data-sq-reject="' + t.id + '" style="color:#ff6b6b">Reject</button></div></div>';
              }).join("");
            while (shost.firstChild) body.appendChild(shost.firstChild);
            els("[data-sq-approve]", body).forEach(function (b) {
              b.addEventListener("click", function () {
                b.disabled = true;
                call(function () { return api.rpc("admin_story_review", { p_id: parseInt(b.dataset.sqApprove, 10), p_action: "approve" }, true); })
                  .then(function () { return api.audit("story_approve", { story: b.dataset.sqApprove }); }).then(render);
              });
            });
            els("[data-sq-reject]", body).forEach(function (b) {
              b.addEventListener("click", function () {
                if (!confirm("Reject this story? It stays hidden.")) return;
                b.disabled = true;
                call(function () { return api.rpc("admin_story_review", { p_id: parseInt(b.dataset.sqReject, 10), p_action: "reject" }, true); })
                  .then(function () { return api.audit("story_reject", { story: b.dataset.sqReject }); }).then(render);
              });
            });
          }
          els("[data-fq-load]", body).forEach(function (b) {
            b.addEventListener("click", function () {
              var holder = el('[data-fq-media="' + b.dataset.fqLoad + '"]', body);
              b.disabled = true; b.textContent = "Loading…";
              call(function () { return api.userToken(); }).then(function () {
                return fetch(api.url + "/storage/v1/object/feed-media/" + b.dataset.fqLoad, { headers: { "apikey": api.key, "Authorization": "Bearer " + (JSON.parse(sessionStorage.getItem("xr_admin_session") || "{}").access_token || api.key) } });
              }).then(function (r) { return r.ok ? r.blob() : null; }).then(function (bl) {
                if (!bl) { holder.innerHTML = '<p style="color:#ffb4ae;font-size:13px">Could not load media.</p>'; return; }
                var url = URL.createObjectURL(bl);
                holder.innerHTML = b.dataset.kind === "clip"
                  ? '<video controls style="max-width:100%;max-height:320px;background:#000" src="' + url + '"></video>'
                  : '<img style="max-width:100%;max-height:320px" src="' + url + '">';
              });
            });
          });
      });
    }
    render();
  }

  /* ---------- Banners (home carousel posters) ---------- */
  function renderBanners() {
    var body = el("#admin-body");
    var editing = null;
    function fmt(dt) { return dt ? String(dt).slice(0, 16).replace("T", " ") : "-"; }
    function render() {
      call(function () { return api.aGet("banners", "?select=*&order=sort.asc,id.asc"); }).then(function (rows) {
        rows = rows || [];
        var html = '<div class="card"><h3>Home banners</h3>' +
          '<p style="font-size:13px">Posters shown in the carousel at the top of Home. Live means: active ON and inside its schedule window. Hidden automatically when none are live.</p></div>';
        html += rows.length ? "" : '<div class="empty"><div class="big">No banners yet</div><p>Add the first one below.</p></div>';
        html += rows.map(function (b) {
          var live = b.active && (!b.starts_at || new Date(b.starts_at) <= new Date()) && (!b.ends_at || new Date(b.ends_at) > new Date());
          return '<div class="news-item" style="border-left-color:' + (live ? "#4ade80" : "#8a7a92") + '">' +
            '<h3 style="font-size:15px">' + esc(b.title || "(no title)") + (live ? ' <span style="color:#4ade80;font-size:11px">LIVE</span>' : ' <span style="color:#8a7a92;font-size:11px">hidden</span>') + '</h3>' +
            '<p>#' + b.id + " · order <b>" + b.sort + "</b> · active <b>" + (b.active ? "on" : "off") + "</b> · window " + fmt(b.starts_at) + " &rarr; " + fmt(b.ends_at) + "</p>" +
            (b.link ? '<p style="font-size:12.5px;color:var(--muted)">Tap opens: ' + esc(b.link) + "</p>" : "") +
            '<div style="margin:8px 0"><img src="' + esc(b.image_path) + '" style="max-width:260px;max-height:120px;object-fit:cover;border-radius:8px"></div>' +
            '<div style="display:flex;gap:8px;flex-wrap:wrap">' +
            '<button class="btn btn-ghost btn-sm" data-bn-edit="' + b.id + '">Edit</button>' +
            '<button class="btn btn-ghost btn-sm" data-bn-del="' + b.id + '" style="color:#ff6b6b">Delete</button></div></div>';
        }).join("");
        html += '<div class="card" style="margin-top:16px"><h3 id="bn-form-title">Add a banner</h3>' +
          '<input type="hidden" id="bn-id">' +
          '<div class="field"><label style="font-size:13px">Poster image <span id="bn-img-req">(required)</span> - wide looks best (about 3:1), up to 5 MB</label>' +
          '<input type="file" id="bn-file" accept="image/*" style="padding:10px"><span id="bn-cur" style="font-size:12px;color:var(--muted)"></span></div>' +
          '<div class="field"><input type="text" id="bn-title" maxlength="120" placeholder="Title (optional, shown over the image)" style="width:100%;padding:10px"></div>' +
          '<div class="field"><input type="text" id="bn-link" maxlength="500" placeholder="Tap link (optional - page like feed.html or full https:// link)" style="width:100%;padding:10px"></div>' +
          '<div class="field" style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">' +
          '<label style="font-size:13px">Order <input type="number" id="bn-sort" value="0" style="width:70px;padding:8px"></label>' +
          '<label style="font-size:13px"><input type="checkbox" id="bn-active" checked> Active</label></div>' +
          '<div class="field" style="display:flex;gap:12px;flex-wrap:wrap">' +
          '<label style="font-size:13px">Show from (optional) <input type="datetime-local" id="bn-starts" style="padding:8px"></label>' +
          '<label style="font-size:13px">Hide after (optional) <input type="datetime-local" id="bn-ends" style="padding:8px"></label></div>' +
          '<div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">' +
          '<button class="btn btn-gold btn-sm" id="bn-save">Save banner</button>' +
          '<button class="btn btn-ghost btn-sm" id="bn-reset" style="display:none">Cancel edit</button>' +
          '<span id="bn-msg" style="font-size:13px"></span></div></div>';
        body.innerHTML = html;

        function toLocal(dt) {
          if (!dt) return "";
          var d = new Date(dt);
          return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
        }
        els("[data-bn-edit]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            var row = rows.filter(function (x) { return String(x.id) === b.dataset.bnEdit; })[0];
            if (!row) return;
            el("#bn-id", body).value = row.id;
            el("#bn-title", body).value = row.title || "";
            el("#bn-link", body).value = row.link || "";
            el("#bn-sort", body).value = row.sort;
            el("#bn-active", body).checked = !!row.active;
            el("#bn-starts", body).value = toLocal(row.starts_at);
            el("#bn-ends", body).value = toLocal(row.ends_at);
            el("#bn-img-req", body).textContent = "(leave empty to keep current)";
            el("#bn-cur", body).textContent = "current image kept unless you pick a new one";
            el("#bn-form-title", body).textContent = "Edit banner #" + row.id;
            el("#bn-reset", body).style.display = "";
            el("#bn-form-title", body).scrollIntoView({ block: "center" });
          });
        });
        el("#bn-reset", body).addEventListener("click", render);
        els("[data-bn-del]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            if (!confirm("Delete this banner? The image stays in storage but the banner is gone.")) return;
            b.disabled = true;
            call(function () { return api.rpc("admin_banner_delete", { p_id: parseInt(b.dataset.bnDel, 10) }, true); })
              .then(function () { return api.audit("banner_delete", { banner: b.dataset.bnDel }); }).then(render);
          });
        });
        el("#bn-save", body).addEventListener("click", function () {
          var idv = el("#bn-id", body).value;
          var file = el("#bn-file", body).files[0];
          var msg = el("#bn-msg", body);
          if (!idv && !file) { msg.textContent = "Pick a poster image first."; return; }
          if (file) {
            if (file.type.indexOf("image/") !== 0) { msg.textContent = "Only images."; return; }
            if (file.size > 5 * 1024 * 1024) { msg.textContent = "Image too large (max 5 MB)."; return; }
          }
          var btn = el("#bn-save", body); btn.disabled = true;
          msg.textContent = file ? "Uploading image…" : "Saving…";
          function upload() {
            if (!file) return Promise.resolve(null);
            return call(function () { return api.userToken(); }).then(function (tok) {
              return fetch(api.url + "/functions/v1/cloudinary-sign", {
                method: "POST",
                headers: { "apikey": api.key, "Authorization": "Bearer " + tok, "Content-Type": "application/json" },
                body: "{}"
              });
            }).then(function (r) { return r.json(); }).then(function (sig) {
              if (!sig || !sig.cloud) throw new Error("Upload unavailable.");
              var fd = new FormData();
              fd.append("file", file); fd.append("api_key", sig.key); fd.append("timestamp", sig.timestamp);
              fd.append("folder", sig.folder); fd.append("signature", sig.signature);
              return fetch("https://api.cloudinary.com/v1_1/" + sig.cloud + "/image/upload", { method: "POST", body: fd })
                .then(function (r) { return r.json(); })
                .then(function (up) { if (!up || !up.secure_url) throw new Error("Upload failed."); return up.secure_url; });
            });
          }
          function dtVal(id) { var v = el(id, body).value; return v ? new Date(v).toISOString() : null; }
          upload().then(function (url) {
            msg.textContent = "Saving…";
            return call(function () {
              return api.rpc("admin_banner_save", {
                p_id: idv ? parseInt(idv, 10) : null,
                p_title: el("#bn-title", body).value.trim(),
                p_image: url,
                p_link: el("#bn-link", body).value.trim(),
                p_sort: parseInt(el("#bn-sort", body).value || "0", 10),
                p_active: el("#bn-active", body).checked,
                p_starts: dtVal("#bn-starts"),
                p_ends: dtVal("#bn-ends")
              }, true);
            });
          }).then(function (r) {
            if (!r || !r.ok) throw new Error((r && r.error) || "Save failed.");
            return api.audit(idv ? "banner_edit" : "banner_add", { banner: r.id });
          }).then(function () { render(); })
            .catch(function (e) { btn.disabled = false; msg.textContent = e.message || "Could not save."; });
        });
      });
    }
    render();
  }

  /* ---------- Rewards (Arena Points catalog + redemption review) ---------- */
  function renderBrackets() {
    var body = el("#admin-body");
    function render() {
      Promise.all([
        call(function () { return api.aGet("tournaments", "?select=id,name&order=sort.asc"); }),
        call(function () { return api.aGet("bracket_matches", "?select=*&order=round.asc,position.asc"); }).catch(function () { return []; })
      ]).then(function (res) {
        var tours = res[0] || [], cells = res[1] || [];
        var tid = body.dataset.tid || (tours[0] && tours[0].id) || "";
        var tcells = cells.filter(function (c) { return c.tournament_id === tid; });
        function cellAt(rd, pos) { return tcells.filter(function (c) { return c.round === rd && c.position === pos; })[0] || {}; }
        var html = '<div class="card"><h3>Bracket editor</h3>' +
          '<p style="font-size:13px">Pick the two squads, then scores, then the winner - winners auto-advance into the next round. Save one match at a time. Everything is public instantly on the Tournaments page.</p>' +
          '<div class="field" style="margin-top:10px"><label>Tournament</label><select id="bk-t">' + tours.map(function (t) { return '<option value="' + esc(t.id) + '"' + (t.id === tid ? " selected" : "") + '>' + esc(t.name) + "</option>"; }).join("") + "</select></div>" +
          '<div class="field"><label>Rounds (cells per round)</label><select id="bk-rounds"><option value="1">Final only</option><option value="2" selected>Semis + Final</option><option value="3">Quarters + Semis + Final</option></select></div></div>';
        html += '<div id="bk-cells"></div>';
        body.innerHTML = html;
        function drawCells() {
          var rounds = parseInt(el("#bk-rounds", body).value, 10);
          var host = el("#bk-cells", body);
          var h = "";
          for (var rd = 1; rd <= rounds; rd++) {
            var n = Math.pow(2, rounds - rd);
            var names = { 1: "Final", 2: "Semi final", 4: "Quarter final" };
            h += '<h3 style="margin:14px 0 8px;font-size:14px;letter-spacing:2px">' + (names[n] || "Round " + rd) + (n > 1 ? "S" : "") + "</h3>";
            for (var pos = 1; pos <= n; pos++) {
              var c = cellAt(rd, pos);
              var idp = rd + "-" + pos;
              h += '<div class="news-item" style="border-left-color:var(--purple)"><div class="frow">' +
                '<div class="field"><input data-bk-a="' + idp + '" placeholder="Squad A" value="' + esc(c.squad_a || "") + '"></div>' +
                '<div class="field"><input data-bk-b="' + idp + '" placeholder="Squad B" value="' + esc(c.squad_b || "") + '"></div></div>' +
                '<div class="frow"><div class="field"><input type="number" data-bk-sa="' + idp + '" placeholder="Score A" value="' + (c.score_a == null ? "" : c.score_a) + '" style="width:90px"></div>' +
                '<div class="field"><input type="number" data-bk-sb="' + idp + '" placeholder="Score B" value="' + (c.score_b == null ? "" : c.score_b) + '" style="width:90px"></div>' +
                '<div class="field"><input data-bk-w="' + idp + '" placeholder="Winner (exact squad name)" value="' + esc(c.winner || "") + '"></div></div>' +
                '<div class="field"><label>Prediction lock time</label><input type="datetime-local" data-bk-start="' + idp + '" value="' + (c.starts_at ? new Date(new Date(c.starts_at).getTime() - new Date(c.starts_at).getTimezoneOffset()*60000).toISOString().slice(0,16) : '') + '"></div>' +
                '<button class="btn btn-gold btn-sm" data-bk-save="' + idp + '">Save match</button>' +
                (c.winner ? ' <button class="btn btn-ghost btn-sm" data-bk-settle="' + c.id + '">Award prediction XP</button>' : '') + '</div>';
            }
          }
          h += '<div style="margin-top:14px"><button class="btn btn-ghost btn-sm" id="bk-clear" style="color:#ff6b6b">Clear whole bracket</button></div>';
          host.innerHTML = h;
          els("[data-bk-save]", host).forEach(function (b) {
            b.addEventListener("click", function () {
              var parts = b.dataset.bkSave.split("-"), rd = parseInt(parts[0], 10), pos = parseInt(parts[1], 10);
              var data = { p_tournament: el("#bk-t", body).value, p_round: rd, p_position: pos,
                p_squad_a: el('[data-bk-a="' + b.dataset.bkSave + '"]', host).value.trim(),
                p_squad_b: el('[data-bk-b="' + b.dataset.bkSave + '"]', host).value.trim(),
                p_score_a: el('[data-bk-sa="' + b.dataset.bkSave + '"]', host).value ? parseInt(el('[data-bk-sa="' + b.dataset.bkSave + '"]', host).value, 10) : null,
                p_score_b: el('[data-bk-sb="' + b.dataset.bkSave + '"]', host).value ? parseInt(el('[data-bk-sb="' + b.dataset.bkSave + '"]', host).value, 10) : null,
                p_winner: el('[data-bk-w="' + b.dataset.bkSave + '"]', host).value.trim() };
              b.disabled = true;
              call(function () { return api.rpc("admin_bracket_set", data, true); }).then(function (r) {
                if (r && r.ok) {
                  var startInput=el('[data-bk-start="' + b.dataset.bkSave + '"]',host).value;
                  return api.aPatch("bracket_matches", "tournament_id=eq." + encodeURIComponent(data.p_tournament) + "&round=eq." + rd + "&position=eq." + pos, { starts_at: startInput ? new Date(startInput).toISOString() : null })
                    .then(function(){ return api.audit("bracket_set", { round:rd,pos:pos }); }).then(render);
                }
                b.disabled = false; alert((r && r.error) || "Save failed.");
              });
            });
          });
          els("[data-bk-settle]",host).forEach(function(b){ b.addEventListener("click",function(){
            if(!confirm("Award 50 XP to each correct prediction for this recorded result? Check the winner first.")) return;
            b.disabled=true;
            api.rpc("settle_bracket_predictions",{p_match:Number(b.dataset.bkSettle)},true).then(function(r){
              if(!r||!r.ok)throw new Error((r&&r.error)||"Settlement failed.");
              alert("Prediction XP awarded to "+r.awarded+" player(s). Repeating this action does not award twice.");render();
            }).catch(function(e){b.disabled=false;alert(e.message);});
          }); });
          el("#bk-clear", host).addEventListener("click", function () {
            if (!confirm("Clear the whole bracket for this tournament?")) return;
            call(function () { return api.rpc("admin_bracket_clear", { p_tournament: el("#bk-t", body).value }, true); })
              .then(function () { return api.audit("bracket_clear", {}); }).then(render);
          });
        }
        el("#bk-t", body).addEventListener("change", function () { body.dataset.tid = el("#bk-t", body).value; render(); });
        el("#bk-rounds", body).addEventListener("change", drawCells);
        drawCells();
      });
    }
    render();
  }

  function renderWars() {
    var body = el("#admin-body");
    function render() {
      call(function () { return api.aGet("squad_wars", "?select=*,challenger:squads!squad_wars_challenger_id_fkey(name),opponent:squads!squad_wars_opponent_id_fkey(name)&order=created_at.desc&limit=50"); }).then(function (wars) {
        wars = wars || [];
        var live = wars.filter(function (w) { return w.status !== "done" && w.status !== "cancelled" && w.status !== "declined"; });
        var done = wars.filter(function (w) { return live.indexOf(w) < 0; }).slice(0, 10);
        function row(w, actions) {
          var cn = (w.challenger && w.challenger.name) || "?", on = (w.opponent && w.opponent.name) || "?";
          var h = '<div class="news-item" style="border-left-color:' + (w.status === "done" ? "var(--muted)" : "var(--gold)") + '">' +
            '<h3 style="font-size:15px">' + esc(cn) + " vs " + esc(on) + ' <span style="font-size:12px;color:var(--muted)">' + esc(w.status) + (w.scheduled_at ? " · " + String(w.scheduled_at).slice(0, 16).replace("T", " ") : "") + "</span></h3>";
          if (w.status === "done") h += '<p style="font-size:13px">' + (w.winner_id ? "Winner: " + esc(w.winner_id === w.challenger_id ? cn : on) : "Draw") + (w.result ? " · " + esc(w.result) : "") + "</p>";
          if (actions) {
            h += '<div class="frow" style="margin-top:8px"><div class="field"><input type="datetime-local" data-wr-when="' + w.id + '"></div>' +
              '<div class="field"><select data-wr-win="' + w.id + '"><option value="">Draw</option><option value="' + w.challenger_id + '">' + esc(cn) + '</option><option value="' + w.opponent_id + '">' + esc(on) + "</option></select></div>" +
              '<div class="field"><input data-wr-res="' + w.id + '" placeholder="Result note (e.g. 2-1)" maxlength="60"></div></div>' +
              '<div style="display:flex;gap:8px;flex-wrap:wrap">' +
              '<button class="btn btn-gold btn-sm" data-wr-sched="' + w.id + '">Schedule</button>' +
              '<button class="btn btn-red btn-sm" data-wr-done="' + w.id + '">Post result (+25 pts to winners)</button>' +
              '<button class="btn btn-ghost btn-sm" data-wr-cancel="' + w.id + '" style="color:#ff6b6b">Cancel</button></div>';
          }
          return h + "</div>";
        }
        body.innerHTML = '<div class="card"><h3>Squad wars (' + live.length + " live)</h3>" +
          '<p style="font-size:13px">Captains challenge on the Squads page; accepted wars land here. Schedule them, then post the result - posting a winner pays each winning squad member +25 points automatically.</p></div>' +
          (live.length ? live.map(function (w) { return row(w, true); }).join("") : '<div class="empty"><div class="big">No live wars</div></div>') +
          (done.length ? '<div class="card" style="margin-top:16px"><h3>Recent results</h3></div>' + done.map(function (w) { return row(w, false); }).join("") : "");
        els("[data-wr-sched]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            var v = el('[data-wr-when="' + b.dataset.wrSched + '"]', body).value;
            if (!v) { alert("Pick a date/time first."); return; }
            b.disabled = true;
            call(function () { return api.rpc("admin_war_update", { p_war: parseInt(b.dataset.wrSched, 10), p_action: "schedule", p_scheduled_at: new Date(v).toISOString() }, true); })
              .then(function () { return api.audit("war_schedule", { war: b.dataset.wrSched }); }).then(render);
          });
        });
        els("[data-wr-done]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            var win = el('[data-wr-win="' + b.dataset.wrDone + '"]', body).value || null;
            var res = el('[data-wr-res="' + b.dataset.wrDone + '"]', body).value.trim() || null;
            if (!confirm("Post this result? Winning squad members get +25 points each.")) return;
            b.disabled = true;
            call(function () { return api.rpc("admin_war_update", { p_war: parseInt(b.dataset.wrDone, 10), p_action: "result", p_winner: win, p_result: res }, true); })
              .then(function (r) {
                if (r && r.ok) return api.audit("war_result", { war: b.dataset.wrDone }).then(render);
                b.disabled = false; alert((r && r.error) || "Failed.");
              });
          });
        });
        els("[data-wr-cancel]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            if (!confirm("Cancel this war?")) return;
            call(function () { return api.rpc("admin_war_update", { p_war: parseInt(b.dataset.wrCancel, 10), p_action: "cancel" }, true); })
              .then(function () { return api.audit("war_cancel", { war: b.dataset.wrCancel }); }).then(render);
          });
        });
      });
    }
    render();
  }

  function xrYtId(s){s=String(s||"").trim();if(!s)return"";var m=s.match(/(?:youtu\.be\/|youtube(?:-nocookie)?\.com\/(?:watch\?(?:[^#]*&)?v=|live\/|embed\/|shorts\/|v\/))([A-Za-z0-9_-]{11})/i);if(m)return m[1];m=s.match(/^[A-Za-z0-9_-]{11}$/);return m?m[0]:"";}
  function renderPlayerReports() {
    var body = el("#admin-body");
    function load() {
      call(function () { return api.rpc("admin_player_reports", {}, true); }).then(function (r) {
        if (!r || !r.ok) { body.innerHTML = '<div class="card">' + esc((r && r.error) || "Could not load.") + "</div>"; return; }
        body.innerHTML = '<div class="card"><h3>Player reports</h3>' + (r.rows.length ? r.rows.map(function (x) {
          return '<div style="padding:10px 0;border-bottom:1px solid rgba(255,255,255,.08)"><b>' + esc(x.reported) + "</b> reported by " + esc(x.reporter) + " \u00b7 " + esc(x.reason) + " \u00b7 " + String(x.created_at).slice(0, 16).replace("T", " ") + " \u00b7 total reports against: " + x.reports_against +
            (x.details ? "<p style='margin:4px 0'>" + esc(x.details) + "</p>" : "") + "<div>Status: <b>" + esc(x.status) + "</b>" + (x.admin_note ? " \u00b7 " + esc(x.admin_note) : "") + "</div>" +
            (x.status === "open" ? '<div style="margin-top:6px"><button class="btn btn-ghost btn-sm" data-pr="' + x.id + '" data-st="dismissed">Dismiss</button> <button class="btn btn-red btn-sm" data-pr="' + x.id + '" data-st="actioned">Mark actioned</button> <span style="font-size:12px;color:var(--muted)">Ban or warn from the Players tab.</span></div>' : "") + "</div>";
        }).join("") : "<p>No reports yet.</p>") + "</div>";
        body.querySelectorAll("[data-pr]").forEach(function (b) {
          b.addEventListener("click", function () {
            var note = prompt("Note (optional)", "") || "";
            call(function () { return api.rpc("admin_handle_player_report", { p_id: parseInt(b.getAttribute("data-pr"), 10), p_status: b.getAttribute("data-st"), p_note: note }, true); }).then(load);
          });
        });
      });
    }
    load();
  }

  function renderPhoneCheck(host) {
    call(function () { return api.rpc("admin_phone_check", {}, true); }).then(function (r) {
      if (!r || !r.ok) { host.innerHTML = '<div class="card">' + esc((r && r.error) || "Phone check could not load.") + "</div>"; return; }
      var col = function (n) { return n >= 50 ? "#ff5d5d" : n >= 25 ? "#f5b301" : "#46d17f"; };
      var lab = function (n) { return n >= 50 ? "HIGH" : n >= 25 ? "MEDIUM" : "LOW"; };
      host.innerHTML = '<div class="card"><h3>Phone Check</h3><p style="font-size:13px">A free rule-based estimate of whether each number looks real: format, fake or patterned digits, similar numbers, shared devices or networks and sign-up bursts. <b>It estimates only. It cannot prove who owns a number.</b> Numbers are masked.</p>' + (r.rows.length ? r.rows.map(function (a) {
        return '<div style="padding:10px 0;border-top:1px solid rgba(255,255,255,.08)"><b>' + esc(a.username) + "</b>" + (a.ign ? " (" + esc(a.ign) + ")" : "") + " \u00b7 " + esc(a.masked || "-") + ' \u00b7 <b style="color:' + col(a.score) + '">' + lab(a.score) + " " + a.score + "/100</b>" + (a.banned ? " \u00b7 BANNED" : "") + (a.phone_verified ? " \u00b7 code confirmed" : "") +
          (a.reasons && a.reasons.length ? "<ul style='margin:6px 0 6px 18px;font-size:13px'>" + a.reasons.map(function (x) { return "<li>" + esc(x) + "</li>"; }).join("") + "</ul>" : "<div style='font-size:13px;margin:4px 0'>No warning signs found.</div>") +
          (a.score >= 25 && !a.banned ? '<button class="btn btn-ghost btn-sm" data-pcflag="' + a.id + '">Flag for review</button> <button class="btn btn-red btn-sm" data-pcban="' + a.id + '" data-un="' + esc(a.username) + '">Suspend</button>' : "") + "</div>";
      }).join("") : "<p>No accounts with a phone number yet.</p>") + "</div>";
      host.querySelectorAll("[data-pcflag]").forEach(function (b) { b.addEventListener("click", function () {
        var note = prompt("Note (optional)", "") || "";
        call(function () { return api.rpc("admin_flag_account", { p_user: b.getAttribute("data-pcflag"), p_note: note }, true); }).then(function () { b.textContent = "Flagged"; b.disabled = true; });
      }); });
      host.querySelectorAll("[data-pcban]").forEach(function (b) { b.addEventListener("click", function () {
        var un = b.getAttribute("data-un");
        if (!confirm("Suspend " + un + "? They will be banned from XARENA until you lift it. The phone check is only an estimate, so be sure.")) return;
        var why = prompt("Reason (shown to the player)", "Suspicious phone number") || "Suspicious phone number";
        call(function () { return api.aPatch("profiles", "id=eq." + b.getAttribute("data-pcban"), { banned: true, banned_reason: why }); })
          .then(function () { return api.audit("phonecheck_suspend", { user: un }); }).then(function () { renderPhoneCheck(host); })
          .catch(function (e) { alert("Failed: " + e.message); });
      }); });
    });
  }

  function renderSuspicious() {
    var body = el("#admin-body");
    body.innerHTML = '<div id="pc-host"></div><div id="sa-host"></div>';
    var pcHost = el("#pc-host", body), body2 = el("#sa-host", body); renderPhoneCheck(pcHost);
    var labels = { device: "Same device", fingerprint: "Similar device profile", network: "Same network, many new accounts" };
    function load() {
      call(function () { return api.rpc("admin_suspicious_accounts", {}, true); }).then(function (r) {
        if (!r || !r.ok) { body2.innerHTML = '<div class="card">' + esc((r && r.error) || "Could not load.") + "</div>"; return; }
        body2.innerHTML = '<div class="card"><h3>Suspicious accounts</h3><p style="font-size:13px">Groups of accounts that share a device, device profile or network. Nothing is banned automatically. Shared family phones and campus Wi-Fi can look the same, so check before acting. Phone numbers are never shown here.</p>' + (r.rows.length ? r.rows.map(function (g) {
          return '<div style="padding:10px 0;border-top:1px solid rgba(255,255,255,.08)"><b>' + esc(labels[g.kind] || g.kind) + "</b> \u00b7 " + String(g.created_at).slice(0, 16).replace("T", " ") + " \u00b7 " + esc(g.status) + (g.note ? " \u00b7 " + esc(g.note) : "") +
            "<ul style='margin:6px 0 6px 18px'>" + (g.accounts || []).map(function (a) { return "<li>" + esc(a.username) + (a.ign ? " (" + esc(a.ign) + ")" : "") + " \u00b7 joined " + String(a.created_at).slice(0, 10) + (a.verified ? " \u00b7 verified" : "") + (a.banned ? " \u00b7 BANNED" : ' <button class="btn btn-red btn-sm" style="padding:2px 8px" data-ban="' + a.id + '" data-un="' + esc(a.username) + '">Suspend</button>') + "</li>"; }).join("") + "</ul>" +
            (g.status === "open" ? '<button class="btn btn-ghost btn-sm" data-af="' + g.id + '" data-st="dismissed">Looks fine</button> <button class="btn btn-red btn-sm" data-af="' + g.id + '" data-st="actioned">Mark handled</button>' : "") + "</div>";
        }).join("") : "<p>No suspicious groups right now.</p>") + "</div>";
        body2.querySelectorAll("[data-ban]").forEach(function (b) {
          b.addEventListener("click", function () {
            var un = b.getAttribute("data-un");
            if (!confirm("Suspend " + un + "? They will be banned from XARENA until you lift it. This does not touch other accounts in the group.")) return;
            var why = prompt("Reason (shown to the player)", "Suspicious duplicate account") || "Suspicious duplicate account";
            call(function () { return api.aPatch("profiles", "id=eq." + b.getAttribute("data-ban"), { banned: true, banned_reason: why }); })
              .then(function () { return api.audit("abuse_suspend", { user: un }); }).then(load)
              .catch(function (e) { alert("Failed: " + e.message); });
          });
        });
        body2.querySelectorAll("[data-af]").forEach(function (b) {
          b.addEventListener("click", function () {
            var note = prompt("Note (optional)", "") || "";
            call(function () { return api.rpc("admin_handle_abuse_flag", { p_id: parseInt(b.getAttribute("data-af"), 10), p_status: b.getAttribute("data-st"), p_note: note }, true); }).then(load);
          });
        });
      });
    }
    load();
  }

  function renderCreators() {
    var body = el("#admin-body");
    function load() {
      call(function () { return api.rpc("admin_creator_queue", {}, true); }).then(function (r) {
        if (!r || !r.ok) { body.innerHTML = '<div class="card">' + esc((r && r.error) || "Could not load.") + "</div>"; return; }
        var h = '<div class="card"><h3>Creator applications</h3><p style="font-size:13px">Approve to give the verified creator badge and let them host FREE-entry events. Followers are self-reported, not verified.</p>' +
          (r.pending.length ? r.pending.map(function (a) { return '<div style="padding:10px 0;border-top:1px solid rgba(255,255,255,.08)"><b>' + esc(a.name) + "</b> &middot; " + esc(a.self_followers) + " followers (self-reported)<br><a href=\"" + esc(a.channel_url) + '" target="_blank" rel="noopener noreferrer">' + esc(a.channel_url) + "</a>" + (a.note ? "<br>" + esc(a.note) : "") + '<br><button class="btn btn-sm" data-ap="' + a.id + '" data-y="1">Approve</button> <button class="btn btn-ghost btn-sm" data-ap="' + a.id + '" data-y="0">Reject</button></div>'; }).join("") : "<p>No pending applications.</p>") + "</div>";
        h += '<div class="card"><h3>Grant a creator directly</h3><input id="cg-u" placeholder="username" style="margin:4px 0"> <input id="cg-f" type="number" placeholder="followers (self-reported)" style="margin:4px 0"> <button class="btn btn-sm" id="cg-go">Grant</button><div id="cg-m" style="font-size:13px;margin-top:6px"></div></div>';
        h += '<div class="card"><h3>Approved creators</h3>' + (r.creators.length ? r.creators.map(function (c) { return '<div style="padding:8px 0;border-top:1px solid rgba(255,255,255,.08)">' + esc(c.name) + " &middot; " + esc(c.tier) + " &middot; " + esc(c.self_followers) + (c.active ? "" : " &middot; revoked") + (c.active ? ' <button class="btn btn-red btn-sm" data-rv="' + c.user_id + '">Revoke</button>' : "") + "</div>"; }).join("") : "<p>None yet.</p>") + "</div>";
        h += '<div class="card"><h3>Creator events</h3>' + (r.events.length ? r.events.map(function (e) { return '<div style="padding:8px 0;border-top:1px solid rgba(255,255,255,.08)">' + esc(e.title) + " &middot; " + esc(e.host) + " &middot; " + esc(e.status) + " &middot; " + String(e.starts_at).slice(0, 16).replace("T", " ") + (e.reports ? " &middot; <b>" + e.reports + " report(s)</b>" : "") + ((e.status === "scheduled" || e.status === "live") ? ' <button class="btn btn-red btn-sm" data-ce="' + e.id + '">Cancel</button>' : "") + "</div>"; }).join("") : "<p>No events.</p>") + "</div>";
        h += '<div class="card" id="csr-card"><h3>Creator sponsor requests</h3><p>Loading…</p></div>';
        body.innerHTML = h;
        body.querySelectorAll("[data-ap]").forEach(function (b) { b.addEventListener("click", function () { call(function () { return api.rpc("admin_creator_review", { p_id: parseInt(b.getAttribute("data-ap"), 10), p_approve: b.getAttribute("data-y") === "1" }, true); }).then(load); }); });
        body.querySelectorAll("[data-rv]").forEach(function (b) { b.addEventListener("click", function () { if (confirm("Revoke this creator? Their open events are cancelled.")) call(function () { return api.rpc("admin_creator_revoke", { p_user: b.getAttribute("data-rv") }, true); }).then(load); }); });
        body.querySelectorAll("[data-ce]").forEach(function (b) { b.addEventListener("click", function () { if (confirm("Cancel this event?")) call(function () { return api.rpc("creator_event_manage", { p_event: b.getAttribute("data-ce"), p_action: "cancel" }, true); }).then(load); }); });
        call(function () { return api.rpc("admin_creator_sponsor_requests", {}, true); }).then(function (x) { var c = el("#csr-card", body); if (!c || !x || !x.ok) return;
          c.innerHTML = "<h3>Creator sponsor requests</h3>" + (x.requests.length ? x.requests.map(function (q) { return '<div style="padding:8px 0;border-top:1px solid rgba(255,255,255,.08)"><b>' + esc(q.creator) + "</b> &rarr; " + esc(q.brand || "any brand") + " &middot; " + esc(q.status) + "<br>" + esc(q.pitch) + (q.status === "pending" ? '<br><button class="btn btn-sm" data-sr="' + q.id + '" data-s="introduced">Mark introduced</button> <button class="btn btn-ghost btn-sm" data-sr="' + q.id + '" data-s="declined">Decline</button>' : "") + "</div>"; }).join("") : "<p>No requests.</p>");
          c.querySelectorAll("[data-sr]").forEach(function (b) { b.addEventListener("click", function () { call(function () { return api.rpc("admin_creator_sponsor_handle", { p_id: parseInt(b.getAttribute("data-sr"), 10), p_status: b.getAttribute("data-s") }, true); }).then(load); }); }); });
        el("#cg-go", body).addEventListener("click", function () { call(function () { return api.rpc("admin_creator_grant", { p_username: el("#cg-u", body).value, p_followers: parseInt(el("#cg-f", body).value, 10) || 0 }, true); }).then(function (x) { if (x && x.ok) load(); else el("#cg-m", body).textContent = (x && x.error) || "Failed."; }); });
      });
    }
    load();
  }

  function renderTips() {
    var body = el("#admin-body");
    call(function () { return api.aGet("site_settings", "?key=eq.tips&select=value"); }).then(function (rows) {
      var v = (rows && rows[0] && rows[0].value) || {}, items = Array.isArray(v.items) ? v.items : [];
      body.innerHTML = '<div class="card"><h3>Aaj ka tip (home page)</h3><p style="font-size:13px">One tip per line (max 60 tips, 160 characters each). The home page shows one tip per day, rotating. With no tips the card is hidden.</p>' +
        '<textarea id="tp-x" rows="10" style="width:100%">' + esc(items.join("\n")) + '</textarea><div style="margin-top:8px"><button class="btn btn-red btn-sm" id="tp-s">Save tips</button> <span id="tp-m" style="font-size:13px"></span></div></div>';
      el("#tp-s", body).addEventListener("click", function () {
        var list = el("#tp-x", body).value.split("\n").map(function (x) { return x.trim().slice(0, 160); }).filter(Boolean).slice(0, 60);
        call(function () { return api.aUpsert("site_settings", { key: "tips", value: { items: list }, updated_at: new Date().toISOString() }, "key"); })
          .then(function () { return api.audit("tips_set", { n: list.length }); })
          .then(function () { el("#tp-m", body).textContent = "Saved " + list.length + " tips."; }).catch(function (e) { el("#tp-m", body).textContent = "Failed: " + e.message; });
      });
    });
  }

  function renderControl() {
    var body = el("#admin-body");
    body.innerHTML = '<p style="color:var(--muted)">Loading…</p>';
    var since = new Date(Date.now() - 86400000).toISOString();
    Promise.all([
      call(function () { return api.aGet("tournaments", "?select=id,name,status,reg_open,slots&order=sort.asc"); }),
      call(function () { return api.aGet("registrations", "?select=id,tournament_id,squad,ign,status,verified,hold,created_at&order=created_at.desc&limit=1000"); }),
      call(function () { return api.aGet("match_checkins", "?select=match_id,checked_in_at&checked_in_at=gte." + since); }).catch(function () { return []; }),
      call(function () { return api.aGet("support_acks", "?select=id,status&status=eq.pending"); }).catch(function () { return []; }),
      call(function () { return api.aGet("matches", "?select=id,title,starts_at&order=created_at.desc&limit=20"); }).catch(function () { return []; })
    ]).then(function (r) {
      var ts = r[0] || [], regs = r[1] || [], chk = r[2] || [], acks = Array.isArray(r[3]) ? r[3] : [], ms = r[4] || [];
      var today = regs.filter(function (x) { return x.created_at >= since; }).length;
      var pend = regs.filter(function (x) { return x.status === "pending"; }).length;
      var h = '<div class="grid c3"><div class="card"><h3>' + today + '</h3><p>Registrations, last 24h</p></div><div class="card"><h3>' + chk.length + '</h3><p>Match check-ins, last 24h</p></div><div class="card"><h3>' + (pend + acks.length) + '</h3><p>Pending approvals (' + pend + ' registrations, ' + acks.length + ' support)</p></div></div>';
      h += '<div class="card" style="margin-top:14px"><h3>Quick actions</h3>' + (ts.map(function (t) {
        var n = regs.filter(function (x) { return x.tournament_id === t.id && !/rejected|cancelled|declined|waitlist/.test(x.status); }).length;
        return '<div style="padding:8px 0;border-bottom:1px solid #8883"><b>' + esc(t.name) + "</b> · " + n + "/" + t.slots + " squads · status <b>" + esc(t.status) + "</b> · registration <b>" + (t.reg_open ? "OPEN" : "CLOSED") + '</b><br>' +
          '<button class="btn btn-sm" data-reg="' + esc(t.id) + '" data-v="' + (t.reg_open ? "0" : "1") + '">' + (t.reg_open ? "Close" : "Open") + ' registration</button> ' +
          '<button class="btn btn-sm" data-wl="' + esc(t.id) + '">Promote next from waitlist (' + regs.filter(function (x) { return x.tournament_id === t.id && x.status === "waitlist"; }).length + ')</button> <button class="btn btn-sm" data-st="' + esc(t.id) + '" data-v="upcoming">Set upcoming</button> <button class="btn btn-sm" data-st="' + esc(t.id) + '" data-v="live">Set live</button></div>';
      }).join("") || "No tournaments.") + '<div id="qa-m" style="margin-top:8px"></div></div>';
      h += '<div class="card" style="margin-top:14px"><h3>Registrations</h3><input id="rg-q" placeholder="Search squad or IGN" style="width:100%"> <select id="rg-s"><option value="">All statuses</option><option>pending</option><option>approved</option><option>rejected</option></select> <button class="btn btn-sm" id="rg-csv">Export CSV (no phone/UID)</button><div id="rg-l"></div></div>';
      h += '<div class="card" style="margin-top:14px"><h3>Match wizard</h3><p style="font-size:13px">Creates 3 qualifier lobbies and 1 final for a tournament. Room ID and password stay empty here: add them in Match Results when ready (players see them only after the reveal time).</p><select id="mw-t">' + ts.map(function (t) { return '<option value="' + esc(t.id) + '">' + esc(t.name) + "</option>"; }).join("") + '</select> <input type="datetime-local" id="mw-d"> <input type="number" id="mw-g" value="45" min="10" max="240" style="width:90px"> min apart <button class="btn btn-red btn-sm" id="mw-go">Create 4 matches</button> <span id="mw-m"></span><p style="font-size:12px">Existing: ' + ms.map(function (m) { return esc(m.title); }).join(", ") + '</p></div>';
      h += '<div class="card" style="margin-top:14px"><h3>Backup / data export</h3><p style="font-size:13px">Downloads a JSON of tournaments, matches, results, hall of fame, spotlights, notices, news (no phones, UIDs, devices or passwords).</p><button class="btn btn-sm" id="bk-go">Download backup</button></div>';
      h += '<div class="card" style="margin-top:14px"><h3>Feature flags</h3><div id="fl"></div></div>';
      body.innerHTML = h;
      function msg(t) { el("#qa-m", body).textContent = t; }
      body.querySelectorAll("[data-reg]").forEach(function (b) { b.addEventListener("click", function () { if (!confirm((b.dataset.v === "1" ? "Open" : "Close") + " registration?")) return; call(function () { return api.aPatch("tournaments", "id=eq." + b.dataset.reg, { reg_open: b.dataset.v === "1" }); }).then(function () { return api.audit("reg_open_set", { id: b.dataset.reg, open: b.dataset.v === "1" }); }).then(renderControl).catch(function (e) { msg("Failed: " + e.message); }); }); });
      body.querySelectorAll("[data-wl]").forEach(function (b) { b.addEventListener("click", function () { call(function () { return api.rpc("admin_promote_waitlist", { p_tournament: b.dataset.wl }, true); }).then(function (r) { msg(r && r.ok ? "Promoted " + r.id : (r && r.error) || "Failed"); }); }); });
      body.querySelectorAll("[data-st]").forEach(function (b) { b.addEventListener("click", function () { if (!confirm("Set status to " + b.dataset.v + "?")) return; call(function () { return api.aPatch("tournaments", "id=eq." + b.dataset.st, { status: b.dataset.v }); }).then(function () { return api.audit("tournament_status_set", { id: b.dataset.st, status: b.dataset.v }); }).then(renderControl).catch(function (e) { msg("Failed: " + e.message); }); }); });
      function paint() {
        var q = el("#rg-q", body).value.toLowerCase(), s = el("#rg-s", body).value;
        var f = regs.filter(function (x) { return (!s || x.status === s) && (!q || (x.squad + " " + x.ign).toLowerCase().indexOf(q) >= 0); });
        el("#rg-l", body).innerHTML = f.length ? f.slice(0, 100).map(function (x) { return "<div style='padding:4px 0;border-bottom:1px solid #8882'>" + esc(x.squad) + " · " + esc(x.ign) + " · " + esc(x.status) + (x.verified ? " · verified" : "") + (x.hold ? " · HOLD" : "") + "</div>"; }).join("") : "<p>No registrations match.</p>";
        return f;
      }
      el("#rg-q", body).addEventListener("input", paint); el("#rg-s", body).addEventListener("change", paint); paint();
      function dl(name, text, type) { var a = document.createElement("a"); a.href = URL.createObjectURL(new Blob([text], { type: type })); a.download = name; document.body.appendChild(a); a.click(); a.remove(); }
      el("#rg-csv", body).addEventListener("click", function () {
        var f = paint(), cell = function (v) { v = String(v == null ? "" : v); if (/^[=+\-@]/.test(v)) v = "'" + v; return '"' + v.replace(/"/g, '""') + '"'; };
        dl("registrations.csv", "tournament,squad,ign,status,verified,hold,created_at\n" + f.map(function (x) { return [x.tournament_id, x.squad, x.ign, x.status, x.verified, x.hold, x.created_at].map(cell).join(","); }).join("\n"), "text/csv");
      });
      el("#mw-go", body).addEventListener("click", function () {
        var d = el("#mw-d", body).value, g = +el("#mw-g", body).value || 45, m = el("#mw-m", body), t = el("#mw-t", body).value;
        if (!d || !t) { m.textContent = "Pick a tournament and a start time."; return; }
        if (!confirm("Create 4 matches?")) return;
        var base = new Date(d).getTime(), names = [["Qualifier Lobby A", "Qualifier"], ["Qualifier Lobby B", "Qualifier"], ["Qualifier Lobby C", "Qualifier"], ["Grand Final", "Final"]];
        var rows = names.map(function (n, i) { var s = new Date(base + i * g * 60000).toISOString(); return { tournament_id: t, title: n[0], stage: n[1], starts_at: s, played_at: s, reveal_minutes: 15, status: "scheduled" }; });
        call(function () { return api.aPost("matches", rows); }).then(function () { return api.audit("match_wizard", { t: t, n: 4 }); }).then(function () { m.textContent = "Created 4 matches."; }).catch(function (e) { m.textContent = "Failed: " + e.message; });
      });
      el("#bk-go", body).addEventListener("click", function () {
        var T = [["tournaments", "?select=*"], ["matches", "?select=id,tournament_id,title,stage,starts_at,status"], ["lb_entries", "?select=*"], ["hall_of_fame", "?select=*"], ["spotlights", "?select=*"], ["notices", "?select=*"], ["news", "?select=*"]];
        Promise.all(T.map(function (x) { return call(function () { return api.aGet(x[0], x[1]); }).catch(function () { return []; }); })).then(function (all) { var o = { exported_at: new Date().toISOString() }; T.forEach(function (x, i) { o[x[0]] = all[i]; }); dl("xarena-backup.json", JSON.stringify(o, null, 1), "application/json"); });
      });
      call(function () { return api.aGet("site_settings", "?key=eq.flags&select=value"); }).then(function (fr) {
        var v = (fr && fr[0] && fr[0].value) || {}, ks = ["live_thread", "mini_cup", "spectator", "dup_check", "waitlist"];
        el("#fl", body).innerHTML = '<p style="font-size:13px">Risky features stay OFF until tested. Turning one ON is your decision.</p>' + ks.map(function (k) { return '<label style="display:block;padding:4px 0"><input type="checkbox" data-f="' + k + '"' + (v[k] ? " checked" : "") + "> " + k + "</label>"; }).join("");
        body.querySelectorAll("[data-f]").forEach(function (c) { c.addEventListener("change", function () { v[c.dataset.f] = c.checked; call(function () { return api.aUpsert("site_settings", { key: "flags", value: v, updated_at: new Date().toISOString() }, "key"); }).then(function () { return api.audit("flag_set", { k: c.dataset.f, on: c.checked }); }); }); });
      });
    });
  }

  function renderThread() {
    var body = el("#admin-body");
    function load() {
      Promise.all([call(function () { return api.aGet("match_thread_posts", "?select=id,body,hidden,created_at,match_id&order=id.desc&limit=80"); }), call(function () { return api.aGet("match_thread_reports", "?select=post_id"); })]).then(function (r) {
        var rows = r[0] || [], rep = {}; (r[1] || []).forEach(function (x) { rep[x.post_id] = (rep[x.post_id] || 0) + 1; });
        body.innerHTML = '<div class="card"><h3>Match thread moderation</h3><p style="font-size:13px">Newest first. Posts auto-hide at 3 reports. The thread is open only when the live_thread flag is ON (Control tab).</p>' + (rows.map(function (x) { return '<div style="padding:5px 0;border-bottom:1px solid #8882">' + (x.hidden ? "[hidden] " : "") + esc(x.body) + " <small>(" + (rep[x.id] || 0) + ' reports)</small> <button class="btn btn-sm" data-h="' + x.id + '" data-v="' + (x.hidden ? "0" : "1") + '">' + (x.hidden ? "Unhide" : "Hide") + "</button></div>"; }).join("") || "No posts yet.") + "</div>";
        body.querySelectorAll("[data-h]").forEach(function (b) { b.addEventListener("click", function () { call(function () { return api.rpc("admin_thread_hide", { p_post: +b.dataset.h, p_hidden: b.dataset.v === "1" }, true); }).then(load); }); });
      });
    }
    load();
  }
  function renderHelpers() {
    var body = el("#admin-body");
    function load() {
      call(function () { return api.rpc("admin_list_helpers", {}, true); }).then(function (r) {
        var l = (r && r.helpers) || [];
        body.innerHTML = '<div class="card"><h3>Helpers</h3><p style="font-size:13px">A helper can set Room ID/password, enter match results and view registrations (no phone, UID or IP) at helper.html. Nothing else. Every action is in the activity log.</p><input id="hp-u" placeholder="Player username"> <button class="btn btn-red btn-sm" id="hp-a">Add helper</button> <span id="hp-m"></span>' + (l.map(function (h) { return '<div style="padding:5px 0">' + esc(h.username || h.user_id) + ' <button class="btn btn-sm" data-rm="' + esc(h.user_id) + '">Remove</button></div>'; }).join("") || "<p>No helpers.</p>") + "</div>";
        el("#hp-a", body).addEventListener("click", function () { call(function () { return api.rpc("admin_add_helper", { p_username: el("#hp-u", body).value }, true); }).then(function (x) { if (x && x.ok) load(); else el("#hp-m", body).textContent = (x && x.error) || "Failed"; }); });
        body.querySelectorAll("[data-rm]").forEach(function (b) { b.addEventListener("click", function () { call(function () { return api.rpc("admin_remove_helper", { p_user: b.dataset.rm }, true); }).then(load); }); });
      });
    }
    load();
  }
  function renderSpot() {
    var body = el("#admin-body"), KINDS = { mvp_match: "MVP of the match", mvp_week: "MVP of the week", squad_week: "Squad of the Week", player_day: "Player of the Day", clip_week: "Clip of the week" };
    function load() {
      call(function () { return api.aGet("spotlights", "?select=*&order=created_at.desc&limit=40"); }).then(function (rows) {
        body.innerHTML = '<div class="card"><h3>Add spotlight</h3><div class="field"><label>Type</label><select id="sp-k">' + Object.keys(KINDS).map(function (k) { return '<option value="' + k + '">' + KINDS[k] + "</option>"; }).join("") + '</select></div>' +
          '<div class="field"><label>Name</label><input id="sp-n" maxlength="80"></div><div class="field"><label>Detail (optional)</label><input id="sp-d" maxlength="240"></div><div class="field"><label>Logo URL (optional, https)</label><input id="sp-l"></div><div class="field"><label>Link (optional, https; for clip)</label><input id="sp-r"></div>' +
          '<button class="btn btn-red btn-sm" id="sp-s">Save</button> <span id="sp-m"></span></div><div class="card"><h3>Recent</h3>' + ((rows || []).map(function (r) { return "<div>" + esc(KINDS[r.kind]) + ": <b>" + esc(r.name) + '</b> <button class="btn btn-sm" data-del="' + r.id + '">Delete</button></div>'; }).join("") || "None yet.") + "</div>";
        el("#sp-s", body).addEventListener("click", function () {
          var l = el("#sp-l", body).value.trim(), r = el("#sp-r", body).value.trim(), m = el("#sp-m", body);
          if ((l && !/^https:\/\//.test(l)) || (r && !/^https:\/\//.test(r))) { m.textContent = "Links must start with https://"; return; }
          if (!el("#sp-n", body).value.trim()) { m.textContent = "Add a name."; return; }
          call(function () { return api.aPost("spotlights", { kind: el("#sp-k", body).value, name: el("#sp-n", body).value.trim(), detail: el("#sp-d", body).value.trim() || null, logo_url: l || null, ref_url: r || null }); })
            .then(load).catch(function (e) { m.textContent = "Failed: " + e.message; });
        });
        body.querySelectorAll("[data-del]").forEach(function (b) { b.addEventListener("click", function () { call(function () { return api.aDel("spotlights", "id=eq." + b.dataset.del); }).then(load); }); });
      });
    }
    load();
  }
  function renderNotices() {
    var body = el("#admin-body");
    function load() {
      call(function () { return api.aGet("notices", "?select=*&order=created_at.desc&limit=40"); }).then(function (rows) {
        body.innerHTML = '<div class="card"><h3>Add notice</h3><div class="field"><label>Title</label><input id="nt-t" maxlength="80"></div><div class="field"><label>Text (optional)</label><input id="nt-b" maxlength="400"></div><div class="field"><label>Link (optional)</label><input id="nt-l"></div>' +
          '<div class="field"><label>Show from (optional, schedule)</label><input type="datetime-local" id="nt-s"></div><div class="field"><label>Hide after (optional)</label><input type="datetime-local" id="nt-e"></div><label><input type="checkbox" id="nt-p"> Pinned</label><br>' +
          '<button class="btn btn-red btn-sm" id="nt-go">Save</button> <span id="nt-m"></span></div><div class="card"><h3>All notices</h3>' + ((rows || []).map(function (r) { return "<div>" + (r.pinned ? "[pinned] " : "") + "<b>" + esc(r.title) + '</b> <button class="btn btn-sm" data-del="' + r.id + '">Delete</button></div>'; }).join("") || "None yet.") + "</div>";
        el("#nt-go", body).addEventListener("click", function () {
          var l = el("#nt-l", body).value.trim(), m = el("#nt-m", body), s = el("#nt-s", body).value, e = el("#nt-e", body).value;
          if (l && !/^https:\/\//.test(l) && !/^[a-z0-9-]+\.html$/i.test(l)) { m.textContent = "Link must be https:// or a site page."; return; }
          if (!el("#nt-t", body).value.trim()) { m.textContent = "Add a title."; return; }
          var row = { title: el("#nt-t", body).value.trim(), body: el("#nt-b", body).value.trim() || null, link: l || null, pinned: el("#nt-p", body).checked, expires_at: e ? new Date(e).toISOString() : null };
          if (s) row.starts_at = new Date(s).toISOString();
          call(function () { return api.aPost("notices", row); }).then(load).catch(function (x) { m.textContent = "Failed: " + x.message; });
        });
        body.querySelectorAll("[data-del]").forEach(function (b) { b.addEventListener("click", function () { call(function () { return api.aDel("notices", "id=eq." + b.dataset.del); }).then(load); }); });
      });
    }
    load();
  }
  function renderFeedback() {
    var body = el("#admin-body");
    call(function () { return api.aGet("feedback", "?select=id,kind,body,status,created_at&order=created_at.desc&limit=100"); }).then(function (rows) {
      body.innerHTML = '<div class="card"><h3>Feedback box (anonymous: no user shown)</h3>' + ((rows || []).map(function (r) { return '<div style="padding:6px 0;border-bottom:1px solid #8884">[' + esc(r.kind) + "] " + esc(r.body) + ' <small>' + esc(r.created_at.slice(0, 10)) + "</small></div>"; }).join("") || "Nothing yet.") + "</div>";
    });
  }

  function renderPinned() {
    var body = el("#admin-body");
    call(function () { return api.aGet("site_settings", "?key=eq.pinned&select=value"); }).then(function (rows) {
      var v = (rows && rows[0] && rows[0].value) || {};
      body.innerHTML = '<div class="card"><h3>Pinned notice (home page)</h3><p style="font-size:13px">One notice shown at the top of the home page "Today" section. Leave it off when there is nothing to say.</p>' +
        '<div class="field"><label>Title</label><input id="pn-t" maxlength="80" value="' + esc(v.title || "") + '"></div>' +
        '<div class="field"><label>Text (optional)</label><input id="pn-x" maxlength="240" value="' + esc(v.text || "") + '"></div>' +
        '<div class="field"><label>Link (optional: https://... or a page like tournaments.html)</label><input id="pn-l" maxlength="200" value="' + esc(v.link || "") + '"></div>' +
        '<div class="field"><label>Hide after (optional, your local time)</label><input type="datetime-local" id="pn-e" value="' + esc(v.expires_at ? new Date(v.expires_at).toISOString().slice(0, 16) : "") + '"></div>' +
        '<label style="display:flex;gap:8px;align-items:center;font-size:14px;margin:8px 0"><input type="checkbox" id="pn-a"' + (v.active ? " checked" : "") + "> Show on the home page</label>" +
        '<button class="btn btn-red btn-sm" id="pn-s">Save</button> <span id="pn-m" style="font-size:13px"></span></div>';
      el("#pn-s", body).addEventListener("click", function () {
        var link = el("#pn-l", body).value.trim(), m = el("#pn-m", body);
        if (link && !/^https:\/\//.test(link) && !/^[a-z0-9-]+\.html(#[a-z0-9-]*)?$/i.test(link)) { m.textContent = "Link must start with https:// or be a site page like tournaments.html."; return; }
        var ex = el("#pn-e", body).value;
        var data = { title: el("#pn-t", body).value.trim(), text: el("#pn-x", body).value.trim(), link: link, active: el("#pn-a", body).checked, expires_at: ex ? new Date(ex).toISOString() : null };
        if (data.active && !data.title) { m.textContent = "Add a title first."; return; }
        call(function () { return api.aUpsert("site_settings", { key: "pinned", value: data, updated_at: new Date().toISOString() }, "key"); })
          .then(function () { return api.audit("pinned_set", { active: data.active }); })
          .then(function () { m.textContent = "Saved."; }).catch(function (e) { m.textContent = "Failed: " + e.message; });
      });
    });
  }

  function renderSupportAcks(host) {
    var box = document.createElement("div"); box.className = "card"; box.style.marginTop = "14px"; host.appendChild(box);
    box.innerHTML = "<h3>I paid acknowledgements</h3><p style='font-size:13px'>Private. Only admins can see these screenshots. They are deleted automatically after 30 days.</p><div id='sa-list'>Loading…</div>";
    var list = box.querySelector("#sa-list");
    function proof(path) {
      var s = api.session();
      return fetch(api.url + "/storage/v1/object/sign/support-proofs/" + path, { method: "POST", headers: { "apikey": api.key, "Authorization": "Bearer " + (s && s.access_token), "Content-Type": "application/json" }, body: JSON.stringify({ expiresIn: 600 }) })
        .then(function (r) { return r.json(); }).then(function (d) { if (!d || !d.signedURL) throw new Error("sign failed"); return api.url + "/storage/v1" + d.signedURL; });
    }
    function load() {
      call(function () { return api.rpc("admin_support_acks", {}, true); }).then(function (rows) {
        if (!Array.isArray(rows) || !rows.length) { list.innerHTML = "<p>No acknowledgements yet.</p>"; return; }
        list.innerHTML = rows.map(function (r) {
          return '<div class="card" style="margin:8px 0" data-id="' + r.id + '" data-path="' + esc(r.path) + '"><b>' + esc(r.name || "(no name)") + "</b> &middot; @" + esc(r.username || "?") + " &middot; " + esc(new Date(r.created_at).toLocaleString()) +
            (r.txn_id ? "<br>Txn: " + esc(r.txn_id) : "") + "<br>Public name: " + (r.consent && r.name ? "yes" : "no (Anonymous)") + " &middot; Status: <b>" + esc(r.status) + "</b>" + (r.amount ? " &middot; &#8377;" + esc(r.amount) : "") + (r.seen ? ' <span style="color:#7bd88f">seen</span>' : ' <span style="color:#f2c94c">new</span>') +
            '<div style="margin-top:6px"><button class="btn btn-ghost btn-sm" data-sa="view">View screenshot</button> <button class="btn btn-ghost btn-sm" data-sa="seen">Mark seen</button> <button class="btn btn-red btn-sm" data-sa="approve">Approve + amount</button> <button class="btn btn-ghost btn-sm" data-sa="hide">Hide from list</button> <button class="btn btn-red btn-sm" data-sa="del">Delete</button></div><div data-img></div></div>';
        }).join("");
        list.querySelectorAll("[data-sa]").forEach(function (b) {
          b.addEventListener("click", function () {
            var c = b.closest("[data-id]"), id = +c.getAttribute("data-id"), path = c.getAttribute("data-path"), a = b.getAttribute("data-sa");
            if (a === "view") { proof(path).then(function (u) { c.querySelector("[data-img]").innerHTML = '<img src="' + esc(u) + '" alt="" style="max-width:100%;max-height:420px;margin-top:8px;border-radius:8px">'; }).catch(function () { c.querySelector("[data-img]").textContent = "Could not load."; }); }
            if (a === "approve") { var amt = prompt("Verified amount received in INR (whole rupees). It will appear on the public supporters list."); if (amt === null) return; amt = parseInt(amt, 10); call(function () { return api.rpc("admin_support_ack_approve", { p_id: id, p_amount: amt }, true); }).then(function (r) { if (r && r.ok === false) alert(r.error); load(); }); }
            if (a === "hide") call(function () { return api.rpc("admin_support_ack_hide", { p_id: id }, true); }).then(load);
            if (a === "seen") call(function () { return api.rpc("admin_support_ack_seen", { p_id: id }, true); }).then(load);
            if (a === "del") { if (!confirm("Delete this acknowledgement and its screenshot?")) return;
              var s = api.session();
              fetch(api.url + "/storage/v1/object/support-proofs/" + path, { method: "DELETE", headers: { "apikey": api.key, "Authorization": "Bearer " + (s && s.access_token) } })
                .then(function () { return call(function () { return api.rpc("admin_support_ack_delete", { p_id: id }, true); }); }).then(load); }
          });
        });
      }).catch(function (e) { list.textContent = "Failed: " + e.message; });
    }
    load();
  }

  function renderSupportQr() {
    var body = el("#admin-body");
    call(function () { return api.aGet("site_settings", "?key=eq.support&select=value"); }).then(function (rows) {
      var v = (rows && rows[0] && rows[0].value) || {};
      body.innerHTML = '<div class="card"><h3>Upload new QR code for Support page</h3><p style="font-size:13px">Upload ONE payment QR image (screenshot of your UPI QR, max 5 MB). It shows on the public Support page. No UPI id or phone number is shown as text; payment happens in the donor\'s own UPI app.</p>' +
        (v.image_url ? '<p>Current QR:</p><img src="' + esc(v.image_url) + '" alt="" style="max-width:220px;background:#fff;padding:6px;border-radius:8px">' : "<p>No QR uploaded yet. The Support page says Coming soon.</p>") +
        '<div style="margin:10px 0"><input type="file" id="sq-file" accept="image/*"></div><input id="sq-text" maxlength="120" placeholder="Optional short text under the QR" value="' + esc(v.text || "") + '" style="width:100%;margin-bottom:8px"><button class="btn btn-sm" id="sq-save">Upload &amp; replace QR</button> ' + (v.image_url ? '<button class="btn btn-red btn-sm" id="sq-del">Remove QR</button>' : "") + '<div id="sq-msg" style="margin-top:8px;font-size:13px"></div></div>';
      renderSupportAcks(body);
      var msg = el("#sq-msg", body);
      function save(data) { return call(function () { return api.aUpsert("site_settings", { key: "support", value: data, updated_at: new Date().toISOString() }, "key"); }).then(function () { return api.audit("support_qr", {}); }).then(renderSupportQr).catch(function (e) { msg.textContent = "Failed: " + e.message; }); }
      el("#sq-save", body).addEventListener("click", function () {
        var file = el("#sq-file", body).files[0];
        if (!file) { save({ image_url: v.image_url || null, text: el("#sq-text", body).value.trim() }); return; }
        if (file.type.indexOf("image/") !== 0) { msg.textContent = "Only images."; return; }
        if (file.size > 5 * 1024 * 1024) { msg.textContent = "Image too large (max 5 MB)."; return; }
        msg.textContent = "Uploading…";
        call(function () { return api.userToken(); }).then(function (tok) {
          return fetch(api.url + "/functions/v1/cloudinary-sign", { method: "POST", headers: { "apikey": api.key, "Authorization": "Bearer " + tok, "Content-Type": "application/json" }, body: "{}" });
        }).then(function (r) { return r.json(); }).then(function (sig) {
          if (!sig || !sig.cloud) throw new Error("Upload unavailable.");
          var fd = new FormData(); fd.append("file", file); fd.append("api_key", sig.key); fd.append("timestamp", sig.timestamp); fd.append("folder", sig.folder); fd.append("signature", sig.signature);
          return fetch("https://api.cloudinary.com/v1_1/" + sig.cloud + "/image/upload", { method: "POST", body: fd }).then(function (r) { return r.json(); });
        }).then(function (up) { if (!up || !up.secure_url) throw new Error("Upload failed."); return save({ image_url: up.secure_url, text: el("#sq-text", body).value.trim() }); })
          .catch(function (e) { msg.textContent = "Failed: " + e.message; });
      });
      var d = el("#sq-del", body); if (d) d.addEventListener("click", function () { if (confirm("Remove the QR? The page goes back to Coming soon.")) save({ image_url: null, text: "" }); });
    });
  }

  function renderLivestream() {
    var body = el("#admin-body");
    call(function () { return api.aGet("site_settings", "?key=eq.youtube_live&select=value"); }).then(function (rows) {
      var v = (rows && rows[0] && rows[0].value) || {};
      body.innerHTML = '<div class="card"><h3>YouTube live stream</h3>' +
        '<p style="font-size:13px">When active, a LIVE card with the stream appears at the top of the home page. Paste the full YouTube link (watch, live, youtu.be, shorts or embed) or just the 11-character ID.</p>' +
        '<div class="field" style="margin-top:10px"><label>Video / live ID</label><input id="lv-id" value="' + esc(v.video_id || "") + '" placeholder="e.g. dQw4w9WgXcQ"></div>' +
        '<div class="field"><label>Title</label><input id="lv-title" maxlength="80" value="' + esc(v.title || "") + '" placeholder="e.g. XARENA Season 1 Finals - LIVE"></div>' +
        '<label style="display:flex;gap:8px;align-items:center;font-size:14px;margin:8px 0"><input type="checkbox" id="lv-active"' + (v.active ? " checked" : "") + "> Show the LIVE card on the home page</label>" +
        '<button class="btn btn-red btn-sm" id="lv-save">Save</button> <span id="lv-msg" style="font-size:13px"></span></div>';
      el("#lv-save", body).addEventListener("click", function () {
        var rawId = el("#lv-id", body).value.trim(); var vid = rawId ? xrYtId(rawId) : ""; if (rawId && !vid) { el("#lv-msg", body).textContent = "Could not find a YouTube video ID in that link."; return; } var data = { video_id: vid, title: el("#lv-title", body).value.trim(), active: el("#lv-active", body).checked };
        call(function () { return api.aUpsert("site_settings", { key: "youtube_live", value: data, updated_at: new Date().toISOString() }, "key"); })
          .then(function () { return api.audit("livestream_set", { active: data.active }); })
          .then(function () { el("#lv-msg", body).textContent = "Saved - home page updated."; })
          .catch(function (e) { el("#lv-msg", body).textContent = "Save failed: " + e.message; });
      });
    });
  }

  function renderProofs() {
    var body = el("#admin-body");
    function render() {
      Promise.all([
        call(function () { return api.aGet("result_proofs", "?select=*&order=created_at.desc&limit=100"); }).catch(function () { return []; }),
        call(function () { return api.aGet("profiles", "?select=id,username,ign"); }).catch(function () { return []; })
      ]).then(function (res) {
        var proofs = res[0] || [], people = res[1] || [];
        var pmap = {};
        people.forEach(function (p) { pmap[p.id] = p.ign || p.username || "player"; });
        var pending = proofs.filter(function (p) { return p.status === "pending"; });
        var handled = proofs.filter(function (p) { return p.status !== "pending"; }).slice(0, 20);
        var html = '<div class="card"><h3>Result proofs (' + pending.length + ' pending)</h3>' +
          '<p style="font-size:13px">Players upload scoreboard screenshots after matches. <b>Approve</b> pays the Arena Points you set; <b>Reject</b> with a note. Everything is logged.</p>' +
          (pending.length > 1 ? '<div style="display:flex;gap:8px;margin-top:10px;align-items:center;flex-wrap:wrap"><input type="number" id="pr-all-pts" value="15" min="0" max="500" style="width:70px;padding:6px"><button class="btn btn-gold btn-sm" id="pr-all-ok">Approve all ' + pending.length + ' at these points</button></div>' : "") + '</div>';
        html += pending.length ? "" : '<div class="empty"><div class="big">No pending proofs</div></div>';
        html += pending.map(function (p) {
          return '<div class="news-item" style="border-left-color:var(--gold)">' +
            '<h3 style="font-size:15px">' + esc(pmap[p.user_id] || "player") + (p.note ? ' - "' + esc(p.note) + '"' : "") + '</h3>' +
            '<p style="font-size:12.5px;color:var(--muted)">' + String(p.created_at).slice(0, 16).replace("T", " ") + (p.match_id ? " · match " + esc(String(p.match_id).slice(0, 8)) : " · general") + '</p>' +
            '<div style="margin:8px 0"><a href="' + esc(p.image_path) + '" target="_blank" rel="noopener"><img src="' + esc(p.image_path) + '" style="max-width:280px;max-height:160px;object-fit:cover;border-radius:8px"></a></div>' +
            '<div class="frow"><div class="field"><input type="number" data-pr-pts="' + p.id + '" value="20" min="0" max="500" style="width:90px"> <label style="font-size:12px">points</label></div>' +
            '<div class="field"><input data-pr-note="' + p.id + '" maxlength="200" placeholder="Note (optional)"></div></div>' +
            '<div style="display:flex;gap:8px;flex-wrap:wrap">' +
            '<button class="btn btn-gold btn-sm" data-pr-ok="' + p.id + '">Approve + pay</button>' +
            '<button class="btn btn-ghost btn-sm" data-pr-no="' + p.id + '" style="color:#ff6b6b">Reject</button></div></div>';
        }).join("");
        if (handled.length) {
          html += '<div class="card" style="margin-top:16px"><h3>Recent handled</h3>' + handled.map(function (p) {
            var col = p.status === "approved" ? "#4ade80" : "#ff6b6b";
            return '<p style="font-size:13px;margin:4px 0"><b style="color:' + col + '">' + p.status.toUpperCase() + "</b> · " + esc(pmap[p.user_id] || "player") + (p.points ? " · +" + p.points + " pts" : "") + (p.admin_note ? " · " + esc(p.admin_note) : "") + "</p>";
          }).join("") + "</div>";
        }
        body.innerHTML = html;
        var prAll = el("#pr-all-ok", body);
        if (prAll) prAll.addEventListener("click", function () {
          var pts = parseInt(el("#pr-all-pts", body).value || "15", 10);
          if (!confirm("Approve ALL " + pending.length + " pending proofs at " + pts + " points each?")) return;
          prAll.disabled = true;
          var seq = Promise.resolve();
          pending.forEach(function (p) {
            seq = seq.then(function () { return call(function () { return api.rpc("admin_review_proof", { p_id: p.id, p_action: "approve", p_points: pts, p_note: "bulk approve" }, true); }); });
          });
          seq.then(function () { return api.audit("proof_bulk_approve", { count: pending.length, points: pts }); }).then(render);
        });
        els("[data-pr-ok],[data-pr-no]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            var id = parseInt(b.dataset.prOk || b.dataset.prNo, 10);
            var action = b.dataset.prOk ? "approve" : "reject";
            var pts = parseInt((el('[data-pr-pts="' + id + '"]', body) || {}).value || "0", 10);
            var note = (el('[data-pr-note="' + id + '"]', body) || {}).value || "";
            b.disabled = true;
            call(function () { return api.rpc("admin_review_proof", { p_id: id, p_action: action, p_points: pts, p_note: note }, true); })
              .then(function (r) {
                if (r && r.ok) return api.audit("proof_review", { proof: id, action: action, points: pts });
                throw new Error((r && r.error) || "Failed.");
              }).then(render).catch(function (e) { b.disabled = false; alert(e.message); });
          });
        });
      });
    }
    render();
  }

  function renderCheats() {
    var body = el("#admin-body");
    function render() {
      Promise.all([
        call(function () { return api.aGet("cheat_reports", "?select=*&order=created_at.desc&limit=100"); }).catch(function () { return []; }),
        call(function () { return api.aGet("profiles", "?select=id,username,ign"); }).catch(function () { return []; })
      ]).then(function (res) {
        var reps = res[0] || [], people = res[1] || [];
        var pmap = {};
        people.forEach(function (p) { pmap[p.id] = p.ign || p.username || "player"; });
        var open = reps.filter(function (r) { return r.status === "open"; });
        var handled = reps.filter(function (r) { return r.status !== "open"; }).slice(0, 20);
        var html = '<div class="card"><h3>Cheat reports (' + open.length + ' open)</h3>' +
          '<p style="font-size:13px">Player-submitted hack/teaming reports. <b>Actioned</b> means you took action (ban/hold in Registrations); <b>Dismissed</b> closes it. Reports never auto-ban.</p></div>';
        html += open.length ? "" : '<div class="empty"><div class="big">No open reports</div></div>';
        html += open.map(function (r) {
          return '<div class="news-item" style="border-left-color:#ff6b6b">' +
            '<h3 style="font-size:15px">Reported: ' + esc(r.reported_name) + '</h3>' +
            '<p style="font-size:13px">"' + esc(r.reason) + '"</p>' +
            '<p style="font-size:12.5px;color:var(--muted)">by ' + esc(pmap[r.reporter] || "player") + " · " + String(r.created_at).slice(0, 16).replace("T", " ") + '</p>' +
            (r.proof_path ? '<div style="margin:8px 0"><a href="' + esc(r.proof_path) + '" target="_blank" rel="noopener"><img src="' + esc(r.proof_path) + '" style="max-width:280px;max-height:160px;object-fit:cover;border-radius:8px"></a></div>' : "") +
            '<div class="field"><input data-ch-note="' + r.id + '" maxlength="200" placeholder="Note (optional)"></div>' +
            '<div style="display:flex;gap:8px;flex-wrap:wrap">' +
            '<button class="btn btn-red btn-sm" data-ch-act="' + r.id + '">Actioned</button>' +
            '<button class="btn btn-ghost btn-sm" data-ch-dis="' + r.id + '">Dismiss</button></div></div>';
        }).join("");
        if (handled.length) {
          html += '<div class="card" style="margin-top:16px"><h3>Recent handled</h3>' + handled.map(function (r) {
            return '<p style="font-size:13px;margin:4px 0"><b style="color:' + (r.status === "actioned" ? "#4ade80" : "var(--muted)") + '">' + r.status.toUpperCase() + "</b> · " + esc(r.reported_name) + (r.admin_note ? " · " + esc(r.admin_note) : "") + "</p>";
          }).join("") + "</div>";
        }
        body.innerHTML = html;
        els("[data-ch-act],[data-ch-dis]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            var id = parseInt(b.dataset.chAct || b.dataset.chDis, 10);
            var action = b.dataset.chAct ? "actioned" : "dismissed";
            var note = (el('[data-ch-note="' + id + '"]', body) || {}).value || "";
            b.disabled = true;
            call(function () { return api.rpc("admin_handle_cheat_report", { p_id: id, p_action: action, p_note: note }, true); })
              .then(function (r) {
                if (r && r.ok) return api.audit("cheat_report_handle", { report: id, action: action });
                throw new Error((r && r.error) || "Failed.");
              }).then(render).catch(function (e) { b.disabled = false; alert(e.message); });
          });
        });
      });
    }
    render();
  }

  function renderReferrals() {
    var body = el("#admin-body");
    function render() {
      Promise.all([
        call(function () { return api.aGet("referrals", "?select=*&order=created_at.desc&limit=200"); }).catch(function () { return []; }),
        call(function () { return api.aGet("profiles", "?select=id,username,ign"); }).catch(function () { return []; })
      ]).then(function (res) {
        var refs = res[0] || [], people = res[1] || [];
        var pmap = {};
        people.forEach(function (p) { pmap[p.id] = p.ign || p.username || "player"; });
        var counts = {};
        refs.forEach(function (r) { counts[r.referrer_id] = (counts[r.referrer_id] || 0) + 1; });
        var top = Object.keys(counts).sort(function (a, b) { return counts[b] - counts[a]; }).slice(0, 10);
        var html = '<div class="card"><h3>Referrals (' + refs.length + ' total)</h3>' +
          '<p style="font-size:13px">Every invite that paid out: the referrer got 25 Arena Points, the new player 10. One reward per new account - codes work only in the first 48 hours of an account. Points are never cash.</p></div>';
        if (top.length) {
          html += '<div class="card" style="margin-top:16px"><h3>Top inviters</h3>' + top.map(function (id, i) {
            return '<div class="news-item" style="border-left-color:var(--gold)"><p style="font-size:14px"><b>' + (i + 1) + '.</b> ' + esc(pmap[id] || "player") + ' <span style="color:var(--gold);float:right">' + counts[id] + " friends - " + counts[id] * 25 + " pts</span></p></div>";
          }).join("") + "</div>";
        }
        html += refs.length
          ? '<div class="card" style="margin-top:16px"><h3>Full log</h3>' + refs.map(function (r) {
              return '<div class="news-item" style="border-left-color:var(--purple)"><p style="font-size:13px"><b>' + esc(pmap[r.referrer_id] || "player") + "</b> invited <b>" + esc(pmap[r.referred_id] || "player") + "</b> &middot; " + String(r.created_at).slice(0, 16).replace("T", " ") + "</p></div>";
            }).join("") + "</div>"
          : '<div class="empty" style="margin-top:16px"><div class="big">No referrals yet</div><p>Players find their invite code on the Play page.</p></div>';
        body.innerHTML = html;
      });
    }
    render();
  }

  function renderRewards() {
    var body = el("#admin-body");
    function render() {
      Promise.all([
        call(function () { return api.aGet("reward_redemptions", "?select=*&order=created_at.desc&limit=80"); }).catch(function () { return []; }),
        call(function () { return api.aGet("rewards_catalog", "?select=*&order=sort.asc,id.asc"); }).catch(function () { return []; }),
        call(function () { return api.aGet("profiles", "?select=id,username,ign"); }).catch(function () { return []; })
      ]).then(function (res) {
        var reds = res[0] || [], cat = res[1] || [], people = res[2] || [];
        var pmap = {};
        people.forEach(function (p) { pmap[p.id] = p.ign || p.username || "player"; });
        var pending = reds.filter(function (r) { return r.status === "pending"; });
        var handled = reds.filter(function (r) { return r.status !== "pending"; }).slice(0, 20);

        var html = '<div class="card"><h3>Rewards requests (' + pending.length + ' pending)</h3>' +
          '<p style="font-size:13px">Players spend Arena Points from check-ins, spins and quiz wins. <b>Fulfil</b> keeps the spend - deliver the reward to the player yourself. <b>Reject</b> automatically refunds the points. Points are never cash.</p></div>';
        html += pending.length ? "" : '<div class="empty"><div class="big">No pending requests</div><p>New redemption requests show up here.</p></div>';
        html += pending.map(function (r) {
          return '<div class="news-item" style="border-left-color:var(--gold)">' +
            '<h3 style="font-size:15px">' + esc(r.title) + ' <span style="color:var(--gold);font-size:13px">' + r.cost + " pts</span></h3>" +
            "<p>by <b>" + esc(pmap[r.user_id] || "player") + "</b> · " + String(r.created_at).slice(0, 16).replace("T", " ") + "</p>" +
            '<div class="field" style="margin-top:6px"><input data-rw-note="' + r.id + '" maxlength="200" placeholder="Note for the player (optional, e.g. how you delivered it)"></div>' +
            '<div style="margin-top:6px;display:flex;gap:8px;flex-wrap:wrap">' +
            '<button class="btn btn-gold btn-sm" data-rw-fulfil="' + r.id + '">Fulfil</button>' +
            '<button class="btn btn-ghost btn-sm" data-rw-reject="' + r.id + '" style="color:#ff6b6b">Reject + refund</button></div></div>';
        }).join("");
        if (handled.length) {
          html += '<div class="card" style="margin-top:16px"><h3>Recent handled</h3>' + handled.map(function (r) {
            var col = r.status === "fulfilled" ? "#4ade80" : "#ff6b6b";
            return '<div class="news-item" style="border-left-color:' + col + '"><p style="font-size:13px"><b style="color:' + col + '">' + r.status.toUpperCase() + "</b> · " + esc(r.title) + " (" + r.cost + " pts) · " + esc(pmap[r.user_id] || "player") + " · " + String(r.handled_at || r.created_at).slice(0, 16).replace("T", " ") + (r.note ? "<br><span style='color:var(--muted)'>Note: " + esc(r.note) + "</span>" : "") + "</p></div>";
          }).join("") + "</div>";
        }

        html += '<div class="card" style="margin-top:16px"><h3 id="rw-form-title">Add a reward</h3><p class="muted" style="margin:0 0 8px">Cosmetic only - no cash or real-world value (no money, gift cards, diamonds or recharge). The database rejects such items.</p>' +
          '<input type="hidden" id="rw-edit-id">' +
          '<div class="grid c2"><div class="field"><label>Title</label><input id="rw-title" maxlength="80" placeholder="e.g. Profile shout-out"></div>' +
          '<div class="field"><label>Cost (Arena Points)</label><input id="rw-cost" type="number" min="1" placeholder="e.g. 50"></div></div>' +
          '<div class="field"><label>Description</label><input id="rw-desc" maxlength="200" placeholder="What the player gets"></div>' +
          '<div class="grid c2"><div class="field"><label>Sort order</label><input id="rw-sort" type="number" value="0"></div>' +
          '<div class="field"><label style="display:flex;gap:8px;align-items:center;margin-top:22px"><input type="checkbox" id="rw-active" checked style="width:auto"> Visible to players</label></div></div>' +
          '<button class="btn btn-gold btn-sm" id="rw-save">Save reward</button> <button class="btn btn-ghost btn-sm" id="rw-cancel" style="display:none">Cancel edit</button> <span id="rw-msg" style="color:var(--muted);font-size:13px"></span></div>';

        html += '<div class="card" style="margin-top:16px"><h3>Catalog (' + cat.length + ")</h3>" +
          (cat.length ? cat.map(function (c) {
            return '<div class="news-item" style="border-left-color:' + (c.active ? "#4ade80" : "var(--muted)") + '">' +
              '<h3 style="font-size:15px">' + esc(c.title) + ' <span style="color:var(--gold);font-size:13px">' + c.cost + " pts</span></h3>" +
              '<p style="font-size:13px;color:var(--muted)">' + esc(c.description || "") + " · sort " + c.sort + " · " + (c.active ? "visible" : "hidden") + "</p>" +
              '<div style="margin-top:6px;display:flex;gap:8px;flex-wrap:wrap">' +
              '<button class="btn btn-ghost btn-sm" data-rw-edit="' + c.id + '">Edit</button>' +
              '<button class="btn btn-ghost btn-sm" data-rw-toggle="' + c.id + '" data-active="' + (c.active ? 1 : 0) + '">' + (c.active ? "Hide" : "Show") + "</button>" +
              '<button class="btn btn-ghost btn-sm" data-rw-del="' + c.id + '" style="color:#ff6b6b">Delete</button></div></div>';
          }).join("") : '<p style="color:var(--muted);font-size:13px">No rewards yet - add the first one above. It appears on the Play page instantly.</p>') + "</div>";

        body.innerHTML = html;

        function noteFor(id) { var n = el('[data-rw-note="' + id + '"]', body); return n ? n.value.trim() : ""; }
        function review(id, action, btn) {
          btn.disabled = true;
          call(function () { return api.rpc("admin_review_redemption", { p_id: parseInt(id, 10), p_action: action, p_note: noteFor(id) || null }, true); })
            .then(function (d) {
              if (d && d.ok) { return api.audit("reward_review", { id: id, action: action }).then(render); }
              btn.disabled = false; alert((d && d.error) || "Review failed.");
            }).catch(function (e) { btn.disabled = false; alert("Failed: " + e.message); });
        }
        els("[data-rw-fulfil]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            if (!confirm("Mark as fulfilled? The points stay spent.")) return;
            review(b.dataset.rwFulfil, "fulfilled", b);
          });
        });
        els("[data-rw-reject]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            if (!confirm("Reject and refund the points to the player?")) return;
            review(b.dataset.rwReject, "rejected", b);
          });
        });

        function clearForm() {
          el("#rw-edit-id").value = ""; el("#rw-title").value = ""; el("#rw-cost").value = "";
          el("#rw-desc").value = ""; el("#rw-sort").value = "0"; el("#rw-active").checked = true;
          el("#rw-form-title").textContent = "Add a reward"; el("#rw-cancel").style.display = "none"; el("#rw-msg").textContent = "";
        }
        el("#rw-cancel").addEventListener("click", clearForm);
        el("#rw-save").addEventListener("click", function () {
          var title = el("#rw-title").value.trim(), cost = parseInt(el("#rw-cost").value || "0", 10), msg = el("#rw-msg");
          if (!title || !(cost > 0)) { msg.textContent = "Title and a positive cost are required."; return; }
          var row = { title: title, cost: cost, kind: "cosmetic", description: el("#rw-desc").value.trim() || null, sort: parseInt(el("#rw-sort").value || "0", 10) || 0, active: el("#rw-active").checked };
          var eid = el("#rw-edit-id").value;
          var b = el("#rw-save"); b.disabled = true; msg.textContent = "Saving…";
          call(function () { return eid ? api.aPatch("rewards_catalog", "id=eq." + eid, row) : api.aPost("rewards_catalog", row); })
            .then(function () { return api.audit(eid ? "reward_edit" : "reward_add", { title: title }); })
            .then(render).catch(function (e) { b.disabled = false; msg.textContent = "Save failed: " + e.message; });
        });
        els("[data-rw-edit]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            var c = cat.filter(function (x) { return String(x.id) === b.dataset.rwEdit; })[0];
            if (!c) return;
            el("#rw-edit-id").value = c.id; el("#rw-title").value = c.title; el("#rw-cost").value = c.cost;
            el("#rw-desc").value = c.description || ""; el("#rw-sort").value = c.sort; el("#rw-active").checked = !!c.active;
            el("#rw-form-title").textContent = "Edit reward #" + c.id; el("#rw-cancel").style.display = "";
            el("#rw-title").scrollIntoView({ block: "center" });
          });
        });
        els("[data-rw-toggle]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            b.disabled = true;
            call(function () { return api.aPatch("rewards_catalog", "id=eq." + b.dataset.rwToggle, { active: b.dataset.active !== "1" }); }).then(render);
          });
        });
        els("[data-rw-del]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            if (!confirm("Delete this reward? Past requests keep their record.")) return;
            b.disabled = true;
            call(function () { return api.aDel("rewards_catalog", "id=eq." + b.dataset.rwDel); })
              .then(function () { return api.audit("reward_delete", { id: b.dataset.rwDel }); }).then(render);
          });
        });
      });
    }
    render();
  }

  /* ---------- Quizzes (Play hub content) ---------- */
  function renderQuizzes() {
    var body = el("#admin-body");
    function render() {
      call(function () { return api.aGet("quizzes", "?select=*&order=created_at.desc&limit=60"); }).then(function (qs) {
        var html = '<div class="card"><h3>Add a quiz</h3>' +
          '<div class="field"><label>Question</label><input id="qz-q" maxlength="300" placeholder="e.g. How many players are in a Free Fire squad?"></div>' +
          '<div class="grid c2">' +
          '<div class="field"><label>Option A</label><input id="qz-o0" maxlength="80"></div>' +
          '<div class="field"><label>Option B</label><input id="qz-o1" maxlength="80"></div>' +
          '<div class="field"><label>Option C</label><input id="qz-o2" maxlength="80"></div>' +
          '<div class="field"><label>Option D</label><input id="qz-o3" maxlength="80"></div></div>' +
          '<div class="grid c2">' +
          '<div class="field"><label>Correct option</label><select id="qz-c"><option value="0">A</option><option value="1">B</option><option value="2">C</option><option value="3">D</option></select></div>' +
          '<div class="field"><label>XP reward</label><input id="qz-p" type="number" min="1" max="500" value="10"></div></div>' +
          '<label style="display:flex;gap:8px;align-items:center;font-size:14px;margin-bottom:10px"><input type="checkbox" id="qz-daily"> Mark as DAILY quiz (highlighted at the top of the Play hub)</label>' +
          '<button class="btn btn-gold btn-sm" id="qz-add">Publish quiz</button>' +
          '<p id="qz-msg" style="margin-top:8px;font-size:13px"></p></div>';
        html += "<h3 style='margin:18px 0 10px'>Live quizzes (" + qs.filter(function (q) { return q.active; }).length + ")</h3>";
        html += qs.length ? qs.map(function (q) {
          var letters = ["A", "B", "C", "D"];
          var opts = (q.options || []).map(function (o, i) {
            return '<span style="' + (i === q.correct ? "color:#8ff0b8;font-weight:700" : "color:var(--muted)") + '">' + letters[i] + ". " + esc(o) + "</span>";
          }).join(" · ");
          return '<div class="news-item" style="border-left-color:' + (q.active ? "#4ade80" : "var(--muted)") + '">' +
            '<h3 style="font-size:15px">' + esc(q.question) + '</h3>' +
            '<p style="font-size:12.5px">' + opts + "</p>" +
            '<p><b style="color:' + (q.active ? "#4ade80" : "var(--muted)") + '">' + (q.active ? "LIVE" : "OFF") + "</b> · " + q.kind.toUpperCase() + " · +" + q.points + " XP</p>" +
            '<div style="margin-top:8px">' +
            (q.active
              ? '<button class="btn btn-ghost btn-sm" data-qz-off="' + q.id + '">Take offline</button>'
              : '<button class="btn btn-ghost btn-sm" data-qz-on="' + q.id + '">Republish</button>') +
            "</div></div>";
        }).join("") : '<div class="empty"><div class="big">No quizzes yet</div><p>Add the first one above.</p></div>';
        body.innerHTML = html;
        el("#qz-add").addEventListener("click", function () {
          var q = el("#qz-q").value.trim();
          var opts = [0, 1, 2, 3].map(function (i) { return el("#qz-o" + i).value.trim(); });
          var m = el("#qz-msg");
          if (q.length < 5 || opts.some(function (o) { return !o; })) { m.textContent = "Question + all 4 options required."; m.style.color = "#ffb4ae"; return; }
          m.textContent = "Publishing…"; m.style.color = "var(--muted)";
          call(function () {
            return api.rpc("admin_quiz_save", {
              p_id: null, p_question: q, p_options: opts, p_correct: parseInt(el("#qz-c").value, 10),
              p_points: parseInt(el("#qz-p").value, 10) || 10, p_kind: el("#qz-daily").checked ? "daily" : "quiz", p_active: true
            }, true);
          }).then(function (r) {
            if (!r || !r.ok) { m.textContent = (r && r.error) || "Failed."; m.style.color = "#ffb4ae"; return; }
            return api.audit("quiz_add", { question: q }).then(render);
          }).catch(function (e) { m.textContent = "Failed: " + e.message; m.style.color = "#ffb4ae"; });
        });
        function toggle(id, active) {
          call(function () {
            return api.aGet("quizzes", "?id=eq." + id + "&select=*");
          }).then(function (rows) {
            var q = rows && rows[0];
            if (!q) return;
            return call(function () {
              return api.rpc("admin_quiz_save", { p_id: q.id, p_question: q.question, p_options: q.options, p_correct: q.correct, p_points: q.points, p_kind: q.kind, p_active: active }, true);
            });
          }).then(render);
        }
        els("[data-qz-off]", body).forEach(function (b) { b.addEventListener("click", function () { b.disabled = true; toggle(parseInt(b.dataset.qzOff, 10), false); }); });
        els("[data-qz-on]", body).forEach(function (b) { b.addEventListener("click", function () { b.disabled = true; toggle(parseInt(b.dataset.qzOn, 10), true); }); });
      });
    }
    render();
  }

  /* ---------- DM Reports (E2E: only reporter-submitted messages are readable) ---------- */
  function renderChatMod() {
    var body = el("#admin-body");
    function render() {
      Promise.all([
        call(function () { return api.aGet("chat_violations", "?select=*&order=created_at.desc&limit=30"); }).catch(function () { return []; }),
        call(function () { return api.aRpc ? api.aRpc("clean_cup_candidates", {}) : api.uRpc("clean_cup_candidates", {}); }).catch(function () { return null; }),
        call(function () { return api.aRpc ? api.aRpc("top_fan_week", {}) : api.uRpc("top_fan_week", {}); }).catch(function () { return null; })
      ]).then(function (rs) {
        var vios = rs[0] || [];
        var cc = rs[1], tf = rs[2];
        var cands = (cc && Array.isArray(cc)) ? cc : [];
        var fans = (tf && Array.isArray(tf)) ? tf : [];
        var html = '<div class="card"><h3>Language filter</h3><p>World Chat messages with abusive words are blocked automatically and logged here. Players with 10+ clean messages this week can earn the <b>Clean Cup</b> badge.</p></div>';
        html += '<div class="card"><h3>Clean Cup candidates (last 7 days)</h3>' +
          (cands.length ? cands.map(function (c) {
            return '<div class="news-item"><h3 style="font-size:15px">' + esc(c.username || c.handle || "Player") + '</h3><p>' + c.msgs + " clean messages this week</p>" +
              (c.has_badge ? '<p style="color:#4ade80;font-size:12.5px">Clean Cup badge earned</p>' : '<button class="btn btn-gold btn-sm" data-cc="' + c.user_id + '">Grant Clean Cup badge</button>') + '</div>';
          }).join("") : '<p style="color:var(--muted);font-size:13px">Nobody qualifies yet - candidates appear after 10+ clean messages in a week.</p>') + '</div>';
        html += '<div class="card"><h3>Top fans this week</h3>' +
          (fans.length ? fans.map(function (f, i) {
            return '<div class="news-item"><h3 style="font-size:15px">#' + (i + 1) + " " + esc(f.ign || f.username || "Player") + '</h3><p>' + f.xp + " points this week</p>" +
              (f.has_badge ? '<p style="color:#4ade80;font-size:12.5px">Top Fan badge earned</p>' : '<button class="btn btn-gold btn-sm" data-tf="' + f.user_id + '">Grant Top Fan badge</button>') + '</div>';
          }).join("") : '<p style="color:var(--muted);font-size:13px">No point activity this week yet.</p>') + '</div>';
        html += '<div class="card"><h3>Blocked messages (last 30)</h3>' +
          (vios.length ? vios.map(function (v) {
            return '<div class="news-item" style="border-left-color:#ff6b6b"><h3 style="font-size:14px">' + esc(v.handle) + '</h3><p style="color:var(--muted);font-size:12.5px">' + esc(v.body || "") + ' · ' + String(v.created_at).slice(0, 16).replace("T", " ") + "</p></div>";
          }).join("") : '<p style="color:var(--muted);font-size:13px">Nothing blocked yet - chat is clean.</p>') + '</div>';
        body.innerHTML = html;
        els("[data-cc]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            b.disabled = true;
            call(function () { return api.uRpc("grant_clean_cup", { p_user: b.getAttribute("data-cc") }); }).then(function () { render(); }).catch(function () { b.disabled = false; });
          });
        });
        els("[data-tf]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            b.disabled = true;
            call(function () { return api.uRpc("grant_top_fan", { p_user: b.getAttribute("data-tf") }); }).then(function () { render(); }).catch(function () { b.disabled = false; });
          });
        });
      });
    }
    render();
  }

  function renderWalkovers() {
    var body = el("#admin-body");
    function render() {
      call(function () { return api.uRpc("admin_walkover_claims", {}); }).then(function (r) {
        var list = (r && r.list) || [];
        var html = '<div class="card"><h3>Walkover claims</h3><p>A squad claims the opponent did not show up. <b>Approve</b> only after opening the screenshot proof - approving sets the claimant as the bracket winner. Reject closes the claim.</p></div>';
        html += list.length ? list.map(function (c) {
          var col = c.status === "new" ? "#f5b301" : c.status === "approved" ? "#4ade80" : "var(--muted)";
          return '<div class="news-item" style="border-left-color:' + col + '">' +
            '<h3 style="font-size:15px">' + esc(c.squad) + ' <span style="color:var(--muted);font-weight:400">claims walkover</span></h3>' +
            '<p>' + esc(c.squad_a || "?") + " vs " + esc(c.squad_b || "?") + ' · Round ' + esc(c.round || "?") + '</p>' +
            '<p><a href="' + esc(c.proof) + '" target="_blank" style="color:var(--gold2);font-size:13px">Open proof screenshot</a></p>' +
            '<p style="color:var(--muted);font-size:12.5px">' + String(c.at).slice(0, 16).replace("T", " ") + ' · <b style="color:' + col + '">' + esc(c.status) + '</b></p>' +
            (c.status === "new"
              ? '<div style="display:flex;gap:8px;flex-wrap:wrap"><button class="btn btn-gold btn-sm" data-wo-ok="' + c.id + '">Approve (set winner)</button><button class="btn btn-ghost btn-sm" data-wo-no="' + c.id + '" style="color:#ff6b6b">Reject</button></div>'
              : "") +
            '</div>';
        }).join("") : '<div class="empty"><div class="big">No walkover claims</div><p>When a squad claims a walkover from the bracket, it lands here for verification.</p></div>';
        body.innerHTML = html;
        els("[data-wo-ok]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            if (!confirm("Approve this walkover? The claiming squad becomes the bracket winner.")) return;
            b.disabled = true;
            call(function () { return api.uRpc("admin_resolve_walkover", { p_claim: parseInt(b.getAttribute("data-wo-ok"), 10), p_ok: true }); })
              .then(function () { return api.audit("walkover_approved", { claim: b.getAttribute("data-wo-ok") }); })
              .then(render).catch(function (e) { b.disabled = false; alert("Failed: " + e.message); });
          });
        });
        els("[data-wo-no]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            b.disabled = true;
            call(function () { return api.uRpc("admin_resolve_walkover", { p_claim: parseInt(b.getAttribute("data-wo-no"), 10), p_ok: false }); })
              .then(function () { return api.audit("walkover_rejected", { claim: b.getAttribute("data-wo-no") }); })
              .then(render).catch(function (e) { b.disabled = false; alert("Failed: " + e.message); });
          });
        });
      });
    }
    render();
  }

  function renderIdVerify() {
    var body = el("#admin-body");
    function render() {
      call(function () { return api.uRpc("admin_id_verifications", {}); }).then(function (r) {
        var list = (r && r.rows) || [];
        var html = '<div class="card"><h3>Photo verification submissions</h3><p>Players uploaded a Free Fire profile screenshot for the VERIFIED badge. Open the screenshot and check the IGN + UID match the player details. <b>Approve</b> grants the real VERIFIED badge on their profile. Reject sends them back to upload a new screenshot.</p></div>';
        html += list.length ? list.map(function (c) {
          return '<div class="news-item" style="border-left-color:#f5b301">' +
            '<h3 style="font-size:15px">' + esc(c.ign || c.username || "player") + ' <span style="color:var(--muted);font-weight:400">' + esc(c.username || "") + '</span></h3>' +
            '<p>FF UID: <b>' + esc(c.ff_uid || "-") + '</b> · ' + String(c.created_at).slice(0, 16).replace("T", " ") + '</p>' +
            '<p><a href="' + esc(c.image_path) + '" target="_blank" style="color:var(--gold2);font-size:13px">Open uploaded screenshot</a></p>' +
            '<div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:6px"><button class="btn btn-gold btn-sm" data-iv-ok="' + c.id + '">Approve (grant VERIFIED)</button><button class="btn btn-ghost btn-sm" data-iv-no="' + c.id + '" style="color:#ff6b6b">Reject</button></div>' +
            '</div>';
        }).join("") : '<div class="empty"><div class="big">No pending submissions</div><p>When a player uploads a verification screenshot from the Photo Verification page, it lands here.</p></div>';
        body.innerHTML = html;
        els("[data-iv-ok]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            if (!confirm("Approve this verification? The player gets the real VERIFIED badge.")) return;
            b.disabled = true;
            call(function () { return api.uRpc("admin_review_id_verification", { p_id: parseInt(b.getAttribute("data-iv-ok"), 10), p_action: "approve", p_note: "" }); })
              .then(function () { return api.audit("id_verify_approved", { id: b.getAttribute("data-iv-ok") }); })
              .then(render).catch(function (e) { b.disabled = false; alert("Failed: " + e.message); });
          });
        });
        els("[data-iv-no]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            var note = prompt("Reason for rejection (shown to the player):", "Screenshot unclear - please upload a clearer Free Fire profile photo.");
            if (note === null) return;
            b.disabled = true;
            call(function () { return api.uRpc("admin_review_id_verification", { p_id: parseInt(b.getAttribute("data-iv-no"), 10), p_action: "reject", p_note: note }); })
              .then(function () { return api.audit("id_verify_rejected", { id: b.getAttribute("data-iv-no") }); })
              .then(render).catch(function (e) { b.disabled = false; alert("Failed: " + e.message); });
          });
        });
      });
    }
    render();
  }

  function renderSponsorApps() {
    var body = el("#admin-body");
    function render() {
      call(function () { return api.aGet("sponsor_inquiries", "?select=*&order=created_at.desc&limit=100"); }).then(function (rows) {
        rows = rows || [];
        var html = '<div class="card"><h3>Sponsor requests</h3><p>Brands that sent an inquiry from the Sponsors page. <b>Approve & publish</b> adds them to the public Sponsors list (edit the tier or link any time in the Sponsors section). Reject closes the request.</p></div>';
        html += rows.length ? rows.map(function (r) {
          var actionable = r.status === "pending" || r.status === "new" || !r.status;
          var col = actionable ? "#f5b301" : r.status === "approved" ? "#4ade80" : "var(--muted)";
          return '<div class="news-item" style="border-left-color:' + col + '">' +
            '<h3 style="font-size:15px">' + esc(r.brand) + '</h3>' +
            '<p>' + esc(r.contact) + (r.note ? ' · ' + esc(r.note) : "") + '</p>' +
            '<p style="color:var(--muted);font-size:12.5px">' + String(r.created_at).slice(0, 16).replace("T", " ") + ' · <b style="color:' + col + '">' + esc(r.status) + '</b></p>' +
            (actionable
              ? '<div style="display:flex;gap:8px;flex-wrap:wrap"><button class="btn btn-gold btn-sm" data-approve="' + r.id + '">Approve & publish</button><button class="btn btn-ghost btn-sm" data-reject="' + r.id + '" style="color:#ff6b6b">Reject</button></div>'
              : r.status === "approved"
                ? '<p style="font-size:12.5px;color:var(--muted)">Published - manage it in the Sponsors section.</p>'
                : "") +
            '</div>';
        }).join("") : '<div class="empty"><div class="big">No sponsor requests</div><p>When a brand sends an inquiry from the Sponsors page it lands here.</p></div>';
        body.innerHTML = html;
        els("[data-approve]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            b.disabled = true; b.textContent = "Publishing…";
            var id = b.getAttribute("data-approve");
            var row = rows.filter(function (x) { return String(x.id) === String(id); })[0];
            call(function () { return api.aPatch("sponsor_inquiries", "id=eq." + id, { status: "approved" }); }).then(function () {
              return call(function () { return api.aPost("sponsors", { name: row.brand, tier: "partner", url: "", active: true }); });
            }).then(function () { render(); }).catch(function () { b.disabled = false; b.textContent = "Approve & publish"; alert("Could not approve - try again."); });
          });
        });
        els("[data-reject]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            b.disabled = true;
            call(function () { return api.aPatch("sponsor_inquiries", "id=eq." + b.getAttribute("data-reject"), { status: "rejected" }); }).then(function () { render(); }).catch(function () { b.disabled = false; });
          });
        });
      });
    }
    render();
  }

  function renderDMReports() {
    var body = el("#admin-body");
    function render() {
      Promise.all([
        call(function () { return api.aGet("dm_reports", "?select=*&order=created_at.desc&limit=50"); }),
        call(function () { return api.aGet("profiles", "?select=id,username,ign"); }).catch(function () { return []; })
      ]).then(function (rs) {
        var rows = rs[0] || [], profs = rs[1] || [];
        var nm = {}; profs.forEach(function (p) { nm[p.id] = p.ign || p.username || "Player"; });
        var html = '<div class="card"><h3>🔐 DM reports</h3><p>Private messages are end-to-end encrypted - the ONLY readable content is what the reporting player submitted below. Reporting locks the conversation for the reported side until you decide.</p></div>';
        html += rows.length ? rows.map(function (r) {
          var msgs = Array.isArray(r.messages) ? r.messages : [];
          return '<div class="news-item" style="border-left-color:' + (r.status === "pending" ? "#f5b301" : "var(--muted)") + '">' +
            '<h3 style="font-size:15px">' + esc(nm[r.reported_id] || "Player") + ' <span style="color:#ff6b6b">' + esc(r.reason.toUpperCase()) + '</span></h3>' +
            '<p>reported by <b>' + esc(nm[r.reporter_id] || "Player") + "</b> · " + String(r.created_at).slice(0, 16).replace("T", " ") +
            ' · <b style="color:' + (r.status === "pending" ? "#f5b301" : "var(--muted)") + '">' + r.status + "</b>" +
            (r.ai_score != null ? ' · AI: <b style="color:' + (r.ai_score >= 70 ? "#ff6b6b" : r.ai_score >= 40 ? "#f5b301" : "#4ade80") + '">' + r.ai_score + "/100</b>" : "") + "</p>" +
            (r.note ? '<p style="color:var(--muted)">Note: ' + esc(r.note) + "</p>" : "") +
            (r.ai_reason ? '<p style="color:var(--gold2)">AI: ' + esc(r.ai_reason) + "</p>" : "") +
            '<div style="margin:8px 0;padding:10px;background:rgba(0,0,0,.3);border-radius:8px;max-height:180px;overflow:auto">' +
              (msgs.length ? msgs.map(function (m) {
                return '<p style="margin:3px 0;font-size:13px"><b style="color:' + (m.from === "them" ? "#ff9d9d" : "#9dc3ff") + '">' + esc(m.from) + ":</b> " +
                  esc(m.type === "text" ? m.text : "[" + m.type + "]") + ' <span style="color:var(--muted);font-size:11px">' + String(m.at || "").slice(5, 16).replace("T", " ") + "</span></p>";
              }).join("") : '<p style="color:var(--muted)">No messages attached.</p>') + "</div>" +
            '<div style="display:flex;gap:8px;flex-wrap:wrap">' +
            '<button class="btn btn-ghost btn-sm" data-ai="' + r.id + '">🤖 AI assess</button>' +
            (r.status === "pending" ?
              '<button class="btn btn-ghost btn-sm" data-unlock="' + r.id + '">Unlock conversation</button>' +
              '<button class="btn btn-ghost btn-sm" data-keep="' + r.id + '" style="color:#ff6b6b">Keep locked (done)</button>' : "") +
            "</div><p data-dmr-msg='" + r.id + "' style='font-size:12.5px;color:var(--muted)'></p></div>";
        }).join("") : '<div class="empty"><div class="big">No reports</div><p>When a player reports a conversation it lands here.</p></div>';
        body.innerHTML = html;
        els("[data-ai]", body).forEach(function (b) {
          b.addEventListener("click", function () {
            b.disabled = true; b.textContent = "Assessing…";
            var s = api.session();
            fetch(api.url + "/functions/v1/dm-abuse", {
              method: "POST",
              headers: { "Authorization": "Bearer " + s.access_token, "Content-Type": "application/json" },
              body: JSON.stringify({ report_id: b.dataset.ai })
            }).then(function (r) { return r.json(); }).then(function (d) {
              if (d && d.ok) render();
              else { b.disabled = false; b.textContent = "🤖 AI assess"; alert((d && d.error) || "AI unavailable."); }
            });
          });
        });
        function review(id, unlock) {
          call(function () { return api.rpc("dm_report_review", { p_report: id, p_unlock: unlock }, true); })
            .then(function () { return api.audit("dm_report_review", { id: id, unlock: unlock }); })
            .then(render);
        }
        els("[data-unlock]", body).forEach(function (b) { b.addEventListener("click", function () { review(b.dataset.unlock, true); }); });
        els("[data-keep]", body).forEach(function (b) { b.addEventListener("click", function () { review(b.dataset.keep, false); }); });
      });
    }
    render();
  }

  boot();
})();
