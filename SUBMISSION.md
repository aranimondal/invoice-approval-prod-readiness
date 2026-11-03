# Submission map

AI in SDLC workshop: Invoice Approval service, preparation for the tier-2 go-live.

| Hand-in item | File |
| --- | --- |
| 0. Stack and module structure | [docs/decisions/ADR-002-java-spring-boot-port.md](docs/decisions/ADR-002-java-spring-boot-port.md): why the service is Java 21 + Spring Boot, with a framework-free domain |
| 1. Mermaid for the as-built and target diagrams, the citation list and the inferred-items list | **[docs/architecture/README.md](docs/architecture/README.md) shows all four diagrams as pictures, each with its exact Mermaid source (paste it into mermaid.live to reproduce the picture) and a one-click Mermaid Live link.** Details: [docs/architecture/as-built.md](docs/architecture/as-built.md) (citations, inferred items, corrections), [docs/architecture/target-state.md](docs/architecture/target-state.md), and the `.mmd` sources in the same folder |
| 2. One architecture test, and the command that runs it | [DomainArchitectureTest.java](backend/invoice-domain/src/test/java/com/invoiceapproval/domain/DomainArchitectureTest.java) (ArchUnit). Command: `./backend/mvnw -f backend/pom.xml -pl invoice-domain test -Dtest=DomainArchitectureTest` |
| 3. Exactly-once decision record, with "Where the agent was wrong or useless" | [docs/decisions/ADR-001-exactly-once-payex-submission.md](docs/decisions/ADR-001-exactly-once-payex-submission.md) |
| 4. One-page autonomy policy, with the day-one level | [docs/policies/autonomy-policy-dependency-bumps.md](docs/policies/autonomy-policy-dependency-bumps.md). Day one: **L2 at most** |
| 5. Two added eval cases, and results for three runs plus the changed-variable run | [ops/evals/cases/case-11.diff](ops/evals/cases/case-11.diff), [case-12.diff](ops/evals/cases/case-12.diff), [ops/evals/RESULTS.md](ops/evals/RESULTS.md), [results.csv](ops/evals/results.csv), [answer-key.md](ops/evals/answer-key.md) |
| 6. Risk audit, with the decision recorded | [docs/risk/lethal-trifecta-audit.md](docs/risk/lethal-trifecta-audit.md). Decision: **remove the exfiltration leg** |
| 7. Measurement plan, with the two captured numbers | [docs/measurement/baseline-plan.md](docs/measurement/baseline-plan.md). Lead time **57.3 h median**; change failure rate **29–40%** |
| 8. Demo video (8–10 min) | _link to be added after recording_ |
| 9. AI augmentation log | [docs/ai-augmentation-log.md](docs/ai-augmentation-log.md) |

## Headline results

- **Stack.** Java 21 + Spring Boot 3, split into a framework-free `invoice-domain` module and an `invoice-api` adapter. It's a behaviour-preserving port of the workshop's .NET code, and `ApiContractTest` proves the HTTP contract is unchanged ([ADR-002](docs/decisions/ADR-002-java-spring-boot-port.md)).
- **Architecture.** The domain may depend only on core JDK types, enforced by an ArchUnit allow-list that runs as its own CI step. The PR includes a deliberate violation commit, so you can see the check go red.
- **Exactly-once.** A transactional outbox in Azure SQL, a lease-based dispatcher, and the idempotency key `invoiceId:approvalVersion`. A timeout puts the payment into an `Unknown` state, and we look it up before ever re-sending. Reconciliation is a report, never a sender. It's blocked on one PayEx answer: do they honour idempotency keys and support lookup?
- **Review eval.** Caught 27/27 and missed 0. False alarms were 2 of 9 clean case-runs, all on the same borderline case. Adding AGENTS.md as context changed what the reviewer said, not its scores. My confidence in that conclusion is low, because there are only 3 clean cases per run.
- **Risk.** All three trifecta legs are present today. Fix: no network egress for sessions that read logs, and no log access for sessions that can push.
- **Baseline.**
  - Lead time: PR to main, median 57.3 h (n = 6).
  - CI: 31.3 min median.
  - Change failure rate: 2 of 5 known releases caused Sev-2s.
  - Review turnaround: can't be captured from the evidence, so it's left blank on purpose.
