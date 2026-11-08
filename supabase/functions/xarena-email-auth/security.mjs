// Isolated preparation only. No network, credentials, or live mutations.
// Importable by Deno edge runtime and Node test runner.
export const POLICY = Object.freeze({ ttlSeconds: 600, maxFailures: 5, resendSeconds: 60 });
export function normalizeEmail(input) {
  if (typeof input !== 'string') throw new Error('Invalid email.');
  const value = input.trim();
  if (value.length > 254 || /[\r\n\x00-\x20\x7f]/.test(value)) throw new Error('Invalid email.');
  const parts = value.split('@');
  if (parts.length !== 2 || !parts[0] || parts[0].length > 64) throw new Error('Invalid email.');
  // Initial version accepts practical ASCII addresses only, not quoted local parts/IDNs.
  const [local, domain] = parts;
  if (!/^[A-Za-z0-9.!#$%&'*+\/=?^_`{|}~-]+$/.test(local) || local.startsWith('.') || local.endsWith('.') || local.includes('..')) throw new Error('Invalid email.');
  const labels = domain.split('.');
  if (labels.length < 2 || labels.some(x => !/^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$/.test(x))) throw new Error('Invalid email.');
  if (domain.toLowerCase() === 'phone.xarena') throw new Error('Use phone signup.');
  // Shared Supabase email matching needs this same normalization everywhere.
  // Never strip '+' tags or Gmail dots: those are not our identity rules.
  return `${local.toLowerCase()}@${domain.toLowerCase()}`;
}
export function validCode(code) { return typeof code === 'string' && /^\d{6}$/.test(code); }
export function validPassword(value) {
  // Existing browser signup uses maximum72. Bound bytes to avoid provider truncation.
  return typeof value === 'string' && value.length >= 8 && new TextEncoder().encode(value).length <= 72;
}
export function makeCode(random = crypto.getRandomValues.bind(crypto)) {
  // Rejection sampling avoids biased modulo on the 32-bit random range.
  const limit = Math.floor(2 ** 32 / 1000000) * 1000000;
  const a = new Uint32Array(1);
  do { random(a); } while (a[0] >= limit);
  return String(a[0] % 1000000).padStart(6, '0');
}
export async function codeDigest(pepper, challengeId, email, code) {
  if (!pepper || pepper.length < 32 || !challengeId || !validCode(code)) throw new Error('Invalid verification input.');
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(pepper), {name:'HMAC', hash:'SHA-256'}, false, ['sign']);
  const payload = JSON.stringify(['xarena-email-code-v1', challengeId, normalizeEmail(email), code]);
  const signed = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(payload));
  return Array.from(new Uint8Array(signed), x=>x.toString(16).padStart(2,'0')).join('');
}
export function digestEqual(a,b) {
  if (typeof a !== 'string' || typeof b !== 'string' || !/^[a-f0-9]{64}$/.test(a) || !/^[a-f0-9]{64}$/.test(b)) return false;
  let d=0; for(let i=0;i<64;i++) d |= a.charCodeAt(i)^b.charCodeAt(i); return d===0;
}
export function challengeEligible(row, nowMs) {
  // Pure predicate only. Database transaction must lock and reserve atomically.
  return row?.state === 'sent' && Number.isInteger(row.failures) && row.failures >= 0 && row.failures < POLICY.maxFailures && Number.isFinite(row.expiresAtMs) && nowMs < row.expiresAtMs;
}
