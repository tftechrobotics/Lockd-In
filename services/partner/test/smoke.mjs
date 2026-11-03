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
const del = await fetch(base + "/api/account", { method: "DELETE", headers: auth });
if (!del.ok) fail("delete " + del.status);
console.log("account deleted");
console.log("::notice::Partner service live check passed at " + base);
