package com.invoiceapproval.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An invoice and the decision taken on it.
 *
 * <p>Deliberately a mutable entity rather than a record: ApprovalService changes status in place,
 * as the reference app does. That aliasing is what makes the double-approve race in
 * docs/architecture/as-built.md possible. It's kept on purpose so the analysis describes the
 * running code; the fix (optimistic concurrency) is part of the persistence work in ADR-001.</p>
 */
public class Invoice {

    private String id = "";
    private String customerName = "";
    private BigDecimal amount = BigDecimal.ZERO;
    private InvoiceStatus status = InvoiceStatus.Draft;
    private Instant createdAt = Instant.now();
    private String decidedBy;
    private Instant decidedAt;
    private String decisionNote;

    public Invoice() {
    }

    public Invoice(String id, String customerName, BigDecimal amount, InvoiceStatus status) {
        this.id = id;
        this.customerName = customerName;
        this.amount = amount;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
}
