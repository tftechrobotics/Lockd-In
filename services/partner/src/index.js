// Lockd In partner service (R49): partner links and codes, messages to admin, admin unlocks.
//
// What it stores: per account, the code key encrypted with MASTER_KEY, a hash of the device
// token, the partner's first name (as typed in the app), the partner link and a hash of its PIN,
// and the person's messages to admin. With a Lockd In account (R50): the email, first name and a
// salted password hash. Partner approval requests (R51) hold a session id and its end time only.
//
// Admin (the owner now, an AI agent later) authenticates with ADMIN_TOKEN and can only: read
// messages, reply, and send an unlock for the session named in the person's own message. Admin
// never sees keys or codes. Every admin action is written to the audit table.
import { b64, open, pinHash, randomId, same, seal, sha256, totp, unb64, unlockGrant, STEP_S } from "./crypto.js";
import { adminPage, partnerPage, requestPage } from "./pages.js";

const json = (data, status = 200) => new Response(JSON.stringify(data), {
  status, headers: { "content-type": "application/json", "cache-control": "no-store" },
});
const err = (status, message) => json({ error: message }, status);
const now = () => Date.now();
const ID = /^[A-Za-z0-9_-]{8,64}$/;
const MAX_FAILS = 5;
const LOCK_MS = 15 * 60 * 1000;

async function body(req, max = 4096) {
  const t = await req.text();
  if (t.length > max) throw new Error("too large");
  return t ? JSON.parse(t) : {};
}

async function audit(env, actor, action, accountId, detail) {
  await env.DB.prepare("INSERT INTO audit (at, actor, action, account_id, detail) VALUES (?, ?, ?, ?, ?)")
    .bind(now(), actor, action, accountId ?? null, detail ?? null).run();
}

/** The device's account from "Authorization: Bearer <accountId>.<token>", or null. */
async function device(req, env) {
  const m = /^Bearer ([A-Za-z0-9_-]{8,64})\.([A-Za-z0-9_-]{16,128})$/.exec(req.headers.get("authorization") || "");
  if (!m) return null;
  const row = await env.DB.prepare("SELECT * FROM accounts WHERE id = ?").bind(m[1]).first();
  if (!row || !same(row.token_hash, await sha256(m[2]))) return null;
  return row;
}

function admin(req, env) {
  const t = (req.headers.get("authorization") || "").replace(/^Bearer /, "");
  return Boolean(env.ADMIN_TOKEN) && same(t, env.ADMIN_TOKEN);
}

async function createAccount(req, env) {
  const ip = req.headers.get("cf-connecting-ip") || "";
  const recent = await env.DB.prepare("SELECT COUNT(*) AS n FROM accounts WHERE created_ip = ? AND created_at > ?").bind(ip, now() - 3600_000).first();
  if ((recent?.n ?? 0) >= 5) return err(429, "Too many new accounts from this network. Try again later.");
  const b = await body(req);
  const key = unb64(String(b.key || ""));
  if (key.length !== 32) return err(400, "key must be 32 bytes");
  const name = String(b.partnerName || "").trim().slice(0, 40) || "Partner";
  const id = randomId(12), token = randomId(32), link = randomId(12);
  const owner = await userFrom(req.headers.get("x-user-auth"), env);
  await env.DB.batch([
    env.DB.prepare("INSERT INTO accounts (id, token_hash, key_enc, partner_name, created_at, created_ip, user_id) VALUES (?, ?, ?, ?, ?, ?, ?)")
      .bind(id, await sha256(token), await seal(env.MASTER_KEY, key), name, now(), ip, owner?.user.id ?? null),
    env.DB.prepare("INSERT INTO links (id, account_id) VALUES (?, ?)").bind(link, id),
  ]);
  const origin = new URL(req.url).origin;
  return json({ accountId: id, token, linkId: link, linkUrl: `${origin}/p/${link}` }, 201);
}

