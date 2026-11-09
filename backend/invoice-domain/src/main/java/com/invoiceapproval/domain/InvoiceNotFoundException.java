package com.invoiceapproval.domain;

/** The invoice doesn't exist. The API maps this to 404. */
public class InvoiceNotFoundException extends RuntimeException {

    public InvoiceNotFoundException(String invoiceId) {
        super("Invoice " + invoiceId + " was not found.");
    }
}
