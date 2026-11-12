# Squad Finder backend contract - 12 November 2026

Deployed to the existing XARENA Supabase project. Backend only. No player, squad, score, invitation, result or settings rows are seeded. New feature-flag table is empty; SF chat is OFF. Existing match chat is unchanged. Free-tier database features only: indexed SQL, RLS, bounded RPCs, no new paid service, no background worker or polling process.

## Integration

Use the connected user's authenticated Supabase JWT for every native-facing RPC. POST `/rest/v1/rpc/<name>` with the named arguments below. All return JSONB. Ordinary domain refusals use `{ok:false,error:...}`; invalid input/permission/constraint/race failures can be PostgreSQL HTTP errors. Handle both. Functions never silently grant creator/admin/verified privileges.

Tables are implementation detail; native uses RPCs. Canonical `squads`, `squad_members`, `squad_invites` remain the one roster/invitation source, shared with the old web app. No separate `xr_sf_squads` roster. Membership authority stays captain/member, separate from Rusher/Support/Sniper/IGL combat role.

## Native-facing RPC signatures

- `xr_sf_call_backup(p_squad uuid, p_role text DEFAULT NULL::text, p_limit integer DEFAULT 5)`
- `xr_sf_chat_list(p_squad uuid, p_after bigint DEFAULT 0, p_limit integer DEFAULT 50)`
- `xr_sf_chat_post(p_squad uuid, p_body text)`
- `xr_sf_friends_activity()`
- `xr_sf_invite_list()`
- `xr_sf_invite_respond(p_invite bigint, p_accept boolean)`
- `xr_sf_invite_send(p_squad uuid, p_player uuid)`
- `xr_sf_join_public(p_squad uuid)`
- `xr_sf_join_request(p_squad uuid, p_note text DEFAULT NULL::text)`
- `xr_sf_member_ready(p_squad uuid, p_ready boolean, p_combat_role text DEFAULT NULL::text)`
- `xr_sf_plan_delete(p_squad uuid, p_expected_revision bigint)`
- `xr_sf_plan_get(p_squad uuid)`
- `xr_sf_plan_save(p_squad uuid, p_body text, p_expected_revision bigint DEFAULT 0)`
- `xr_sf_player_get(p_user uuid)`
- `xr_sf_player_list(p_role text DEFAULT NULL::text, p_language text DEFAULT NULL::text, p_mic boolean DEFAULT NULL::boolean, p_min_trust integer DEFAULT NULL::integer, p_verified_only boolean DEFAULT false, p_free_agents boolean DEFAULT false, p_limit integer DEFAULT 30)`
- `xr_sf_player_preferences(p_patch jsonb DEFAULT '{}'::jsonb)`
- `xr_sf_presence_set(p_state text)`
- `xr_sf_request_decide(p_requests uuid[], p_accept boolean)`
- `xr_sf_request_list(p_squad uuid DEFAULT NULL::uuid)`
- `xr_sf_role_catalog()`
- `xr_sf_squad_create(p_name text, p_tag text DEFAULT NULL::text, p_intro_line text DEFAULT NULL::text, p_visibility text DEFAULT 'public'::text)`
- `xr_sf_squad_get(p_squad uuid)`
- `xr_sf_squad_list(p_role text DEFAULT NULL::text, p_visibility text DEFAULT NULL::text, p_mine boolean DEFAULT false, p_limit integer DEFAULT 30)`
- `xr_sf_squad_settings(p_squad uuid, p_patch jsonb DEFAULT '{}'::jsonb)`
- `xr_sf_tryout_close(p_room uuid)`
- `xr_sf_tryout_create(p_mode text, p_map text, p_duration_min integer, p_squad uuid DEFAULT NULL::uuid)`
- `xr_sf_tryout_get(p_room uuid)`
- `xr_sf_tryout_join(p_room uuid)`
- `xr_sf_tryout_leave(p_room uuid)`
- `xr_sf_tryout_list(p_limit integer DEFAULT 30)`
- `xr_sf_tryout_mark_test(p_room uuid)`

## Tables and limits

