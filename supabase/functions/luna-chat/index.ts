// XARENA ESPORTS · Luna chat edge function (Phase 3)
// Free tier. Gemini key lives ONLY as a dashboard secret (GEMINI_API_KEY) -
// never in browser code. Public site -> this function -> Gemini.

const GEMINI_KEY = Deno.env.get("GEMINI_API_KEY") || "";
const SUPA_URL = Deno.env.get("SUPABASE_URL") || "";
const SUPA_SERVICE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, apikey, authorization",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

const PERSONA = `You are Luna, the official AI companion of XARENA ESPORTS - a professional Free Fire esports organisation founded by Shivhari Rajput.
Personality: cute, friendly, clear and simple English. Always reply in English by default; only if the player writes in another language (e.g. Hindi), mirror their language. Short replies (2-4 lines). Warm and encouraging, gamer vibe.
You answer questions about XARENA: tournaments, registration, players, schedule, scrims, rules.
Owner-confirmed correction: XARENA Ignition Cup was cancelled on October 25, 2026. No replacement date has been set. This overrides stale rows describing it as upcoming. Do not imply that a past scheduled date proves a match was played. Repeat dates exactly as provided, never subtract a day. Use ONLY the LIVE DATA below for other facts. Events awaiting owner review are drafts, not confirmed upcoming events. Registration capacity is per event: never claim 48 slots for every event; use its own slots.  The current UTC date is provided in the live data. Never call a past date an upcoming tournament. Respect cancelled/hold/closed states even if old descriptions suggest registration. If no confirmed future date is present, say the next date is not announced. If something is not in the data (e.g. prize pool not announced), say honestly it is not announced yet - never invent dates, prizes or results.
If asked about registration: they need a free account first (sign up on the Account page with phone + password, no email), then DM the verification code shown there to the owner on Instagram (@ud_speed). Once verified, use the Register form: squad name + captain IGN + FF UID + WhatsApp number. 48 slots, free entry.
If asked about prizes/rewards: winners earn public honors granted by the owner after real matches - Champion, Runner-up, 3rd Place, MVP badges, custom titles (like "Season 1 MVP") and leaderboard points. All of it shows on the Winners page and the live leaderboard. No cash prizes for now.
If asked about their profile/player card: every player with an account gets a public player card page (IGN, UID, role, device, FF rank, K/D, Instagram - all entered by the player and labeled as such) plus XARENA-earned matches, wins, badges and titles granted by the owner. They can edit theirs on the Account page and share it as an Instagram story card.
If asked about squads: approved creators (granted by the owner) can create a squad on the Squads page; any logged-in player can join one squad (6 players max). To become a creator, request access on the Squads page - the owner reviews it.
If asked about tasks: the Tasks page lists solo and team tasks. A player marks a solo task done themselves; team tasks are submitted by the squad captain for the whole squad. The owner approves real completions, and approved tasks grant points and badges/titles that show on the player account and the Task Score leaderboard.
If asked about rules: point them to the RRs page (Rules and Regulations) - fair play, registration, conduct, match day, disputes, penalties. Cheating or fake proof = permanent ban.
If asked something unrelated to XARENA/Free Fire, answer briefly and playfully steer back.

LIVE DATA:
`;

