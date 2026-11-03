# Invoice Approval Demo App

A small invoice approval app used for live AI-in-SDLC demos. It is deliberately simple: a .NET 8 API, a plain web UI, and an in-memory store, so it runs with one command and no database.

> **Production-readiness work for the January tier-2 go-live** lives in [`docs/`](#production-readiness-work). Start with [SUBMISSION.md](SUBMISSION.md) for a map of what's where.

## Run it

```
dotnet run --project backend/src/InvoiceApp.Api
```

Open http://localhost:5000 and sign in:

| User | Password | Role |
| --- | --- | --- |
| `demo.approver` | `ChangeMe123!` | Approver |
| `demo.admin` | `ChangeMe123!` | Admin |

These are demo accounts for a local, in-memory app. They are not real credentials.

## What it does

1. Create an invoice (it starts as a draft).
2. Submit it for approval.
3. An approver approves it, or rejects it with a reason.

Invoices move through Draft, Pending Approval, then Approved or Rejected.

## Tests

```
dotnet test backend/InvoiceApp.sln
```

Architecture rules only:

```
dotnet test backend/InvoiceApp.sln --filter "FullyQualifiedName~ArchitectureTests"
```

## Project layout

```
backend/
  src/InvoiceApp.Domain   business rules
  src/InvoiceApp.Api      controllers, sign-in, web UI (wwwroot)
  tests/InvoiceApp.Tests  xUnit tests (incl. ArchitectureTests)
docs/use-cases            short feature briefs
docs/architecture         as-built and target diagrams (Mermaid)
docs/decisions            architecture decision records
docs/policies             agent autonomy policy
docs/risk                 lethal-trifecta audit of the agent setup
docs/measurement          pre-rotation baseline
ops/evals                 review eval cases, answer key, results
scripts/metrics           baseline script for CI history
AGENTS.md                 conventions for AI coding agents
```

## Use cases

- [UC-1 Bulk invoice approval](docs/use-cases/UC-1-bulk-approval.md)
- [UC-2 Partial approval](docs/use-cases/UC-2-partial-approval.md)

## Production-readiness work

| # | Deliverable | Where |
| --- | --- | --- |
| 1 | As-built and target diagrams, with citations and the inferred-items list | [as-built](docs/architecture/as-built.md), [target](docs/architecture/target-state.md) |
| 2 | Architecture test and the command that runs it | [ArchitectureTests.cs](backend/tests/InvoiceApp.Tests/ArchitectureTests.cs) |
| 3 | Exactly-once decision record | [ADR-001](docs/decisions/ADR-001-exactly-once-payex-submission.md) |
| 4 | Autonomy policy (dependency bumps), with the day-one level | [policy](docs/policies/autonomy-policy-dependency-bumps.md) |
| 5 | Two new eval cases and results for 3 runs plus the changed-variable run | [RESULTS](ops/evals/RESULTS.md), [cases](ops/evals/cases) |
| 6 | Risk audit with a recorded decision | [lethal-trifecta audit](docs/risk/lethal-trifecta-audit.md) |
| 7 | Measurement plan with the two captured numbers | [baseline plan](docs/measurement/baseline-plan.md) |
| 8 | Demo video | linked in [SUBMISSION.md](SUBMISSION.md) |
| 9 | AI augmentation log | [log](docs/ai-augmentation-log.md) |
