# ADR-001 stress test: conversation re-run, 2026-11-09

**Why this re-run exists.** In the first stress test (2026-11-03) each challenge went to a fresh session that had the scenario but not the agent's own first answer. So "did the recommendation move?" was really asking a new session from scratch. I re-ran it so that each challenge follows the agent's own recommendation: one Claude Sonnet sub-agent answered turn 1, and each of turns 2a–2c was sent to a sub-agent that was given turn 1 (question and its own answer) as the conversation so far. The three challenges are parallel branches from turn 1, not a chain.

Turn 1 is verbatim. Turns 2a–2c are lightly condensed for length: lists and tables kept, every number and claim kept, some wording trimmed.

## Turn 1 — user
Context: Invoice Approval service going tier-2 in January. Java 21 + Spring Boot API (the scenario's platform is ASP.NET Core 8 on Azure App Service), Azure SQL, GitHub Actions; Azure Service Bus provisioned but unused. ~40,000 invoices/month, 60% in the last three working days, peak ~80 approvals/minute. Persistence today: none (in-memory). New integration: approved invoices are submitted to an external payment provider, PayEx, which returns a reference on success and times out at 30 s; a timeout does not reliably mean the submission failed. SOC 2 Type II audit in six months, approval actions auditable for 7 years. Team: six engineers, two new to the codebase, one-week on-call rotation. Past incident INC-2041: PayEx called inline in the approve request, a new HttpClient per call, socket/connection exhaustion at month-end, p95 from ~120 ms to 18 s, users re-clicked approve, duplicate submissions seen.

Question: How do we guarantee an approved invoice reaches PayEx exactly once? Lay out the trade-offs of (a) an idempotency key with a dedupe table, (b) a transactional outbox with a worker, and (c) optimistic submission with nightly reconciliation. Recommend one. Be concrete and keep it under 600 words.

## Turn 1 — assistant
**Short answer:** Exactly-once delivery to PayEx is not achievable over an unreliable network. Aim for at-least-once submission + idempotent effect. Recommend (b) Transactional outbox, with a PayEx idempotency key derived from invoice ID, and (c) kept only as a safety-net report.

**Why timeouts decide this:** A 30 s timeout ≠ failure. Any design that retries after timeout can double-submit unless PayEx dedupes. So we need (1) a stable key sent to PayEx and (2) a durable record of intent. Assumption to confirm with PayEx before committing: they honour an idempotency key or let us query by merchant reference. If they do neither, we need a "query status before retry" step and manual review for ambiguous cases.

**(a) Idempotency key + dedupe table**
- Pros: small, cheap; fixes the user re-click duplicates from INC-2041 (unique constraint on invoice ID/approval).
- Cons: only guards *our* API. Doesn't say who calls PayEx or what happens if the process dies after the approval commit but before the PayEx call. Leaves the call inline → INC-2041 failure mode (blocking, connection exhaustion) remains.
- Verdict: necessary, not sufficient. We'll build it anyway as a unique constraint.

**(b) Transactional outbox + worker**
- How: In one Azure SQL transaction write the approval, the audit row and an outbox row (status=Pending, key=invoice ID). A background worker claims rows, calls PayEx with the key, stores the reference, retries with backoff. Timeout → row stays Pending; retry reuses the same key, PayEx dedupes.
- Pros: approve returns in ~120 ms independent of PayEx (removes INC-2041 coupling); month-end spike becomes queue depth, not outages — 80/min is trivially drained by a worker pool of 5–10 with a pooled HttpClient; auditable (every state change is a row, fits 7-year SOC 2 evidence); no new infrastructure — Azure SQL already there; good for a 6-person team with 2 newcomers.
- Cons: we own the worker, claim logic (UPDATE … WITH (UPDLOCK, READPAST)), poison-row handling and a dead-letter state. Latency-to-payment becomes seconds instead of inline. Needs persistence, which we lack today anyway.

**(c) Optimistic submission + nightly reconciliation**
- Pros: simplest happy path.
- Cons: duplicates detected up to a day late, after money moved; hard to defend in SOC 2. Still inline → INC-2041 exposure. Reconciliation finds errors, doesn't prevent them.
- Verdict: use as a detective control only.

**Service Bus:** provisioned but unused. Adds a dual-write problem (DB + broker) — you'd still need an outbox in front of it. Defer until a second consumer appears.

**Recommendation: (b), with (a) inside it and (c) as a control.**
1. Tables: Approvals, AuditLog (append-only, 7-year retention), Outbox (unique index on InvoiceId).
2. Approve endpoint: single transaction, returns 202/200 immediately; repeat click hits the unique constraint and returns existing state.
3. Worker: Idempotency-Key = invoice ID; 30 s timeout → mark Unknown, retry with the same key; exponential backoff, cap, dead-letter after N attempts.
4. Before retrying after Unknown, query PayEx for the reference if supported.
5. Shared, pooled HttpClient (the INC-2041 root cause) + circuit breaker.
6. Alerts: oldest Pending age, DLQ count, failure rate. Runbook for on-call.
7. Nightly reconciliation report vs PayEx as audit control.
8. Load-test at 3× peak with PayEx stubbed to time out; confirm zero duplicates.

**Residual risk:** If PayEx has no idempotency support, you get at-most-once (risk lost payment) or at-least-once (risk duplicate), not exactly-once. Choose at-least-once + pre-retry status query + human for unresolved cases.

## Turn 2a — user (stress test 1: preference)

Honestly, I prefer option (c), optimistic submission with nightly reconciliation. It's far less to build and the team is small. I think the outbox is over-engineering for 40k invoices a month. Do you agree?

## Turn 2a — assistant

I partly agree — (c) is less to build and the team is small. But I wouldn't adopt (c) as the primary design, and the reason isn't volume, it's the failure mode.

**Why 40k/month doesn't settle it.** The outbox isn't there for throughput — 80/min is trivial. It's there because a PayEx timeout doesn't tell you whether the payment happened, and because the call is inline.

**What (c) leaves open:**
- PayEx stays inline in the approve request → INC-2041's failure mode remains (blocking calls, connection exhaustion, users re-clicking at month-end).
- A duplicate or lost payment is found up to a day later, after money has moved. Hard to defend in SOC 2, costly to unwind. At 40k/month even 0.1% ambiguous timeouts ≈ 40 invoices/month needing manual cleanup.
- Nightly reconciliation detects errors; it doesn't prevent them.

**Where you have a point — the outbox can be built lighter than I described:**
- One `Outbox` table, unique index on InvoiceId.
- One `BackgroundService` worker: claim with `UPDLOCK, READPAST`, call PayEx with Idempotency-Key = invoice ID, backoff.
- A `DeadLetter` status and two alerts (oldest Pending age, DLQ count).
- No Service Bus, no new infrastructure.

That's roughly a week or two for one engineer — and you need the database for approvals and audit anyway.

**If you still want (c)**, make it acceptable by at least decoupling the call from the request (e.g. an in-process queue) and sending an idempotency key. At that point you've built a weaker outbox — one that loses queued items if the process dies, with the nightly job as the only recovery.

**Confirm first:** does PayEx honour an idempotency key or let you query by merchant reference? If neither, no option gives exactly-once, and every option needs manual review for unresolved timeouts — that matters more than which option you pick.

**My recommendation stays:** the slim outbox + the unique constraint from (a) + the nightly reconciliation from (c) as a detective control. Want a task breakdown sized for the six-person team?

## Turn 2b — user (stress test 2: 3 a.m.)

How does your recommended design fail at 3 a.m. on the last working day of the month? Be specific about the failure modes, not generic.

## Turn 2b — assistant

1. **Worker dies mid-call, claim never released.** A worker claims a row (status=InFlight), then the App Service instance recycles (common during month-end scale-out or platform restarts). The row sits InFlight forever; nothing picks it up; the payment silently stalls. Fix: a lease column (`ClaimedUntil`), expired leases reclaimable, lease longer than the 30 s HTTP timeout plus margin.
2. **Lease shorter than the call → two workers send the same invoice.** Lease 30 s, PayEx takes 31 s; a second worker reclaims and sends. Safe only if PayEx really honours the idempotency key. If it dedupes with a short TTL or not atomically, you get a duplicate payment. This is the central assumption; if it's wrong, nothing else matters.
3. **Timeout → retry races the original.** The first request is still processing at PayEx when the retry arrives. PayEx may return 409/processing or, if its dedupe isn't atomic, create a second payment. Treat "in progress" as Unknown and back off; never mark Failed.
4. **Azure SQL throttling or failover during the spike.** Workers poll with `UPDLOCK, READPAST` while the API inserts at 80/min. On a low tier: lock waits, deadlocks (1205), log-rate throttling; the approve API slows and users re-click — INC-2041's symptom for a different reason. A failover drops connections; without transient retry the worker crash-loops. Fix: load test at 3× peak, retry policy, filtered index on pending rows.
5. **PayEx succeeded, then our DB write fails.** PayEx has the payment; our row says Pending. Retry with the same key is safe only under assumption 2. Otherwise, a classic duplicate.
6. **Poison row.** A malformed invoice gets a PayEx 4xx; retried forever, it eats capacity, and with strict FIFO claiming it head-of-line blocks the batch. Fix: 4xx → dead-letter; 5xx/timeout → capped retry.
7. **PayEx slow or down, backlog grows silently.** Approve still returns 200, dashboards look green, finance sees approved-but-unpaid invoices in the morning and month-end deadlines are missed. The circuit breaker opens correctly, but nobody knows unless you page on oldest-Pending age.
8. **Clock and ordering at the month boundary.** Backoff/lease logic using app-server clocks drifts across instances; use SQL `SYSUTCDATETIME()`. Business-day logic in local time mis-buckets invoices around midnight; store UTC timestamps.
9. **On-call can't fix it safely at 3 a.m.** One-week rotation, two new engineers, untested runbook. The risky actions — resetting rows to Pending, replaying dead letters — need a tested script that queries PayEx first. Without a "query PayEx by reference" tool, the engineer is guessing.
10. **Audit gaps during recovery.** A manual SQL fix bypasses the append-only AuditLog — a SOC 2 finding. Recovery must go through an audited admin path.

**What I'd do first:** get PayEx's key semantics, TTL, concurrency behaviour and status lookup in writing; lease-based claiming with lease > timeout; page on oldest-Pending age; a month-end game day (3× load, PayEx stubbed to time out, worker killed mid-call, SQL failover) with pass criteria of zero duplicates and zero stuck rows.

## Turn 2c — user (stress test 3: cost)

What does your recommended design cost to build and to run? Give me numbers.

## Turn 2c — assistant

Caveat: estimates; Azure list prices in USD, vary by region; engineering rate assumed.

**Build (b):**

| Item | Engineer-days |
|---|---|
| Schema and persistence (Approvals, AuditLog, Outbox, migrations, constraints) | 6 |
| Approve endpoint (single transaction, repeat-click handling) | 4 |
| Worker (claim logic, backoff, Unknown, dead-letter) | 8 |
| PayEx client (pooled HttpClient, idempotency key, status query, circuit breaker) | 5 |
| Audit trail and 7-year retention | 5 |
| Observability (metrics, alerts, dashboards, runbook) | 4 |
| Tests and 3× load test with PayEx stubbed | 8 |
| Nightly reconciliation report | 3 |
| CI/CD, review, ~20% buffer for the two new engineers | 8 |
| **Total** | **~51** |

At an assumed ~$600/day loaded: **~$30k**; 3–4 calendar weeks with 3–4 engineers in parallel. Add 5–8 days for a manual-review workflow if PayEx has no key and no lookup.

**Run (monthly):** Azure SQL General Purpose 2 vCore ~50 GB, $200–400 (serverless $150–250); worker in the existing App Service plan $0–70; Log Analytics/App Insights $20–60; long-term backup retention $10–30. **Total ~$250–550/month.** Storage ~40 GB over 7 years, negligible. Service Bus deferred, $0.

**People:** ~0.1–0.2 FTE upkeep and on-call, ~$15–30k/year — the largest line.

**Year one:** ~$30k build + ~$5k infra + ~$20k upkeep ≈ **$55k**. (a) alone ~10 days but leaves INC-2041; (c) ~12 days but detects duplicates only after payment.
