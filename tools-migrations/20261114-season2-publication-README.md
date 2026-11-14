# Season 2 publication and reminder, 14 November 2026

- Published exact approved scoring rules through xr_scoring_rules(p_tournament text), backed by site_settings.scoring_XR-S2. Unknown tournaments return published=false. Public rules contain no private roster or credential data.
- Website Match Center renders placement points 12/9/8/7/6/5/4/3/2/1/0/0, one point per kill, total across six matches, ties by Booyahs, kills, then Match 6 placement. No automatic score awards are implied.
- Approved Season 2 news published with current schedule: Sunday 15 November 2026, 21:00 IST, six starts through 23:30. Registration stays open, prior registrations valid.
- Only descriptions of closed xr-s1 and xr-5oar were replaced with: This tournament is marked completed. Registration is closed.
- Existing register_squad already has a reg_open/status check under a row lock. Rollback-only direct-call tests confirmed both closed tournaments reject crafted registration attempts without inserting rows. No duplicate guard rewrite.
- Reminder approved for 15 November 20:00 IST. reminder-send.sql is idempotent for reference season2-20261115-2000 and checks event state/time before enqueue. It is not executed on deployment. Recheck approval and newer schedule changes before executing.
- Reminder audience: nonbanned app profiles. In-app notifications cover those accounts; handset push requires a registered device token and device permission. Existing minute-level FCM dispatch handles queued notifications. Database enqueue is not proof of handset receipt.

No private credential dumps, account identifiers, message history or keys are included. Native app rendering and handset receipt require separate verification.
