package com.invoiceapproval.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Port. The domain owns the contract; adapters (in-memory today, SQL later) implement it. */
public interface InvoiceRepository {

    List<Invoice> findAll();

    Optional<Invoice> findById(String id);

    List<Invoice> findByIds(Collection<String> ids);

    Invoice add(Invoice invoice);

    void update(Invoice invoice);

    boolean remove(String id);
}
