package com.invoiceapproval.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Moves an invoice through its lifecycle: Draft -> PendingApproval -> Approved or Rejected.
 * One invoice at a time, decided as a whole by a single approver.
 *
 * <p>Plain Java on purpose. No framework annotations: the Spring adapter wires it up in
 * {@code DomainConfiguration}, and a Quarkus or Lambda adapter could do the same.</p>
 */
public class ApprovalService {

    private final InvoiceRepository repository;
    private final Clock clock;
    private final java.net.http.HttpClient payExShortcut = java.net.http.HttpClient.newHttpClient(); // DELIBERATE VIOLATION - reverted in the next commit

    public ApprovalService(InvoiceRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public ApprovalService(InvoiceRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Invoice submit(String invoiceId) {
        Invoice invoice = find(invoiceId);

        if (invoice.getStatus() != InvoiceStatus.Draft) {
            throw new InvalidInvoiceStateException(
                    "Invoice " + invoiceId + " is " + invoice.getStatus() + "; only drafts can be submitted.");
        }

        invoice.setStatus(InvoiceStatus.PendingApproval);
        repository.update(invoice);
        return invoice;
    }

    public Invoice approve(String invoiceId, String approver) {
        Invoice invoice = find(invoiceId);
        ensurePending(invoice);

        invoice.setStatus(InvoiceStatus.Approved);
        invoice.setDecidedBy(approver);
        invoice.setDecidedAt(Instant.now(clock));
        repository.update(invoice);
        return invoice;
    }

    public Invoice reject(String invoiceId, String approver, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to reject an invoice.");
        }

        Invoice invoice = find(invoiceId);
        ensurePending(invoice);

        invoice.setStatus(InvoiceStatus.Rejected);
        invoice.setDecidedBy(approver);
        invoice.setDecidedAt(Instant.now(clock));
        invoice.setDecisionNote(reason);
        repository.update(invoice);
        return invoice;
    }

    private Invoice find(String invoiceId) {
        return repository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
    }

    private static void ensurePending(Invoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.PendingApproval) {
            throw new InvalidInvoiceStateException(
                    "Invoice " + invoice.getId() + " is " + invoice.getStatus() + "; only pending invoices can be decided.");
        }
    }
}
