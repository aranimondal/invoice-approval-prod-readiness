import { expect, type Page } from '@playwright/test';

export class ApprovalsPage {
  constructor(private readonly page: Page) {}

  async open() {
    await this.page.getByRole('tab', { name: 'Approvals' }).click();
    await expect(this.page.getByRole('heading', { name: 'Pending approvals' })).toBeVisible();
  }

  approveButton(invoiceId: string) {
    return this.page.getByRole('button', { name: `Approve invoice ${invoiceId}` });
  }

  async approve(invoiceId: string) {
    await this.approveButton(invoiceId).click();
  }

  async reject(invoiceId: string, reason: string) {
    await this.page.getByLabel(`Rejection reason for ${invoiceId}`).fill(reason);
    await this.page.getByRole('button', { name: `Reject invoice ${invoiceId}` }).click();
  }
}
