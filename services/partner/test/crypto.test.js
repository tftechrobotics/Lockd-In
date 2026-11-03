// Run with: node --test test/   (Node 20+; WebCrypto is built in)
import { test } from "node:test";
import assert from "node:assert/strict";
import { b64, unb64, totp, unlockGrant, seal, open, pinHash, same } from "../src/crypto.js";

const ascii = (s) => new TextEncoder().encode(s);

test("TOTP matches the RFC 6238 SHA-1 test vectors (last 6 digits)", async () => {
  const key = ascii("12345678901234567890");
  const vectors = [[59, "287082"], [1111111109, "081804"], [1111111111, "050471"], [1234567890, "005924"], [2000000000, "279037"], [20000000000, "353130"]];
  for (const [t, code] of vectors) assert.equal(await totp(key, t * 1000), code);
});

test("base64url round trip", () => {
  const b = crypto.getRandomValues(new Uint8Array(32));
  assert.deepEqual(unb64(b64(b)), b);
});

test("seal/open round trip, and a wrong master key fails", async () => {
  const master = b64(crypto.getRandomValues(new Uint8Array(32)));
  const key = crypto.getRandomValues(new Uint8Array(32));
  assert.deepEqual(await open(master, await seal(master, key)), key);
  const other = b64(crypto.getRandomValues(new Uint8Array(32)));
  await assert.rejects(open(other, await seal(master, key)));
});

test("unlock grant is per session and 16 hex chars", async () => {
  const key = ascii("12345678901234567890123456789012");
  const a = await unlockGrant(key, "session-a"), b = await unlockGrant(key, "session-b");
  assert.match(a, /^[0-9a-f]{16}$/);
  assert.notEqual(a, b);
  assert.equal(a, await unlockGrant(key, "session-a"));
});

test("PIN hash depends on the salt; constant-time compare", async () => {
  const s1 = b64(crypto.getRandomValues(new Uint8Array(16))), s2 = b64(crypto.getRandomValues(new Uint8Array(16)));
  const h1 = await pinHash("1234", s1);
  assert.equal(h1, await pinHash("1234", s1));
  assert.notEqual(h1, await pinHash("1234", s2));
  assert.ok(same("abc", "abc")); assert.ok(!same("abc", "abd")); assert.ok(!same("abc", "ab"));
});
