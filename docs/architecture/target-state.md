# Target state: persistence and PayEx in place

This is where we want to be by the January go-live. **Anything drawn with a dashed orange border doesn't exist in the code yet.** Solid boxes exist today, sometimes with a small extension that's called out in the label. Service Bus has a grey dotted border: it's provisioned in Azure, and on purpose nothing uses it in v1. [ADR-001](../decisions/ADR-001-exactly-once-payex-submission.md) explains why.

Sources: [`target-container.mmd`](target-container.mmd), [`target-approval-sequence.mmd`](target-approval-sequence.mmd).

## Container view

```mermaid
flowchart LR
    user["Approver / Admin<br/>(browser)"]
    payex["PayEx API<br/>external, 30 s timeout"]:::external

    subgraph app["Azure App Service — 3 instances"]
        direction TB
        ui["Static UI (exists)"]
        api["API: controllers + auth (exists)<br/>Entra ID sign-in replaces demo users (NEW)"]
        domain["InvoiceApp.Domain<br/>ApprovalService (exists)<br/>+ PaymentSubmission (NEW)"]
        disp["PayExDispatcher<br/>BackgroundService, lease-based (NEW)"]:::planned
        payexc["PayExClient<br/>typed HttpClient, idempotency key (NEW)"]:::planned
        recon["ReconciliationJob<br/>hourly in month-end window, else daily (NEW)"]:::planned
    end

    subgraph sql["Azure SQL (NEW)"]
        inv[("Invoices<br/>rowversion")]:::planned
        outbox[("PaymentOutbox<br/>unique(invoice_id, approval_version)")]:::planned
        audit[("ApprovalAudit<br/>append-only, 7-year retention")]:::planned
    end

    sb[["Azure Service Bus<br/>provisioned, NOT used in v1"]]:::unused

    user --> ui --> api --> domain
    domain -->|"one transaction:<br/>invoice + outbox row + audit row"| sql
    disp -->|"claim with lease<br/>UPDLOCK, READPAST"| outbox
    disp --> payexc -->|"POST /v2/payments<br/>Idempotency-Key"| payex
    payexc -->|"on Unknown: GET by merchant reference"| payex
    disp -->|"Sent / Unknown / Parked + attempt rows"| sql
    recon -->|"list PayEx payments vs Sent rows"| payex
    recon -->|"mismatch report, never re-sends"| sql
    outbox -. "later, if a 2nd consumer appears (ADR-001 revisit)" .-> sb

    classDef planned stroke-dasharray: 5 5,stroke:#b45309,color:#7c2d12;
    classDef external fill:#f3f4f6,stroke:#374151;
    classDef unused stroke-dasharray: 2 4,stroke:#9ca3af,color:#6b7280;
```

## Approval and payment sequence

Everything from the SQL transaction onwards is new.

```mermaid
sequenceDiagram
    autonumber
    actor A as Approver
    participant API as ApprovalsController (exists)
    participant S as ApprovalService (exists, extended)
    participant DB as Azure SQL (NEW)
    participant D as PayExDispatcher (NEW)
    participant P as PayEx (external)

    A->>API: POST /api/approvals/{id}/approve
    API->>S: Approve(id, approver, expectedRowVersion)
    S->>DB: BEGIN TRAN
    S->>DB: UPDATE Invoices SET Status=Approved WHERE Id=@id AND RowVersion=@rv
    alt 0 rows (someone else decided first)
        S-->>API: 409 Conflict — no outbox row, no payment
    end
    S->>DB: INSERT PaymentOutbox(invoice_id, approval_version, key, state=Pending)
    S->>DB: INSERT ApprovalAudit(who, what, when, before/after)
    S->>DB: COMMIT
    API-->>A: 200 Approved — payment pending (returns in ms, PayEx not on this path)

    loop every 2 s, per instance
        D->>DB: claim up to 20 Pending/expired-lease rows (UPDLOCK, READPAST), set lease 90 s
    end
    D->>P: POST /v2/payments, Idempotency-Key = invoiceId:approvalVersion (25 s client timeout)
    alt 2xx with reference
        D->>DB: state=Sent, payex_reference, attempt row
    else timeout / connection reset
        D->>DB: state=Unknown, attempt row
        D->>P: GET /v2/payments?merchantReference=key (next cycle)
        alt found
            D->>DB: state=Sent, payex_reference
        else not found after settle window (5 min)
            D->>P: re-POST with the SAME key
        end
    else 4xx validation error
        D->>DB: state=Parked, alert finance-ops queue (no retry)
    end
```

## What doesn't exist yet, and what has to be true first

| Part | Status | Hard dependency |
| --- | --- | --- |
| Azure SQL schema (`Invoices`, `PaymentOutbox`, `PaymentAttempts`, `ApprovalAudit`) | not started | migration tooling decision (I'd go with plain SQL migrations via DbUp rather than EF, see below) |
| `IInvoiceRepository` SQL implementation with `rowversion` | not started | schema |
| `ApprovalService` writing the outbox row in the same unit of work | not started | a small `IUnitOfWork` port in Domain, so the architecture test still passes |
| `PayExDispatcher` hosted service | not started | PayEx answers the idempotency question (ADR-001, open question 1) |
| `PayExClient` via `AddHttpClient` | not started | API key in Key Vault, not appsettings |
| Reconciliation job | not started | a PayEx "list payments by date" endpoint, to be confirmed |
| Entra ID sign-in | not started | IT ticket for the app registration |
| Service Bus | provisioned, unused | only if a second consumer of approval events appears |

A few choices in that table are worth explaining.

**DbUp over EF Core migrations.** The domain must stay free of storage code (that's what the architecture test enforces), and the outbox claim query uses `UPDLOCK, READPAST` hints. Hand-written SQL makes those visible in review. EF would generate the claim query for us, and that's exactly the query I want a reviewer to read line by line.

**The idempotency key is `invoiceId:approvalVersion`, not just the invoice ID.** UC-2 (partial approval) is on the roadmap. Once an invoice can be partially approved and later topped up, the invoice ID alone stops being a unique payment instruction. The agent got this right during the 3 a.m. stress test, after initially suggesting the bare invoice ID.

**The dispatcher runs inside the App Service instances, not as a separate Function.** At 80 approvals a minute peak, a separate compute unit costs more in moving parts than it saves. The lease column means three instances can run it safely. If it ever needs to scale independently, moving it out is a hosting change, not a design change.

## What stays the same

The UI, the role model, and the Draft → PendingApproval → Approved/Rejected lifecycle stay as they are. The approval API keeps its route and response shape. The only visible difference for an approver is that the response now means "approved, payment queued" rather than "done". The UI should say so, because at month-end the payment can lag the approval by minutes.
