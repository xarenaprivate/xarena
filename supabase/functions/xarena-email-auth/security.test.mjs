import test from 'node:test';
import assert from 'node:assert/strict';
import {normalizeEmail,validCode,validPassword,makeCode,codeDigest,digestEqual,challengeEligible} from './security.mjs';
test('normalization preserves plus and dots, exact domain rules',()=>{
 assert.equal(normalizeEmail(' A.B+test@GMAIL.com '),'a.b+test@gmail.com');
 for(const x of ['a@phone.xarena','a\r\nBcc:x@y.com','a@localhost','a..b@gmail.com','a@-bad.com','a@@gmail.com','x'.repeat(65)+'@gmail.com']) assert.throws(()=>normalizeEmail(x));
});
test('code and password bounds',()=>{
 assert.equal(validCode('000001'),true); for(const x of ['12345','1234567',123456,'１２３４５６']) assert.equal(validCode(x),false);
 assert.equal(validPassword('abcdefgh'),true); assert.equal(validPassword('a'.repeat(73)),false); assert.equal(validPassword('😀'.repeat(19)),false);
});
test('six digit code and rejection sampling',()=>{
 let calls=0; const code=makeCode(a=>{a[0]=calls++ === 0 ? 4294967295 : 7;return a;});
 assert.equal(code,'000007');assert.equal(calls,2);
});
test('code HMAC bound to challenge/email, digest comparison',async()=>{
 const pepper='sample-nonsecret-test-value-only-123456';
 const a=await codeDigest(pepper,'id1','a@example.com','123456');
 assert.equal(digestEqual(a,a),true);assert.equal(digestEqual(a,await codeDigest(pepper,'id2','a@example.com','123456')),false);
 assert.equal(digestEqual(a,await codeDigest(pepper,'id1','b@example.com','123456')),false);assert.equal(digestEqual(a,''),false);
});
test('expiry, attempts, reservation fail closed',()=>{
 const row={state:'sent',failures:0,expiresAtMs:1000};assert.equal(challengeEligible(row,999),true);
 assert.equal(challengeEligible(row,1000),false);assert.equal(challengeEligible({...row,failures:5},999),false);
 for(const state of ['reserved','consumed','pending','failed']) assert.equal(challengeEligible({...row,state},999),false);
});
