package com.invoiceapproval.api.web;

import com.invoiceapproval.domain.InvalidInvoiceStateException;
import com.invoiceapproval.domain.InvoiceNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain failures to HTTP: not found -> 404, wrong state -> 409, bad input -> 400.
 * Anything else is deliberately NOT caught here, so it surfaces as a 500 and shows up in
 * the error rate. (Eval case-10 is exactly the bug of swallowing it.)
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvoiceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(InvoiceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidInvoiceStateException.class)
    public ResponseEntity<Map<String, String>> conflict(InvalidInvoiceStateException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
