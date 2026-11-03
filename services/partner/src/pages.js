// The partner page and the admin console. Plain HTML with inline script (CSP allows only that and
// same-origin requests). All user text is inserted with textContent, never as HTML.

const STYLE = `
:root{--ground:#F5F0E6;--surface:#FBF8F2;--line:#D3C5B1;--ink:#302A25;--muted:#5C5249;--onink:#FBF8F2;--soft:#EBE2D4;--danger:#8E2F25}
@media (prefers-color-scheme: dark){:root{--ground:#1C1814;--surface:#25201B;--line:#4B4137;--ink:#F0E8DB;--muted:#C3B6A4;--onink:#221C17;--soft:#322B24;--danger:#EBA597}}
*{box-sizing:border-box}
body{margin:0;background:var(--ground);color:var(--ink);font:16px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:440px;margin:0 auto;padding:40px 20px;display:flex;flex-direction:column;gap:14px}
h1{font-size:24px;margin:8px 0 0}
p{margin:0}.muted{color:var(--muted);font-size:14px}
input{height:52px;border:1px solid var(--line);border-radius:12px;background:var(--surface);color:var(--ink);font-size:24px;letter-spacing:8px;text-align:center;width:100%}
button{height:52px;border-radius:12px;border:0;background:var(--ink);color:var(--onink);font-size:17px;font-weight:600;width:100%;cursor:pointer}
button.ghost{background:transparent;color:var(--ink);border:1px solid var(--line)}
.code{font-size:52px;font-weight:700;letter-spacing:6px;text-align:center;font-variant-numeric:tabular-nums}
.bar{height:4px;border-radius:2px;background:var(--soft)}.bar>div{height:4px;border-radius:2px;background:var(--ink)}
.err{color:var(--danger);font-size:14px;min-height:20px}
`;

const LOGO = `<svg width="86" height="29" viewBox="0 0 339 114" role="img" aria-label="lockd in"><g fill="currentColor"><path d="M20.70 104L5.40 104L5.40 31.40L20.70 31.40"/><path d="M107.80 105.10Q100.30 105.10 94.20 101.80Q88.10 98.50 84.60 92.70Q81.10 86.90 81.10 79.70Q81.10 72.40 84.65 66.65Q88.20 60.90 94.30 57.60Q100.40 54.30 108 54.30Q113.70 54.30 118.45 56.25Q123.20 58.20 126.90 62.10L117.10 71.90Q115.40 70 113.15 69.10Q110.90 68.20 108 68.20Q104.70 68.20 102.150 69.65Q99.60 71.10 98.15 73.65Q96.70 76.20 96.70 79.60Q96.70 83 98.15 85.60Q99.60 88.20 102.20 89.70Q104.80 91.20 108 91.20Q111 91.20 113.35 90.15Q115.70 89.10 117.40 87.20L127.10 97Q123.30 101 118.50 103.05Q113.70 105.10 107.80 105.10"/><path d="M181.50 104L163.50 104L146.50 78.80L163.40 55.40L180.50 55.40L160.10 81.70L160.60 75.40L181.50 104M147.70 104L132.40 104L132.40 31.40L147.70 31.40"/><path d="M206.20 105Q199.30 105 193.95 101.70Q188.60 98.40 185.55 92.70Q182.50 87 182.50 79.70Q182.50 72.40 185.55 66.70Q188.60 61 193.95 57.70Q199.30 54.40 206.20 54.40Q211.20 54.40 215.25 56.30Q219.30 58.20 221.95 61.55Q224.60 64.90 224.90 69.20L224.90 89.70Q224.60 94 222 97.45Q219.40 100.90 215.30 102.95Q211.20 105 206.20 105M208.90 91.20Q212.20 91.20 214.60 89.75Q217 88.30 218.40 85.70Q219.80 83.10 219.80 79.70Q219.80 76.30 218.45 73.75Q217.10 71.20 214.65 69.70Q212.20 68.20 209 68.20Q205.80 68.20 203.35 69.70Q200.90 71.20 199.45 73.80Q198 76.40 198 79.70Q198 83 199.40 85.60Q200.80 88.20 203.30 89.70Q205.80 91.20 208.90 91.20M234.20 31.40L234.20 104L219.20 104L219.20 90.90L221.50 79.10L218.90 67.30L218.90 31.40"/><path d="M275.90 104L260.60 104L260.60 55.40L275.90 55.40L275.90 104M268.30 48.70Q264.70 48.70 262.35 46.25Q260 43.80 260 40.30Q260 36.70 262.35 34.30Q264.70 31.90 268.30 31.90Q271.90 31.90 274.20 34.30Q276.50 36.70 276.50 40.30Q276.50 43.80 274.20 46.25Q271.90 48.70 268.30 48.70"/><path d="M332.60 104L317.30 104L317.30 76.30Q317.30 72.50 314.95 70.15Q312.60 67.80 309 67.80Q306.50 67.80 304.60 68.85Q302.70 69.90 301.60 71.85Q300.50 73.80 300.50 76.30L294.60 73.40Q294.60 67.70 297.10 63.40Q299.60 59.10 304.05 56.75Q308.50 54.40 314.10 54.40Q319.50 54.40 323.65 56.95Q327.80 59.50 330.20 63.70Q332.60 67.90 332.60 72.90L332.60 104M300.50 104L285.20 104L285.20 55.40L300.50 55.40"/><path fill-rule="evenodd" d="M33.67 62.65A25.4 25.4 0 1 0 71.33 62.65A4.06 4.06 0 0 1 70.28 59.93V48.36A17.78 17.78 0 0 0 34.72 48.36V59.93A4.06 4.06 0 0 1 33.67 62.65ZM44.88 55.47V48.36A7.62 7.62 0 0 1 60.12 48.36V55.47A25.4 25.4 0 0 0 44.88 55.47ZM72.31 79.7A19.81 19.81 0 1 0 32.69 79.7A19.81 19.81 0 1 0 72.31 79.7ZM67.23 79.7A14.73 14.73 0 1 0 37.77 79.7A14.73 14.73 0 1 0 67.23 79.7Z"/></g></svg>`;

