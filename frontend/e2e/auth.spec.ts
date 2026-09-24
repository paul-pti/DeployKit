import { expect, test, type Page } from '@playwright/test'

// Matches the admin created by docs/testing.md's e2e command; override if you seeded a different one.
const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@example.com'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'e2e-admin-password-123'

// Unique per run so the suite can be re-run against the same, already-seeded database without a 409.
const RUN_ID = Date.now()
const NEW_USER_EMAIL = `e2e-user-${RUN_ID}@example.com`
const NEW_USER_PASSWORD = 'e2e-user-password-123'

async function login(page: Page, email: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

test('a wrong password shows an error and leaves the visitor on the login page', async ({ page }) => {
  await login(page, ADMIN_EMAIL, 'definitely-the-wrong-password')

  await expect(page.getByRole('alert')).toHaveText('Invalid email or password')
  await expect(page).toHaveURL(/\/login$/)
})

test('an anonymous visitor is sent to /login, then reaches the page they asked for', async ({ page }) => {
  await page.goto('/projects')
  await expect(page).toHaveURL(/\/login$/)

  await page.getByLabel('Email').fill(ADMIN_EMAIL)
  await page.getByLabel('Password').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page).toHaveURL(/\/projects$/)
})

test('an administrator creates a USER account that cannot reach the Users page', async ({ page }) => {
  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD)
  await expect(page).toHaveURL(/\/projects$/)

  await page.getByRole('link', { name: 'Users' }).click()
  await page.getByLabel('Email', { exact: true }).fill(NEW_USER_EMAIL)
  await page.getByLabel('Password', { exact: true }).fill(NEW_USER_PASSWORD)
  await page.getByRole('button', { name: 'Create user' }).click()
  await expect(page.getByText(NEW_USER_EMAIL)).toBeVisible()

  await page.getByRole('button', { name: 'Log out' }).click()
  await expect(page).toHaveURL(/\/login$/)

  await login(page, NEW_USER_EMAIL, NEW_USER_PASSWORD)

  await expect(page).toHaveURL(/\/projects$/)
  await expect(page.getByRole('link', { name: 'Users' })).toHaveCount(0)

  await page.goto('/users')
  await expect(page).toHaveURL(/\/projects$/)
})
