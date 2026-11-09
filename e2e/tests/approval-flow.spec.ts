import { expect, test } from '@playwright/test';
import { ApprovalsPage } from '../pages/ApprovalsPage';
import { InvoicesPage } from '../pages/InvoicesPage';
import { LoginPage } from '../pages/LoginPage';

// Smoke test for the one path that will move money once PayEx is wired in (ADR-001).
// Runs against a fresh app: the in-memory store is seeded with INV-1001 and INV-1002 pending.

test('an approver approves one invoice and rejects another', async ({ page }) => {
  const login = new LoginPage(page);
  const approvals = new ApprovalsPage(page);
  const invoices = new InvoicesPage(page);

  await login.open();
  await login.signInAs('demo.approver');
  await expect(page.getByText('demo.approver (Approver)')).toBeVisible();

  await approvals.open();
  await approvals.approve('INV-1001');
  await expect(approvals.approveButton('INV-1001')).toHaveCount(0);

  await approvals.reject('INV-1002', 'Duplicate of last month');
  await expect(approvals.approveButton('INV-1002')).toHaveCount(0);

  await invoices.open();
  await expect(invoices.row('INV-1001')).toContainText('Approved');
  await expect(invoices.row('INV-1002')).toContainText('Rejected');
});

test('a wrong password shows an error and stays on sign-in', async ({ page }) => {
  const login = new LoginPage(page);

  await login.open();
  await login.signInAs('demo.approver', 'wrong-password');

  await expect(page.getByRole('alert')).toContainText('Invalid username or password.');
});