// Bounds include response-body consumption. Logs never contain messages or keys.
async function timedFetch(url:string,init:RequestInit={},ms=3000):Promise<Response>{
 const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),ms);
 try{const r=await fetch(url,{...init,signal:controller.signal});const bytes=await r.arrayBuffer();return new Response(bytes,{status:r.status,statusText:r.statusText,headers:r.headers});}
 finally{clearTimeout(timer);}
}
async function generate(body:any,requestId:string):Promise<string>{
 const start=Date.now();
 // Existing configured Gemini models only. Lite alias first avoids four speculative version probes.
 const models=["gemini-flash-lite-latest","gemini-2.5-flash","gemini-3.8-flash","gemini-3.7-flash","gemini-3.6-flash","gemini-3.5-flash"];
 for(const model of models){
  const remaining=20000-(Date.now()-start);if(remaining<1000)break;
  const at=Date.now();
  try{const r=await timedFetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${GEMINI_KEY}`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)},Math.min(7000,remaining));
   console.log(JSON.stringify({request_id:requestId,phase:"model",model,status:r.status,ms:Date.now()-at}));
   if(!r.ok)continue;const j=await r.json();const t=j?.candidates?.[0]?.content?.parts?.filter((p:any)=>!p.thought&&typeof p.text==="string").map((p:any)=>p.text).join("\n");if(t)return t;
  }catch{console.log(JSON.stringify({request_id:requestId,phase:"model_timeout_or_network",model,ms:Date.now()-at}));}
 }
 return "";
}
async function dbGet(path: string) {
  try {
    const r = await timedFetch(`${SUPA_URL}/rest/v1/${path}`, {
      headers: { apikey: SUPA_SERVICE, Authorization: `Bearer ${SUPA_SERVICE}` },
    });
    return await r.json();
  } catch {
    return null;
  }
}

async function dbPost(table: string, rows: unknown[]) {
  try {
    await timedFetch(`${SUPA_URL}/rest/v1/${table}`, {
      method: "POST",
      headers: {
        apikey: SUPA_SERVICE,
        Authorization: `Bearer ${SUPA_SERVICE}`,
        "Content-Type": "application/json",
        Prefer: "return=minimal,resolution=merge-duplicates",
      },
      body: JSON.stringify(rows),
    });
  } catch {
    // conversation logging is best-effort; never block a reply on it
  }
}

async function fetchContext(): Promise<string> {
  const [tournaments, news, scrims, lb] = await Promise.all([
    dbGet("tournaments?select=id,name,status,date_text,prize,slots,reg_open,mode,format,description&order=sort.asc&limit=10"),
    dbGet("news?select=tag,title,body,published_at&published=eq.true&order=published_at.desc&limit=5"),
    dbGet("scrims?select=title,day_text,time_text,note&active=eq.true&limit=10"),
    dbGet("v_leaderboard?select=name,points&order=points.desc&limit=10"),
  ]);
  const confirmedTournaments=Array.isArray(tournaments)?tournaments.filter((t:any)=>!(/owner review pending/i.test(t.description||""))):null;
  return JSON.stringify({current_utc_date:new Date().toISOString().slice(0,10),tournaments:confirmedTournaments,latest_news:news,scrims,leaderboard_top10:lb}, null, 1);
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("{}", { headers: cors });
  try {
    if (!GEMINI_KEY) {
      return new Response(JSON.stringify({ error: "Luna is not configured yet (missing API key)." }), { status: 503, headers: cors });
    }
    const { session_id, message, mode, handle, flavor, subject } = await req.json();
    if (!message || typeof message !== "string" || typeof session_id !== "string" || !/^[A-Za-z0-9_:.\-]{1,100}$/.test(session_id)) {
      return new Response(JSON.stringify({ error: "message and session_id required" }), { status: 400, headers: cors });
    }
    const requestId=crypto.randomUUID();
    const started=Date.now();
    const text = message.slice(0, 800);

    // short-term memory: last 8 turns of this session
    const hist = await dbGet(
      `luna_conversations?select=role,message&session_id=eq.${encodeURIComponent(session_id)}&order=created_at.desc&limit=8`
    );
    const turns = (Array.isArray(hist) ? hist : []).reverse().map((r: any) => ({
      role: r.role === "luna" ? "model" : "user",
      parts: [{ text: String(r.message||"").slice(0,800) }],
    }));

    const isWC = mode === "worldchat" && typeof handle === "string" && /^[A-Za-z0-9_]{3,20}$/.test(handle);

    // funline mode: one-shot hype lines (squad roast, win caption, Bhojpuri caster). No conversation logging.
    if (mode === "funline") {
      const fl = String(flavor || "");
      const subj = String(subject || "").replace(/[<>"'`\\]/g, "").trim().slice(0, 40);
      const PROMPTS: Record<string, string> = {
        roast: "You write playful esports banter for an Indian Free Fire community. Write ONE playful roast line about the Free Fire squad named \"" + subj + "\". Rules: one line only, max 20 words, fun gamer vibe, no abusive words, no slurs, no real insults, keep it friendly. Output only the line.",
        win: "Write ONE short victory caption the Free Fire squad \"" + subj + "\" can post after winning a match. Rules: one line only, max 18 words, hype tone, at most one emoji, no hashtags. Output only the caption.",
        caster: "You are a Bhojpuri esports commentator at a local Free Fire LAN in Bihar. Give ONE energetic Bhojpuri commentary line (roman script) about the squad \"" + subj + "\" playing a close match. Rules: one line only, max 24 words, roman-script Bhojpuri, fun local flavor, no abuse. Output only the line.",
      };
      const sys = PROMPTS[fl];
      if (!sys || !subj) {
        return new Response(JSON.stringify({ error: "bad funline request" }), { status: 400, headers: cors });
      }
      const qh = "fun:" + String(session_id || "anon").slice(0, 60);
      const q = await dbGet(`luna_chat_quota?select=last_at&handle=eq.${encodeURIComponent(qh)}`);
      const last = Array.isArray(q) && q[0] ? Date.parse(q[0].last_at) : 0;
      if (Date.now() - last < 15000) {
        return new Response(JSON.stringify({ error: "Wait a few seconds, then try again." }), { status: 429, headers: cors });
      }
      await dbPost("luna_chat_quota", [{ handle: qh, last_at: new Date().toISOString() }]);
      const fbody = {
        contents: [{ role: "user", parts: [{ text: "Squad: " + subj }] }],
        systemInstruction: { parts: [{ text: sys }] },
        generationConfig: { temperature: 0.9, maxOutputTokens: 512 },
      };
      let line = await generate(fbody,requestId);
      line = line.replace(/[*#`>]/g, "").trim();
      // models sometimes prepend lead-in chatter ("Sure! Here's...") - keep the best actual line
      const parts = line.split("\n").map((x) => x.trim()).filter((x) => x.length > 0);
      if (parts.length > 1) {
        const cand = parts.filter((x) => !/:$/.test(x) && !/^(sure|here|okay|banter|line|caption|roast|commentary)/i.test(x));
        if (cand.length) line = cand.reduce((a, b) => (b.length > a.length ? b : a));
      }
      if (/:$/.test(line) && parts.length > 1) line = parts[parts.length - 1].replace(/:$/, "");
      line = line.replace(/^[\u201C\u201D"']+|[\u201C\u201D"']+$/g, "").trim();
      if (line.length > 220) line = line.slice(0, 217) + "...";
      if (!line) return new Response(JSON.stringify({ error: "Luna is busy - try again in a bit." }), { status: 503, headers: cors });
      return new Response(JSON.stringify({ reply: line }), { headers: cors });
    }

    // world-chat rate limit: one Luna answer per handle per 45 seconds (silent skip)
    if (isWC) {
      const q = await dbGet(`luna_chat_quota?select=last_at&handle=eq.${encodeURIComponent(handle)}`);
      const last = Array.isArray(q) && q[0] ? Date.parse(q[0].last_at) : 0;
      if (Date.now() - last < 45000) {
        return new Response(JSON.stringify({ skipped: true }), { headers: cors });
      }
      await dbPost("luna_chat_quota", [{ handle, last_at: new Date().toISOString() }]);
    }

    const context = await fetchContext();
    const wcNote = isWC
      ? "\nWORLD CHAT MODE: you are answering inside the busy public World Chat room. Reply in 1-2 short lines only (max 40 words). Address the player by their handle (" + handle + "). Never use markdown.\n"
      : "\n";
    const body = {
      contents: [...turns, { role: "user", parts: [{ text }] }],
      systemInstruction: { parts: [{ text: PERSONA + context + wcNote }] },
      generationConfig: { temperature: 0.8, maxOutputTokens: isWC ? 150 : 512 },
    };
    let reply = await generate(body,requestId);
    if (!reply) return new Response(JSON.stringify({error:"Luna is temporarily unavailable. Please try again.",request_id:requestId}),{status:503,headers:cors});
    console.log(JSON.stringify({request_id:requestId,phase:"answer_ready",ms:Date.now()-started}));

    if (isWC) {
      reply = reply.replace(/[*#`>]/g, "").trim();
      if (reply.length > 300) reply = reply.slice(0, 297) + "...";
      await dbPost("chat_messages", [{ handle: "Luna", body: reply }]);
      return new Response(JSON.stringify({ reply, posted: true }), { headers: cors });
    }

    // store both turns (best-effort)
    await dbPost("luna_conversations", [
      { session_id, role: "user", message: text },
      { session_id, role: "luna", message: reply },
    ]);

    return new Response(JSON.stringify({ reply }), { headers: cors });
  } catch (e) {
    return new Response(JSON.stringify({ error: "Luna is temporarily unavailable. Please try again." }), { status: 500, headers: cors });
  }
});
