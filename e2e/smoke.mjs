// BoxPilot real-browser smoke test. Plain Playwright, no framework. Exit code != 0 on any failed step.
import { chromium } from 'playwright';
import { deflateSync } from 'node:zlib';
import { randomUUID } from 'node:crypto';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const FRONT = process.env.E2E_FRONTEND_URL || 'http://localhost:5173';
const API = process.env.E2E_API_URL || 'http://localhost:8080';
const USER = process.env.E2E_USERNAME;
const PASS = process.env.E2E_PASSWORD;
const OUT = process.env.E2E_OUT || mkdtempSync(join(tmpdir(), 'boxpilot-e2e-'));
if (!USER || !PASS) { console.error('E2E_USERNAME and E2E_PASSWORD are required'); process.exit(2); }
mkdirSync(OUT, { recursive: true });

const RUN = Date.now().toString(36);
const ITEM = `E2E-Hammer-${RUN}`;
const ITEM_NEXT1 = `E2E-Nagel-${RUN}-1`;
const ITEM_NEXT2 = `E2E-Nagel-${RUN}-2`;
const NEWUSER = `e2e-user-${RUN}`;
const NEWPASS = `Pw-${RUN}-long-enough-1!`;
const NEWPASS2 = `${NEWPASS}-2`;
const OIDC_USER = 'e2e-nextcloud-user';

// Simulated clients for the throttle steps (X-Forwarded-For, accepted because the Docker gateway is a trusted proxy).
// Random per run: the limiter is in-memory with a 15-minute window and the backend is not restarted between runs.
// The rest of the journey (incl. its deliberate wrong passwords) runs from its own random address IP_M, so repeated runs never
// exhaust the shared "no header" address.
const oct = () => 1 + Math.floor(Math.random() * 253);
const IP_E = `100.64.0.${oct()}`, IP_M = `100.64.1.${oct()}`; // IP_M: the main journey (and every context without its own address)
const IP_A = `203.0.113.${oct()}`, IP_B = `192.0.2.${oct()}`, IP_C = `198.51.100.${oct()}`, IP_D = `203.0.113.${oct()}`;
const xff = (ip) => ({ 'X-Forwarded-For': ip });

// ---- tiny valid PNG (solid colour) ----
const crcT = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = (b) => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
const SIG = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
function png(w = 64, h = 64, rgb = [200, 40, 40]) {
  const row = Buffer.concat([Buffer.from([0]), Buffer.from(Array.from({ length: w }, () => rgb).flat())]);
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
  return Buffer.concat([SIG, chunk('IHDR', ihdr), chunk('IDAT', deflateSync(Buffer.concat(Array(h).fill(row)))), chunk('IEND', Buffer.alloc(0))]);
}
// Few bytes, but the header claims w x h pixels (what a decompression-bomb check looks at).
function pngHuge(w, h) {
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
  return Buffer.concat([SIG, chunk('IHDR', ihdr), chunk('IDAT', deflateSync(Buffer.alloc(1))), chunk('IEND', Buffer.alloc(0))]);
}

// ---- issue collection (console errors + bad responses), per context ----
let issues = [];
let allowed = []; // [{re, status}] expected non-2xx for the current step
const cspHits = []; // console messages mentioning the Content Security Policy, whole run
const allow = (re, ...statuses) => allowed.push({ re, statuses });
function watch(page, label) {
  page.on('console', (m) => {
    if (/Content Security Policy/i.test(m.text())) cspHits.push(`[${label}] ${m.text().slice(0, 300)}`);
    if (m.type() !== 'error') return;
    if (/Failed to load resource/.test(m.text())) return; // covered by response check below
    issues.push(`[${label}] console error: ${m.text().slice(0, 200)}`);
  });
  page.on('pageerror', (e) => issues.push(`[${label}] pageerror: ${String(e).slice(0, 200)}`));
  page.on('response', (r) => {
    const s = r.status(), u = r.url();
    if (s < 400 && !(s >= 500)) return;
    if (allowed.some((a) => a.re.test(u) && a.statuses.includes(s))) return;
    issues.push(`[${label}] HTTP ${s} ${r.request().method()} ${u.replace(/\?.*/, '')}`);
  });
}

const results = [];
const findings = [];
async function step(name, fn) {
  issues = []; allowed = [];
  let err = null;
  try { await fn(); } catch (e) { err = e; }
  const bad = [...(err ? [`assertion: ${String(err.message).split('\n')[0]}`] : []), ...issues];
  results.push({ name, ok: bad.length === 0 });
  console.log(`${bad.length ? 'FAIL' : 'PASS'}  ${name}`);
  for (const b of bad) console.log(`        - ${b}`);
  if (bad.length) findings.push(`${name}: ${bad.join(' | ')}`);
}
async function until(fn, msg, ms = 8000) { const t = Date.now(); while (Date.now() - t < ms) { if (await fn()) return; await new Promise((r) => setTimeout(r, 200)); } throw new Error(msg); }
const ok = (c, msg) => { if (!c) throw new Error(msg); };
const shot = (page, n) => page.screenshot({ path: join(OUT, `${n}.png`), fullPage: true }).catch(() => {});

