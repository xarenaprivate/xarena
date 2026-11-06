# Admin checklist implementation map, November 6, 2026

Existing Classic and Nova choices retained. Owner tools is a separate entry in both. Owner authentication is enforced server-side.

| Requested module | Implementation / limit |
|---|---|
| User management | Real searchable/filterable/paged directory, existing manual verification, validated ban/unban/warn; no phone/UID/device/IP/token display |
| Content management | Existing content/banners/news/catalog/admin editors retained |
| Payments | Excluded: free-entry/AP/XP-only product, no commerce/commission/refund machinery introduced |
| Notifications | Existing announcements and per-player composer, owner confirmation; push device delivery not asserted |
| Reports/exports | Snapshot metrics, accounts page CSV, audit CSV, existing registration exports |
| Roles | Existing owner/helper semantics preserved, no new privileged identities created |
| Bookings | Existing squad registrations/match management retained, no real registration/test moderation changed |
| Support | Feedback inbox resolve/reopen + audit |
| Analytics | Server counts/time-stamped snapshot, no fabricated live online/revenue/growth |
| Audit | New mutations audited, filtered bounded history and export |
| Feature flags/config | Existing controls preserved |
| Coupons | User-defined free gifts: owner single-recipient AP/avatar/logo/banner, mailbox/claim, request and claim idempotence |
| Partners | Existing sponsors/creators/support sections retained, no payouts |
| Moderation | Existing queues plus account action tools; no live moderation tests |
| Sessions/devices | Real record counts only. Force logout not implemented: must respect Auth refresh revocation and access JWT expiry, never claim push deletion logs out sessions |
| Force update | Existing web app config retained; native launch/skip/manual-check update work handled separately |

Recovery: exact email callback live/allowlisted with previous Luna callbacks and site URL preserved. No custom SMTP configured or email/reset test sent. Phone-only player recovery key enrollment/redeem now supported using server-side Auth API, private hash-only one-time keys. No actual player key/reset used in tests. Key consumption is atomic but external Auth change is separate: fail closed on ambiguity, key stays consumed.

Visual checks and endpoint tests are not a real-phone notification/decryption test. Do not describe the whole checklist as complete while session revocation, SMTP and native validation limits remain.