async function linkRow(env, linkId) {
  if (!ID.test(linkId)) return null;
  return env.DB.prepare("SELECT links.*, accounts.key_enc, accounts.partner_name FROM links JOIN accounts ON accounts.id = links.account_id WHERE links.id = ?").bind(linkId).first();
}

async function claim(req, env, linkId) {
  const row = await linkRow(env, linkId);
  if (!row) return err(404, "This link isn’t valid.");
  if (row.claimed_at) return err(409, "This link has already been claimed.");
  const pin = String((await body(req)).pin || "");
  if (!/^\d{4}$/.test(pin)) return err(400, "Choose a 4-digit PIN.");
  const salt = randomId(16);
  await env.DB.prepare("UPDATE links SET pin_hash = ?, pin_salt = ?, claimed_at = ? WHERE id = ? AND claimed_at IS NULL")
    .bind(await pinHash(pin, salt), salt, now(), linkId).run();
  return json({ ok: true });
}

/** Checks a partner PIN against a link row, counting failures. Returns an error response, or null when right. */
async function checkPin(env, row, pin) {
  if (!row.claimed_at) return err(409, "Set a PIN first.");
  if (row.locked_until > now()) return err(429, `Too many wrong PINs. Try again in ${Math.ceil((row.locked_until - now()) / 60000)} min.`);
  if (!same(await pinHash(String(pin || ""), row.pin_salt), row.pin_hash)) {
    const fails = row.failures + 1;
    await env.DB.prepare("UPDATE links SET failures = ?, locked_until = ? WHERE id = ?")
      .bind(fails >= MAX_FAILS ? 0 : fails, fails >= MAX_FAILS ? now() + LOCK_MS : 0, row.id).run();
    return err(403, fails >= MAX_FAILS ? "Too many wrong PINs. Locked for 15 minutes." : "Wrong PIN.");
  }
  if (row.failures) await env.DB.prepare("UPDATE links SET failures = 0 WHERE id = ?").bind(row.id).run();
  return null;
}

async function code(req, env, linkId) {
  const row = await linkRow(env, linkId);
  if (!row) return err(404, "This link isn’t valid.");
  const bad = await checkPin(env, row, (await body(req)).pin);
  if (bad) return bad;
  const t = now();
  const key = await open(env.MASTER_KEY, row.key_enc);
  const pending = await env.DB.prepare("SELECT id, session_end FROM requests WHERE account_id = ? AND decision IS NULL AND expires_at > ? ORDER BY created_at DESC LIMIT 1")
    .bind(row.account_id, t).first();
  return json({
    code: await totp(key, t), secondsLeft: STEP_S - Math.floor(t / 1000) % STEP_S,
    request: pending ? { id: pending.id, minutesLeft: Math.max(0, Math.ceil((pending.session_end - t) / 60000)) } : null,
  });
}

// ---------- Partner approval requests (R51) ----------
const REQUEST_MS = 30 * 60 * 1000;

function requestStatus(r) {
  if (r.decision === "allow") return "allowed";
  if (r.decision === "deny") return "denied";
  return r.expires_at > now() ? "pending" : "expired";
}

async function createRequest(req, env, acct) {
  const b = await body(req);
  const sessionId = String(b.sessionId || "");
  const sessionEnd = Number(b.sessionEnd);
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(sessionId)) return err(400, "sessionId is required");
  if (!Number.isFinite(sessionEnd) || sessionEnd <= now()) return err(400, "This session has already ended.");
  const link = await env.DB.prepare("SELECT claimed_at FROM links WHERE account_id = ?").bind(acct.id).first();
  if (!link?.claimed_at) return err(409, "Your partner hasn’t set their PIN yet.");
  const recent = await env.DB.prepare("SELECT COUNT(*) AS n FROM requests WHERE account_id = ? AND created_at > ?").bind(acct.id, now() - 3600_000).first();
  if ((recent?.n ?? 0) >= 10) return err(429, "Too many requests this hour.");
  const id = randomId(12);
  const t = now();
  await env.DB.prepare("INSERT INTO requests (id, account_id, session_id, session_end, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)")
    .bind(id, acct.id, sessionId, Math.min(sessionEnd, t + 24 * 3600_000), t, Math.min(t + REQUEST_MS, sessionEnd)).run();
  return json({ id, url: `${new URL(req.url).origin}/r/${id}` }, 201);
}

