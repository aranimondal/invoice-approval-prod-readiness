import { type Page } from '@playwright/test';

export class InvoicesPage {
  constructor(private readonly page: Page) {}

  async open() {
    await this.page.getByRole('tab', { name: 'Invoices' }).click();
  }

  row(invoiceId: string) {
    return this.page.getByRole('row').filter({ hasText: invoiceId });
  }
}