// fake camera device for the photo dialog step; the permission prompt is auto-accepted
const browser = await chromium.launch({ args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] });
async function newCtx(opts = {}) {
  const ctx = await browser.newContext({ locale: 'de-DE', extraHTTPHeaders: xff(IP_M), ...opts });
  const page = await ctx.newPage();
  watch(page, opts.label || 'main');
  return { ctx, page };
}
const cookiesOf = async (ctx) => (await ctx.cookies(API)).concat(await ctx.cookies(FRONT));
const hasSession = async (ctx) => (await cookiesOf(ctx)).some((c) => c.name === 'SESSION' && c.value);
const apiStatus = (page, path) => page.evaluate(async (u) => (await fetch(u, { credentials: 'include' })).status, API + path);

// The password field has a show/hide button whose label also contains "Passwort": address the fields by autocomplete.
const userField = (p) => p.locator('input[autocomplete="username"]');
const passField = (p) => p.locator('input[autocomplete="current-password"]');
async function submitLogin(page, user, pass) {
  await userField(page).fill(user);
  await passField(page).fill(pass);
  await page.getByRole('button', { name: 'Anmelden', exact: true }).click();
}
async function login(page, user, pass) {
  await page.goto(FRONT + '/app/boxes');
  await submitLogin(page, user, pass);
}
const loggedIn = (page) => page.getByRole('button', { name: 'Abmelden' }).waitFor({ timeout: 10000 });
const adminRow = (p, name) => p.getByRole('row', { name: new RegExp(name) });
const thumb = (p, name = ITEM) => p.getByRole('button', { name: `Foto von ${name} anzeigen` }).locator('img').first();
async function thumbLoads(p, msg) {
  const img = thumb(p);
  await img.waitFor({ timeout: 10000 });
  await p.waitForFunction((el) => el.complete, await img.elementHandle());
  ok(await img.evaluate((e) => e.naturalWidth > 0), msg);
  return img;
}

// CSRF-protected POST through a browser-context request context (carries that context's X-Forwarded-For).
async function apiPost(rc, path, opts) {
  const tok = (await (await rc.get(API + '/api/v1/csrf')).json()).token;
  return rc.post(API + path, { ...opts, headers: { 'X-XSRF-TOKEN': tok } });
}
// Fire send(i) until a 429 arrives; every answer before must be 401. Returns the 429 response.
const TOO_MANY = /Zu viele Fehlversuche/;
async function exhaust(send, what) {
  const seen = [];
  for (let i = 0; i < 25; i++) {
    const r = await send(i);
    seen.push(r.status());
    if (r.status() !== 429) continue;
    ok(i > 0, `${what}: first attempt already 429 - address throttled by an earlier run (random collision), rerun`);
    ok(seen.slice(0, -1).every((x) => x === 401), `${what}: expected only 401 before the 429, got ${seen.join(',')}`);
    const ra = Number(r.headers()['retry-after']);
    ok(ra > 0, `${what}: Retry-After header missing/invalid: "${r.headers()['retry-after']}"`);
    const body = await r.json().catch(() => ({}));
    ok(typeof body.error === 'string' && body.retryAfterSeconds > 0, `${what}: unexpected 429 body ${JSON.stringify(body)}`);
    return r;
  }
  throw new Error(`${what}: no 429 after ${seen.length} attempts, statuses ${seen.join(',')}`);
}
// The page navigates away right after the logout answer, so the body is read inside a route handler.
async function captureLogout(page) {
  const got = new Promise((resolve) => page.route(/\/api\/auth\/logout$/, async (route) => {
    const r = await route.fetch();
    resolve({ status: r.status(), body: await r.json().catch(() => ({})) });
    await route.fulfill({ response: r });
  }));
  return got;
}
async function deleteUserViaUi(p, name) {
  await p.goto(FRONT + '/app/admin');
  const row = adminRow(p, name);
  await row.waitFor({ timeout: 8000 });
  await row.getByRole('button', { name: 'Benutzer löschen' }).click();
  await p.getByRole('dialog').getByRole('button', { name: 'Löschen', exact: true }).click();
  await row.waitFor({ state: 'detached', timeout: 8000 });
}

let { ctx, page } = await newCtx();
let boxNo = null, publicUrl = null, boxId = null, userCreated = false, userDeleted = false;
const editUrl = () => `${FRONT}/app/boxes/${boxId}/edit`;

