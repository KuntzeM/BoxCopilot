// BoxPilot real-browser smoke test. Plain Playwright, no framework. Exit code != 0 on any failed step.
import { chromium } from 'playwright';
import { deflateSync } from 'node:zlib';
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
const NEWUSER = `e2e-user-${RUN}`;
const NEWPASS = `Pw-${RUN}-long-enough-1!`;
const OIDC_USER = 'e2e-nextcloud-user';

// ---- tiny valid PNG (solid colour) ----
const crcT = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = (b) => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
function png(w = 64, h = 64, rgb = [200, 40, 40]) {
  const row = Buffer.concat([Buffer.from([0]), Buffer.from(Array.from({ length: w }, () => rgb).flat())]);
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(Buffer.concat(Array(h).fill(row)))), chunk('IEND', Buffer.alloc(0))]);
}

// ---- issue collection (console errors + bad responses), per context ----
let issues = [];
let allowed = []; // [{re, status}] expected non-2xx for the current step
const allow = (re, ...statuses) => allowed.push({ re, statuses });
function watch(page, label) {
  page.on('console', (m) => {
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

const browser = await chromium.launch();
async function newCtx(opts = {}) {
  const ctx = await browser.newContext({ locale: 'de-DE', ...opts });
  const page = await ctx.newPage();
  watch(page, opts.label || 'main');
  return { ctx, page };
}
const cookiesOf = async (ctx) => (await ctx.cookies(API)).concat(await ctx.cookies(FRONT));
const hasSession = async (ctx) => (await cookiesOf(ctx)).some((c) => c.name === 'SESSION' && c.value);
const apiStatus = (page, path) => page.evaluate(async (u) => (await fetch(u, { credentials: 'include' })).status, API + path);

async function login(page, user, pass) {
  await page.goto(FRONT + '/app/boxes');
  await page.getByLabel('Benutzername').fill(user);
  await page.getByLabel('Passwort').fill(pass);
  await page.getByRole('button', { name: 'Anmelden', exact: true }).click();
}
const loggedIn = (page) => page.getByRole('button', { name: 'Abmelden' }).waitFor({ timeout: 10000 });

let { ctx, page } = await newCtx();
let boxNo = null, publicUrl = null, boxId = null;

try {
  await step('1 login page + localized error on wrong password', async () => {
    await page.goto(FRONT + '/app/boxes');
    await page.getByRole('heading', { name: 'BoxCopilot' }).waitFor();
    await shot(page, '01-login');
    allow(/\/api\/auth\/login/, 400, 401, 403);
    await page.getByLabel('Benutzername').fill(USER);
    await page.getByLabel('Passwort').fill('definitely-wrong-' + RUN);
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
    await page.getByLabel('Passwort').fill(PASS);
    await page.getByRole('button', { name: 'Anmelden', exact: true }).click();
    await loggedIn(page);
    await page.getByRole('button', { name: 'Admin' }).waitFor();
    ok(await hasSession(ctx), 'no SESSION cookie after login');
    await shot(page, '02-boxlist');
  });

  await step('3 create box', async () => {
    await page.getByRole('button', { name: 'Neue Box' }).click();
    await page.getByLabel('Aktuelles Zimmer').fill(`Flur-${RUN}`);
    await page.getByLabel('Zielzimmer').fill(`Keller-${RUN}`);
    await page.getByLabel('Beschreibung').fill(`e2e ${RUN}`);
    await page.getByRole('button', { name: 'Erstellen' }).click();
    await page.waitForURL(/\/app\/boxes\/(\d+)\/edit/);
    boxId = page.url().match(/boxes\/(\d+)\/edit/)[1];
    await page.getByRole('heading', { name: /^Box #\d+/ }).waitFor();
    boxNo = (await page.getByRole('heading', { name: /^Box #\d+/ }).innerText()).match(/\d+/)[0];
    await page.getByRole('button', { name: 'Zurück' }).click();
    await page.getByText(`Aktuell: Flur-${RUN}`).waitFor();
    await shot(page, '03-box-created');
  });

  await step('4 add item + upload photo (thumbnail loads, immutable cache)', async () => {
    await page.goto(`${FRONT}/app/boxes/${boxId}/edit`);
    await page.getByRole('button', { name: 'Neues Item hinzufügen' }).click();
    await page.getByLabel('Item-Name').fill(ITEM);
    await page.getByRole('button', { name: 'Hinzufügen', exact: true }).click();
    await page.getByText(ITEM).first().waitFor();
    await page.getByRole('button', { name: 'Bearbeiten' }).first().click();
    const up = page.waitForResponse((r) => /\/api\/v1\/items\/\d+\/image$/.test(r.url()) && r.request().method() === 'POST');
    await page.locator('[role=dialog] input[type=file]').setInputFiles({ name: 'foto.png', mimeType: 'image/png', buffer: png() });
    await page.locator('[role=dialog] img').first().waitFor();
    await shot(page, '04-item-edit-with-photo');
    await page.getByRole('button', { name: 'Speichern' }).click();
    const ur = await up;
    ok(ur.status() < 300, `upload returned ${ur.status()}`);
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    await page.reload();
    const img = page.locator(`img[alt="${ITEM}"]`).first();
    await img.waitFor({ timeout: 10000 });
    await page.waitForFunction((el) => el.complete, await img.elementHandle());
    ok(await img.evaluate((e) => e.naturalWidth > 0), 'thumbnail naturalWidth is 0');
    const src = await img.getAttribute('src');
    const r = await page.request.get(src);
    ok(r.status() === 200, `thumbnail GET ${r.status()}`);
    const cc = r.headers()['cache-control'] || '';
    ok(/immutable/i.test(cc), `Cache-Control lacks immutable: "${cc}"`);
    await shot(page, '04-thumbnail');
  });

  await step('5 upload rejection keeps previous photo', async () => {
    await page.getByRole('button', { name: 'Bearbeiten' }).first().click();
    allow(/\/api\/v1\/items\/\d+\/image$/, 400, 413, 415, 422);
    const up = page.waitForResponse((r) => /\/api\/v1\/items\/\d+\/image$/.test(r.url()) && r.request().method() === 'POST');
    await page.locator('[role=dialog] input[type=file]').setInputFiles({ name: 'fake.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('this is not an image, just text. '.repeat(20)) });
    await page.getByRole('button', { name: 'Speichern' }).click();
    const ur = await up;
    ok(ur.status() >= 400 && ur.status() < 500, `fake image answered ${ur.status()} (expected 4xx)`);
    const al = page.getByRole('alert').filter({ hasText: /\S/ }).first();
    await al.waitFor({ timeout: 8000 });
    console.log('        (info) rejection message shown: ' + (await al.innerText()).replace(/\s+/g, ' '));
    await shot(page, '05-rejected');
    await page.reload();
    const img = page.locator(`img[alt="${ITEM}"]`).first();
    await img.waitFor();
    await page.waitForFunction((el) => el.complete, await img.elementHandle());
    ok(await img.evaluate((e) => e.naturalWidth > 0), 'previous photo no longer loads');
  });

  await step('6 item search on box list', async () => {
    await page.goto(FRONT + '/app/boxes');
    await page.getByText(`Aktuell: Flur-${RUN}`).waitFor();
    await page.getByLabel('Item-Name').fill(ITEM);
    await page.getByText(`Aktuell: Flur-${RUN}`).waitFor();
    await until(async () => (await page.getByRole('heading', { name: /^Box #\d+/ }).count()) === 1, 'filter did not narrow list to the single box');
    await shot(page, '06-search');
    await page.getByLabel('Item-Name').fill('zzz-no-such-item-' + RUN);
    await page.getByText('Keine Boxen gefunden').waitFor({ timeout: 8000 });
    await page.getByRole('button', { name: 'Zurücksetzen' }).click();
  });

  await step('7 public preview in fresh context + API 401', async () => {
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
      const img = f.page.locator('img').filter({ hasNot: f.page.locator('nothing') }).last();
      await f.page.getByText(ITEM).first().waitFor();
      const pimg = f.page.locator('img[src*="/public/items/"]').first();
      await pimg.waitFor({ timeout: 10000 });
      await f.page.waitForFunction((el) => el.complete, await pimg.elementHandle());
      ok(await pimg.evaluate((e) => e.naturalWidth > 0), 'public item image does not load');
      void img;
      await shot(f.page, '07-public');
      allow(/\/api\/v1\/boxes/, 401, 403);
      ok([401].includes(await apiStatus(f.page, '/api/v1/boxes')), 'GET /api/v1/boxes without session is not 401');
    } finally { await f.ctx.close(); }
  });

  await step('8 admin: create user (short pw rejected, long ok), delete', async () => {
    await page.goto(FRONT + '/app/admin');
    await page.getByRole('button', { name: 'Neuer Benutzer' }).click();
    const dlg = page.getByRole('dialog');
    allow(/\/api\/v1\/admin\//, 400, 422);
    await dlg.getByLabel('Benutzername').fill(NEWUSER);
    await dlg.getByRole('textbox', { name: 'Name', exact: true }).fill('E2E Temp');
    await dlg.getByLabel(/^Passwort/).fill('short');
    await dlg.getByRole('button', { name: 'Speichern' }).click();
    await page.waitForTimeout(1200);
    await shot(page, '08-short-password');
    ok(await dlg.isVisible(), 'dialog closed despite short password');
    const msg = (await dlg.innerText()) + ' ' + (await page.getByRole('alert').allInnerTexts().catch(() => [])).join(' ');
    ok(/12|Zeichen|zu kurz|mindestens|ungültig|Passwort/i.test(msg.replace(/Passwort \(optional.*?\)/, '')), 'no visible validation error for short password');
    await dlg.getByLabel(/^Passwort/).fill(NEWPASS);
    await dlg.getByRole('button', { name: 'Speichern' }).click();
    await dlg.waitFor({ state: 'hidden', timeout: 8000 });
    const row = page.getByRole('row', { name: new RegExp(NEWUSER) });
    await row.waitFor();
    await shot(page, '08-user-created');
    page.once('dialog', (d) => d.accept());
    await row.getByRole('button', { name: 'Benutzer löschen' }).click();
    const confirm = page.getByRole('dialog').getByRole('button', { name: /Löschen|Bestätigen|Ja/ });
    if (await confirm.count()) await confirm.last().click();
    await row.waitFor({ state: 'detached', timeout: 8000 });
  });

  await step('9 session expiry -> login screen', async () => {
    await page.goto(FRONT + '/app/boxes');
    await page.getByText(`Aktuell: Flur-${RUN}`).waitFor();
    await ctx.clearCookies();
    allow(/\/api\//, 401, 403);
    await page.getByLabel('Item-Name').fill(ITEM);
    await page.getByLabel('Benutzername').waitFor({ timeout: 10000 });
    ok(!(await page.getByRole('button', { name: 'Abmelden' }).count()), 'stale authenticated UI still visible');
    await shot(page, '09-expired');
  });

  await step('10 logout: login screen, API 401, SESSION cookie gone', async () => {
    await page.getByLabel('Passwort').fill(PASS);
    await page.getByLabel('Benutzername').fill(USER);
    await page.getByRole('button', { name: 'Anmelden', exact: true }).click();
    await loggedIn(page);
    await page.getByRole('button', { name: 'Abmelden' }).click();
    await page.getByLabel('Benutzername').waitFor();
    allow(/\/api\//, 401, 403);
    ok(!(await hasSession(ctx)), 'SESSION cookie still present after logout');
    ok((await apiStatus(page, '/api/v1/boxes')) === 401, '/api/v1/boxes not 401 after logout');
    await shot(page, '10-logout');
  });

  await step('11 Nextcloud/OIDC login via mock provider, no Admin', async () => {
    const o = await newCtx({ label: 'oidc' });
    try {
      const p = o.page;
      await p.goto(FRONT + '/app/boxes');
      await p.getByRole('button', { name: 'Mit Nextcloud anmelden' }).click();
      await p.waitForURL(/oidc\.localhost:9090/, { timeout: 15000 });
      await p.locator('input[name=username]').fill(OIDC_USER);
      await p.locator('textarea[name=claims]').fill(JSON.stringify({ name: 'E2E Nextcloud', email: 'e2e@example.test', preferred_username: OIDC_USER }));
      await shot(p, '11-mock-idp');
      await p.locator('input[type=submit], button[type=submit]').first().click();
      await p.waitForURL((u) => u.origin === FRONT, { timeout: 20000 });
      await loggedIn(p);
      ok((await p.getByRole('button', { name: 'Admin' }).count()) === 0, 'OIDC user sees Admin button');
      const me = await p.evaluate(async (u) => (await fetch(u + '/api/v1/me', { credentials: 'include' })).json(), API);
      ok(me.username === OIDC_USER || me.username?.includes(OIDC_USER), `mapped username unexpected: ${me.username}`);
      ok(me.name === 'E2E Nextcloud', `mapped name unexpected: ${me.name}`);
      await shot(p, '11-oidc-boxlist');
    } finally { await o.ctx.close(); }
  });

  await step('12 mobile 360x740: no horizontal overflow', async () => {
    const m = await newCtx({ label: 'mobile', viewport: { width: 360, height: 740 } });
    try {
      const over = (p) => p.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.clientWidth]);
      await m.page.goto(FRONT + '/app/boxes');
      await m.page.getByLabel('Benutzername').waitFor();
      let [sw, cw] = await over(m.page);
      await shot(m.page, '12-mobile-login');
      ok(sw <= cw, `login overflow ${sw} > ${cw}`);
      await login(m.page, USER, PASS);
      await m.page.getByRole('heading', { name: 'Boxen' }).waitFor();
      await m.page.getByText(`Aktuell: Flur-${RUN}`).waitFor();
      [sw, cw] = await over(m.page);
      await shot(m.page, '12-mobile-boxlist');
      ok(sw <= cw, `box list overflow ${sw} > ${cw}`);
    } finally { await m.ctx.close(); }
  });
} finally {
  await step('13 cleanup: delete test box', async () => {
    const c = await newCtx({ label: 'cleanup' });
    try {
      await login(c.page, USER, PASS);
      await loggedIn(c.page);
      const card = c.page.getByRole('heading', { name: `Box #${boxNo}` });
      ok(boxNo, 'box was never created, nothing to clean up');
      await card.waitFor({ timeout: 8000 });
      c.page.on('dialog', (d) => d.accept());
      const tile = c.page.locator('div').filter({ has: card }).filter({ has: c.page.getByRole('button', { name: 'Aktionen' }) }).last();
      await tile.getByRole('button', { name: 'Aktionen' }).click();
      await c.page.getByRole('button', { name: 'Löschen' }).or(c.page.getByRole('menuitem', { name: 'Löschen' })).first().click();
      const confirm = c.page.getByRole('dialog').getByRole('button', { name: /Löschen|Bestätigen|Ja/ });
      if (await confirm.count()) await confirm.last().click();
      await c.page.getByRole('heading', { name: `Box #${boxNo}` }).waitFor({ state: 'detached', timeout: 8000 });
    } finally { await c.ctx.close(); }
  });
  await browser.close();
}

const failed = results.filter((r) => !r.ok);
writeFileSync(join(OUT, 'results.txt'), results.map((r) => `${r.ok ? 'PASS' : 'FAIL'} ${r.name}`).join('\n') + '\n' + findings.join('\n') + '\n');
console.log(`\n${results.length - failed.length}/${results.length} steps passed. Artifacts: ${OUT}`);
process.exit(failed.length ? 1 : 0);
