import { expect, type Page } from '@playwright/test';

export class LoginPage {
  constructor(private readonly page: Page) {}

  async open() {
    await this.page.goto('/');
    await expect(this.page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  }

  async signInAs(username: string, password = 'ChangeMe123!') {
    await this.page.getByLabel('Username').fill(username);
    await this.page.getByLabel('Password').fill(password);
    await this.page.getByRole('button', { name: 'Sign in' }).click();
  }
}
