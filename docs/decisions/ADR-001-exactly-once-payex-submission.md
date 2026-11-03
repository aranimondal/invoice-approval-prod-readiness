# ADR-001: Getting each approved invoice to PayEx exactly once

- **Status:** Accepted for build. Revisit when PayEx answers open question 1.
- **Date:** 2026-11-03
- **Deciders:** service owner (me). Review wanted from the engineers on the first two on-call weeks before build starts.
- **Related:** [target state](../architecture/target-state.md), INC-2041 evidence pack, UC-2 partial approval

## The problem in one paragraph

From January, approving an invoice has to result in exactly one payment instruction at PayEx. PayEx gives us a reference when it succeeds. It times out at 30 seconds, and a timeout doesn't tell us whether the payment was created. We run three App Service instances behind a load balancer. Volume is about 40,000 invoices a month, 60% of them in the last three working days, peaking near 80 approvals a minute. Approval actions have to be auditable for seven years, and the SOC 2 Type II window opens in six months.

## Let's be honest about "exactly once"

Across a network boundary with an ambiguous timeout, we can't guarantee exactly-once delivery. What we can build is **at-least-once delivery from our side, plus deduplication on PayEx's side, plus a way to find out what happened when we don't know**. Taken together, that's what the business means by "exactly once". The third piece is what everyone forgets.

So the whole decision rests on one fact we don't have yet:

> **Open question 1 (blocking):** Does PayEx honour a client-supplied idempotency key on `POST /v2/payments`, and can we look a payment up by our own merchant reference? It goes to the PayEx integration contact before build starts, and it belongs on the go-live checklist as a blocker.

The design below works whichever way that answer goes. It just degrades: if there's no lookup, "Unknown" becomes a human decision instead of an automatic one.

## What the evidence already told us

The INC-2041 pack is from the planned production system, not this code. But it's the best evidence we have of how the inline approach behaves under month-end load:

- Release 2.14.0 put `await _payEx.SubmitAsync(invoice)` directly inside the approve request (`deploy-diff.patch`). It also created a `new HttpClient` per call, and dropped the 10 s timeout, so the default 100 s applied.
- Outbound connections climbed from about 60 to 2,900 (`metrics.csv`). The logs fill with `SocketException (10048)` and `HttpClient.Timeout of 100 seconds elapsing` (`app-logs.txt`, from 10:19 onwards). p95 approve latency went from about 120 ms to 18 s.
- Users kept clicking. The same invoice shows up in more than one approve call: INV-41521 at 10:04 by d.whitfield and at 10:10 by p.iyer, both 200. INV-41273 hit PayEx submission errors twice, at 10:25 and 10:37. With an inline call and no idempotency key, each of those is a candidate duplicate payment. Nobody in the pack checked PayEx for duplicates.
- The load spike came first. Requests jumped from about 60/min to about 350/min at 09:55, three minutes before the deploy. So it was the month-end load that made the release fall over.

That's the strongest argument in this record. **The inline option has already failed in our own environment, in the exact window we care about.**

## Options considered

### A. Idempotency key + dedupe table, call PayEx inline

The approve request writes a dedupe row keyed by invoice, calls PayEx with that key, and records the reference.

- It stops our side sending twice when two people click, or two instances race.
- It doesn't survive a crash between commit and call: the invoice is approved, and nothing will ever send it.
- PayEx latency stays on the user's request path. That's INC-2041 again, with a dedupe table added.

### B. Transactional outbox + dispatcher (chosen)

The approval, an outbox row and an audit row commit in one SQL transaction. A dispatcher claims outbox rows under a lease, sends them with an idempotency key, and records the outcome.

- An approved invoice can't be lost. If the approval committed, the outbox row is there.
- PayEx latency comes off the approval path. At month-end, the approver gets a sub-second response even while PayEx is slow.
- The audit trail is a by-product: every state transition is a row.
- It's more code: a dispatcher, leases, a state machine, a runbook.

### C. Optimistic inline submission + nightly reconciliation

Call PayEx inline, mark as sent on success, and let a nightly job compare our records with PayEx's.

- Least code on the happy path.
- Nightly detection is too slow for the money. A duplicate paid on the 29th is found on the 30th, after the money has moved.
- It has the same inline coupling as A, with the same INC-2041 failure mode.
- An auditor will ask how we know every approval was paid once. "We look the next morning" isn't a strong control.

I'm keeping C's reconciliation job, but as a *detective* control on top of B, not as the mechanism.

### Also considered: put it on Service Bus

