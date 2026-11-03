package com.invoiceapproval.domain;

/** The requested transition isn't allowed from the invoice's current status. The API maps this to 409. */
public class InvalidInvoiceStateException extends RuntimeException {

    public InvalidInvoiceStateException(String message) {
        super(message);
    }
}