export function partnerPage(linkId) {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Lockd In partner code</title><style>${STYLE}</style></head><body><main>
${LOGO}
<h1 id="title">Loading…</h1>
<p id="intro" class="muted"></p>
<div id="pinBox" hidden><label for="pin" class="muted" id="pinLabel">PIN</label><input id="pin" inputmode="numeric" pattern="[0-9]*" maxlength="4" autocomplete="off" aria-describedby="err"></div>
<button id="go" hidden></button>
<p id="err" class="err" role="alert"></p>
<div id="codeBox" hidden><p class="code" id="code" aria-live="polite"></p><div class="bar"><div id="fill"></div></div><p class="muted" id="left" style="text-align:center;margin-top:8px"></p></div>
<p class="muted" style="margin-top:24px">This page is locked with your PIN. Lockd In doesn’t ask for your name, number or email.</p>
</main><script>
const link = ${JSON.stringify(linkId)};
const $ = (id) => document.getElementById(id);
let pin = "", timer = null, state = null;
async function api(path, data) {
  const r = await fetch("/api/p/" + link + "/" + path, data ? { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(data) } : {});
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(j.error || "Something went wrong. Try again.");
  return j;
}
function show(s) {
  state = s;
  $("err").textContent = "";
  $("pinBox").hidden = s === "code"; $("go").hidden = s === "code"; $("codeBox").hidden = s !== "code";
}
async function start() {
  try {
    const st = await api("status");
    if (!st.claimed) {
      $("title").textContent = "Set your PIN";
      $("intro").textContent = "You’ve been asked to hold a Lockd In code for " + st.name + "’s focus sessions. Choose a 4-digit PIN. You’ll need it each time you open this page.";
      $("pinLabel").textContent = "New 4-digit PIN"; $("go").textContent = "Save PIN"; show("claim");
    } else {
      $("title").textContent = "Open the code";
      $("intro").textContent = "Only give the code if the person really needs to stop a focus session early.";
      $("pinLabel").textContent = "Your PIN"; $("go").textContent = "Show code"; show("pin");
    }
  } catch (e) { $("title").textContent = "This link doesn’t work"; $("intro").textContent = e.message; $("pinBox").hidden = true; }
}
async function refresh() {
  try {
    const r = await api("code", { pin });
    $("code").textContent = r.code.slice(0, 3) + " " + r.code.slice(3);
    let left = r.secondsLeft;
    const tick = () => { $("left").textContent = "New code in " + left + " s"; $("fill").style.width = (left / 30 * 100) + "%"; if (left-- <= 0) { clearInterval(timer); refresh(); } };
    clearInterval(timer); tick(); timer = setInterval(tick, 1000);
  } catch (e) { clearInterval(timer); show("pin"); $("err").textContent = e.message; }
}
$("go").onclick = async () => {
  pin = $("pin").value.trim();
  if (!/^\\d{4}$/.test(pin)) { $("err").textContent = "Enter 4 digits."; return; }
  try {
    if (state === "claim") { await api("claim", { pin }); }
    $("title").textContent = "The current code"; $("intro").textContent = "Read it to the person if they really need to stop early. It changes every 30 seconds.";
    show("code"); refresh();
  } catch (e) { $("err").textContent = e.message; }
};
start();
</script></body></html>`;
}

export function adminPage() {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Lockd In admin</title><style>${STYLE}
main{max-width:1100px}.cols{display:flex;flex-wrap:wrap;gap:20px}.list{flex:1 1 260px;max-width:340px}.thread{flex:999 1 420px;min-width:0;display:flex;flex-direction:column;gap:10px}
.t{display:block;width:100%;text-align:left;height:auto;padding:12px 14px;margin-bottom:6px;background:var(--surface);color:var(--ink);border:1px solid var(--line);font-weight:500}
.msg{max-width:80%;padding:10px 14px;border-radius:14px;white-space:pre-wrap}.me{align-self:flex-start;background:var(--surface);border:1px solid var(--line)}.us{align-self:flex-end;background:var(--ink);color:var(--onink)}
textarea{width:100%;min-height:80px;border:1px solid var(--line);border-radius:12px;background:var(--surface);color:var(--ink);font:inherit;padding:10px}
input{letter-spacing:normal;font-size:16px;text-align:left;padding:0 12px}.row{display:flex;gap:10px;flex-wrap:wrap}.row button{width:auto;padding:0 18px}
</style></head><body><main>
<h1>Lockd In admin</h1>
<p class="muted">Every action here is logged. Unlocks end only the session the person asked about. Codes and keys are never shown.</p>
<div id="login"><label for="pw" class="muted">Admin password</label><input id="pw" type="password" autocomplete="current-password"><button id="signin" style="margin-top:10px">Sign in</button></div>
<p id="err" class="err" role="alert"></p>
<div class="cols" id="app" hidden><nav class="list" aria-label="Requests" id="threads"></nav><section class="thread" id="thread" aria-live="polite"></section></div>
</main><script>
const $ = (id) => document.getElementById(id);
let token = sessionStorage.getItem("lockdin-admin") || "";
async function api(path, data) {
  const r = await fetch("/api/admin/" + path, { method: data ? "POST" : "GET", headers: { authorization: "Bearer " + token, "content-type": "application/json" }, body: data ? JSON.stringify(data) : undefined });
  const j = await r.json().catch(() => ({}));
  if (r.status === 401) { sessionStorage.removeItem("lockdin-admin"); $("app").hidden = true; $("login").hidden = false; }
  if (!r.ok) throw new Error(j.error || "Request failed");
  return j;
}
function el(tag, cls, text) { const e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; }
async function loadThreads() {
  const { threads } = await api("threads");
  const nav = $("threads"); nav.replaceChildren(el("p", "muted", threads.length ? "Requests" : "No messages yet."));
  for (const t of threads) {
    const b = el("button", "t", "Account " + t.accountId.slice(0, 6) + (t.unread ? " · " + t.unread + " new" : ""));
    b.onclick = () => openThread(t.accountId); nav.append(b);
  }
}
async function openThread(id) {
  const t = await api("threads/" + id);
  const box = $("thread");
  box.replaceChildren(el("h2", null, "Account " + id.slice(0, 6)),
    el("p", "muted", "Partner link " + (t.partnerClaimed ? "claimed" : "not claimed yet") + " · unlocks in the last 30 days: " + t.unlocksLast30Days));
  const sessions = new Set();
  for (const m of t.messages) {
    if (m.kind === "unlock") { box.append(el("p", "msg us", "Unlock sent for session " + (m.sessionId || "").slice(0, 8))); continue; }
    box.append(el("p", "msg " + (m.fromAdmin ? "us" : "me"), m.body));
    if (!m.fromAdmin && m.sessionId) sessions.add(m.sessionId);
  }
  const ta = el("textarea"); ta.setAttribute("aria-label", "Reply");
  const row = el("div", "row");
  const send = el("button", null, "Send reply");
  send.onclick = async () => { try { await api("threads/" + id + "/reply", { body: ta.value }); openThread(id); } catch (e) { $("err").textContent = e.message; } };
  row.append(send);
  for (const s of sessions) {
    const u = el("button", "ghost", "Send unlock for session " + s.slice(0, 8));
    u.onclick = async () => { if (!confirm("Send an unlock that ends this session?")) return; try { await api("threads/" + id + "/unlock", { sessionId: s }); openThread(id); } catch (e) { $("err").textContent = e.message; } };
    row.append(u);
  }
  box.append(ta, row);
  loadThreads();
}
$("signin").onclick = async () => {
  token = $("pw").value; $("err").textContent = "";
  try { await loadThreads(); sessionStorage.setItem("lockdin-admin", token); $("login").hidden = true; $("app").hidden = false; } catch (e) { $("err").textContent = e.message; }
};
if (token) loadThreads().then(() => { $("login").hidden = true; $("app").hidden = false; }).catch(() => {});
</script></body></html>`;
}
