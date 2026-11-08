// XARENA Agent Worker — the brain that executes queued agent tasks.
// Safe/draft actions execute immediately. Sensitive actions execute only
// after the owner approves them in the Approvals tab. Everything is
// audit-logged. Actions that need accounts we do not have (social posting)
// fail honestly instead of pretending.
const GEMINI_KEY = Deno.env.get("GEMINI_API_KEY") || "";
const SUPA_URL = Deno.env.get("SUPABASE_URL") || "";
const SUPA_SERVICE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";

const MODELS = ["gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash", "gemini-3.5-flash", "gemini-flash-lite-latest", "gemini-2.5-flash"];

const CATALOG: Record<string, { standard: string[]; sensitive: string[] }> = {
  "tournament-manager": { standard: ["draft_tournament", "update_schedule"], sensitive: ["publish_tournament", "modify_prize_pool", "delete_tournament"] },
  "team-manager": { standard: ["draft_roster_change"], sensitive: ["remove_player", "edit_roster"] },
  "statistics-agent": { standard: ["draft_result_entry"], sensitive: ["modify_results", "recalculate_rankings"] },
  "announcement-agent": { standard: ["draft_announcement"], sensitive: ["publish_announcement", "post_social"] },
  "security-monitor": { standard: ["flag_activity"], sensitive: ["ban_registration"] },
  "executive-agent": { standard: ["delegate_tasks"], sensitive: [] },
  "coach-agent": { standard: ["send_coach_tip"], sensitive: [] },
  "tournament-host": { standard: ["host_weekly_draft"], sensitive: [] },
  "content-agent": { standard: ["content_weekly_draft"], sensitive: [] },
  "fairplay-agent": { standard: ["flag_proof"], sensitive: [] },
  "clip-contest-agent": { standard: ["clip_pick_winner"], sensitive: [] },
  "squad-matchmaker": { standard: ["suggest_squads"], sensitive: [] },
  "support-agent": { standard: ["answer_support"], sensitive: [] },
};
const NOT_EXECUTABLE: Record<string, string> = {
  post_social: "No social media accounts are connected to XARENA. Connect an account first, then this becomes real.",
};

function hdrs() {
  return { apikey: SUPA_SERVICE, Authorization: "Bearer " + SUPA_SERVICE, "Content-Type": "application/json" };
}
async function resolveTournament(tid: string | null): Promise<string> {
  if (tid) {
    const rows = await rest("GET", "tournaments?or=(id.eq." + encodeURIComponent(tid) + ",name.ilike.*" + encodeURIComponent(tid) + "*)&select=id&limit=1") as Array<{ id: string }>;
    if (rows.length) return rows[0].id;
  }
  const rows2 = await rest("GET", "tournaments?status=in.(live,upcoming)&order=created_at.desc&select=id&limit=1") as Array<{ id: string }>;
  if (!rows2.length) throw new Error("no live/upcoming tournament found");
  return rows2[0].id;
}
async function patchTournament(id: string, patch: Record<string, unknown>) {
  const rows = await rest("PATCH", "tournaments?id=eq." + encodeURIComponent(id) + "&select=id", patch) as unknown[];
  if (!Array.isArray(rows) || !rows.length) throw new Error("tournament update matched 0 rows");
}

