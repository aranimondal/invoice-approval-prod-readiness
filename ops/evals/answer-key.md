# My answer key for the review eval

The README says which cases contain defects is deliberately left unrecorded. These are my own labels, made by reading each diff against the code it targets, before I looked at any review output. The diffs target the workshop's reference app ([workshop repository](https://github.com/NITIN1670/ai-in-sdlc-assignment/tree/0616158bdd735dfdf87c88ae381a26902dacfdfe)), and the eval set stays exactly as the workshop gave it. If the facilitator's key disagrees, the rates in [RESULTS.md](RESULTS.md) move. I've noted the one case where a different label would change the headline number.

| Case | Label | The defect, or why it's clean |
| --- | --- | --- |
| 01 | **defect** | `request.Ids` is declared nullable and iterated without a check → NRE/500. The loop isn't wrapped in `Handle`, so a 404 or 409 on item N leaves items 1..N-1 approved with no report. Secondary: `[RequireRole("Approver")]` locks out Admin, against the AGENTS.md rule "approvals are Approver or Admin". |
| 02 | **defect** | SQL injection through `customer` and `status`, interpolated into the SQL string. |
| 03 | **defect** | The rounding mode changes from AwayFromZero to ToZero, and the discount is truncated before it's subtracted. Totals move by a cent in the merchant's favour, with no test or rationale. Money code with unexplained semantics changes is a defect even when it compiles. |
| 04 | **clean** | Pure rename of `_repository` to `_invoices`. |
| 05 | **defect** | `DELETE` without `[RequireRole("Admin")]`, so any signed-in user can delete. AGENTS.md: "destructive actions are Admin only". |
| 06 | **clean** | Three tests that match the existing service behaviour: whitespace reason → `ArgumentException`, `DecidedAt` set from `UtcNow`, unknown id → `KeyNotFoundException`. |
| 07 | **defect** | `{@Invoice}` and `{@Session}` destructure customer data and the session into Information-level logs. With a seven-year audit trail and SOC 2 coming, that's sensitive data in a store with weaker access control. |
| 08 | **defect** | `ConcurrentDictionary` → `Dictionary` on a singleton, plus a check-then-act race in `Add`. |
| 09 | **defect** (judgement call) | The README claims "sign out and sign in again" resets the sample data. It doesn't: the repository is a process-wide singleton seeded once. The appsettings change is fine. I count a false statement in operator docs as a defect, because someone will follow it during a demo or an incident. If you label this case clean, every run's false-alarm count goes up by one. |
| 10 | **defect** | `catch (Exception)` returns 200 `{status: "completed"}`, so failures are reported as success and never logged. |
| 11 | **defect** (mine) | A retry loop re-POSTs `/v2/payments` after a client-side timeout, with no idempotency key. PayEx timeouts don't mean failure, so this creates duplicate payments. It's the same class of bug as the repeated INV-41273 submissions in the INC-2041 logs, and as the "Retry policy for the PayEx client" in release 2.14.3. |
| 12 | **clean** (mine) | The fix for the INC-2041 root cause: go back to a typed `HttpClient` through `AddHttpClient`, with the timeout read from config. It's a correct change. Findings that only apply when config is missing or malformed are robustness suggestions, not defects in this diff (the reference app's config has no `PayEx:Timeout` key, so the 10 s default applies). Known caveat: the typed client is captured by the singleton `ApprovalService`, so its handler is never rotated (stale DNS over a long uptime). That's a real but secondary concern; no review run raised it, so it doesn't move the scores. |

That makes 9 defect cases and 3 clean cases per run. Cases 11 and 12 are the two I added. Case 12 is the clean one.

**Provenance of my two cases.** Both come from defects I saw in this scenario's evidence, not from an earlier job: case-11 from the repeated INV-41273 submissions and the 2.14.3 retry-policy release, case-12 from the INC-2041 deploy diff, reversed into its fix.

## Scoring rules I applied

- **Caught**: a finding names the actual defect above, at any confidence. Vague notes near the right line don't count.
- **Missed**: none of the findings names it.
- **False alarm** (clean cases only): the review presents a non-existent problem *as a defect*, at medium confidence or higher, with a fix. Low-confidence "this depends on code I can't see" notes, or an explicit "no definite defects", are noise, not false alarms. I applied this the same way to every run, and noted the borderline calls in `results.csv`.
