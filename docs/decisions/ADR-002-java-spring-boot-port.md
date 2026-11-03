# ADR-002: Port the service to Java 21 + Spring Boot, with a framework-free domain

- **Status:** Accepted, and implemented in this repository.
- **Date:** 2026-11-03
- **Deciders:** service owner (me).
- **Related:** [as-built](../architecture/as-built.md), [ADR-001](ADR-001-exactly-once-payex-submission.md), the [autonomy policy](../policies/autonomy-policy-dependency-bumps.md)

## Context

The workshop handed us an ASP.NET Core 8 implementation. The team that will own this service on the 24x7 rotation works in Java 21, Spring Boot, Quarkus and microservices. A tier-2 service with a payment integration needs to be in the stack the on-call engineers can read at 3 a.m. That matters most for the two engineers who are new to the codebase. Making them new to the language as well doubles the risk.

There's a real counter-argument, so I'm writing it down. The workshop scenario names ASP.NET Core 8 on Azure App Service, and the ten review eval cases and both incident packs are .NET artefacts. A port can make the submission harder to compare with others who stayed on .NET.

## Decision

**Port the service to Java 21 + Spring Boot 3, as two Maven modules, with behaviour kept identical.**

- **`invoice-domain`: plain Java 21, zero runtime dependencies.** `ApprovalService`, `Invoice`, the `InvoiceRepository` port and the domain exceptions. There are no annotations, so Spring, Quarkus, a Lambda handler or a CLI can all host it.
- **`invoice-api`: the Spring Boot adapter.** Controllers, the session filter, the `@RequireRole` interceptor, exception mapping, the in-memory repository and the unchanged static UI.

**What "behaviour kept identical" means, and how it's proven:**

- **Same routes, status codes, JSON shape and port (5000).** `ApiContractTest` pins all of it: 401, 403, 404, 409, 400, and 201 with a `Location` header. The original `app.js` runs against the Java API without a single edit.
- **The same unit tests.** All seven domain tests from the original suite were ported one-for-one.
- **The same known bugs, on purpose.** The double-approve race (the shared object, plus a lock per call) is still there, and documented. A port that quietly fixes things can't be verified as a port. The fixes belong to ADR-001's persistence work, where they get their own tests.

**What deliberately did not change:**

- **The evidence packs and the review eval set.** `ops/incidents` and `ops/evals/cases` are the workshop's fixed .NET artefacts, and my two added cases are written in the same language as the rest of the set. Translating the eval cases would change the very thing the eval measures, so they stay as they are, and are scored against the .NET reference implementation in [the workshop repository](https://github.com/NITIN1670/ai-in-sdlc-assignment/tree/0616158bdd735dfdf87c88ae381a26902dacfdfe) (commit `0616158`).
- **The platform in the scenario (Azure).** The [target state](../architecture/target-state.md) shows the AWS equivalents for each component, and what would change in code. In short: very little, because the cloud lives only in the adapter.

## Why Spring Boot and not Quarkus

Both would work, and the module split keeps the choice reversible.

- **Spring Boot wins on the things a six-person on-call team feels:** familiarity across the team, and depth of operational tooling (Actuator, Micrometer, Spring Security's OIDC resource server). And this service's profile doesn't need what Quarkus is best at: it's three always-on instances, so cold start and memory per instance don't matter much at 80 approvals a minute.
- **When I'd revisit:** if the dispatcher in ADR-001 moves to scale-to-zero compute (a container that starts on demand, or Lambda), Quarkus native start-up time starts to matter. Then `invoice-domain` moves across unchanged, and only an adapter module is new.

## Why the architecture test is ArchUnit and not just Maven modules

The module boundary already stops the domain importing Spring, because Spring isn't on the domain's classpath. But it can't stop `java.net.http.HttpClient` or `java.sql.Connection`, because the JDK ships both. INC-2041 started with an HTTP client created inside the request path. So `DomainArchitectureTest` is an allow-list (`java.lang`, `java.util`, `java.math`, `java.time`, the domain itself), and it runs as its own CI step. The PR that introduced the port includes a deliberate violation commit, to show that the step goes red.

## Consequences

- One language and one build for the on-call team: `./backend/mvnw verify`.
- AGENTS.md is rewritten for Java. The eval's changed-variable run used the *original* AGENTS.md, and [RESULTS.md](../../ops/evals/RESULTS.md) says so.
- This repository contains only the Java implementation. The .NET reference that the C# eval diffs and incident evidence describe lives upstream, in [the workshop repository](https://github.com/NITIN1670/ai-in-sdlc-assignment/tree/0616158bdd735dfdf87c88ae381a26902dacfdfe), at a pinned commit, so anyone can read those artefacts against the exact code they were written for.
- Dependency bumps are now Maven and npm. The [autonomy policy](../policies/autonomy-policy-dependency-bumps.md) is updated to match.