async function requestForDevice(env, acct, id) {
  if (!ID.test(id)) return err(404, "No such request");
  const r = await env.DB.prepare("SELECT * FROM requests WHERE id = ? AND account_id = ?").bind(id, acct.id).first();
  if (!r) return err(404, "No such request");
  const status = requestStatus(r);
  return json({ status, grant: status === "allowed" ? r.grant_code : null });
}

/** The partner's side: with their PIN, see the request; with a decision, allow or refuse it. */
async function partnerDecide(req, env, id) {
  if (!ID.test(id)) return err(404, "This request isn’t valid.");
  const r = await env.DB.prepare(`SELECT requests.*, links.id AS link_id, links.pin_hash, links.pin_salt, links.claimed_at, links.failures, links.locked_until,
      accounts.key_enc, accounts.user_id, users.name AS person
    FROM requests JOIN accounts ON accounts.id = requests.account_id JOIN links ON links.account_id = accounts.id
    LEFT JOIN users ON users.id = accounts.user_id WHERE requests.id = ?`).bind(id).first();
  if (!r) return err(404, "This request isn’t valid.");
  const b = await body(req);
  const bad = await checkPin(env, { id: r.link_id, pin_hash: r.pin_hash, pin_salt: r.pin_salt, claimed_at: r.claimed_at, failures: r.failures, locked_until: r.locked_until }, b.pin);
  if (bad) return bad;
  let status = requestStatus(r);
  if (b.decision != null) {
    if (status !== "pending") return err(409, status === "expired" ? "This request has expired." : "This request was already answered.");
    const allow = b.decision === "allow";
    const grant = allow ? await unlockGrant(await open(env.MASTER_KEY, r.key_enc), r.session_id) : null;
    await env.DB.prepare("UPDATE requests SET decision = ?, decided_at = ?, grant_code = ? WHERE id = ? AND decision IS NULL")
      .bind(allow ? "allow" : "deny", now(), grant, id).run();
    await audit(env, "partner", allow ? "allow_request" : "deny_request", r.account_id, id);
    status = allow ? "allowed" : "denied";
  }
  return json({ status, person: r.person || null, minutesLeft: Math.max(0, Math.ceil((r.session_end - now()) / 60000)) });
}

// ---------- Lockd In accounts (R50) ----------
const EMAIL = /^[^\s@]{1,64}@[^\s@]{1,190}\.[^\s@]{2,}$/;
const USER_MAX_FAILS = 10;

async function newDevice(env, userId, label) {
  const id = randomId(12), token = randomId(32);
  await env.DB.prepare("INSERT INTO devices (id, user_id, token_hash, label, created_at, last_seen) VALUES (?, ?, ?, ?, ?, ?)")
    .bind(id, userId, await sha256(token), String(label || "Phone").slice(0, 40), now(), now()).run();
  return `${id}.${token}`;
}

function publicUser(u) { return { userId: u.id, email: u.email, name: u.name }; }

async function signUp(req, env) {
  const ip = req.headers.get("cf-connecting-ip") || "";
  const recent = await env.DB.prepare("SELECT COUNT(*) AS n FROM users WHERE created_ip = ? AND created_at > ?").bind(ip, now() - 3600_000).first();
  if ((recent?.n ?? 0) >= 5) return err(429, "Too many new accounts from this network. Try again later.");
  const b = await body(req);
  const email = String(b.email || "").trim().toLowerCase();
  const name = String(b.name || "").trim().slice(0, 40);
  const password = String(b.password || "");
  if (!name) return err(400, "Add your first name.");
  if (!EMAIL.test(email) || email.length > 254) return err(400, "That email doesn’t look right.");
  if (password.length < 8) return err(400, "Use at least 8 characters for your password.");
  if (password.length > 200) return err(400, "That password is too long.");
  const taken = await env.DB.prepare("SELECT id FROM users WHERE email = ?").bind(email).first();
  if (taken) return err(409, "There’s already an account with this email. Sign in instead.");
  const id = randomId(12), salt = randomId(16);
  await env.DB.prepare("INSERT INTO users (id, email, name, pw_hash, pw_salt, created_at, created_ip) VALUES (?, ?, ?, ?, ?, ?, ?)")
    .bind(id, email, name, await pinHash(password, salt), salt, now(), ip).run();
  return json({ ...publicUser({ id, email, name }), deviceToken: await newDevice(env, id, b.device) }, 201);
}