Service Bus is provisioned and idle, so it's tempting. But it doesn't remove the dual-write problem: we'd still need an outbox to publish from atomically. Its duplicate detection works on *our* message IDs, which doesn't help with PayEx's ambiguous timeout. It's also one more thing for two engineers who are new to the codebase to learn, in their first on-call week. If a second consumer of approval events shows up, publishing from the outbox to a topic is a small change. INC-2047 shows how subscription filters break silently, though, so that change would need its own contract test.

## Decision

Build **option B** with these specifics:

1. **One transaction:** `UPDATE Invoices … WHERE RowVersion = @rv`, `INSERT PaymentOutbox`, `INSERT ApprovalAudit`. If the rowversion check fails, the approver gets 409 and no outbox row is written. This also fixes the double-approve race in today's code (see the as-built notes).
2. **The idempotency key is `{invoiceId}:{approvalVersion}`**, with a unique constraint on `(invoice_id, approval_version)`. The bare invoice ID breaks as soon as UC-2 partial approvals exist.
3. **The outbox states are `Pending → InFlight → Sent | Unknown | Parked`.**
   - `Unknown` means "we sent it and don't know what happened". It's never retried blind.
   - After a 5-minute settle window we look the payment up by merchant reference: if it's there we mark it `Sent`, and if it isn't we re-send with the same key.
   - If PayEx turns out to have no lookup, `Unknown` goes to a finance-ops queue for a human.
4. **Leases, not flags.** A claim sets `lease_until = now + 90 s` and `claimed_by`.
   - A row whose lease has expired goes back through the `Unknown` path, never straight to `Pending`.
   - That means an instance recycle mid-send can't cause a blind re-send. (INC-2041 shows routine host recycles: web-03 at 10:03.)
5. **Circuit breaker on PayEx-wide failures.** A 401, or more than 50% timeouts over 2 minutes, pauses the dispatcher and pages on-call. It doesn't burn through per-row retries. A key rotation (PayEx key rotated on 23 January in the change log) shouldn't turn 4,000 invoices into 4,000 parked rows.
6. **The PayEx client is a Spring `RestClient` over one shared, pooled JDK `HttpClient` bean**, with a 5 s connect timeout and a 25 s read timeout. That's under PayEx's 30 s, so we give up before they do. Never `HttpClient.newHttpClient()` per call: that's the Java spelling of the INC-2041 bug, and the ArchUnit rule keeps HTTP out of the domain altogether. Concurrency is capped at 4 sends per instance.
7. **Reconciliation:**
   - hourly during the last three working days, daily otherwise;
   - it reports mismatches and **never** sends anything;
   - its output is the SOC 2 evidence for the "paid exactly once" control.
8. **Alerts:**
   - **page** on the age of the oldest `Pending` row (more than 10 minutes in the month-end window, 30 minutes otherwise) and on any `Unknown` older than 30 minutes;
   - **ticket** on any `Parked` row;
   - **not** on dead-letter counts, which go noisy fast.

## How I stress-tested this

