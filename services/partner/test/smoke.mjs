// Live check against a deployed service: create an account, claim the partner link with a PIN,
// read the code, check it against the code computed here from the same key, check a wrong PIN is
// refused, then delete the account. Usage: node test/smoke.mjs https://…workers.dev
import { b64, totp } from "../src/crypto.js";
const base = process.argv[2].replace(/\/$/, "");
const post = async (path, body, headers = {}) => {
  const r = await fetch(base + path, { method: "POST", headers: { "content-type": "application/json", ...headers }, body: JSON.stringify(body) });
  return { status: r.status, body: await r.json().catch(() => ({})) };
};
const fail = (m) => { console.log("::error::" + m); process.exit(1); };
const key = crypto.getRandomValues(new Uint8Array(32));
const acct = await post("/api/accounts", { key: b64(key), partnerName: "Smoke test" });
if (acct.status !== 201) fail("create account: " + JSON.stringify(acct));
console.log("account created");
const page = await fetch(acct.body.linkUrl); if (!page.ok) fail("partner page " + page.status);
const claim = await post(`/api/p/${acct.body.linkId}/claim`, { pin: "4821" });
if (claim.status !== 200) fail("claim: " + JSON.stringify(claim));
const again = await post(`/api/p/${acct.body.linkId}/claim`, { pin: "1111" });
if (again.status !== 409) fail("second claim should be refused: " + again.status);
console.log("link claimed once; second claim refused");
const wrong = await post(`/api/p/${acct.body.linkId}/code`, { pin: "0000" });
if (wrong.status !== 403) fail("wrong PIN should be refused: " + wrong.status);
const t = Date.now();
const got = await post(`/api/p/${acct.body.linkId}/code`, { pin: "4821" });
if (got.status !== 200) fail("code: " + JSON.stringify(got));
const ok = [t - 30000, t, t + 30000].map((x) => totp(key, x));
if (!(await Promise.all(ok)).includes(got.body.code)) fail("code mismatch");
console.log("partner code matches the phone's code");
const auth = { authorization: `Bearer ${acct.body.accountId}.${acct.body.token}` };
const msg = await post("/api/messages", { body: "smoke test message", sessionId: "smoke-session" }, auth);
if (msg.status !== 200 || !msg.body.partnerClaimed) fail("messages: " + JSON.stringify(msg));
const bad = await post("/api/messages", { body: "x" }, { authorization: `Bearer ${acct.body.accountId}.wrongwrongwrongwrong` });
if (bad.status !== 401) fail("bad token should be refused: " + bad.status);
console.log("messages work; bad token refused");
// Partner approval (R51): the phone asks, the partner allows with their PIN, the phone gets a grant it can check.
const { unlockGrant } = await import("../src/crypto.js");
const ask = await post("/api/requests", { sessionId: "smoke-session-2", sessionEnd: Date.now() + 40 * 60000 }, auth);
if (ask.status !== 201 || !ask.body.url) fail("request: " + JSON.stringify(ask));
const reqPage = await fetch(ask.body.url); if (!reqPage.ok) fail("request page " + reqPage.status);
const get = async () => (await fetch(base + "/api/requests/" + ask.body.id, { headers: auth })).json();
if ((await get()).status !== "pending") fail("request should be pending");
const peek = await post("/api/r/" + ask.body.id, { pin: "0000" });
if (peek.status !== 403) fail("wrong PIN on request should be refused: " + peek.status);
const view = await post("/api/r/" + ask.body.id, { pin: "4821" });
if (view.status !== 200 || view.body.status !== "pending") fail("view request: " + JSON.stringify(view));
const listed = await post(`/api/p/${acct.body.linkId}/code`, { pin: "4821" });
if (listed.body.request?.id !== ask.body.id) fail("partner page should list the request: " + JSON.stringify(listed.body));
const allow = await post("/api/r/" + ask.body.id, { pin: "4821", decision: "allow" });
if (allow.status !== 200 || allow.body.status !== "allowed") fail("allow: " + JSON.stringify(allow));
const twice = await post("/api/r/" + ask.body.id, { pin: "4821", decision: "deny" });
if (twice.status !== 409) fail("second decision should be refused: " + twice.status);
const after = await get();
if (after.status !== "allowed" || after.grant !== await unlockGrant(key, "smoke-session-2")) fail("grant mismatch: " + JSON.stringify(after));
console.log("partner approval works; grant matches the phone's check");

// Lockd In account (R50): sign up, sign in, wrong password refused, delete.
const email = `smoke-${Date.now()}@example.com`;
const up = await post("/api/users", { email, password: "correct horse", name: "Smoke", device: "smoke" });
if (up.status !== 201 || !up.body.deviceToken) fail("sign up: " + JSON.stringify(up));
const dup = await post("/api/users", { email, password: "another one", name: "Smoke" });
if (dup.status !== 409) fail("duplicate email should be refused: " + dup.status);
const badPw = await post("/api/signin", { email, password: "wrong password" });
if (badPw.status !== 403) fail("wrong password should be refused: " + badPw.status);
const inn = await post("/api/signin", { email: email.toUpperCase(), password: "correct horse", device: "smoke 2" });
if (inn.status !== 200) fail("sign in: " + JSON.stringify(inn));
const meR = await fetch(base + "/api/me", { headers: { authorization: "Bearer " + inn.body.deviceToken } });
if ((await meR.json()).email !== email) fail("me");
const meDel = await fetch(base + "/api/me", { method: "DELETE", headers: { authorization: "Bearer " + up.body.deviceToken } });
if (!meDel.ok) fail("delete user " + meDel.status);
const gone = await fetch(base + "/api/me", { headers: { authorization: "Bearer " + inn.body.deviceToken } });
if (gone.status !== 401) fail("deleted user's devices should be signed out: " + gone.status);
console.log("accounts: sign up, sign in, wrong password refused, delete signs out every device");

const del = await fetch(base + "/api/account", { method: "DELETE", headers: auth });
if (!del.ok) fail("delete " + del.status);
console.log("account deleted");
console.log("::notice::Partner service live check passed at " + base);