async function rest(method: string, path: string, body?: unknown) {
  const r = await fetch(SUPA_URL + "/rest/v1/" + path, {
    method, headers: { ...hdrs(), Prefer: "return=representation" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const t = await r.text();
  let j: unknown = null;
  try { j = JSON.parse(t); } catch { j = t; }
  if (!r.ok) throw new Error("DB " + r.status + ": " + t.slice(0, 200));
  return j;
}
async function audit(actor: string, action: string, details: unknown) {
  try { await rest("POST", "audit_log", { actor, action, details }); } catch { /* never block on audit */ }
}

function route(taskText: string, allowed: string[]): string | null {
  const t = taskText.toLowerCase();
  const pick = (re: RegExp, action: string) => re.test(t) && allowed.includes(action) ? action : null;
  return pick(/prize|paisa|paise|cash|amount|₹|inr|money/, "modify_prize_pool")
    || pick(/schedule|date\b|time\b|kab|timing|din\b/, "update_schedule")
    || pick(/delete|hatao|remove tournament/, "delete_tournament")
    || pick(/publish tournament|tournament.*live|live.*tournament/, "publish_tournament")
    || pick(/publish|announcement.*live|news.*live/, "publish_announcement")
    || pick(/post.*social|instagram|twitter|social/, "post_social")
    || pick(/ranking|rank|leaderboard/, "recalculate_rankings")
    || pick(/result|score|match.*enter|points table/, "modify_results")
    || pick(/ban|block registration/, "ban_registration")
    || pick(/player.*(hatao|remove|nikalo)|remove.*player/, "remove_player")
    || pick(/roster|player.*(edit|change|role)/, "edit_roster")
    || pick(/delegate|baanto|sab agents/, "delegate_tasks")
    || pick(/tournament.*(draft|banao|naya|new)|draft.*tournament|naya tournament/, "draft_tournament")
    || pick(/announcement|announce|news|batana/, "draft_announcement")
    || pick(/coach|tip|improve/, "send_coach_tip")
    || pick(/weekly.*tournament|host_weekly|draft this week/, "host_weekly_draft")
    || pick(/content_weekly|weekly.*news|news post|content post/, "content_weekly_draft")
    || null;
}

async function geminiPlan(agentId: string, task: string, perms: string[], forced: string | null): Promise<{ action: string; params: Record<string, unknown>; summary: string } | null> {
  const cat = CATALOG[agentId] || { standard: [], sensitive: [] };
  const allowed = [...cat.standard, ...cat.sensitive].filter((a) => perms.includes(a));
  if (!allowed.length) return null;
  const sys =
    "You are the execution planner for the XARENA esports admin panel. " +
    (forced
      ? "The action is ALREADY DECIDED: " + forced + ". Do not choose another action. Only fill its params and write the summary. "
      : "Map the owner's task to EXACTLY ONE action from this allowed list: " + allowed.join(", ") + ". ") +
    "Reply with ONLY compact JSON: {\"action\":\"<one allowed action>\",\"params\":{...},\"summary\":\"<one short English line of what you did or drafted>\"}. " +
    "Routing rules: prize/paisa/cash/amount/paise -> modify_prize_pool; date/time/schedule/kab -> update_schedule; publish/live karo -> publish_tournament or publish_announcement; draft/likho/banao -> the matching draft_* action; delete/hatao -> delete_tournament or remove_player; ban -> ban_registration; results/score/points -> modify_results; rankings -> recalculate_rankings. " +
    "Params you may use per action: draft_tournament {name,mode,date_text,prize,slots,format,description}; update_schedule {tournament_id?,date_text,start_at?}; publish_tournament {tournament_id?}; modify_prize_pool {tournament_id?,prize}; delete_tournament {tournament_id}; " +
    "draft_roster_change {ign,change}; remove_player {ign}; edit_roster {ign,role?,bio?,team?}; draft_result_entry {tournament_id?,title,entries}; modify_results {tournament_id,title,stage?,entries:[{player_name,squad,kills,placement,points}]}; recalculate_rankings {}; " +
    "draft_announcement {tag,title,body}; publish_announcement {title?}; post_social {platform,text}; flag_activity {finding}; ban_registration {registration_id? ,squad?}; delegate_tasks {subtasks:[{agent_id,task}]}. " +
    "Fill params from the task text. If a needed detail is missing, invent nothing - use null and mention it in summary.";
  const body = {
    contents: [{ role: "user", parts: [{ text: sys + "\n\nAGENT: " + agentId + "\nTASK: " + task }] }],
    generationConfig: { temperature: 0.2, maxOutputTokens: 600 },
  };
  for (const m of MODELS) {
    try {
      const r = await fetch("https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent?key=" + GEMINI_KEY, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
      });
      if (!r.ok) continue;
      const j = await r.json();
      const txt = j?.candidates?.[0]?.content?.parts?.[0]?.text || "";
      const match = txt.match(/\{[\s\S]*\}/);
      if (!match) continue;
      const plan = JSON.parse(match[0]);
      if (plan && typeof plan.action === "string") {
        if (forced) plan.action = forced;
        if (allowed.includes(plan.action)) return { action: plan.action, params: (plan.params || {}) as Record<string, unknown>, summary: String(plan.summary || "Done") };
      }
    } catch { /* next model */ }
  }
  return null;
}


async function geminiText(prompt: string, maxTokens: number): Promise<string | null> {
  const body = {
    contents: [{ role: "user", parts: [{ text: prompt }] }],
    generationConfig: { temperature: 0.7, maxOutputTokens: Math.max(maxTokens, 1500) },
  };
  for (const m of MODELS) {
    try {
      const r = await fetch("https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent?key=" + GEMINI_KEY, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
      });
      if (!r.ok) continue;
      const j = await r.json();
      const txt = j?.candidates?.[0]?.content?.parts?.[0]?.text || "";
      if (txt.trim()) return txt.trim();
    } catch { /* next model */ }
  }
  return null;
}

function s(v: unknown): string | null { return typeof v === "string" && v.trim() ? v.trim() : null; }

async function execute(agentId: string, action: string, p: Record<string, unknown>): Promise<string> {
  switch (action) {
    case "draft_tournament": {
      const id = "xr-" + Math.random().toString(36).slice(2, 6);
      await rest("POST", "tournaments", {
        id, name: s(p.name) || "Untitled Cup", game: "Free Fire", mode: s(p.mode) || "Squad BR",
        status: "upcoming", date_text: s(p.date_text), prize: s(p.prize), slots: typeof p.slots === "number" ? p.slots : 48,
        reg_open: false, format: s(p.format), description: s(p.description) || "Drafted by Tournament Manager. Owner review pending.",
        sort: 99,
      });
      return "Tournament draft created (id " + id + ", registration closed).";
    }
    case "update_schedule": {
      const row: Record<string, unknown> = {};
      if (s(p.date_text)) row.date_text = s(p.date_text);
      if (s(p.start_at)) row.start_at = s(p.start_at);
      if (!Object.keys(row).length) throw new Error("no schedule fields given");
      row.updated_at = new Date().toISOString();
      const tid = await resolveTournament(s(p.tournament_id));
      await patchTournament(tid, row);
      return "Schedule updated for " + tid + ".";
    }
    case "publish_tournament": {
      const tid = await resolveTournament(s(p.tournament_id));
      await patchTournament(tid, { status: "live", reg_open: true, updated_at: new Date().toISOString() });
      return "Tournament " + tid + " is LIVE with registration open.";
    }
    case "modify_prize_pool": {
      if (!s(p.prize)) throw new Error("no prize text given");
      const tid = await resolveTournament(s(p.tournament_id));
      await patchTournament(tid, { prize: s(p.prize), updated_at: new Date().toISOString() });
      return "Prize pool updated to: " + s(p.prize) + " (" + tid + ")";
    }
    case "delete_tournament": {
      if (!s(p.tournament_id)) throw new Error("no tournament id given");
      const tid = await resolveTournament(s(p.tournament_id));
      const rows = await rest("DELETE", "tournaments?id=eq." + encodeURIComponent(tid) + "&select=id") as unknown[];
      if (!Array.isArray(rows) || !rows.length) throw new Error("tournament delete matched 0 rows");
      return "Tournament " + tid + " deleted.";
    }
    case "draft_roster_change":
      return "Draft ready for owner review: " + (s(p.change) || "roster change for " + (s(p.ign) || "player"));
    case "remove_player": {
      if (!s(p.ign)) throw new Error("no player IGN given");
      await rest("PATCH", "players?ign=eq." + encodeURIComponent(s(p.ign) as string), { active: false });
      return "Player " + s(p.ign) + " deactivated (hidden from site).";
    }
    case "edit_roster": {
      if (!s(p.ign)) throw new Error("no player IGN given");
      const row: Record<string, unknown> = {};
      if (s(p.role)) row.role = s(p.role);
      if (s(p.bio)) row.bio = s(p.bio);
      if (!Object.keys(row).length) throw new Error("no roster fields given");
      await rest("PATCH", "players?ign=eq." + encodeURIComponent(s(p.ign) as string), row);
      return "Roster updated for " + s(p.ign) + ".";
    }
    case "draft_result_entry":
      return "Result draft ready for owner review: " + (s(p.title) || "match") + (Array.isArray(p.entries) ? " (" + p.entries.length + " entries)" : "");
    case "modify_results": {
      if (!s(p.tournament_id) || !s(p.title)) throw new Error("tournament_id and title required");
      const m = await rest("POST", "matches", { tournament_id: s(p.tournament_id), title: s(p.title), stage: s(p.stage) || "Group", played_at: new Date().toISOString() }) as Array<{ id: string }>;
      const mid = m[0]?.id;
      const entries = Array.isArray(p.entries) ? p.entries : [];
      for (const e of entries as Array<Record<string, unknown>>) {
        await rest("POST", "lb_entries", { match_id: mid, player_name: s(e.player_name) || "?", squad: s(e.squad), kills: Number(e.kills) || 0, placement: Number(e.placement) || null, points: Number(e.points) || 0 });
      }
      return "Match result saved with " + entries.length + " leaderboard entries.";
    }
    case "recalculate_rankings": {
      const rows = await rest("GET", "lb_entries?select=id,points") as unknown[];
      return "Leaderboard verified: " + rows.length + " entries aggregated live by the rankings view. Rankings update automatically when results are entered.";
    }
    case "draft_announcement": {
      await rest("POST", "news", { tag: s(p.tag) || "GENERAL", title: s(p.title) || "Announcement", body: s(p.body) || "Drafted by Announcement Agent.", published: false });
      return "Announcement drafted (hidden until owner publishes).";
    }
    case "publish_announcement": {
      const target = s(p.title) ? "title=ilike." + encodeURIComponent("*" + (s(p.title) as string) + "*") + "&published=eq.false" : "published=eq.false&order=created_at.desc&limit=1";
      await rest("PATCH", "news?" + target, { published: true, published_at: new Date().toISOString() });
      return "Announcement is LIVE on the site.";
    }
    case "flag_activity": {
      await audit("security-monitor", "flag", { finding: s(p.finding) || "manual flag", source: "agent-task" });
      return "Flag written to the Activity Log.";
    }
    case "ban_registration": {
      const target = s(p.registration_id) ? "id=eq." + encodeURIComponent(s(p.registration_id) as string) : s(p.squad) ? "squad=eq." + encodeURIComponent(s(p.squad) as string) : null;
      if (!target) throw new Error("registration id or squad required");
      await rest("PATCH", "registrations?" + target, { status: "rejected" });
      return "Registration rejected.";
    }
    case "delegate_tasks": {
      const subs = Array.isArray(p.subtasks) ? p.subtasks : [];
      let n = 0;
      for (const st of subs as Array<Record<string, unknown>>) {
        const aid = s(st.agent_id), tk = s(st.task);
        if (aid && tk && CATALOG[aid]) {
          await rest("POST", "agent_tasks", { agent_id: aid, task: tk, status: "queued", needs_approval: false });
          n++;
        }
      }
      return "Delegated " + n + " sub-task(s) to other agents.";
    }

    case "send_coach_tip": {
      const uid = s(p.user_id);
      if (!uid) throw new Error("no user_id given");
      const statsRows = await rest("GET", "player_game_stats?user_id=eq." + encodeURIComponent(uid) + "&select=*&limit=1") as Array<Record<string, unknown>>;
      const profRows = await rest("GET", "profiles?id=eq." + encodeURIComponent(uid) + "&select=ign,username&limit=1") as Array<Record<string, unknown>>;
      const entries = await rest("GET", "lb_entries?select=kills,placement,points&order=created_at.desc&limit=50") as Array<Record<string, unknown>>;
      const st = statsRows[0] || {};
      const name = (profRows[0] && ((profRows[0].ign as string) || (profRows[0].username as string))) || "player";
      const prompt = "You are the XARENA esports coach for the game Free Fire. Give ONE personal training tip to " + name +
        " in 2-3 short sentences, English only, friendly and specific. Base it only on these real stats: XP " + (st.xp ?? 0) +
        ", matches played " + (st.matches_played ?? st.matches ?? 0) + ", wins " + (st.wins ?? 0) + ", kills " + (st.kills ?? 0) +
        ". Recent leaderboard entries sample: " + JSON.stringify(entries.slice(0, 5)) +
        ". If stats are all zero, give a beginner tip about playing first matches and uploading proof. Never invent stats.";
      const tip = await geminiText(prompt, 220);
      if (!tip) throw new Error("Gemini busy - try again later");
      await rest("POST", "notifications", { user_id: uid, kind: "coach", title: "Your AI coach tip", body: tip, link: "leaderboard.html", ref: "coach:" + Date.now() });
      return "Coach tip sent to " + name + ".";
    }
    case "host_weekly_draft": {
      const recent = await rest("GET", "tournaments?select=name&order=created_at.desc&limit=5") as Array<Record<string, unknown>>;
      const names = recent.map((r) => r.name).join("; ");
      const prompt = "You are the XARENA Free Fire esports tournament host. Draft NEXT WEEK's tournament. Reply with raw JSON only, no markdown, no explanation: " +
        "{\"name\":\"...\",\"mode\":\"...\",\"date_text\":\"...\",\"slots\":48,\"format\":\"...\",\"description\":\"2 short sentences\"}. " +
        "Today is " + new Date().toISOString().slice(0, 10) + ". Pick the coming Saturday, prime time 8 PM IST. " +
        "Vary the mode (Squad BR, Clash Squad, Lone Wolf, Rush Hour). Do NOT reuse these recent names: " + (names || "none") + ". English only.";
      const raw = await geminiText(prompt, 1500);
      if (!raw) throw new Error("Gemini busy - try again later");
      const m = raw.replace(/```json|```/g, "").match(/\{[\s\S]*\}/);
      if (!m) throw new Error("could not parse tournament draft");
      const d = JSON.parse(m[0]);
      const id = "xr-" + Math.random().toString(36).slice(2, 6);
      await rest("POST", "tournaments", {
        id, name: s(d.name) || "Weekly Cup", game: "Free Fire", mode: s(d.mode) || "Squad BR",
        status: "upcoming", date_text: s(d.date_text), prize: "Free entry", slots: typeof d.slots === "number" ? d.slots : 48,
        reg_open: false, format: s(d.format), description: (s(d.description) || "Weekly tournament.") + " (Drafted by the Tournament Host - owner review pending.)", sort: 99,
      });
      await rest("POST", "approvals", { agent_id: "tournament-host", action: "publish_tournament",
        details: { tournament_id: id, name: s(d.name), reason: "Weekly draft ready: " + (s(d.name) || id) + " on " + (s(d.date_text) || "Saturday") + ". Approve to publish and open registration." } });
      return "Weekly tournament drafted (" + id + ") - publish approval requested.";
    }
    case "content_weekly_draft": {
      const regs = await rest("GET", "registrations?select=id&created_at=gte." + new Date(Date.now() - 7 * 864e5).toISOString()) as unknown[];
      const lb = await rest("GET", "v_leaderboard?select=*&limit=3") as Array<Record<string, unknown>>;
      const upcoming = await rest("POST", "rpc/xr_weekly_news_events", {}) as Array<Record<string, unknown>>;
      const asOf = new Date().toISOString();
      const prompt = "You are the XARENA Free Fire esports content writer. Write this week's recap intro. Reply with raw JSON only: " +
        "{\"tag\":\"WEEKLY\",\"title\":\"short recap title\",\"body\":\"1-2 short paragraphs, plain text\"}. English only. " +
        "Real context as of " + asOf + ": " + regs.length + " new registrations in the preceding 7 days. Top players: " + JSON.stringify(lb) +
        ". Celebrate real numbers only, never invent winners or stats. Do not mention events, tournaments, matches, dates, schedules, future plans or registration availability. A separately verified event section will be appended.";
      const raw = await geminiText(prompt, 1500);
      if (!raw) throw new Error("Gemini busy - try again later");
      const m = raw.replace(/```json|```/g, "").match(/\{[\s\S]*\}/);
      if (!m) throw new Error("could not parse news draft");
      const d = JSON.parse(m[0]);
      // Scheduling claims are rendered solely from the current structured event snapshot.
      const hasEventClaim = /tournament|\bcup\b|\bmatch(?:es)?\b|\bevent|upcoming|scheduled|tonight|tomorrow|week ahead|next week|register now|registration(?:s)? (?:is|are) open|\b(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\b|\b20\d{2}\b/i.test(s(d.title) + " " + s(d.body));
      const title = hasEventClaim ? "This week at XARENA" : (s(d.title) || "This week at XARENA");
      const recap = hasEventClaim ? regs.length + " new squad registrations were recorded in the preceding 7 days. See the leaderboard for current player standings." : (s(d.body) || regs.length + " new registrations in the preceding 7 days.");
      const eventText = upcoming.length ? "Confirmed upcoming events (checked " + asOf + "):\n" + upcoming.map(e => s(e.name) + " - " + new Date(String(e.next_at)).toLocaleString("en-IN", { timeZone: "Asia/Kolkata", dateStyle: "medium", timeStyle: "short" }) + " IST").join("\n") : "No upcoming event with a confirmed current schedule is available for this weekly draft. Check the tournament page for later announcements.";
      const body = recap + "\n\n" + eventText;
      // PostgreSQL computes the content hash from the stored row when approval is requested.
      const rows = await rest("POST", "news", { tag: s(d.tag) || "WEEKLY", title, body, published: false }) as Array<{ id: string }>;
      const nid = rows[0]?.id;
      if (!nid) throw new Error("weekly news draft insert returned no id");
      await rest("POST", "rpc/xr_weekly_news_request_review", { p_news: nid, p_events: upcoming });
      return "Weekly news drafted - publish approval requested.";
    }
    default:
      throw new Error("action not implemented: " + action);
  }
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("ok", { status: 200 });
  const out = { processed: 0, executed: 0, waiting_approval: 0, failed: 0, notes: [] as string[] };
  try {
    const tasks = await rest("GET", "agent_tasks?status=eq.queued&order=created_at.asc&limit=10") as Array<Record<string, unknown>>;
    const agents = await rest("GET", "agents?select=id,status,permissions") as Array<Record<string, unknown>>;
    for (const t of tasks) {
      out.processed++;
      const tid = String(t.id);
      const agentId = String(t.agent_id);
      const taskText = String(t.task);
      const agent = agents.find((a) => a.id === agentId);
      const finish = async (status: string, result: string) => {
        await rest("PATCH", "agent_tasks?id=eq." + tid, { status, result, updated_at: new Date().toISOString() });
      };
      try {
        if (!agent) { await finish("failed", "Unknown agent " + agentId); out.failed++; continue; }
        if (agent.status === "paused") { out.notes.push(agentId + " paused - task left queued"); continue; }
        const perms = (agent.permissions as string[]) || [];
        const fullCatalog = [...CATALOG[agentId].standard, ...CATALOG[agentId].sensitive];
        const wanted = route(taskText, fullCatalog);
        if (wanted && !perms.includes(wanted)) {
          await finish("failed", "Permission not granted: " + wanted + ". Owner can grant it in the Agents tab, then re-assign.");
          await audit(agentId, "worker_blocked", { task: taskText, reason: "permission not granted: " + wanted });
          out.failed++; continue;
        }
        const allowedAll = fullCatalog.filter((x) => perms.includes(x));
        const forced = wanted;
        const plan = await geminiPlan(agentId, taskText, perms, forced);
        if (!plan) { await finish("failed", "Could not map this task to a permitted action (Gemini busy or no permission granted)."); out.failed++; continue; }
        const cat = CATALOG[agentId];
        const sensitive = cat.sensitive.includes(plan.action) || t.needs_approval === true;
        if (NOT_EXECUTABLE[plan.action]) {
          await finish("failed", NOT_EXECUTABLE[plan.action]);
          await audit(agentId, "worker_blocked", { task: taskText, action: plan.action, reason: NOT_EXECUTABLE[plan.action] });
          out.failed++; continue;
        }
        if (sensitive) {
          const appr = await rest("GET", "approvals?agent_id=eq." + encodeURIComponent(agentId) + "&details->>task=eq." + encodeURIComponent(taskText) + "&order=created_at.desc&limit=1") as Array<Record<string, unknown>>;
          const a = appr[0];
          if (!a) {
            await rest("POST", "approvals", { agent_id: agentId, action: "execute_task", details: { task: taskText, planned_action: plan.action, params: plan.params } });
            out.waiting_approval++; out.notes.push("approval requested: " + taskText); continue;
          }
          if (a.status === "pending") { out.waiting_approval++; continue; }
          if (a.status === "rejected") { await finish("rejected", "Owner rejected this task."); continue; }
        }
        const result = await execute(agentId, plan.action, plan.params);
        await finish("done", result + " — " + plan.summary);
        await audit(agentId, "worker_exec", { task: taskText, action: plan.action, params: plan.params, result });
        out.executed++;
      } catch (e) {
        await finish("failed", String(e).slice(0, 300));
        await audit(agentId, "worker_error", { task: taskText, error: String(e).slice(0, 300) });
        out.failed++;
      }
    }
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), { status: 500, headers: { "Content-Type": "application/json" } });
  }
  return new Response(JSON.stringify(out), { headers: { "Content-Type": "application/json" } });
});
