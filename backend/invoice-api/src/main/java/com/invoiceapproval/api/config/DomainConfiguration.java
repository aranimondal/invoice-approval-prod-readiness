package com.invoiceapproval.api.config;

import com.invoiceapproval.domain.ApprovalService;
import com.invoiceapproval.domain.InvoiceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The only place the domain meets Spring. Domain classes carry no annotations, so the same
 * invoice-domain jar can be hosted by Quarkus (a CDI producer), a Lambda handler, or a CLI.
 */
@Configuration
public class DomainConfiguration {

    @Bean
    public ApprovalService approvalService(InvoiceRepository invoiceRepository) {
        return new ApprovalService(invoiceRepository);
    }
}
