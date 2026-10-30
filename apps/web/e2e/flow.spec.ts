import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const MIN = 60_000;

test.beforeEach(async ({ page }) => {
  await page.clock.install({ time: new Date('2026-11-03T18:00:00-06:00') });
});

test('configure → start → focus → break → resume → finish', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('button', { name: 'Start timer' })).toBeVisible();
  await page.getByLabel(/^Task/).fill('TAM 212 · Practice problems');
  await page.getByRole('radio', { name: '25 min' }).click();
  await page.getByLabel('Breaks').selectOption('5');
  await expect(page.getByLabel('Focus rounds')).toHaveValue('2');
  await expect(page.locator('.plan')).toContainText('55 min');
  await expect(page.locator('.plan')).toContainText('2 rounds of 25 min, 5 min breaks');
  await page.getByRole('button', { name: 'Start timer' }).click();

  await expect(page.locator('.tag')).toHaveText('Focus · round 1 of 2');
  await expect(page.getByRole('heading', { name: 'TAM 212 · Practice problems' })).toBeVisible();
  // Timer only: the lock never shows as closed when nothing is restricted.
  await expect(page.locator('.lock')).toHaveClass(/is-open/);
  await expect(page.locator('.lock__time')).toHaveText('25:00');
  await expect(page.locator('.facts')).toContainText('Next break');

  await page.clock.fastForward(25 * MIN + 1000);
  await expect(page.locator('.tag')).toHaveText('Break · round 1 of 2');
  await expect(page.locator('.facts')).toContainText('Focus resumes');

  await page.clock.fastForward(4 * MIN + 10_000); // 50 s left in the break
  await expect(page.getByText(/resumes in about a minute/)).toBeVisible();

  await page.clock.fastForward(60_000);
  await expect(page.locator('.tag')).toHaveText('Focus · round 2 of 2');

  await page.clock.fastForward(25 * MIN);
  await expect(page.locator('.tag')).toHaveText('Session complete');
  await expect(page.locator('.facts')).toContainText('50 min');
  await expect(page.getByText('Finished as planned.', { exact: false })).toBeVisible();
  await page.getByRole('button', { name: 'Done' }).click();
  await expect(page.getByRole('button', { name: 'Start timer' })).toBeVisible();
  // Last choices are remembered.
  await expect(page.getByRole('radio', { name: '25 min' })).toHaveAttribute('aria-checked', 'true');
  await expect(page.getByLabel('Breaks')).toHaveValue('5');
});

test('a custom length equal to a preset selects the preset', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('radio', { name: 'Custom' }).click();
  await page.getByLabel('Custom length').fill('60');
  await expect(page.getByRole('radio', { name: '60 min' })).toHaveAttribute('aria-checked', 'true');
  await page.getByLabel('Custom length').fill('45');
  await expect(page.getByRole('radio', { name: 'Custom' })).toHaveAttribute('aria-checked', 'true');
});

test('a running session survives a reload', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('radio', { name: 'Custom' }).click();
  await page.getByLabel('Custom length').fill('10');
  await page.getByLabel('Breaks').selectOption('0');
  await page.getByRole('button', { name: 'Start timer' }).click();
  await page.clock.fastForward(3 * MIN);
  await page.reload();
  await expect(page.locator('.tag')).toHaveText('Focus');
  await expect(page.locator('.lock__time')).toHaveText('07:00');
});

test('unlock for this round waits, can be cancelled, then later rounds start as planned', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('radio', { name: '25 min' }).click();
  await page.getByLabel('Breaks').selectOption('5');
  await page.getByRole('button', { name: 'Start timer' }).click();

  await page.getByRole('button', { name: 'Need out?' }).click();
  await page.getByRole('radio', { name: 'This round' }).check();
  await page.getByRole('button', { name: 'Start unlock' }).click();
  await expect(page.getByText(/Unlocking in 01:30/)).toBeVisible();
  await page.getByRole('button', { name: 'Cancel unlock' }).click();
  await page.clock.fastForward(2 * MIN);
  await expect(page.locator('.tag')).toHaveText('Focus · round 1 of 2');

  await page.getByRole('button', { name: 'Need out?' }).click();
  await page.getByRole('button', { name: 'Start unlock' }).click();
  await page.clock.fastForward(91_000);
  await expect(page.locator('.tag')).toHaveText('Unlocked · round 1 of 2');

  await page.clock.fastForward(28 * MIN); // ~31.5 min in: through the break into round 2
  await expect(page.locator('.tag')).toHaveText('Focus · round 2 of 2');
});

test('emergency recovery ends immediately and says how it ended', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Start timer' }).click();
  await page.getByRole('button', { name: 'End now' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Keep going' }).last()).toBeFocused();
  await page.getByRole('button', { name: 'End session now' }).click();
  await expect(page.locator('.tag')).toHaveText('Session complete');
  await expect(page.getByText('Ended with emergency recovery.', { exact: false })).toBeVisible();
});

test('the web app never claims to block anything and offers a path to set it up', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.protection')).toContainText('Timer only');
  await page.getByRole('button', { name: 'Start timer' }).click();
  await expect(page.locator('.protection')).toContainText('apps and websites stay available');
  await expect(page.getByText(/restricted/i)).toHaveCount(0);
  await page.getByRole('link', { name: 'Set up blocking' }).click();
  await expect(page.getByRole('heading', { name: 'Devices' })).toBeVisible();
});

