package com.invoiceapproval.api.web;

import com.invoiceapproval.api.auth.CurrentSession;
import com.invoiceapproval.api.auth.RequireRole;
import com.invoiceapproval.domain.ApprovalService;
import com.invoiceapproval.domain.Invoice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
@RequireRole({"Approver", "Admin"})
public class ApprovalsController {

    private final ApprovalService approvals;

    public ApprovalsController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    public record RejectRequest(String reason) {
    }

    @PostMapping("/{invoiceId}/approve")
    public Invoice approve(@PathVariable String invoiceId, HttpServletRequest request) {
        return approvals.approve(invoiceId, CurrentSession.of(request).username());
    }

    @PostMapping("/{invoiceId}/reject")
    public Invoice reject(@PathVariable String invoiceId, @RequestBody RejectRequest body, HttpServletRequest request) {
        return approvals.reject(invoiceId, CurrentSession.of(request).username(), body.reason());
    }
}
