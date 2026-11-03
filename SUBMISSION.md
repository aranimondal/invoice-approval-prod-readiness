# Submission map

AI in SDLC workshop: Invoice Approval service, preparation for the tier-2 go-live.

| Hand-in item | File |
| --- | --- |
| 1. Mermaid for the as-built and target diagrams, the citation list and the inferred-items list | [docs/architecture/as-built.md](docs/architecture/as-built.md) (citations, inferred items, corrections), [docs/architecture/target-state.md](docs/architecture/target-state.md), and the `.mmd` sources in the same folder |
| 2. One architecture test, and the command that runs it | [backend/tests/InvoiceApp.Tests/ArchitectureTests.cs](backend/tests/InvoiceApp.Tests/ArchitectureTests.cs). Command: `dotnet test backend/InvoiceApp.sln --filter "FullyQualifiedName~ArchitectureTests"` |
| 3. Exactly-once decision record, with "Where the agent was wrong or useless" | [docs/decisions/ADR-001-exactly-once-payex-submission.md](docs/decisions/ADR-001-exactly-once-payex-submission.md) |
| 4. One-page autonomy policy, with the day-one level | [docs/policies/autonomy-policy-dependency-bumps.md](docs/policies/autonomy-policy-dependency-bumps.md). Day one: **L2 at most** |
| 5. Two added eval cases, and results for three runs plus the changed-variable run | [ops/evals/cases/case-11.diff](ops/evals/cases/case-11.diff), [case-12.diff](ops/evals/cases/case-12.diff), [ops/evals/RESULTS.md](ops/evals/RESULTS.md), [results.csv](ops/evals/results.csv), [answer-key.md](ops/evals/answer-key.md) |
| 6. Risk audit, with the decision recorded | [docs/risk/lethal-trifecta-audit.md](docs/risk/lethal-trifecta-audit.md). Decision: **remove the exfiltration leg** |
| 7. Measurement plan, with the two captured numbers | [docs/measurement/baseline-plan.md](docs/measurement/baseline-plan.md). Lead time **57.3 h median**; change failure rate **29–40%** |
| 8. Demo video (8–10 min) | _link to be added after recording_. The talk track is in [docs/demo/demo-script.md](docs/demo/demo-script.md) |
| 9. AI augmentation log | [docs/ai-augmentation-log.md](docs/ai-augmentation-log.md) |

## Headline results

- **Architecture.** The Domain project stays free of web, storage and transport code. That's enforced in CI as its own step.
- **Exactly-once.** A transactional outbox in Azure SQL, a lease-based dispatcher, and the idempotency key `invoiceId:approvalVersion`. A timeout puts the payment into an `Unknown` state, and we look it up before ever re-sending. Reconciliation is a report, never a sender. It's blocked on one PayEx answer: do they honour idempotency keys and support lookup?
- **Review eval.** Caught 27/27 and missed 0. False alarms were 2 of 9 clean case-runs, all on the same borderline case. Adding AGENTS.md as context changed what the reviewer said, not its scores. My confidence in that conclusion is low, because there are only 3 clean cases per run.
- **Risk.** All three trifecta legs are present today. Fix: no network egress for sessions that read logs, and no log access for sessions that can push.
- **Baseline.**
  - Lead time: PR to main, median 57.3 h (n = 6).
  - CI: 31.3 min median.
  - Change failure rate: 2 of 5 known releases caused Sev-2s.
  - Review turnaround: can't be captured from the evidence, so it's left blank on purpose.
