// The Windows app's desktop layout, exercised in Chromium with ?shell=desktop.
// (In the real app the same code runs in WebView2; the native shell is tested in CI on Windows.)
import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const D = '/';

/** Stand-in for WebView2's `window.chrome.webview`: answers ui.ready like the Windows shell does
 *  and records every message the interface sends, so the real bridge code runs. */
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const handlers: ((e: { data: unknown }) => void)[] = [];
    const sent: unknown[] = [];
    (window as unknown as { __sent: unknown[] }).__sent = sent;
    (window as unknown as { chrome: unknown }).chrome = {
      webview: {
        addEventListener: (_t: string, f: (e: { data: unknown }) => void) => handlers.push(f),
        postMessage: (m: { type?: string }) => {
          sent.push(m);
          if (m.type === 'ui.ready') setTimeout(() => handlers.forEach((h) => h({ data: { v: 1, type: 'host.hello', appVersion: '0.1.0', agent: 'not_installed' } })), 50);
        },
      },
    };
    (window as unknown as { __host: (d: unknown) => void }).__host = (d) => handlers.forEach((h) => h({ data: d }));
  });
});

test('desktop layout: switch + menu, no masthead, honest PC status', async ({ page }) => {
  await page.setViewportSize({ width: 640, height: 860 });
  await page.goto(D);
  await expect(page.getByRole('button', { name: /Lock in/ })).toBeVisible();
  await expect(page.locator('.masthead')).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Focus' })).toHaveAttribute('aria-current', 'page');
  await expect(page.getByText('PC blocking off')).toBeVisible();
  await expect(page.getByText('This browser')).toHaveCount(0);
});

test('desktop menu works with the keyboard', async ({ page }) => {
  await page.goto(D);
  const menuButton = page.getByRole('button', { name: 'Menu' });
  await menuButton.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('menu')).toBeVisible();
  await expect(page.getByRole('menuitem', { name: 'Profiles' })).toBeFocused();
  await page.keyboard.press('ArrowDown');
  await expect(page.getByRole('menuitem', { name: 'Devices' })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('menu')).toHaveCount(0);
  await expect(menuButton).toBeFocused();
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('End');
  await expect(page.getByRole('menuitem', { name: 'Settings' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
});

test('desktop: running session says blocking is not set up on this PC', async ({ page }) => {
  await page.goto(D);
  await page.getByRole('button', { name: /Lock in/ }).click();
  await expect(page.getByText(/blocking isn’t set up on this PC/)).toBeVisible();
});

for (const scheme of ['light', 'dark'] as const) {
  test(`axe: desktop layout, ${scheme}`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: scheme });
    await page.setViewportSize({ width: 640, height: 860 });
    await page.goto(D);
    await page.getByRole('button', { name: 'Menu' }).click();
    const r = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze();
    expect(r.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target).join(' | ')}`)).toEqual([]);
  });
}

test('desktop: minimum window size with 200% text has no horizontal scrolling', async ({ page }) => {
  await page.setViewportSize({ width: 480, height: 600 });
  for (const route of ['focus', 'schedule', 'profiles', 'devices', 'settings']) {
    await page.goto(`${D}#/${route}`);
    await page.addStyleTag({ content: 'html { font-size: 200% !important; }' });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow, route).toBeLessThanOrEqual(0);
  }
});

test('bridge: reports session state and theme; tray command starts a 10-minute focus', async ({ page }) => {
  await page.goto(D);
  await expect(page.getByText('PC blocking off')).toBeVisible();
  const sent = () => page.evaluate(() => (window as unknown as { __sent: { type: string }[] }).__sent);
  expect((await sent()).map((m) => m.type)).toEqual(expect.arrayContaining(['ui.ready', 'ui.theme', 'ui.session_state']));
  await page.evaluate(() => (window as unknown as { __host: (d: unknown) => void }).__host({ v: 1, type: 'host.command', command: 'start_quick' }));
  await expect(page.getByRole('heading', { name: 'Locked in.' })).toBeVisible();
  await expect(page.locator('.lock__time')).toHaveText(/^(10:00|09:5\d)$/);
  const last = (await sent()).filter((m) => m.type === 'ui.session_state').pop() as { active: boolean; endsAt: number };
  expect(last.active).toBe(true);
  expect(last.endsAt).toBeGreaterThan(Date.now() + 9 * 60_000);
  // Unknown commands are ignored.
  await page.evaluate(() => (window as unknown as { __host: (d: unknown) => void }).__host({ v: 1, type: 'host.command', command: 'end_session' }));
  await expect(page.getByRole('heading', { name: 'Locked in.' })).toBeVisible();
});
