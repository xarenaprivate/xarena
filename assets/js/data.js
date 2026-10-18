/* XARENA ESPORTS — content source (Phase 1: local data file.
   Phase 2: this file is replaced by live reads from the XARENA backend —
   every renderer in app.js already consumes these exact shapes.) */
window.XARENA = {
  site: {
    name: "XARENA ESPORTS",
    short: "XARENA",
    tagline: "Where Free Fire legends are forged.",
    founded: "2026",
    game: "Free Fire",
    owner: "Shivhari Rajput",
    ownerInstagram: "ud_speed"
  },
  stats: [
    { k: "SEASON", v: "01" },
    { k: "SQUAD SLOTS", v: "48" },
    { k: "GAME", v: "FREE FIRE" },
    { k: "ROSTER", v: "RECRUITING" }
  ],
  tournaments: [
    {
      id: "xr-s1",
      name: "XARENA SEASON 1",
      game: "Free Fire",
      mode: "Squad · Battle Royale",
      status: "upcoming",
      dateText: "Dates revealed soon",
      prize: "Prize pool reveal soon",
      slots: 48,
      registered: 0,
      regOpen: true,
      format: "48 squads · 3 qualifier groups · Top 16 advance to Grand Finals · Points from placement + kills",
      description: "The first official XARENA championship. 48 squads drop in. One walks out with the crown. Register your squad now — slots are first come, first served.",
      schedule: [],
      results: null
    }
  ],
  teams: [],
  players: [],
  leaderboard: { season: "SEASON 1", rows: [] },
  scrims: [],
  news: [
    {
      date: "2026-10-15",
      tag: "ANNOUNCEMENT",
      title: "XARENA ESPORTS is live",
      body: "XARENA ESPORTS officially launches today — a professional Free Fire esports organisation built for competitive squads. Season 1 is announced, registrations are open, and the arena is being prepared. This is day one."
    },
    {
      date: "2026-10-15",
      tag: "RECRUITMENT",
      title: "Official roster recruitment open",
      body: "XARENA is building its founding roster. IGLs, rushers, snipers and support players — if you grind Free Fire seriously, send your tryout application from the Teams page. Early recruits get priority for Season 1 squad placement."
    }
  ]
};
