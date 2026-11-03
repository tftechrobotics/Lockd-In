// Small crypto helpers for the partner service (WebCrypto only; works in Workers and Node 20+).
// The same TOTP (RFC 6238: HMAC-SHA1, 30 s steps, 6 digits) and unlock-grant functions run in the
// Android app (app.lockdin.partner.PartnerCode), so codes match without either side going online.

const enc = new TextEncoder();

export function b64(bytes) {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function unb64(str) {
  const s = atob(str.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((str.length + 3) % 4));
  return Uint8Array.from(s, (c) => c.charCodeAt(0));
}

export function randomId(bytes = 16) {
  return b64(crypto.getRandomValues(new Uint8Array(bytes)));
}

export async function sha256(text) {
  return b64(new Uint8Array(await crypto.subtle.digest("SHA-256", enc.encode(text))));
}

/** Constant-time string comparison (same length strings). */
export function same(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}

/** PBKDF2-SHA256 for partner PINs (4 digits: the real protection is the attempt limit). */
export async function pinHash(pin, saltB64) {
  const key = await crypto.subtle.importKey("raw", enc.encode(pin), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt: unb64(saltB64), iterations: 100000 }, key, 256);
  return b64(new Uint8Array(bits));
}

async function masterKey(masterB64) {
  return crypto.subtle.importKey("raw", unb64(masterB64), "AES-GCM", false, ["encrypt", "decrypt"]);
}

/** Encrypts the account's code key at rest with the Worker secret MASTER_KEY. */
export async function seal(masterB64, keyBytes) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, await masterKey(masterB64), keyBytes));
  return b64(iv) + "." + b64(ct);
}

export async function open(masterB64, sealed) {
  const [iv, ct] = sealed.split(".");
  return new Uint8Array(await crypto.subtle.decrypt({ name: "AES-GCM", iv: unb64(iv) }, await masterKey(masterB64), unb64(ct)));
}

async function hmac(hash, keyBytes, msgBytes) {
  const k = await crypto.subtle.importKey("raw", keyBytes, { name: "HMAC", hash }, false, ["sign"]);
  return new Uint8Array(await crypto.subtle.sign("HMAC", k, msgBytes));
}

export const STEP_S = 30;

/** RFC 6238 TOTP: 6 digits for the 30-second step containing `nowMs`. */
export async function totp(keyBytes, nowMs) {
  const counter = Math.floor(nowMs / 1000 / STEP_S);
  const msg = new Uint8Array(8);
  let c = counter;
  for (let i = 7; i >= 0; i--) { msg[i] = c & 0xff; c = Math.floor(c / 256); }
  const h = await hmac("SHA-1", keyBytes, msg);
  const off = h[h.length - 1] & 0x0f;
  const bin = ((h[off] & 0x7f) << 24) | (h[off + 1] << 16) | (h[off + 2] << 8) | h[off + 3];
  return String(bin % 1000000).padStart(6, "0");
}

/** An admin unlock for one session: the first 16 hex of HMAC-SHA256(key, "unlock:" + sessionId). */
export async function unlockGrant(keyBytes, sessionId) {
  const h = await hmac("SHA-256", keyBytes, enc.encode("unlock:" + sessionId));
  return Array.from(h.slice(0, 8), (b) => b.toString(16).padStart(2, "0")).join("");
}
