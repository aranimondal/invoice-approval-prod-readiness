# Project conventions for AI agents

Invoice approval demo app: Java 21, Spring Boot 3 REST API with a small static web UI.

## Layout

- `backend/invoice-domain`: business rules, plain Java 21, **no framework and no I/O**. `ApprovalService` owns the invoice lifecycle (Draft, PendingApproval, Approved, Rejected). `InvoiceRepository` is the port.
- `backend/invoice-api`: the Spring Boot adapter. Controllers (`web/`), sign-in (`auth/`), the in-memory repository (`persistence/`), wiring (`config/`), and the web UI under `src/main/resources/static/`.
- Tests live next to the module they test: JUnit 5 + AssertJ, one test class per class under test, named `<ClassUnderTest>Test`. API behaviour is pinned in `ApiContractTest` (MockMvc).
- `e2e`: Playwright end-to-end tests (`tests/`) and page objects (`pages/`).
- `docs/use-cases`: short briefs for features we may build.
- `ops`: incident evidence, the CI pipeline and review eval cases. This folder describes a *planned* production deployment, including a database, a message topic and a payment provider, none of which exist in the code. The eval diffs and incident packs come from the workshop and are written against its reference app. Never treat anything under `ops/` as a description of what this system currently does.

## Conventions

- Controllers live in `web/`, one per resource, under `/api/<resource>`. Request bodies are `record` types declared inside the controller.
- Domain failures are exceptions from the domain (`InvoiceNotFoundException`, `InvalidInvoiceStateException`, `IllegalArgumentException`). `ApiExceptionHandler` maps them to 404, 409 and 400. Don't catch `Exception` anywhere in the web layer.
- Every endpoint is behind sign-in (`SessionAuthFilter`). Restrict actions with `@RequireRole(...)`: destructive actions are Admin only, approvals are Approver or Admin.
- Business rules go in `invoice-domain`, never in controllers. Domain classes carry no annotations; they're wired in `DomainConfiguration`.
- The domain may depend only on `java.lang`, `java.util`, `java.math`, `java.time` and itself. `DomainArchitectureTest` (ArchUnit) enforces this. Need something else? Add a port in the domain and implement it in `invoice-api`.
- Every new rule or endpoint needs tests. Use `FakeInvoiceRepository` for domain tests.
- Use clear names; no one-letter variables or `flag` parameters.
- End-to-end tests use page objects and role-based locators (`getByRole`, `getByLabel`), never CSS or XPath selectors.

## Commands

- Build and test everything: `./backend/mvnw -f backend/pom.xml verify`
- Architecture rule only: `./backend/mvnw -f backend/pom.xml -pl invoice-domain test -Dtest=DomainArchitectureTest`
- Run the app: `./backend/mvnw -f backend/pom.xml install -DskipTests` then `./backend/mvnw -f backend/pom.xml -pl invoice-api spring-boot:run`, and open http://localhost:5000
- End-to-end tests: `cd e2e && npm ci && npx playwright install chromium && npx playwright test`

(On Windows use `backend\mvnw.cmd` instead of `./backend/mvnw`.)

## Demo accounts (not real credentials)

- `demo.approver` / `ChangeMe123!` (role Approver)
- `demo.admin` / `ChangeMe123!` (role Admin)

## Never

- Commit real credentials, tokens or customer data.
- Add business logic to controllers or Spring configuration classes.
- Add a framework annotation or dependency to `invoice-domain`.
