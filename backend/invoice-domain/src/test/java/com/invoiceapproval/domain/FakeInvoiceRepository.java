package com.invoiceapproval.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

final class FakeInvoiceRepository implements InvoiceRepository {

    private final List<Invoice> invoices = new ArrayList<>();

    FakeInvoiceRepository(Invoice... seed) {
        invoices.addAll(List.of(seed));
    }

    @Override public List<Invoice> findAll() { return List.copyOf(invoices); }

    @Override public Optional<Invoice> findById(String id) {
        return invoices.stream().filter(invoice -> invoice.getId().equals(id)).findFirst();
    }

    @Override public List<Invoice> findByIds(Collection<String> ids) {
        return invoices.stream().filter(invoice -> ids.contains(invoice.getId())).toList();
    }

    @Override public Invoice add(Invoice invoice) { invoices.add(invoice); return invoice; }

    @Override public void update(Invoice invoice) { }

    @Override public boolean remove(String id) { return invoices.removeIf(invoice -> invoice.getId().equals(id)); }
}