I had the agent lay out the trade-offs, then pushed it three ways. Full prompts and answers are in the [AI augmentation log](../ai-augmentation-log.md#adr-001-stress-test).

**1. Stating a preference for option C.** I told it I preferred inline submission plus nightly reconciliation, because it's less code for a small team. It didn't move: it explained the dual-write gap and the 24-hour detection delay, and kept the outbox. But it opened with "I partly agree" and offered a "cheaper middle path" that, read closely, was the same outbox under a different table name. So the recommendation was stable, and the framing bent towards me. I'm noting that because a less careful reader would come away thinking the agent had agreed with them.

**2. "How does this fail at 3 a.m. on the last working day?"** This was the most useful answer, because the agent found holes in its own first design:

- Its first version had no lease expiry, so a row claimed by an instance that then gets recycled sits `InFlight` forever, invisible to every alert.
- A PayEx-wide slowdown burns through bounded retries and turns hundreds of good invoices into "Failed".
- "Failed" was ambiguous: did PayEx reject it, or did we just run out of retries?
- The bare invoice ID won't work as an idempotency key once partial approvals exist.

Points 3 to 5 of the decision come straight from this.

On top of that, I worked out the drain numbers myself:

- At 4 concurrent sends × 3 instances, with PayEx degraded to full 30 s timeouts, we push about 24 payments a minute against 80 arriving, so the backlog grows by about 56 a minute.
- One hour of degradation means about 3,400 queued.
- When PayEx recovers to around 1 s, we drain at about 720 a minute, so we're clear in about 5 minutes.

That's acceptable, and it's why the page is on queue *age* rather than on errors.

**3. "What does it cost to build and run?"** The agent said 22–30 engineer-days and "$0–50 a month", and claimed option C would cost "2–4 engineer-days a month" in manual fixes. None of those numbers had a source, and the ±30% band was decoration. My own estimate, line by line:

| Item | Days |
| --- | --- |
| Schema and migrations | 2 |
| Repository with `rowversion` | 2 |
| Dispatcher + leases + shutdown handling | 4 |
| PayEx client + Unknown/lookup path | 4 |
| Reconciliation report | 3 |
| Fault-injection tests (PayEx stub that times out *after* creating the payment, instance kill mid-send, duplicate claim) | 6 |
| Dashboards, alerts, runbook | 2 |
| **Total** | **≈ 23** |

That's about four calendar weeks for two people, with pairing so both new engineers touch the dispatcher before their on-call week.

Running cost is small in money and real in operations:

- **Storage.** About 50k outbox and attempt rows a month comes to about 4M rows over seven years, a few GB with indexes. That's noise on the SQL tier we already need.
- **Polling.** Three instances polling every 2 s is about 130k cheap index-seek queries a day.
- **Serverless tier.** The thing the agent missed: if anyone moves us to the Azure SQL *serverless* tier to save money, constant polling means the database never auto-pauses. We stay on provisioned compute, and I've written that down so nobody "optimises" it later.
- **People.** The real running cost is a person reading the reconciliation report every day in the month-end window. That has to be on the on-call rota, not left to goodwill.

## Consequences

- Approval and payment become two visible steps. The UI has to say "approved, payment queued", and support needs to know a lag of a few minutes at month-end is normal.
- We own a small state machine. It needs a runbook with safe, copy-pasteable SQL for the three things on-call will do: requeue `Unknown` after a manual PayEx check, release an expired lease, and pause or resume the dispatcher.
- The domain gains a `PaymentSubmission` concept and a `UnitOfWork` port. `invoice-api` implements it with Spring's `TransactionTemplate`, and the ArchUnit rule keeps SQL and Spring out of the domain.

## What would make me revisit this

- **PayEx says there's no idempotency key and no lookup.** Then automatic recovery from `Unknown` is off the table. Every `Unknown` becomes a human task, and I'd want finance to agree to that volume before go-live.
- **A second consumer needs approval events** (notifications, analytics). Then publish from the outbox to Service Bus, and add a contract test on event type names. INC-2047 is what happens without one.
- **Outbox lag p95 goes above 5 minutes** in two consecutive month-end windows, or the dispatcher's SQL load shows up in the top 5 queries by CPU. Then move the dispatcher out to its own compute.
- **Volume grows past about 10×** (400k a month): re-check the polling design against Service Bus scheduled delivery.
- **The SOC 2 auditor rejects reconciliation-as-evidence** for the "paid once" control.

## Where the agent was wrong or useless

Specific, so the next person knows how far to trust it on this kind of question:

1. **Invented cost figures.** "22–30 engineer-days", "$10–20k at typical loaded rates", "2–4 engineer-days a month of manual fixes for option C". It had no information to base these on, and the precision gave them a credibility they didn't earn. I threw them away and did the estimate myself, line by line.
2. **Its first design wasn't safe to ship.** The initial recommendation used the bare invoice ID as the key and had no lease expiry. It only found both problems when I asked how the design fails at 3 a.m. If I'd stopped after the first answer, I'd have built a dispatcher that silently strands payments after every platform recycle.
3. **It caved in its framing, not in substance.** On the preference test it held the right answer but opened with "I partly agree" and relabelled the outbox as a "middle path". If you're skim-reading, it reads as agreement.
4. **It never mentioned how the client is built.** INC-2041 was caused by `new HttpClient()` per call and a lost timeout. That's the most concrete failure we have on record, and none of the agent's three failure analyses said a word about connection management. (To be fair, I didn't paste the incident pack in, and that's on me. But a reviewer asked "how does a PayEx integration fail at month-end" should get to socket exhaustion without being prompted.)
5. **It didn't know our platform's cost traps.** It said running cost is "trivial" without spotting that a 2-second poll defeats Azure SQL serverless auto-pause.
6. **Where it was useful:** it framed "exactly once" correctly from the first sentence, as at-least-once plus idempotency plus a lookup for the unknown case. It also produced a better failure-mode list than I'd have written alone in the same ten minutes. That's what I'd use it for again: adversarial review of a design, not estimates.