async function signIn(req, env) {
  const b = await body(req);
  const email = String(b.email || "").trim().toLowerCase();
  const u = await env.DB.prepare("SELECT * FROM users WHERE email = ?").bind(email).first();
  const wrong = () => err(403, "That email and password don’t match.");
  if (!u) { await pinHash(String(b.password || ""), "AAAAAAAAAAAAAAAAAAAAAA"); return wrong(); }
  if (u.locked_until > now()) return err(429, `Too many tries. Try again in ${Math.ceil((u.locked_until - now()) / 60000)} min.`);
  if (!same(await pinHash(String(b.password || ""), u.pw_salt), u.pw_hash)) {
    const fails = u.failures + 1;
    await env.DB.prepare("UPDATE users SET failures = ?, locked_until = ? WHERE id = ?")
      .bind(fails >= USER_MAX_FAILS ? 0 : fails, fails >= USER_MAX_FAILS ? now() + LOCK_MS : 0, u.id).run();
    return fails >= USER_MAX_FAILS ? err(429, "Too many tries. Locked for 15 minutes.") : wrong();
  }
  if (u.failures) await env.DB.prepare("UPDATE users SET failures = 0 WHERE id = ?").bind(u.id).run();
  return json({ ...publicUser(u), deviceToken: await newDevice(env, u.id, b.device) });
}

/** The signed-in user from "Bearer <deviceId>.<token>" (a device token from sign-up or sign-in). */
async function userFrom(auth, env) {
  const m = /^(?:Bearer )?([A-Za-z0-9_-]{8,64})\.([A-Za-z0-9_-]{16,128})$/.exec(auth || "");
  if (!m) return null;
  const d = await env.DB.prepare("SELECT * FROM devices WHERE id = ?").bind(m[1]).first();
  if (!d || !same(d.token_hash, await sha256(m[2]))) return null;
  const u = await env.DB.prepare("SELECT * FROM users WHERE id = ?").bind(d.user_id).first();
  if (!u) return null;
  if (now() - d.last_seen > 3600_000) await env.DB.prepare("UPDATE devices SET last_seen = ? WHERE id = ?").bind(now(), d.id).run();
  return { user: u, device: d };
}

async function me(req, env) {
  const who = await userFrom(req.headers.get("authorization"), env);
  if (!who) return err(401, "Signed out. Sign in again.");
  if (req.method === "GET") return json(publicUser(who.user));
  if (req.method === "DELETE") {
    await env.DB.batch([
      env.DB.prepare("UPDATE accounts SET user_id = NULL WHERE user_id = ?").bind(who.user.id),
      env.DB.prepare("DELETE FROM devices WHERE user_id = ?").bind(who.user.id),
      env.DB.prepare("DELETE FROM users WHERE id = ?").bind(who.user.id),
    ]);
    await audit(env, "device", "delete_user", who.user.id);
    return json({ ok: true });
  }
  return err(405, "Method not allowed");
}