try {
  await step('1 login page + localized error on wrong password', async () => {
    await page.goto(FRONT + '/app/boxes');
    await page.getByRole('heading', { name: 'BoxCopilot' }).waitFor();
    await shot(page, '01-login');
    allow(/\/api\/auth\/login/, 400, 401, 403);
    await userField(page).fill(USER);
    await passField(page).fill('definitely-wrong-' + RUN);
    await page.getByRole('button', { name: 'Anmelden', exact: true }).click();
    const alert = page.getByRole('alert');
    await alert.waitFor({ timeout: 8000 });
    const txt = (await alert.innerText()).trim();
    await shot(page, '01-wrong-password');
    ok(!/bad credentials|invalid|unauthori|error|failed|locked/i.test(txt), `raw English error shown: "${txt}"`);
    ok(/[äöüß]|Anmeld|Passwort|Benutzer|ungültig|falsch/i.test(txt), `error not recognisably German: "${txt}"`);
    ok(!(await hasSession(ctx)), 'session cookie exists after failed login');
  });

  await step('2 form login, authenticated UI, Admin visible', async () => {
    await passField(page).fill(PASS);
    await page.getByRole('button', { name: 'Anmelden', exact: true }).click();
    await loggedIn(page);
    await page.getByRole('button', { name: 'Admin' }).waitFor();
    ok(await hasSession(ctx), 'no SESSION cookie after login');
    await shot(page, '02-boxlist');
  });

  await step('3 create box', async () => {
    await page.getByRole('button', { name: 'Neue Box' }).click();
    const dlg = page.getByRole('dialog');
    await dlg.getByLabel('Aktuelles Zimmer').fill(`Flur-${RUN}`);
    await dlg.getByLabel('Zielzimmer').fill(`Keller-${RUN}`);
    await dlg.getByLabel('Beschreibung').fill(`e2e ${RUN}`);
    await dlg.getByRole('button', { name: 'Erstellen', exact: true }).click();
    await page.waitForURL(/\/app\/boxes\/(\d+)\/edit/);
    boxId = page.url().match(/boxes\/(\d+)\/edit/)[1];
    await page.getByText(/^Box #\d+/).first().waitFor();
    boxNo = (await page.getByText(/^Box #\d+/).first().innerText()).match(/\d+/)[0];
    await page.getByRole('button', { name: 'Zurück', exact: true }).click();
    await page.getByText(`Aktuell: Flur-${RUN}`).waitFor();
    await shot(page, '03-box-created');
  });

  await step('4 add item + upload photo (thumbnail loads, immutable cache)', async () => {
    await page.goto(editUrl());
    await page.getByRole('button', { name: 'Neues Item hinzufügen' }).click();
    const dlg = page.getByRole('dialog');
    await dlg.getByLabel('Item-Name').fill(ITEM);
    await dlg.getByRole('button', { name: 'Hinzufügen', exact: true }).click();
    await dlg.waitFor({ state: 'hidden' });
    await page.getByText(ITEM).first().waitFor();
    await page.getByRole('button', { name: 'Bearbeiten', exact: true }).first().click();
    const up = page.waitForResponse((r) => /\/api\/v1\/items\/\d+\/image$/.test(r.url()) && r.request().method() === 'POST');
    await dlg.locator('input[type=file]').setInputFiles({ name: 'foto.png', mimeType: 'image/png', buffer: png() });
    await dlg.locator('img').first().waitFor();
    await shot(page, '04-item-edit-with-photo');
    await dlg.getByRole('button', { name: 'Speichern', exact: true }).click();
    const ur = await up;
    ok(ur.status() < 300, `upload returned ${ur.status()}`);
    await dlg.waitFor({ state: 'hidden' });
    await page.reload();
    const img = await thumbLoads(page, 'thumbnail naturalWidth is 0');
    const src = await img.evaluate((e) => e.currentSrc);
    const r = await page.request.get(src);
    ok(r.status() === 200, `thumbnail GET ${r.status()}`);
    const cc = r.headers()['cache-control'] || '';
    ok(/immutable/i.test(cc), `Cache-Control lacks immutable: "${cc}"`);
    await shot(page, '04-thumbnail');
  });

  await step('5 upload rejections (non-image, > 25 MP) keep previous photo', async () => {
    const tryUpload = async (file, tag) => {
      await page.getByRole('button', { name: 'Bearbeiten', exact: true }).first().click();
      const dlg = page.getByRole('dialog');
      allow(/\/api\/v1\/items\/\d+\/image$/, 400, 413, 415, 422);
      const up = page.waitForResponse((r) => /\/api\/v1\/items\/\d+\/image$/.test(r.url()) && r.request().method() === 'POST');
      await dlg.locator('input[type=file]').setInputFiles(file);
      await dlg.getByRole('button', { name: 'Speichern', exact: true }).click();
      const ur = await up;
      ok(ur.status() >= 400 && ur.status() < 500, `${tag} answered ${ur.status()} (expected 4xx)`);
      const al = page.getByRole('alert').filter({ hasText: /\S/ }).first();
      await al.waitFor({ timeout: 8000 });
      console.log(`        (info) ${tag}: rejection message shown: ` + (await al.innerText()).replace(/\s+/g, ' '));
      await shot(page, `05-rejected-${tag}`);
      await page.reload();
      await thumbLoads(page, `previous photo no longer loads after ${tag}`);
    };
    await tryUpload({ name: 'fake.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('this is not an image, just text. '.repeat(20)) }, 'fake-image');
    const huge = pngHuge(6000, 5000); // 30 MP claimed
    ok(huge.length < 1000, 'generated pixel-bomb PNG should be tiny');
    await tryUpload({ name: 'huge.png', mimeType: 'image/png', buffer: huge }, 'over-25MP');
  });

  await step('6 item form: "Speichern und nächstes" keeps dialog open, Enter submits', async () => {
    await page.goto(editUrl());
    await page.getByRole('button', { name: 'Neues Item hinzufügen' }).click();
    const dlg = page.getByRole('dialog');
    const nameF = dlg.getByLabel('Item-Name');
    await nameF.fill(ITEM_NEXT1);
    await dlg.getByRole('button', { name: 'Speichern und nächstes' }).click();
    await until(async () => (await nameF.inputValue()) === '', 'name field was not cleared after "Speichern und nächstes"');
    ok(await dlg.isVisible(), 'dialog closed after "Speichern und nächstes"');
    await until(() => nameF.evaluate((e) => document.activeElement === e), 'name field not focused for the next item');
    await nameF.fill(ITEM_NEXT2);
    await nameF.press('Enter');
    await dlg.waitFor({ state: 'hidden', timeout: 8000 });
    await page.getByText(ITEM_NEXT1).first().waitFor();
    await page.getByText(ITEM_NEXT2).first().waitFor();
    await shot(page, '06-save-and-next');
  });

  await step('7 item search on box list', async () => {
    await page.goto(FRONT + '/app/boxes');
    await page.getByText(`Aktuell: Flur-${RUN}`).waitFor();
    await page.getByLabel('Item-Name').fill(ITEM);
    await page.getByText(`Aktuell: Flur-${RUN}`).waitFor();
    await until(async () => (await page.getByRole('heading', { name: /^Box #\d+/ }).count()) === 1, 'filter did not narrow list to the single box');
    await shot(page, '07-search');
    await page.getByLabel('Item-Name').fill('zzz-no-such-item-' + RUN);
    await page.getByText('Keine Boxen gefunden').waitFor({ timeout: 8000 });
    await page.getByRole('button', { name: 'Zurücksetzen' }).click();
  });

  await step('8 public preview in fresh context + API 401', async () => {
    await page.getByText(`Aktuell: Flur-${RUN}`).waitFor();
    await page.getByRole('button', { name: 'Aktionen' }).first().click();
    const popup = page.waitForEvent('popup', { timeout: 8000 }).catch(() => null);
    await page.getByRole('menuitem', { name: 'Public Link öffnen' }).or(page.getByRole('button', { name: 'Public Link öffnen' })).first().click();
    const pp = await popup;
    if (pp) { publicUrl = pp.url(); await pp.close(); }
    else publicUrl = page.url();
    ok(/\/public\/[0-9a-f-]{36}/.test(publicUrl), `no /public/<uuid> link, got ${publicUrl}`);
    const f = await newCtx({ label: 'public' });
    try {
      await f.page.goto(publicUrl);
      await f.page.getByText(`Flur-${RUN}`).first().waitFor({ timeout: 10000 });
      await f.page.getByText(`Keller-${RUN}`).first().waitFor();
      await f.page.getByText(ITEM, { exact: true }).first().waitFor();
      await thumbLoads(f.page, 'public item image does not load');
      await shot(f.page, '08-public');
      allow(/\/api\/v1\/boxes/, 401, 403);
      ok([401].includes(await apiStatus(f.page, '/api/v1/boxes')), 'GET /api/v1/boxes without session is not 401');
    } finally { await f.ctx.close(); }
  });

  await step('9 public error page: unknown link = branded 404 page without retry, 5xx = retry', async () => {
    const f = await newCtx({ label: 'public-err' });
    try {
      allow(/\/api\/v1\/public\//, 404, 503);
      const resp = f.page.waitForResponse((r) => /\/api\/v1\/public\//.test(r.url()));
      await f.page.goto(`${FRONT}/public/${randomUUID()}`);
      ok((await resp).status() === 404, 'unknown public uuid did not answer 404');
      await f.page.getByText('BoxCopilot').first().waitFor();
      await f.page.getByText('Link ungültig oder widerrufen').waitFor({ timeout: 8000 });
      ok((await f.page.getByRole('button', { name: 'Erneut versuchen' }).count()) === 0, 'retry button shown for an invalid link');
      await shot(f.page, '09-public-invalid');
      // simulated server error -> temporary error with retry button; retry (route removed) then reaches the real 404
      const handler = (route) => route.fulfill({ status: 503, contentType: 'application/json', body: '{}' });
      await f.page.route(/\/api\/v1\/public\//, handler);
      await f.page.goto(`${FRONT}/public/${randomUUID()}`);
      await f.page.getByText('Vorübergehender Fehler').waitFor({ timeout: 8000 });
      const retry = f.page.getByRole('button', { name: 'Erneut versuchen' });
      await retry.waitFor();
      await shot(f.page, '09-public-5xx');
      await f.page.unroute(/\/api\/v1\/public\//, handler);
      await retry.click();
      await f.page.getByText('Link ungültig oder widerrufen').waitFor({ timeout: 8000 });
    } finally { await f.ctx.close(); }
  });

  await step('10 share-link rotation: warning dialog, old link invalid, new link works', async () => {
    await page.goto(editUrl());
    const field = page.getByRole('textbox', { name: 'Public Link', exact: true });
    const oldUrl = await field.inputValue();
    ok(/\/public\/[0-9a-f-]{36}/.test(oldUrl), `public link field has no /public/<uuid>: "${oldUrl}"`);
    await page.getByRole('button', { name: 'Link neu erzeugen', exact: true }).click();
    const dlg = page.getByRole('dialog');
    await dlg.getByText(/gedruckten QR-Etiketten/).waitFor();
    await shot(page, '10-rotate-warning');
    const rot = page.waitForResponse((r) => /\/api\/v1\/boxes\/\d+\/rotate-share$/.test(r.url()) && r.request().method() === 'POST');
    await dlg.getByRole('button', { name: 'Link neu erzeugen', exact: true }).click();
    ok((await rot).status() < 300, 'rotate-share did not answer 2xx');
    await until(async () => (await field.inputValue()) !== oldUrl, 'public link field did not change after rotation');
    const newUrl = await field.inputValue();
    ok(/\/public\/[0-9a-f-]{36}/.test(newUrl) && newUrl !== oldUrl, `unexpected new link "${newUrl}"`);
    const f = await newCtx({ label: 'public-rotated' });
    try {
      allow(/\/api\/v1\/public\//, 404);
      await f.page.goto(oldUrl);
      await f.page.getByText('Link ungültig oder widerrufen').waitFor({ timeout: 8000 });
      await shot(f.page, '10-old-link');
      await f.page.goto(newUrl);
      await f.page.getByText(`Keller-${RUN}`).first().waitFor({ timeout: 10000 });
      await thumbLoads(f.page, 'item image on the rotated public link does not load');
    } finally { await f.ctx.close(); }
    publicUrl = newUrl;
  });

  await step('11 security headers on the frontend', async () => {
    const r = await ctx.request.get(FRONT + '/app/boxes');
    const h = r.headers();
    ok(h['content-security-policy'] || h['content-security-policy-report-only'], 'neither Content-Security-Policy nor -Report-Only header');
    ok(/nosniff/i.test(h['x-content-type-options'] || ''), `X-Content-Type-Options: "${h['x-content-type-options']}"`);
    ok(/DENY/i.test(h['x-frame-options'] || ''), `X-Frame-Options: "${h['x-frame-options']}"`);
    if (FRONT.startsWith('https://')) ok(h['strict-transport-security'], 'HSTS header missing on https');
    console.log(`        (info) CSP mode: ${h['content-security-policy'] ? 'enforced' : 'report-only'}`);
  });

  await step('12 admin: password policy (>= 12) and user creation', async () => {
    await page.goto(FRONT + '/app/admin');
    await page.getByRole('button', { name: 'Neuer Benutzer' }).click();
    const dlg = page.getByRole('dialog');
    await dlg.getByLabel('Benutzername').fill(NEWUSER);
    await dlg.getByRole('textbox', { name: /^Name/ }).fill('E2E Temp');
    const pw = dlg.getByLabel(/^Passwort/);
    const save = dlg.getByRole('button', { name: 'Speichern', exact: true });
    await pw.fill('Abcdefgh-12'); // 11 characters
    await dlg.getByText(/mindestens 12 Zeichen/).waitFor({ timeout: 5000 });
    ok(await save.isDisabled(), 'save is enabled with an 11 character password');
    await shot(page, '12-short-password');
    await pw.fill('Abcdefgh-123'); // 12 characters
    await until(() => save.isEnabled(), 'save still disabled with a 12 character password');
    await pw.fill(NEWPASS);
    await save.click();
    await dlg.waitFor({ state: 'hidden', timeout: 8000 });
    userCreated = true;
    await adminRow(page, NEWUSER).waitFor();
    await shot(page, '12-user-created');
  });

  await step('13 admin password reset ends the user\'s sessions, admin stays, old magic link dead', async () => {
    const n = await newCtx({ label: 'user-n', extraHTTPHeaders: xff(IP_D) });
    const m = await newCtx({ label: 'old-magic', extraHTTPHeaders: xff(IP_D) });
    try {
      await login(n.page, NEWUSER, NEWPASS);
      await loggedIn(n.page);
      ok(await hasSession(n.ctx), 'user N has no SESSION cookie after login');
      ok((await apiStatus(n.page, '/api/v1/boxes')) === 200, 'user N cannot call the API after login');

      // admin: magic link for N (must die with the reset), then reset the password
      await page.goto(FRONT + '/app/admin');
      const row = adminRow(page, NEWUSER);
      await row.waitFor();
      await row.getByRole('button', { name: 'Magic-Link generieren' }).click();
      let dlg = page.getByRole('dialog');
      await dlg.getByRole('button', { name: 'Generieren', exact: true }).click();
      const magicUrl = await page.getByRole('dialog').getByLabel('Login-Link').inputValue();
      ok(/#t=/.test(magicUrl), `magic link has no #t= token fragment: ${magicUrl.replace(/t=.*/, 't=<redacted>')}`);
      await page.getByRole('dialog').getByRole('button', { name: 'Schließen', exact: true }).click();
      await page.getByRole('dialog').waitFor({ state: 'hidden' });

      await row.getByRole('button', { name: 'Passwort setzen' }).click();
      dlg = page.getByRole('dialog');
      await dlg.getByLabel(/^Passwort/).fill(NEWPASS2);
      await dlg.getByRole('button', { name: 'Speichern', exact: true }).click();
      await page.getByText(/Aktive Sitzungen des Nutzers wurden beendet/).waitFor({ timeout: 8000 });
      await shot(page, '13-admin-after-reset');

      // N's old session is dead on the next API call and the UI falls back to the login form
      allow(/\/api\//, 401, 403);
      ok((await apiStatus(n.page, '/api/v1/boxes')) === 401, 'user N session still valid after password reset');
      await n.page.reload();
      await userField(n.page).waitFor({ timeout: 10000 });
      await shot(n.page, '13-user-thrown-out');

      // admin is untouched
      ok((await apiStatus(page, '/api/v1/boxes')) === 200, 'admin session ended by resetting another user');
      await page.reload();
      await page.getByRole('button', { name: 'Abmelden' }).waitFor();

      // old password rejected, new one works
      const bad = await apiPost(n.ctx.request, '/api/auth/login', { form: { username: NEWUSER, password: NEWPASS } });
      ok(bad.status() === 401, `old password after reset answered ${bad.status()} (expected 401)`);
      await submitLogin(n.page, NEWUSER, NEWPASS2);
      await loggedIn(n.page);

      // magic link generated before the reset is invalid
      allow(/\/api\/v1\/auth\/magic-login/, 401);
      await m.page.goto(magicUrl);
      await m.page.getByText(/ungültig oder abgelaufen/).waitFor({ timeout: 10000 });
      ok(!(await hasSession(m.ctx)), 'old magic link created a session');
      await shot(m.page, '13-old-magic-link');
    } finally { await n.ctx.close(); await m.ctx.close(); }
  });

  await step('14 no account lock: no badge, unlock endpoint gone', async () => {
    await page.goto(FRONT + '/app/admin');
    const row = adminRow(page, NEWUSER);
    await row.waitFor();
    const tableText = await page.locator('table').innerText();
    ok(!/gesperrt|locked|sperr/i.test(tableText), 'admin table mentions a lock state');
    allow(/\/unlock$/, 404); // registered before the request: the response listener fires during evaluate()
    const info = await page.evaluate(async ({ api, name }) => {
      const j = await (await fetch(`${api}/api/v1/admin/users`, { credentials: 'include' })).json();
      const arr = Array.isArray(j) ? j : (j.content || j.users || []);
      const u = arr.find((x) => x.username === name);
      if (!u) return { missing: true };
      const tok = (await (await fetch(`${api}/api/v1/csrf`, { credentials: 'include' })).json()).token;
      const r = await fetch(`${api}/api/v1/admin/users/${u.id}/unlock`, { method: 'PUT', credentials: 'include', headers: { 'X-XSRF-TOKEN': tok } });
      return { lockField: /lock/i.test(JSON.stringify(u)), unlock: r.status };
    }, { api: API, name: NEWUSER });
    ok(!info.missing, 'created user not found in admin user list response');
    ok(!info.lockField, 'user DTO still contains a lock field');
    ok(info.unlock === 404, `PUT .../unlock answered ${info.unlock} (expected 404)`);
  });

  await step('15 admin: delete test user', async () => {
    await deleteUserViaUi(page, NEWUSER);
    userDeleted = true;
  });

  await step('16 session expiry -> login screen', async () => {
    await page.goto(FRONT + '/app/boxes');
    await page.getByText(`Aktuell: Flur-${RUN}`).waitFor();
    await ctx.clearCookies();
    allow(/\/api\//, 401, 403);
    await page.getByLabel('Item-Name').fill(ITEM);
    await userField(page).waitFor({ timeout: 10000 });
    ok(!(await page.getByRole('button', { name: 'Abmelden' }).count()), 'stale authenticated UI still visible');
    await shot(page, '16-expired');
  });

  await step('17 logout: login screen, API 401, SESSION cookie gone', async () => {
    await submitLogin(page, USER, PASS);
    await loggedIn(page);
    const lo = captureLogout(page);
    await page.getByRole('button', { name: 'Abmelden' }).click();
    const { status: ls, body: lb } = await lo;
    ok(ls === 200, `POST /api/auth/logout answered ${ls} (expected 200 JSON)`);
    ok(typeof lb.redirectUrl === 'string' && lb.redirectUrl.startsWith(FRONT), `form-login logout redirectUrl should be the frontend, got ${JSON.stringify(lb)}`);
    await userField(page).waitFor();
    allow(/\/api\//, 401, 403);
    ok(!(await hasSession(ctx)), 'SESSION cookie still present after logout');
    ok((await apiStatus(page, '/api/v1/boxes')) === 401, '/api/v1/boxes not 401 after logout');
    await shot(page, '17-logout');
  });

  await step('18 Nextcloud/OIDC login via mock provider, no Admin', async () => {
    const o = await newCtx({ label: 'oidc', extraHTTPHeaders: {} }); // no XFF here: it would also be sent to the IdP's third-party font host (CORS preflight)
    try {
      const p = o.page;
      await p.goto(FRONT + '/app/boxes');
      await p.getByRole('button', { name: 'Mit Nextcloud anmelden' }).click();
      await p.waitForURL(/oidc\.localhost:9090/, { timeout: 15000 });
      await p.locator('input[name=username]').fill(OIDC_USER);
      await p.locator('textarea[name=claims]').fill(JSON.stringify({ name: 'E2E Nextcloud', email: 'e2e@example.test', preferred_username: OIDC_USER }));
      await shot(p, '18-mock-idp');
      await p.locator('input[type=submit], button[type=submit]').first().click();
      await p.waitForURL((u) => u.origin === FRONT, { timeout: 20000 });
      await loggedIn(p);
      ok((await p.getByRole('button', { name: 'Admin' }).count()) === 0, 'OIDC user sees Admin button');
      const me = await p.evaluate(async (u) => (await fetch(u + '/api/v1/me', { credentials: 'include' })).json(), API);
      ok(me.username === OIDC_USER || me.username?.includes(OIDC_USER), `mapped username unexpected: ${me.username}`);
      ok(me.name === 'E2E Nextcloud', `mapped name unexpected: ${me.name}`);
      await shot(p, '18-oidc-boxlist');
      // OIDC logout: backend answers JSON {redirectUrl} (IdP end-session), the SPA navigates there top-level and comes back
      const lo = captureLogout(p);
      const idp = p.waitForResponse((r) => r.request().isNavigationRequest() && /oidc\.localhost:9090\/.*endsession/.test(r.url()), { timeout: 15000 });
      await p.getByRole('button', { name: 'Abmelden' }).click();
      const { status: ls, body: lb } = await lo;
      ok(ls === 200, `OIDC logout answered ${ls} (expected 200 JSON)`);
      ok(/oidc\.localhost:9090\/.*endsession/.test(lb.redirectUrl || ''), `OIDC logout redirectUrl is not the IdP end-session URL: ${JSON.stringify(lb)}`);
      await idp;
      await p.waitForLoadState('load');
      // The return trip from the IdP to BoxPilot (redirect_uri handling) cannot be verified against the mock: check it against a real Nextcloud.
      ok(/[?&]redirect_uri=/.test(lb.redirectUrl) && decodeURIComponent(lb.redirectUrl.split('redirect_uri=')[1]).startsWith(FRONT), `redirectUrl does not carry the frontend as redirect_uri: ${JSON.stringify(lb)}`);
      ok(!(await hasSession(o.ctx)), 'SESSION cookie still present after OIDC logout');
      allow(/\/api\//, 401, 403);
      ok((await o.ctx.request.get(API + '/api/v1/boxes')).status() === 401, '/api/v1/boxes not 401 after OIDC logout');
      await shot(p, '18-after-logout-nav');
      await p.goto(FRONT + '/app/boxes'); // open BoxPilot again
      await userField(p).waitFor({ timeout: 10000 });
      await shot(p, '18-oidc-logged-out');
    } finally { await o.ctx.close(); }
  });

  await step('19 mobile 360x740: no horizontal overflow', async () => {
    const m = await newCtx({ label: 'mobile', viewport: { width: 360, height: 740 } });
    try {
      const over = (p) => p.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.clientWidth]);
      await m.page.goto(FRONT + '/app/boxes');
      await userField(m.page).waitFor();
      let [sw, cw] = await over(m.page);
      await shot(m.page, '19-mobile-login');
      ok(sw <= cw, `login overflow ${sw} > ${cw}`);
      await login(m.page, USER, PASS);
      await m.page.getByRole('heading', { name: 'Boxen' }).waitFor();
      await m.page.getByText(`Aktuell: Flur-${RUN}`).waitFor();
      [sw, cw] = await over(m.page);
      await shot(m.page, '19-mobile-boxlist');
      ok(sw <= cw, `box list overflow ${sw} > ${cw}`);
    } finally { await m.ctx.close(); }
  });

  // ---- throttle steps: dedicated simulated clients, run after every step that uses the default client bucket ----
  await step('20 login throttle: client A gets 429 (+Retry-After, localized UI), client B logs into the same account', async () => {
    const a = await newCtx({ label: 'client-a', extraHTTPHeaders: xff(IP_A) });
    const b = await newCtx({ label: 'client-b', extraHTTPHeaders: xff(IP_B) });
    try {
      await exhaust((i) => apiPost(a.ctx.request, '/api/auth/login', { form: { username: USER, password: `wrong-${RUN}-${i}` } }), 'login throttle');
      // UI of the throttled client: localized message, no raw English
      allow(/\/api\/auth\/login/, 401, 429);
      await a.page.goto(FRONT + '/app/boxes');
      await submitLogin(a.page, USER, 'still-wrong-' + RUN);
      const al = a.page.getByRole('alert');
      await al.waitFor({ timeout: 8000 });
      const txt = (await al.innerText()).trim();
      await shot(a.page, '20-throttled-ui');
      ok(TOO_MANY.test(txt), `429 not shown as localized message: "${txt}"`);
      ok(!/too many|failed/i.test(txt), `raw English in 429 message: "${txt}"`);
      ok(!(await hasSession(a.ctx)), 'throttled client got a session');
      // other client, same account, correct password
      await login(b.page, USER, PASS);
      await loggedIn(b.page);
      ok(await hasSession(b.ctx), 'client B has no SESSION cookie');
      await shot(b.page, '20-client-b-ok');
    } finally { await a.ctx.close(); await b.ctx.close(); }
  });

  await step('21 magic-link throttle: repeated wrong tokens -> 429, localized page', async () => {
    const c = await newCtx({ label: 'client-c', extraHTTPHeaders: xff(IP_C) });
    try {
      await exhaust((i) => apiPost(c.ctx.request, '/api/v1/auth/magic-login', { data: { token: `bogus-${RUN}-${i}` } }), 'magic-link throttle');
      allow(/\/api\/v1\/auth\/magic-login/, 401, 429);
      await c.page.goto(`${FRONT}/magic#t=bogus-${RUN}-ui`);
      await c.page.getByText(TOO_MANY).waitFor({ timeout: 10000 });
      await shot(c.page, '21-magic-throttled');
    } finally { await c.ctx.close(); }
  });

  // ---- steps that need the test box: label PDF (blob URLs, react-pdf) and camera dialog ----
  await step('21b label printing: PDF generated and opened as blob, no errors', async () => {
    const l = await newCtx({ label: 'label', extraHTTPHeaders: xff(IP_E) });
    try {
      const p = l.page;
      await p.addInitScript(() => {
        const orig = URL.createObjectURL.bind(URL); window.__blobs = [];
        URL.createObjectURL = (b) => { window.__blobs.push(b); return orig(b); };
      });
      await login(p, USER, PASS);
      await loggedIn(p);
      await p.goto(editUrl());
      const popup = p.waitForEvent('popup', { timeout: 40000 });
      await p.getByRole('button', { name: 'Etikett drucken' }).click();
      const pp = await popup;
      await pp.close();
      const head = await p.evaluate(async () => { const b = window.__blobs.at(-1); return b ? { size: b.size, head: await b.slice(0, 5).text() } : null; });
      ok(head && head.head === '%PDF-' && head.size > 1000, `no PDF blob created: ${JSON.stringify(head)}`);
      ok((await p.getByText(/PDF-Generierung fehlgeschlagen/).count()) === 0, 'PDF failure snackbar shown');
      await shot(p, '21b-label');
    } finally { await l.ctx.close(); }
  });

  await step('21c camera dialog: opens, live frame, capture + accept shows preview', async () => {
    const l = await newCtx({ label: 'camera', extraHTTPHeaders: xff(IP_E), permissions: ['camera'] });
    try {
      const p = l.page;
      await login(p, USER, PASS);
      await loggedIn(p);
      await p.goto(editUrl());
      await p.getByRole('button', { name: 'Neues Item hinzufügen' }).click();
      await p.getByRole('dialog').getByRole('button', { name: 'Foto aufnehmen' }).click();
      const video = p.locator('video');
      await video.waitFor({ timeout: 10000 });
      await until(() => video.evaluate((v) => v.videoWidth > 0), 'camera video shows no frame', 10000);
      await shot(p, '21c-camera');
      await p.getByRole('dialog').getByRole('button', { name: 'Foto aufnehmen' }).click();
      await p.getByRole('button', { name: 'Verwenden' }).click();
      await p.getByRole('dialog').locator('img').first().waitFor({ timeout: 8000 });
      await shot(p, '21c-camera-accepted');
      await p.getByRole('dialog').getByRole('button', { name: 'Abbrechen' }).click();
    } finally { await l.ctx.close(); }
  });
} finally {
  await step('22 cleanup: delete test box (and test user if left over)', async () => {
    const c = await newCtx({ label: 'cleanup' });
    try {
      await login(c.page, USER, PASS);
      await loggedIn(c.page);
      if (userCreated && !userDeleted) await deleteUserViaUi(c.page, NEWUSER).catch((e) => console.log('        (warn) user cleanup failed: ' + String(e.message).split('\n')[0]));
      ok(boxNo, 'box was never created, nothing to clean up');
      await c.page.goto(FRONT + '/app/boxes');
      const heading = c.page.getByRole('heading', { name: `Box #${boxNo}`, exact: true });
      await heading.waitFor({ timeout: 8000 });
      const tile = c.page.locator('div').filter({ has: heading }).filter({ has: c.page.getByRole('button', { name: 'Aktionen' }) }).last();
      await tile.getByRole('button', { name: 'Aktionen' }).click();
      await c.page.getByRole('button', { name: 'Löschen', exact: true }).or(c.page.getByRole('menuitem', { name: 'Löschen' })).first().click();
      const dlg = c.page.getByRole('dialog');
      await dlg.getByText(`Box ${boxNo} wird dauerhaft gelöscht`).waitFor({ timeout: 5000 });
      await dlg.getByRole('button', { name: 'Löschen', exact: true }).click();
      await heading.waitFor({ state: 'detached', timeout: 8000 });
    } finally { await c.ctx.close(); }
  });

  await step('23 no Content-Security-Policy console violations during the whole run', async () => {
    const uniq = [...new Set(cspHits)];
    ok(uniq.length === 0, `${uniq.length} CSP console message(s): ${uniq.slice(0, 5).join(' || ')}`);
  });
  await browser.close();
}

const failed = results.filter((r) => !r.ok);
writeFileSync(join(OUT, 'results.txt'), results.map((r) => `${r.ok ? 'PASS' : 'FAIL'} ${r.name}`).join('\n') + '\n' + findings.join('\n') + '\n');
console.log(`\n${results.length - failed.length}/${results.length} steps passed. Artifacts: ${OUT}`);
process.exit(failed.length ? 1 : 0);
