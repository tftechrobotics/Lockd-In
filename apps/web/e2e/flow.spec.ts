import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const MIN = 60_000;

test.beforeEach(async ({ page }) => {
  await page.clock.install({ time: new Date('2026-11-03T18:00:00-06:00') });
});

test('configure → start → focus → break → resume → finish', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('button', { name: /Lock in/ })).toBeVisible();
  await page.getByLabel('Task').fill('TAM 212 · Practice problems');
  await page.getByRole('radio', { name: '25 min' }).click();
  await page.getByLabel('Break').selectOption('5');
  await page.getByLabel('Focus periods').selectOption('2');
  await page.getByRole('button', { name: /Lock in/ }).click();

  await expect(page.locator('.status')).toContainText('Locked in');
  await expect(page.getByText('TAM 212 · Practice problems')).toBeVisible();
  await expect(page.locator('.lock')).toHaveClass(/is-locked/);
  await expect(page.locator('.lock__time')).toHaveText('25:00');
  await expect(page.locator('.lock__caption')).toHaveText('Focus 1 of 2');

  await page.clock.fastForward(25 * MIN + 1000);
  await expect(page.locator('.status')).toContainText('On a break');
  await expect(page.locator('.lock')).toHaveClass(/is-open/);

  await page.clock.fastForward(4 * MIN + 10_000); // 50 s left in the break
  await expect(page.getByText(/resumes in about a minute/)).toBeVisible();

  await page.clock.fastForward(60_000);
  await expect(page.locator('.status')).toContainText('Locked in');
  await expect(page.locator('.lock__caption')).toHaveText('Focus 2 of 2');

  await page.clock.fastForward(25 * MIN);
  await expect(page.locator('.status')).toContainText('Session complete');
  await expect(page.getByText(/50 min focused/)).toBeVisible();
  await expect(page.getByText('Finished as planned')).toBeVisible();
  await page.getByRole('button', { name: 'Done' }).click();
  await expect(page.getByRole('button', { name: /Lock in/ })).toBeVisible();
  // Last choices are remembered.
  await expect(page.getByRole('radio', { name: '25 min' })).toHaveAttribute('aria-checked', 'true');
});

test('a running session survives a reload', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('radio', { name: '10 min' }).click();
  await page.getByLabel('Break').selectOption('0');
  await page.getByRole('button', { name: /Lock in/ }).click();
  await page.clock.fastForward(3 * MIN);
  await page.reload();
  await expect(page.locator('.status')).toContainText('Locked in');
  await expect(page.locator('.lock__time')).toHaveText('07:00');
});

test('unlock for this focus period waits, can be cancelled, then locks again next period', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('radio', { name: '25 min' }).click();
  await page.getByLabel('Break').selectOption('5');
  await page.getByRole('button', { name: /Lock in/ }).click();

  await page.getByRole('button', { name: 'Need out?' }).click();
  await page.getByRole('radio', { name: /This focus period/ }).check();
  await page.getByRole('button', { name: 'Start unlock' }).click();
  await expect(page.getByText(/Unlocking in 01:30/)).toBeVisible();
  await page.getByRole('button', { name: 'Cancel unlock' }).click();
  await page.clock.fastForward(2 * MIN);
  await expect(page.locator('.status')).toContainText('Locked in');

  await page.getByRole('button', { name: 'Need out?' }).click();
  await page.getByRole('button', { name: 'Start unlock' }).click();
  await page.clock.fastForward(91_000);
  await expect(page.locator('.status')).toContainText('Unlocked');
  await expect(page.locator('.lock')).toHaveClass(/is-open/);

  await page.clock.fastForward(28 * MIN); // ~31.5 min in: through the break into focus 2
  await expect(page.locator('.status')).toContainText('Locked in');
  await expect(page.locator('.lock__caption')).toHaveText('Focus 2 of 2');
});

test('emergency recovery ends immediately and says how it ended', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: /Lock in/ }).click();
  await page.getByRole('button', { name: 'End now' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Keep going' }).last()).toBeFocused();
  await page.getByRole('button', { name: 'End session now' }).click();
  await expect(page.locator('.status')).toContainText('Session complete');
  await expect(page.getByText('Ended with emergency recovery')).toBeVisible();
});

test('the web app never claims to block anything', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByText('Timer only')).toBeVisible();
  await page.getByRole('button', { name: /Lock in/ }).click();
  await expect(page.getByText(/Not blocking/)).toBeVisible();
  await expect(page.getByText('Not paired').first()).toBeVisible();
  await expect(page.getByText(/protected/i)).toHaveCount(0);
});

test('schedule: plan, overlap error, next-up on Focus', async ({ page }) => {
  await page.goto('/#/schedule');
  await expect(page.getByText(/No sessions planned/)).toBeVisible();
  await page.getByRole('button', { name: 'Plan a session' }).click();
  await page.getByLabel('Name or intention').fill('Physics review');
  await page.getByLabel('Starts').fill('19:00');
  await page.getByRole('button', { name: 'Save session' }).click();
  await expect(page.getByText('Physics review')).toBeVisible();

  await page.getByRole('button', { name: 'Plan a session' }).click();
  await page.getByLabel('Name or intention').fill('Reading');
  await page.getByLabel('Starts').fill('19:30');
  await page.getByRole('button', { name: 'Save session' }).click();
  await expect(page.getByRole('alert')).toContainText('Overlaps “Physics review”');

  await page.goto('/#/focus');
  await expect(page.locator('.next')).toContainText('Physics review');
});

test('invalid custom length is explained, nothing starts', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('radio', { name: 'Custom' }).click();
  await page.getByLabel('Minutes', { exact: true }).fill('500');
  await page.getByRole('button', { name: /Lock in/ }).click();
  await expect(page.getByRole('alert')).toContainText('Focus periods are 1–240 minutes');
  await expect(page.getByRole('button', { name: /Lock in/ })).toBeVisible();
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
    await page.getByRole('button', { name: /Lock in/ }).click();
    await page.getByRole('button', { name: 'Need out?' }).click();
    const r = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze();
    expect(r.violations.map((v) => v.id)).toEqual([]);
  });
}

test('keyboard: skip link, then reach and start a session without a mouse', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('button', { name: /Lock in/ })).toBeVisible();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Skip to content' })).toBeFocused();
  for (let i = 0; i < 25; i++) {
    await page.keyboard.press('Tab');
    if (await page.getByRole('button', { name: /Lock in/ }).evaluate((el) => el === document.activeElement)) break;
  }
  await expect(page.getByRole('button', { name: /Lock in/ })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.locator('.status')).toContainText('Locked in');
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