async function deviceMessages(req, env, acct) {
  if (req.method === "POST") {
    const b = await body(req);
    const text = String(b.body || "").trim().slice(0, 1000);
    const sessionId = b.sessionId == null ? null : String(b.sessionId).slice(0, 64);
    if (!text) return err(400, "Write a message.");
    const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM messages WHERE account_id = ? AND from_admin = 0 AND created_at > ?").bind(acct.id, now() - 3600_000).first();
    if ((count?.n ?? 0) >= 20) return err(429, "Too many messages this hour.");
    await env.DB.prepare("INSERT INTO messages (account_id, from_admin, kind, body, session_id, created_at) VALUES (?, 0, 'text', ?, ?, ?)")
      .bind(acct.id, text, sessionId, now()).run();
  }
  const rows = await env.DB.prepare("SELECT id, from_admin AS fromAdmin, kind, body, session_id AS sessionId, created_at AS at FROM messages WHERE account_id = ? ORDER BY id DESC LIMIT 50").bind(acct.id).all();
  const link = await env.DB.prepare("SELECT claimed_at FROM links WHERE account_id = ?").bind(acct.id).first();
  return json({ messages: rows.results.reverse(), partnerClaimed: Boolean(link?.claimed_at) });
}

async function adminApi(req, env, path) {
  if (!env.ADMIN_TOKEN) return err(503, "Admin isn’t set up yet (LOCKDIN_ADMIN_PASSWORD).");
  if (!admin(req, env)) return err(401, "Sign in again.");
  if (path === "/api/admin/threads" && req.method === "GET") {
    const rows = await env.DB.prepare(`SELECT account_id AS accountId, MAX(created_at) AS lastAt,
      SUM(CASE WHEN from_admin = 0 AND read_by_admin = 0 THEN 1 ELSE 0 END) AS unread
      FROM messages GROUP BY account_id ORDER BY lastAt DESC LIMIT 100`).all();
    return json({ threads: rows.results });
  }
  const m = /^\/api\/admin\/threads\/([A-Za-z0-9_-]{8,64})(\/reply|\/unlock)?$/.exec(path);
  if (!m) return err(404, "Not found");
  const acct = await env.DB.prepare("SELECT id, partner_name, key_enc, created_at FROM accounts WHERE id = ?").bind(m[1]).first();
  if (!acct) return err(404, "No such account");
  if (!m[2] && req.method === "GET") {
    const msgs = await env.DB.prepare("SELECT id, from_admin AS fromAdmin, kind, body, session_id AS sessionId, created_at AS at FROM messages WHERE account_id = ? ORDER BY id DESC LIMIT 100").bind(acct.id).all();
    await env.DB.prepare("UPDATE messages SET read_by_admin = 1 WHERE account_id = ?").bind(acct.id).run();
    const link = await env.DB.prepare("SELECT claimed_at FROM links WHERE account_id = ?").bind(acct.id).first();
    const unlocks = await env.DB.prepare("SELECT COUNT(*) AS n FROM messages WHERE account_id = ? AND kind = 'unlock' AND created_at > ?").bind(acct.id, now() - 30 * 86400_000).first();
    await audit(env, "admin", "read_thread", acct.id);
    return json({ accountId: acct.id, partnerClaimed: Boolean(link?.claimed_at), unlocksLast30Days: unlocks?.n ?? 0, messages: msgs.results.reverse() });
  }
  if (req.method !== "POST") return err(405, "Method not allowed");
  const b = await body(req);
  if (m[2] === "/reply") {
    const text = String(b.body || "").trim().slice(0, 1000);
    if (!text) return err(400, "Write a reply.");
    await env.DB.prepare("INSERT INTO messages (account_id, from_admin, kind, body, created_at) VALUES (?, 1, 'text', ?, ?)").bind(acct.id, text, now()).run();
    await audit(env, "admin", "reply", acct.id);
    return json({ ok: true });
  }
  // Unlock: only for a session the person named in one of their own messages.
  const sessionId = String(b.sessionId || "");
  const asked = await env.DB.prepare("SELECT id FROM messages WHERE account_id = ? AND from_admin = 0 AND session_id = ? LIMIT 1").bind(acct.id, sessionId).first();
  if (!sessionId || !asked) return err(400, "Unlocks are only for a session the person asked about.");
  const grant = await unlockGrant(await open(env.MASTER_KEY, acct.key_enc), sessionId);
  await env.DB.prepare("INSERT INTO messages (account_id, from_admin, kind, body, session_id, created_at) VALUES (?, 1, 'unlock', ?, ?, ?)").bind(acct.id, grant, sessionId, now()).run();
  await audit(env, "admin", "unlock", acct.id, sessionId);
  return json({ ok: true });
}

