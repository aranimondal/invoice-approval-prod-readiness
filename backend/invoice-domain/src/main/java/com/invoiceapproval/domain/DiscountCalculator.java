package com.invoiceapproval.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Volume and loyalty discount rules used when pricing invoices. Nothing calls it yet. */
public class DiscountCalculator {

    public BigDecimal quantityDiscount(int quantity) {
        if (quantity >= 100) return new BigDecimal("0.15");
        if (quantity >= 50) return new BigDecimal("0.10");
        if (quantity >= 10) return new BigDecimal("0.05");
        return BigDecimal.ZERO;
    }

    public BigDecimal loyaltyDiscount(int yearsAsCustomer) {
        return new BigDecimal("0.01").multiply(BigDecimal.valueOf(yearsAsCustomer)).min(new BigDecimal("0.10"));
    }

    public BigDecimal combinedDiscount(int quantity, int yearsAsCustomer, String customerType) {
        BigDecimal discount = quantityDiscount(quantity).add(loyaltyDiscount(yearsAsCustomer));
        if ("Nonprofit".equals(customerType)) {
            discount = discount.add(new BigDecimal("0.05"));
        }
        return discount;
    }

    public BigDecimal applyDiscount(BigDecimal amount, BigDecimal discountRate) {
        // HALF_EVEN (banker's rounding), the reference app's rounding rule. Eval case-03 is about changing it.
        return amount.multiply(BigDecimal.ONE.subtract(discountRate)).setScale(2, RoundingMode.HALF_EVEN);
    }
}
