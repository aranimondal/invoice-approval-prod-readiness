package com.invoiceapproval.api.persistence;

import com.invoiceapproval.domain.Invoice;
import com.invoiceapproval.domain.InvoiceRepository;
import com.invoiceapproval.domain.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * In-memory store so the app runs with no database. Seeded with a few invoices.
 *
 * <p>Note for reviewers: the lock protects each call, not a read-check-write sequence, and
 * findById hands back the live object. That's a faithful port of the original and the reason
 * two concurrent approvals can both succeed. See docs/architecture/as-built.md.</p>
 */
@Repository
public class InMemoryInvoiceRepository implements InvoiceRepository {

    private final Object lock = new Object();
    private final List<Invoice> invoices = new ArrayList<>();
    private int nextNumber = 1006;

    public InMemoryInvoiceRepository() {
        invoices.add(new Invoice("INV-1001", "Lakeside School District", new BigDecimal("4250.00"), InvoiceStatus.PendingApproval));
        invoices.add(new Invoice("INV-1002", "Hilltop Academy", new BigDecimal("980.50"), InvoiceStatus.PendingApproval));
        Invoice decided = new Invoice("INV-1003", "Riverside Charter", new BigDecimal("12300.00"), InvoiceStatus.Approved);
        decided.setDecidedBy("demo.approver");
        decided.setDecidedAt(Instant.now().minus(Duration.ofDays(2)));
        invoices.add(decided);
        invoices.add(new Invoice("INV-1004", "Oakwood Independent", new BigDecimal("615.75"), InvoiceStatus.PendingApproval));
        invoices.add(new Invoice("INV-1005", "Maple Grove Prep", new BigDecimal("310.00"), InvoiceStatus.Draft));
    }

    @Override
    public List<Invoice> findAll() {
        synchronized (lock) {
            return List.copyOf(invoices);
        }
    }

    @Override
    public Optional<Invoice> findById(String id) {
        synchronized (lock) {
            return invoices.stream().filter(invoice -> invoice.getId().equals(id)).findFirst();
        }
    }

    @Override
    public List<Invoice> findByIds(Collection<String> ids) {
        Set<String> wanted = Set.copyOf(ids);
        synchronized (lock) {
            return invoices.stream().filter(invoice -> wanted.contains(invoice.getId())).toList();
        }
    }

    @Override
    public Invoice add(Invoice invoice) {
        synchronized (lock) {
            if (invoice.getId() == null || invoice.getId().isEmpty()) {
                invoice.setId("INV-" + nextNumber++);
            }
            invoices.add(invoice);
            return invoice;
        }
    }

    @Override
    public void update(Invoice invoice) {
        synchronized (lock) {
            for (int index = 0; index < invoices.size(); index++) {
                if (invoices.get(index).getId().equals(invoice.getId())) {
                    invoices.set(index, invoice);
                    return;
                }
            }
        }
    }

    @Override
    public boolean remove(String id) {
        synchronized (lock) {
            return invoices.removeIf(invoice -> invoice.getId().equals(id));
        }
    }
}