const PAGE_HEADERS = {
  "content-type": "text/html; charset=utf-8",
  "cache-control": "no-store",
  "content-security-policy": "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
  "referrer-policy": "no-referrer",
  "x-robots-tag": "noindex",
};

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    const path = url.pathname;
    try {
      if (path === "/" ) return new Response("Lockd In partner service", { headers: { "content-type": "text/plain" } });
      if (path === "/health") return json({ ok: true, admin: Boolean(env.ADMIN_TOKEN) });
      if (path === "/admin") return new Response(adminPage(), { headers: PAGE_HEADERS });
      let m = /^\/p\/([A-Za-z0-9_-]{8,64})$/.exec(path);
      if (m && req.method === "GET") return new Response(partnerPage(m[1]), { headers: PAGE_HEADERS });
      if (path === "/api/accounts" && req.method === "POST") return await createAccount(req, env);
      if (path === "/api/users" && req.method === "POST") return await signUp(req, env);
      if (path === "/api/signin" && req.method === "POST") return await signIn(req, env);
      if (path === "/api/me") return await me(req, env);
      if (path === "/api/me/device" && req.method === "DELETE") {
        const who = await userFrom(req.headers.get("authorization"), env);
        if (!who) return err(401, "Signed out.");
        await env.DB.prepare("DELETE FROM devices WHERE id = ?").bind(who.device.id).run();
        return json({ ok: true });
      }
      m = /^\/r\/([A-Za-z0-9_-]{8,64})$/.exec(path);
      if (m && req.method === "GET") return new Response(requestPage(m[1]), { headers: PAGE_HEADERS });
      m = /^\/api\/r\/([A-Za-z0-9_-]{8,64})$/.exec(path);
      if (m && req.method === "POST") return await partnerDecide(req, env, m[1]);
      if (path === "/api/requests" && req.method === "POST") {
        const acct = await device(req, env);
        if (!acct) return err(401, "Not signed in");
        return await createRequest(req, env, acct);
      }
      m = /^\/api\/requests\/([A-Za-z0-9_-]{8,64})$/.exec(path);
      if (m && req.method === "GET") {
        const acct = await device(req, env);
        if (!acct) return err(401, "Not signed in");
        return await requestForDevice(env, acct, m[1]);
      }
      m = /^\/api\/p\/([A-Za-z0-9_-]{8,64})\/(claim|code|status)$/.exec(path);
      if (m) {
        if (m[2] === "status") { const r = await linkRow(env, m[1]); return r ? json({ claimed: Boolean(r.claimed_at), name: r.partner_name }) : err(404, "This link isn’t valid."); }
        if (req.method !== "POST") return err(405, "Method not allowed");
        return m[2] === "claim" ? await claim(req, env, m[1]) : await code(req, env, m[1]);
      }
      if (path === "/api/messages") {
        const acct = await device(req, env);
        if (!acct) return err(401, "Not signed in");
        return await deviceMessages(req, env, acct);
      }
      if (path === "/api/account" && req.method === "DELETE") {
        const acct = await device(req, env);
        if (!acct) return err(401, "Not signed in");
        await env.DB.batch([
          env.DB.prepare("DELETE FROM messages WHERE account_id = ?").bind(acct.id),
          env.DB.prepare("DELETE FROM requests WHERE account_id = ?").bind(acct.id),
          env.DB.prepare("DELETE FROM links WHERE account_id = ?").bind(acct.id),
          env.DB.prepare("DELETE FROM accounts WHERE id = ?").bind(acct.id),
        ]);
        await audit(env, "device", "delete_account", acct.id);
        return json({ ok: true });
      }
      if (path.startsWith("/api/admin/")) return await adminApi(req, env, path);
      return err(404, "Not found");
    } catch (e) {
      return err(400, "Bad request");
    }
  },
};