test('schedule: empty state, add weekly and one-time, overlap error, switch off, next-up on Focus', async ({ page }) => {
  await page.goto('/#/schedule');
  await expect(page.getByText('No sessions planned yet')).toBeVisible();
  await page.getByRole('button', { name: 'Add session' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByLabel('Name').fill('Physics review');
  await page.getByLabel('Starts at').fill('19:00');
  await expect(page.locator('.summary-box')).toContainText('Monday to Thursday, every week');
  await page.getByRole('button', { name: 'Save session' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Weekly routines' })).toBeVisible();

  await page.getByRole('button', { name: 'Add session' }).click();
  await page.getByLabel('Name').fill('Reading');
  await page.getByLabel('Starts at').fill('19:30');
  await page.getByRole('button', { name: 'Save session' }).click();
  await expect(page.getByRole('alert')).toContainText('This overlaps “Physics review”');
  await page.getByRole('radio', { name: 'One time' }).click();
  await page.getByLabel('Date').fill('2026-11-10');
  await page.getByLabel('Starts at').fill('14:00');
  await page.getByRole('button', { name: 'Save session' }).click();
  await expect(page.getByRole('heading', { name: 'One-time' })).toBeVisible();

  await page.goto('/#/focus');
  await expect(page.locator('.next')).toContainText('Physics review');

  await page.goto('/#/schedule');
  await page.getByRole('switch', { name: 'Physics review on' }).click();
  await expect(page.getByRole('switch', { name: 'Physics review on' })).toHaveAttribute('aria-checked', 'false');
  await page.goto('/#/focus');
  await expect(page.locator('.next')).not.toContainText('Physics review');
});

test('schedule: days have full accessible names; edit and delete', async ({ page }) => {
  await page.goto('/#/schedule');
  await page.getByRole('button', { name: 'Add session' }).click();
  await expect(page.getByRole('button', { name: 'Wednesday' })).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: 'Saturday' }).click();
  await page.getByLabel('Name').fill('Lab report');
  await page.getByRole('button', { name: 'Save session' }).click();
  await page.getByRole('button', { name: 'Edit Lab report' }).click();
  await expect(page.getByRole('heading', { name: 'Edit session' })).toBeVisible();
  await page.getByRole('button', { name: 'Delete' }).click();
  await page.getByRole('button', { name: 'Delete' }).last().click();
  await expect(page.getByText('No sessions planned yet')).toBeVisible();
});

test('invalid custom length is explained, nothing starts', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('radio', { name: 'Custom' }).click();
  await page.getByLabel('Custom length').fill('500');
  await page.getByRole('button', { name: 'Start timer' }).click();
  await expect(page.getByRole('alert')).toContainText('Focus periods are 1–240 minutes');
  await expect(page.getByRole('button', { name: 'Start timer' })).toBeVisible();
});

test('theme choice applies and persists', async ({ page }) => {
  await page.goto('/#/settings');
  await page.getByRole('radio', { name: 'Dark' }).check();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
});

for (const scheme of ['light', 'dark'] as const) {
  for (const route of ['focus', 'schedule', 'profiles', 'devices', 'settings']) {
    test(`axe: no WCAG A/AA violations — ${route}, ${scheme}`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: scheme });
      await page.goto(`/#/${route}`);
      await page.waitForTimeout(300);
      const r = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
      expect(r.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target).join(' | ')}`)).toEqual([]);
    });
  }
  test(`axe: active session, ${scheme}`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: scheme });
    await page.goto('/');
    await page.getByRole('button', { name: 'Start timer' }).click();
    await page.getByRole('button', { name: 'Need out?' }).click();
    const r = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze();
    expect(r.violations.map((v) => v.id)).toEqual([]);
  });
}

test('keyboard: skip link, then reach and start a session without a mouse', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('button', { name: 'Start timer' })).toBeVisible();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Skip to content' })).toBeFocused();
  for (let i = 0; i < 30; i++) {
    await page.keyboard.press('Tab');
    if (await page.getByRole('button', { name: 'Start timer' }).evaluate((el) => el === document.activeElement)) break;
  }
  await expect(page.getByRole('button', { name: 'Start timer' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.locator('.tag')).toHaveText(/^Focus/);
});

for (const w of [320, 390]) {
  test(`no horizontal scrolling at ${w}px, including 200% text`, async ({ page }) => {
    await page.setViewportSize({ width: w, height: 800 });
    for (const route of ['focus', 'schedule', 'profiles', 'devices', 'settings']) {
      await page.goto(`/#/${route}`);
      for (const size of ['100%', '200%']) {
        await page.addStyleTag({ content: `html { font-size: ${size} !important; }` });
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
        expect(overflow, `${route} at ${size}`).toBeLessThanOrEqual(0);
      }
    }
  });
}
