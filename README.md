# Invoice Approval service

A small invoice approval service: Java 21 + Spring Boot 3, a plain web UI, and an in-memory store, so it runs with one command and no database.

## Run it

Needs Java 21 (Maven comes via the wrapper).

```
./backend/mvnw -f backend/pom.xml install -DskipTests
./backend/mvnw -f backend/pom.xml -pl invoice-api spring-boot:run
```

Open http://localhost:5000 and sign in as `demo.approver` or `demo.admin` (password `ChangeMe123!`). These are demo accounts for a local, in-memory app, not real credentials.

## What it does

Create an invoice (draft) → submit it for approval → an approver approves it, or rejects it with a reason.

## Tests

```
./backend/mvnw -f backend/pom.xml verify
```

## Layout

- `backend/invoice-domain`: business rules, plain Java, no framework
- `backend/invoice-api`: Spring Boot adapter (HTTP, sign-in, in-memory store, web UI)
- `ops/`: evidence packs and review eval cases for the planned production deployment. Nothing there runs.
- `AGENTS.md`: conventions for AI coding agents
