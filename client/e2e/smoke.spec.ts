import { test, expect } from '@playwright/test';

test('Sentinel Ring UI smoke test', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/Sentinel Ring/);
  await expect(page.getByText('Sentinel Ring').first()).toBeVisible();
  await expect(page.getByText('System Overview')).toBeVisible();
});