- `xr_sf_player_prefs`: selected combat_role; nullable mic, languages[], primary_language; user-entered region; discoverable/free_agent/backup_opt_in (default false); available_from/to timestamptz plus IANA availability_timezone. Opt-in free-agent window at most seven days. No inferred languages, mic or role. Up to eight nonempty language names; examples Hindi, Hinglish, Bhojpuri, English are not an exhaustive enum. Patch fields not listed in the contract are rejected.
- `xr_sf_squad_settings`: canonical squad FK, public/private, discoverable, generated ten-character squad_code, intro_line, mode/map/region/comms_voice, roles_needed, max_members 2..6. New SF squads have max 4; legacy squads retain cap 6 until captain settings change. Creating any squad still requires existing approved-creator permission. No new creator grants.
- `xr_sf_member_prefs`: combat_role, ready; only own ready/combat role changes. Missing member preferences are null, not a fabricated READY status.
- `xr_sf_join_requests`: requester/captain workflow; ten-minute server expiry. Requester sees own pending/answered status via request_list(NULL); captain queue via request_list(squad). Up to ten new requests/hour. Bulk decide accepts 1..20 IDs; returns per-ID success/failure, does not overfill a roster. Capacity contention leaves a request pending rather than falsely marking it accepted.
- Existing `squad_invites` adds nullable expires_at and source. New SF invites use source=sf, expire after thirty seconds, with server enforcement on both new and legacy accept routes. Legacy preexisting invitations are preserved. Re-sending after expiry supersedes an expired pending row. Live retry returns the same invite without extending its expiry. Captain send limit ten/minute. Recipient inbox list is limited to fifty.
- `xr_sf_presence`: explicit player-reported online/in_match/offline, two-minute freshness. Expired in_match never persists as in-match. Falls back to recent profile activity or offline, with source. Existing show_activity opt-out and reciprocal sharing honored. No DB presence_state table; native Realtime channel remains separate.
- `xr_sf_strategy_plans`: captain text CRUD, members read. Plain text 1..4000 chars. Save/delete require current revision; global revision sequence prevents stale-write reuse after deletion/recreation.
- `xr_sf_chat_messages`: member-only text 1..400 chars, two-second minimum interval, at most twenty/minute; list after numeric ID, max 100. `xr_sf_feature_flags` squad_chat must be explicitly enabled later. No flag row is seeded. Voice notes unsupported. Existing clan-room and match chat routes are not switched off or on by this migration. Moderation hide is a protected table field; enabling SF chat is a separate product decision.
- `xr_sf_tryout_rooms` + `xr_sf_tryout_members`: generated room code, explicit mode/map/duration 5..120 minutes, four-player cap including host; expiry calculated from creation. A host can keep two live rooms and create at most ten/hour. Public/standalone rooms discoverable, private-squad rooms member-only. Joining stops when active/closed/expired. Host closes instead of leaving. mark_test only records the host/captain's marker with source `captain_recorded`; it does not verify a match or provision a Free Fire room. Add-to-squad uses the ordinary captain invite.
- `xr_sf_backup_limits`: Call Backup once per squad/five minutes; at most five recipients opted into discoverable free-agent availability AND backup invites, no existing squad and not blocked/banned. Creates only thirty-second in-app invitation inbox rows. No DM, push, email or third-party message. Cooldown starts even if no eligible recipients.
- `xr_sf_trust_evidence`: nullable 0..100 score plus policy/evidence/assessor; no rows or scoring formula. Protected from all client table access. No self-edit RPC and no admin score UI in this version.
- `xr_sf_result_bindings`: reserved admin-confirmed lb_entry-to-account binding, optional deaths and evidence; no automatic IGN matching. Protected from client writes. No binding UI in this version. Only such bound completed results can populate last_matches; current table is empty.

## Response shapes and honest omissions

