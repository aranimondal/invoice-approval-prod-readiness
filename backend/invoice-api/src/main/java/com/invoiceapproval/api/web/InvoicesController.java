package com.invoiceapproval.api.web;

import com.invoiceapproval.api.auth.RequireRole;
import com.invoiceapproval.domain.ApprovalService;
import com.invoiceapproval.domain.Invoice;
import com.invoiceapproval.domain.InvoiceRepository;
import com.invoiceapproval.domain.InvoiceStatus;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoicesController {

    private final InvoiceRepository repository;
    private final ApprovalService approvals;

    public InvoicesController(InvoiceRepository repository, ApprovalService approvals) {
        this.repository = repository;
        this.approvals = approvals;
    }

    public record CreateInvoiceRequest(String customerName, BigDecimal amount) {
    }

    @GetMapping
    public ResponseEntity<?> getAll(@RequestParam(required = false) String status) {
        List<Invoice> invoices = repository.findAll();

        if (status != null && !status.isEmpty()) {
            Optional<InvoiceStatus> parsed = Arrays.stream(InvoiceStatus.values())
                    .filter(value -> value.name().equalsIgnoreCase(status))
                    .findFirst();
            if (parsed.isEmpty()) {
                return ApiExceptionHandler.error(HttpStatus.BAD_REQUEST, "Unknown status '" + status + "'.");
            }
            invoices = invoices.stream().filter(invoice -> invoice.getStatus() == parsed.get()).toList();
        }

        return ResponseEntity.ok(invoices);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Invoice> getById(@PathVariable String id) {
        return repository.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateInvoiceRequest request) {
        if (request.customerName() == null || request.customerName().isBlank()) {
            throw new IllegalArgumentException("Customer name is required.");
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }

        Invoice invoice = new Invoice();
        invoice.setCustomerName(request.customerName().trim());
        invoice.setAmount(request.amount());
        Invoice created = repository.add(invoice);

        return ResponseEntity.created(URI.create("/api/invoices/" + created.getId())).body(created);
    }

    @PostMapping("/{id}/submit")
    public Invoice submit(@PathVariable String id) {
        return approvals.submit(id);
    }

    @DeleteMapping("/{id}")
    @RequireRole("Admin")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return repository.remove(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
