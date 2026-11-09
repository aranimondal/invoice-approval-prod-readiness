# ADR-002: Java 21 + Spring Boot, with a framework-free domain

- **Status:** Accepted, and implemented in this repository.
- **Date:** 2026-11-03
- **Deciders:** service owner (me).
- **Related:** [as-built](../architecture/as-built.md), [ADR-001](ADR-001-exactly-once-payex-submission.md), the [autonomy policy](../policies/autonomy-policy-dependency-bumps.md)

## Context

The workshop's reference app is ASP.NET Core 8. I'm a Java engineer, not a .NET one. I could read the reference app with the agent's help, but I wouldn't own production code I can't debug on my own at 3 a.m. So I implemented the service in Java 21 and Spring Boot, the stack I can design, test and support properly. The routes, JSON shape and web page match the reference app, and `ApiContractTest` pins that contract. The eval cases and evidence packs in `ops/` are the workshop's originals.

This is a deviation, and I'm stating it rather than burying it. What the brief assesses is design and judgement: where the boundaries sit, how a payment reaches PayEx exactly once, how much autonomy an agent gets, and whether the numbers were measured. None of that depends on the language. The layering is the same in any stack: a domain module with no framework and no I/O, and adapters around it for HTTP, persistence and PayEx. So are the concurrency analysis (a check-then-act race, fixed with optimistic locking), the outbox and lease design, and the failure modes. A .NET team could take ADR-001 and the target state as they stand.

The counter-argument, in full: the brief wants everyone on the same codebase so that results can be compared, and the eval diffs and incident packs are written against the .NET stack. Choosing Java makes this submission harder to compare like for like. I accept that cost. Everything else the brief fixes (Azure, the evidence, the eval set) is kept exactly as given, so the comparison that matters, the decisions and the measurements, still holds.

## Decision

**Java 21 + Spring Boot 3, as two Maven modules.**

- **`invoice-domain`: plain Java 21, zero runtime dependencies.** `ApprovalService`, `Invoice`, the `InvoiceRepository` port and the domain exceptions. There are no annotations, so Spring, Quarkus, a Lambda handler or a CLI can all host it.
- **`invoice-api`: the Spring Boot adapter.** Controllers, the session filter, the `@RequireRole` interceptor, exception mapping, the in-memory repository and the static UI.

**How the contract is pinned:**

- **Routes, status codes, JSON shape and port (5000).** `ApiContractTest` pins all of it: 401, 403, 404, 409, 400, and 201 with a `Location` header.
- **The lifecycle rules.** Seven domain tests in `ApprovalServiceTest`.
- **Known bugs kept visible, on purpose.** The double-approve race (the shared object, plus a lock per call) is there and documented, so the as-built analysis describes what really runs. The fix belongs to ADR-001's persistence work, where it gets its own tests.

**What deliberately stays as the workshop gave it:**

- **The evidence packs and the review eval set.** `ops/incidents` and `ops/evals/cases` are the workshop's fixed artefacts, and my two added cases are written in the same language as the rest of the set. Translating the eval cases would change the very thing the eval measures.
- **The platform in the scenario (Azure).** The [target state](../architecture/target-state.md) shows the AWS equivalents for each component, and what would change in code. In short: very little, because the cloud lives only in the adapter.

## Why Spring Boot and not Quarkus

Both would work, and the module split keeps the choice reversible.

- **Spring Boot wins on the things a six-person on-call team feels:** familiarity across the team, and depth of operational tooling (Actuator, Micrometer, Spring Security's OIDC resource server). This service doesn't need what Quarkus is best at: it's three always-on instances, so cold start and memory per instance don't matter much at 80 approvals a minute.
- **When I'd revisit:** if the dispatcher in ADR-001 moves to scale-to-zero compute (a container that starts on demand, or Lambda), Quarkus native start-up time starts to matter. Then `invoice-domain` moves across unchanged, and only an adapter module is new.

## Why the architecture test is ArchUnit and not just Maven modules

The module boundary already stops the domain importing Spring, because Spring isn't on the domain's classpath. But it can't stop `java.net.http.HttpClient` or `java.sql.Connection`, because the JDK ships both. The rule keeps transport out of the domain; it would not have caught INC-2041 itself, whose per-call client was in the adapter. So `DomainArchitectureTest` is an allow-list (`java.lang`, `java.util`, `java.math`, `java.time`, the domain itself), and it runs as its own CI step. The PR that added it includes a deliberate violation commit, to show that the step goes red.

## Consequences

- One language and one build for the on-call team: `./backend/mvnw verify`.
- The eval's changed-variable run used the workshop's AGENTS.md, which matches the language of the eval diffs. [RESULTS.md](../../ops/evals/RESULTS.md) says so.
- Dependency bumps are Maven and npm. The [autonomy policy](../policies/autonomy-policy-dependency-bumps.md) is written for those.
