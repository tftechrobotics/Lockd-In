// Lockd In partner service (R49): partner links and codes, messages to admin, admin unlocks.
//
// What it stores: per account, the code key encrypted with MASTER_KEY, a hash of the device
// token, the partner's first name (as typed in the app), the partner link and a hash of its PIN,
// and the person's messages to admin. No phone numbers, emails or contacts.
//
// Admin (the owner now, an AI agent later) authenticates with ADMIN_TOKEN and can only: read
// messages, reply, and send an unlock for the session named in the person's own message. Admin
// never sees keys or codes. Every admin action is written to the audit table.
import { b64, open, pinHash, randomId, same, seal, sha256, totp, unb64, unlockGrant, STEP_S } from "./crypto.js";
import { adminPage, partnerPage } from "./pages.js";

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
  await env.DB.batch([
    env.DB.prepare("INSERT INTO accounts (id, token_hash, key_enc, partner_name, created_at, created_ip) VALUES (?, ?, ?, ?, ?, ?)")
      .bind(id, await sha256(token), await seal(env.MASTER_KEY, key), name, now(), ip),
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

async function code(req, env, linkId) {
  const row = await linkRow(env, linkId);
  if (!row) return err(404, "This link isn’t valid.");
  if (!row.claimed_at) return err(409, "Set a PIN first.");
  if (row.locked_until > now()) return err(429, `Too many wrong PINs. Try again in ${Math.ceil((row.locked_until - now()) / 60000)} min.`);
  const pin = String((await body(req)).pin || "");
  if (!same(await pinHash(pin, row.pin_salt), row.pin_hash)) {
    const fails = row.failures + 1;
    await env.DB.prepare("UPDATE links SET failures = ?, locked_until = ? WHERE id = ?")
      .bind(fails >= MAX_FAILS ? 0 : fails, fails >= MAX_FAILS ? now() + LOCK_MS : 0, linkId).run();
    return err(403, fails >= MAX_FAILS ? "Too many wrong PINs. Locked for 15 minutes." : "Wrong PIN.");
  }
  if (row.failures) await env.DB.prepare("UPDATE links SET failures = 0 WHERE id = ?").bind(linkId).run();
  const t = now();
  const key = await open(env.MASTER_KEY, row.key_enc);
  return json({ code: await totp(key, t), secondsLeft: STEP_S - Math.floor(t / 1000) % STEP_S });
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
