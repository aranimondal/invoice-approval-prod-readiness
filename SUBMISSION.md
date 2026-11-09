# Submission map

AI in SDLC workshop: Invoice Approval service, preparation for the tier-2 go-live.

**Repository:** https://github.com/aranimondal/invoice-approval-prod-readiness/tree/main · **Demo video:** [OneDrive](https://nagarro-my.sharepoint.com/:v:/p/arani_mondal/IQD9jDD-ToZXQL3cGQW8rZihAScXbo_tdEWJleTMQXtfZWQ?nav=eyJyZWZlcnJhbEluZm8iOnsicmVmZXJyYWxBcHAiOiJPbmVEcml2ZUZvckJ1c2luZXNzIiwicmVmZXJyYWxBcHBQbGF0Zm9ybSI6IldlYiIsInJlZmVycmFsTW9kZSI6InZpZXciLCJyZWZlcnJhbFZpZXciOiJNeUZpbGVzTGlua0NvcHkifX0&e=m7VXCO)

| Hand-in item | File |
| --- | --- |
| 0. Stack and module structure (deviation: Java instead of the brief's .NET, because Java is my stack; the design and decisions are language-independent) | [docs/decisions/ADR-002-java-spring-boot-stack.md](docs/decisions/ADR-002-java-spring-boot-stack.md): why the service is Java 21 + Spring Boot, with a framework-free domain |
| 1. Mermaid for the as-built and target diagrams, the citation list and the inferred-items list | **[docs/architecture/README.md](docs/architecture/README.md) shows all four diagrams as pictures, each with its exact Mermaid source (paste it into mermaid.live to reproduce the picture) and a one-click Mermaid Live link.** Details: [docs/architecture/as-built.md](docs/architecture/as-built.md) (citations, inferred items, corrections), [docs/architecture/target-state.md](docs/architecture/target-state.md), and the `.mmd` sources in the same folder. **Deviation:** the Mermaid MCP wasn't available in my environment, so the agent wrote Mermaid text directly and I rendered it with mermaid-cli ([log](docs/ai-augmentation-log.md)) |
| 2. One architecture test, and the command that runs it | [DomainArchitectureTest.java](backend/invoice-domain/src/test/java/com/invoiceapproval/domain/DomainArchitectureTest.java) (ArchUnit). Command: `./backend/mvnw -f backend/pom.xml -pl invoice-domain test -Dtest=DomainArchitectureTest` |
| 3. Exactly-once decision record, with "Where the agent was wrong or useless" | [docs/decisions/ADR-001-exactly-once-payex-submission.md](docs/decisions/ADR-001-exactly-once-payex-submission.md) |
| 4. One-page autonomy policy, with the day-one level | [docs/policies/autonomy-policy-dependency-bumps.md](docs/policies/autonomy-policy-dependency-bumps.md). Day one: **L2 at most** |
| 5. Two added eval cases, and results for three runs plus the changed-variable run | [ops/evals/cases/case-11.diff](ops/evals/cases/case-11.diff), [case-12.diff](ops/evals/cases/case-12.diff), [ops/evals/RESULTS.md](ops/evals/RESULTS.md), [results.csv](ops/evals/results.csv), [answer-key.md](ops/evals/answer-key.md) |
| 6. Risk audit, with the decision recorded | [docs/risk/lethal-trifecta-audit.md](docs/risk/lethal-trifecta-audit.md). Decision: **remove the exfiltration leg** |
| 7. Measurement plan, with the two captured numbers | [docs/measurement/baseline-plan.md](docs/measurement/baseline-plan.md). Lead-time proxy (first PR CI run → main push) **57.3 h median, n = 6**; change failure rate **29–40%** |
| 8. Demo video (8–10 min) | [Arani_3214638_invoice-approval-prod-readiness-main.mp4](https://nagarro-my.sharepoint.com/:v:/p/arani_mondal/IQD9jDD-ToZXQL3cGQW8rZihAScXbo_tdEWJleTMQXtfZWQ?nav=eyJyZWZlcnJhbEluZm8iOnsicmVmZXJyYWxBcHAiOiJPbmVEcml2ZUZvckJ1c2luZXNzIiwicmVmZXJyYWxBcHBQbGF0Zm9ybSI6IldlYiIsInJlZmVycmFsTW9kZSI6InZpZXciLCJyZWZlcnJhbFZpZXciOiJNeUZpbGVzTGlua0NvcHkifX0&e=m7VXCO) (OneDrive) |
| 9. AI augmentation log | [docs/ai-augmentation-log.md](docs/ai-augmentation-log.md) |

## How AI was used in this SDLC

The product has no AI in it. The assignment is about using AI agents in the delivery of a service, safely and measurably. Every artefact either uses an agent, constrains one, or measures one:

| Phase | What the agent did | What I decided or verified | Where |
| --- | --- | --- | --- |
| Understand the code | Drafted the as-built diagrams, citations and inferred list from the code alone | Checked every citation; corrected what the race costs, a wrong risk, the delete bypass | [as-built](docs/architecture/as-built.md), [log §1](docs/ai-augmentation-log.md) |
| Design | Laid out the three exactly-once options; was stress-tested three ways, twice | Wrote the ADR; rejected its cost figures; recorded where it was wrong | [ADR-001](docs/decisions/ADR-001-exactly-once-payex-submission.md), [log §3](docs/ai-augmentation-log.md) |
| Build | Wrote the Java service, the ArchUnit rule, contract and e2e tests | Chose the allow-list; kept the race on purpose; proved the rule goes red | [ADR-002](docs/decisions/ADR-002-java-spring-boot-stack.md), PR #1 |
| Review | Acted as the code reviewer in 48 isolated sessions | Wrote the answer key first; scored every run; reported intervals | [RESULTS](ops/evals/RESULTS.md) |
| Govern | Drafted the policy structure | Set the levels, the day-one level and the promotion rules for agent PRs | [autonomy policy](docs/policies/autonomy-policy-dependency-bumps.md) |
| Secure | Checked vendor docs and pinned the MCP version | Audited the agent setup; cut the exfiltration leg | [risk audit](docs/risk/lethal-trifecta-audit.md), `.mcp.json` |
| Measure | Wrote the baseline script | Hand-checked its output; that check found the deploy-before-CI and direct-push issues | [baseline](docs/measurement/baseline-plan.md) |

## Headline results

- **Stack.** Java 21 + Spring Boot 3, split into a framework-free `invoice-domain` module and an `invoice-api` adapter. It implements the workshop's reference app, and `ApiContractTest` pins its HTTP contract ([ADR-002](docs/decisions/ADR-002-java-spring-boot-stack.md)).
- **Architecture.** The domain may depend only on core JDK types, enforced by an ArchUnit allow-list that runs as its own CI step. The PR includes a deliberate violation commit, so you can see the check go red.
- **Exactly-once.** A transactional outbox in Azure SQL, a lease-based dispatcher, and the idempotency key `invoiceId:approvalVersion`. A timeout puts the payment into an `Unknown` state, and we look it up before ever re-sending. Reconciliation is a report, never a sender. It's blocked on one PayEx answer: do they honour idempotency keys and support lookup?
- **Review eval.** Caught 27/27 and missed 0. False alarms were 2 of 9 clean case-runs, all on the same borderline case. Adding AGENTS.md as context changed what the reviewer said, not its scores. My confidence in that conclusion is low, because there are only 3 clean cases per run.
- **Risk.** All three trifecta legs are present today. Fix: no network egress for sessions that read logs, and no log access for sessions that can push.
- **Baseline.**
  - Lead-time proxy: first PR CI run to main push, median 57.3 h (n = 6).
  - CI: 31.3 min median.
  - Change failure rate: 2 of 5 known releases caused Sev-2s.
  - Review turnaround: can't be captured from the evidence, so it's left blank on purpose.