- role_catalog -> `{ok,roles:[Rusher,Support,Sniper,IGL]}`.
- player_preferences -> `{ok,preferences:object|null}`. `p_patch` is partial and uses the table field names above. Read with `{}`; missing prefs remain null. Never write direct profiles to set the SF role.
- player_list -> `{ok,players:[card]}`; player_get -> `{ok,player:card,activity:[],activity_source:xp_events,last_matches:[]}`. Cards contain id, username/name/avatar, role, XP-derived nullable level + level_source, profile tier/verified, nullable Trust Score, K/D + `kd_source:self_reported`, mic/languages/primary_language/region, opted-in free-agent window, presence/source/last_seen, spectate_allowed=false. No phone, Free Fire UID, device or private reasons. Activity only exposes kind/amount/time, never XP metadata or private text; show_activity controls it. Last matches expose actual match_id/title/ended_at/kills/nullable deaths/won plus binding source. Missing source = empty section. No official full Free Fire history is claimed.
- squad_list -> `{ok,squads:[summary]}`; squad_get -> `{ok,squad:summary}`. Summary includes id/name/logo/intro_line/visibility/mode/map/region/comms_voice/roles_needed, member_count/max_members, is_member/is_captain. A private outsider sees only an explicitly discoverable summary: no squad_code, roster or private content. Code is members-only. Roster entries contain player card, membership_role, combat_role and nullable ready. Non-discoverable players' detailed cards may be null for a public outsider: hide missing details rather than invent them.
- join_public -> `{ok,squad_id}` (or already_member). No invite/request is needed for discoverable PUBLIC squads. Request private squads with join_request; request response contains request object.
- request_list -> `{ok,requests:[id,squad_id,requester,status,note,created_at,expires_at,decided_at]}`. Expiry is derived at read time, no scheduler required. request_decide -> `{ok,results:[{id,ok,accepted?,error?}]}`. Never treat top-level ok as proof every request was accepted.
- invite_send -> `{ok,invite_id,expires_at}`; invite_list -> `{ok,invites:[id,squad_id,squad_name,inviter,created_at,expires_at,status]}`; invite_respond -> `{ok,accepted,squad_id,chat_requires_flag:true}`. Send null-safe UI for expired invitation profiles. Acceptance adds canonical membership, which grants the room when its flag is enabled; it does not post a welcome message.
- member_ready -> `{ok}`; set via explicit boolean. No other member's readiness may be changed.
- friends_activity -> `{ok,friends:[card]}` from actual existing friend edges, privacy/block checks, spectate disabled. Presence state can be null when not shared. No made-up activity captions.
- plan_get -> `{ok,plan:null|{squad_id,body,revision,updated_at}}`; plan_save -> `{ok,revision}` or current_revision on conflict; plan_delete -> `{ok}` or a stale-revision refusal.
- chat_list/post return `{ok:false,enabled:false,error}` while OFF. No voice media support.
- tryout_create/get -> `{ok,room:{id,room_code,host_id,squad_id,status,mode,map,duration_min,players,max_players:4,created_at,expires_at,mine,is_member,test_recorded,test_source,test_recorded_at}}`; tryout_list -> rooms array; joining returns the updated room. close/leave -> ok. Test marker source is captain_recorded even for standalone host tryouts, never verified.
- call_backup -> `{ok,invites:[{ok,invite_id,expires_at}],next_allowed_at,delivery:in_app_invite_inbox}`. Empty invites means nobody eligible or no available capacity; no fictitious calls.

Compatibility percentages, individual percentages, traits, synergy bullets, rating, ping_ms, RP, squad wins/rank/streak/matches-played are absent or NULL until a grounded source exists. No proxy/constant scores. Player K/D is explicitly self-reported, not derived from verified results. Verified flag is the existing owner-controlled profile flag; this migration never changes it. Levels exist only when an earned-XP source exists. No money, credits, purchases or paid infrastructure.

## Legacy privacy and compatibility

All new tables have RLS and no direct anon/authenticated grants. Only the thirty-one intended native RPCs have authenticated EXECUTE. Internal helpers are revoked except the minimal current-user membership/public-status predicates needed by RLS. Canonical squads/members RLS and squads_public view now exclude private rosters from outsiders. Legacy squad_profile, squad_board, squad_battle_board, wars_board, scrim_find and matchmaker suggestions are hardened. join_squad retains signature and delegates to public instant join, never bypasses private approval. Old invitation accept cannot bypass SF expiry. Shared membership trigger enforces capacity and one canonical squad per account under row locks across old and new entry routes; global unique(user_id) is a final race backstop. Existing public squads are not made private. Historic public tournament results/registrations are not retroactively unpublished by squad privacy.

Original slot/roster/MVP/kill-feed/match-chat RPCs and data are unchanged. Legacy create/leave/disband/chat/logo APIs keep their signatures. Private-squad legacy profile pages intentionally return unavailable to outsiders rather than leak data.

## Verification and release

Migration compiled in a rolled-back transaction, then rollback regression tests passed before deployment and again against the deployed functions. Tests use selected existing accounts only inside transactions and roll back all fixture rows/account-context changes. They do not send real chat messages, notifications, emails or pushes. Actual anon/authenticated grants and anon RLS were tested; live REST denies anon SF RPCs/direct table reads while xr_slot_list still returns twelve Season 2 slots. Real rows before/after remained: 1 squad, 1 member, 1 invitation, 8 tournaments, 10 matches, 2 registrations, 0 results. All new SF data/flag tables remain empty.

Simultaneous multi-session stress testing was not performed; row-lock/unique constraints and serialized capacity tests cover the intended concurrency path. Native UI and end-to-end real authenticated HTTP flows are still the native agent's work. No UI was changed here.

Migration is single-apply and transactional; rerunning after success fails safely on existing table names. Source includes the migration, self-contained BEGIN/ROLLBACK regression tests and original legacy definitions for audit. Do not blindly restore old public policies after private squads exist. A rollback must first freeze SF writes, review new private data and preserve private access controls; older views/routines would otherwise disclose those rosters.
