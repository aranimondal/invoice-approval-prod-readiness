# Invoice Approval service

A small invoice approval service, getting ready to go to production. It's Java 21 with Spring Boot 3, a plain web UI, and an in-memory store, so it runs with one command and no database.

> **Reviewers:** start with [SUBMISSION.md](SUBMISSION.md). It maps every hand-in item to a file. All the diagrams are in [docs/architecture](docs/architecture/README.md).

## Run it

You need Java 21. Maven is downloaded by the wrapper.

```
./backend/mvnw -f backend/pom.xml install -DskipTests
./backend/mvnw -f backend/pom.xml -pl invoice-api spring-boot:run
```

On Windows, use `backend\mvnw.cmd` in place of `./backend/mvnw`.

Open http://localhost:5000 and sign in:

| User | Password | Role |
| --- | --- | --- |
| `demo.approver` | `ChangeMe123!` | Approver |
| `demo.admin` | `ChangeMe123!` | Admin |

These are demo accounts for a local, in-memory app. They are not real credentials.

## What it does

1. Create an invoice. It starts as a draft.
2. Submit it for approval.
3. An approver approves it, or rejects it with a reason.

Invoices move through Draft, Pending Approval, then Approved or Rejected.

## Tests

```
./backend/mvnw -f backend/pom.xml verify
```

End-to-end tests. Playwright starts the app for you:

```
cd e2e && npm ci && npx playwright install chromium && npx playwright test
```

Architecture rule only (ArchUnit):

```
./backend/mvnw -f backend/pom.xml -pl invoice-domain test -Dtest=DomainArchitectureTest
```

## Project layout

```
backend/
  pom.xml                     parent: Java 21, Spring Boot 3 BOM, ArchUnit
  invoice-domain/             business rules, plain Java, zero runtime dependencies
    ApprovalService, Invoice, InvoiceRepository (port), domain exceptions
    test: ApprovalServiceTest, DomainArchitectureTest (ArchUnit)
  invoice-api/                Spring Boot adapter
    web/ auth/ persistence/ config/, resources/static (web UI)
    test: ApiContractTest (MockMvc, pins the HTTP contract)
docs/architecture             as-built and target diagrams (Mermaid source + SVG)
docs/decisions                ADR-001 exactly-once to PayEx, ADR-002 Java/Spring Boot stack
docs/policies                 agent autonomy policy (dependency bumps)
docs/risk                     lethal-trifecta audit of the agent setup
docs/measurement              pre-rotation baseline
docs/use-cases                short feature briefs
ops/                          planned-production evidence, CI history, review eval set
scripts/metrics               baseline script for CI history
scripts/diagrams              keeps docs in sync with the .mmd diagram sources (CI-checked)
e2e/                          Playwright smoke tests with page objects (run in CI)
AGENTS.md                     conventions for AI coding agents
```

## Architecture in one paragraph

The domain module knows nothing about HTTP, SQL, Spring or the cloud it runs on. ArchUnit enforces that with an allow-list, and the check runs as its own CI step. Everything else is an adapter. Today that means a Spring Boot web layer and an in-memory repository. Next come a JDBC repository, a transactional outbox and a PayEx client ([ADR-001](docs/decisions/ADR-001-exactly-once-payex-submission.md)). The same domain jar could be hosted by Quarkus or run on AWS instead of Azure, without changes ([ADR-002](docs/decisions/ADR-002-java-spring-boot-stack.md), and the [target state](docs/architecture/target-state.md#if-this-ran-on-aws-instead)).

## Use cases

- [UC-1 Bulk invoice approval](docs/use-cases/UC-1-bulk-approval.md)
- [UC-2 Partial approval](docs/use-cases/UC-2-partial-approval.md)

## Production-readiness work

| # | Deliverable | Where |
| --- | --- | --- |
| 1 | As-built and target diagrams, with citations and the inferred-items list | [diagrams index](docs/architecture/README.md), [as-built](docs/architecture/as-built.md), [target](docs/architecture/target-state.md) |
| 2 | Architecture test, and the command that runs it | [DomainArchitectureTest.java](backend/invoice-domain/src/test/java/com/invoiceapproval/domain/DomainArchitectureTest.java) |
| 3 | Exactly-once decision record | [ADR-001](docs/decisions/ADR-001-exactly-once-payex-submission.md) |
| 4 | Autonomy policy (dependency bumps), with the day-one level | [policy](docs/policies/autonomy-policy-dependency-bumps.md) |
| 5 | Two new eval cases, and results for 3 runs plus the changed-variable run | [RESULTS](ops/evals/RESULTS.md), [cases](ops/evals/cases) |
| 6 | Risk audit, with a recorded decision | [lethal-trifecta audit](docs/risk/lethal-trifecta-audit.md) |
| 7 | Measurement plan, with the two captured numbers | [baseline plan](docs/measurement/baseline-plan.md) |
| 8 | Demo video | linked in [SUBMISSION.md](SUBMISSION.md) |
| 9 | AI augmentation log | [log](docs/ai-augmentation-log.md) |
