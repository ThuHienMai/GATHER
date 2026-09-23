import { expect, test } from '@playwright/test';
test('browser entry renders accessibly', async ({ page }) => {
  await page.route('https://telegram.org/js/telegram-web-app.js', route => route.fulfill({ contentType: 'application/javascript', body: 'window.Telegram = { WebApp: { initData: "" } };' }));
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Good plans start');
  await expect(page.getByRole('main')).toBeVisible();
});
test('signed-in identity is displayed without browser token persistence', async ({ page }) => {
  await page.route('https://telegram.org/js/telegram-web-app.js', route => route.fulfill({ contentType: 'application/javascript', body: 'window.Telegram = { WebApp: { initData: "signed-test-fixture", ready() {}, expand() {} } };' }));
  await page.route('**/api/v1/auth/telegram', route => route.fulfill({ json: { token: 'test-response', expiresAt: new Date(Date.now() + 3600000).toISOString(), user: { id: 'one', firstName: 'Alice' } } }));
  await page.goto('/auth');
  await expect(page.getByRole('heading')).toHaveText('Welcome, Alice');
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
  await page.evaluate(() => window.dispatchEvent(new Event('gather-session-expired')));
  await expect(page.getByRole('main').getByRole('alert')).toContainText('session expired');
});
