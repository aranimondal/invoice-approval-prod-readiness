package com.invoiceapproval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ApprovalServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-29T10:15:30Z");

    private static Invoice invoiceWith(String id, InvoiceStatus status) {
        return new Invoice(id, "Test School", new BigDecimal("100.00"), status);
    }

    private static ApprovalService serviceFor(Invoice... invoices) {
        return new ApprovalService(new FakeInvoiceRepository(invoices), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void submit_movesDraftToPendingApproval() {
        Invoice result = serviceFor(invoiceWith("INV-1", InvoiceStatus.Draft)).submit("INV-1");

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.PendingApproval);
    }

    @Test
    void submit_rejectsInvoiceThatIsNotADraft() {
        ApprovalService service = serviceFor(invoiceWith("INV-1", InvoiceStatus.Approved));

        assertThatThrownBy(() -> service.submit("INV-1")).isInstanceOf(InvalidInvoiceStateException.class);
    }

    @Test
    void approve_setsStatusApproverAndTimestamp() {
        Invoice result = serviceFor(invoiceWith("INV-1", InvoiceStatus.PendingApproval)).approve("INV-1", "demo.approver");

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.Approved);
        assertThat(result.getDecidedBy()).isEqualTo("demo.approver");
        assertThat(result.getDecidedAt()).isEqualTo(NOW);
    }

    @Test
    void approve_rejectsInvoiceThatIsStillADraft() {
        ApprovalService service = serviceFor(invoiceWith("INV-1", InvoiceStatus.Draft));

        assertThatThrownBy(() -> service.approve("INV-1", "demo.approver")).isInstanceOf(InvalidInvoiceStateException.class);
    }

    @Test
    void approve_throwsWhenInvoiceDoesNotExist() {
        ApprovalService service = serviceFor();

        assertThatThrownBy(() -> service.approve("INV-404", "demo.approver")).isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void reject_recordsReasonAndApprover() {
        Invoice result = serviceFor(invoiceWith("INV-1", InvoiceStatus.PendingApproval))
                .reject("INV-1", "demo.approver", "Duplicate invoice");

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.Rejected);
        assertThat(result.getDecisionNote()).isEqualTo("Duplicate invoice");
        assertThat(result.getDecidedBy()).isEqualTo("demo.approver");
    }

    @Test
    void reject_requiresAReason() {
        ApprovalService service = serviceFor(invoiceWith("INV-1", InvoiceStatus.PendingApproval));

        assertThatThrownBy(() -> service.reject("INV-1", "demo.approver", "  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
