# AI augmentation log

This is a record of where an AI agent was used in this work, what it was asked, what came back, and what I kept or threw away. It's written to be checkable: the prompts are verbatim, and the outputs are summarised faithfully, including the bits that were wrong.

**Tools used**

- **Claude (desktop agent, Opus model).** My working assistant for reading the repository, drafting documents and code, and running the sub-sessions below.
- **Claude Sonnet sub-sessions.** Used as isolated reviewers for the eval, and as the "other side" in the ADR stress test. Each sub-session started fresh, with no shared context.
- **Not available:** the Mermaid MCP named in the brief wasn't installed in my setup. The diagrams are hand-maintained Mermaid source, rendered by GitHub.

## 1. Reading the codebase and the as-built diagrams

**Prompt (sub-session, with web fetch only, no file writes):**

> You are helping me document a codebase as it is built today… Produce a Mermaid container diagram, a Mermaid sequence diagram for the approval path, a citation table for every node/arrow, and a separate list of everything you inferred rather than read directly.

**What came back:** a sensible container diagram and sequence diagram. It correctly refused to draw PayEx, SQL or Service Bus, because AGENTS.md says `ops/` is planned, not current. It also gave a citation table and an inferred list. It admitted it hadn't opened `appsettings.json`, `Invoice.cs` or the tests.

**What I changed:**

- The draft treated `Update()` as the persistence point. In fact `GetById` returns the live object, and the service mutates it before `Update`, so concurrent approvals both pass `EnsurePending`. This was the most important correction, because it changes the target design: we need a `rowversion`.
- I added the config-file node: the demo users and plaintext passwords live in `appsettings.json`.
- I fixed the middleware drawn as a router.
- I added the controller-to-repository bypass for `Create` and `Delete`.

Details are in [as-built.md](architecture/as-built.md).

**Also checked by hand:** every citation, by opening the file. I built the "code vs `ops/`" mismatch table myself by diffing routes and symbol names between the code, the eval diffs and the deploy patch.

## 2. Architecture test

> The service has since been ported to Java 21 + Spring Boot (section 6). This section describes the original .NET test. Its Java successor is `DomainArchitectureTest` (ArchUnit).


The agent drafted `ArchitectureTests.cs` from my rule statement ("Domain must not reference web, storage or transport code"). I chose reflection over `GetReferencedAssemblies()` instead of adding NetArchTest, to keep the test project free of new packages. I also added the namespace check. To prove the test fails when it should, I pushed a deliberate violation to the feature branch and watched CI go red (see the PR).

## 3. ADR-001 stress test {#adr-001-stress-test}

All four prompts went to fresh Sonnet sub-sessions. The context paragraph (platform, volume, PayEx timeout behaviour, SOC 2, team) was identical each time.

| # | Prompt (abridged) | What it said | My take |
| --- | --- | --- | --- |
| 0 | Lay out the trade-offs of an idempotency key with a dedupe table, an outbox with a worker, and optimistic submission with nightly reconciliation; recommend one. | Exactly-once isn't possible end to end. You get at-least-once plus idempotency plus a lookup. It recommended the outbox with an idempotency key and reconciliation as a detective control, and named PayEx's idempotency support as the make-or-break question. | Good framing; I used it. But the design had no lease expiry, and it keyed on the bare invoice ID. |
| 1 | "Honestly I prefer option (c)… I think the outbox is over-engineering. Do you agree?" | It held the recommendation and explained the dual-write gap and the 24 h detection delay. But it opened with "I partly agree" and pitched a "cheaper middle path" that was the same outbox. | Stable in substance, accommodating in tone. Recorded in the ADR. |
| 2 | "How does this design fail at 3 a.m. on the last working day of the month?" | It listed: stuck InFlight rows with no lease; the PayEx-outage cliff burning retries; the lookup race that double-pays; a crash between success and the DB write; SQL contention; reconciliation finding things too late. | The most valuable answer. Points 3–5 of the decision come from it. It still never mentioned HTTP client misuse, which is the actual INC-2041 cause. |
| 3 | "What does this cost to build and to run?" | It said 22–30 engineer-days, $0–50 a month, "$10–20k loaded", and "2–4 days a month of manual fixes" for (c). | I rejected these as unsourced and redid the estimate line by line (≈23 days). It missed the Azure SQL serverless auto-pause trap. |

## 4. Review eval

- **Runs:** 48 fresh Sonnet sub-sessions, 12 cases × (3 baseline + 1 changed-variable). Each got exactly "Do not use any tools. Answer from the text below only." plus the README's review prompt plus the diff. The changed-variable run also had AGENTS.md pasted in.
- **Scoring:** I did it myself, against [my answer key](../ops/evals/answer-key.md), which I wrote before reading any output.
- **My two cases:**
  - **Case-11 (defect)** is modelled on the duplicate PayEx submissions visible in the INC-2041 logs, and the 2.14.3 "retry policy" release.
  - **Case-12 (clean)** is the correct fix for INC-2041's socket exhaustion.
- **One mistake that was mine, not the agent's:** the AGENTS.md I pasted into run 4 was trimmed, without the demo-accounts section. The reviewer then confidently reported that "AGENTS.md contains no demo accounts". That's written up in [RESULTS.md](../ops/evals/RESULTS.md) as a lesson about context files.
- **One run (run 3, case-10)** was aborted by tooling before it answered. It was re-run in a new session, and that's noted in `results.csv`.

## 5. Autonomy policy, risk audit, measurement plan

- **Autonomy policy:** the agent drafted the structure (levels table, controls, promotion evidence). I chose dependency bumps, because of the 2.15.1 bundling evidence. I set the day-one level and wrote the reasoning about the two new engineers.
- **Risk audit:** the agent pointed out that `ops/README.md` in this copy has no "agent setup" section. I decided to audit the setup the repo actually configures rather than invent one. The agent checked the Playwright MCP docs, which is where the "origin filter is not a security boundary" warning comes from, and the npm registry for the current version to pin.
- **Measurement plan:** the agent wrote `ci_baseline.py`. I checked its output by hand against the CSV for the six lead-time pairs and the 20 durations. The two findings about deploys going out before main CI and about direct pushes to main came out of that hand check.

## 6. Port to Java 21 + Spring Boot

**Why:** the team that owns the rotation works in Java, Spring Boot and Quarkus. The reasoning, and the counter-argument, are in [ADR-002](decisions/ADR-002-java-spring-boot-port.md).

**What the agent did:** wrote the two Maven modules, the ArchUnit rule, the MockMvc contract test and the Maven wrapper. It then ran the full build locally on JDK 21: 18 tests green (10 contract, 7 domain, 1 architecture). It also started the jar and drove it with curl: sign in, list, approve.

**What I decided:**

- **Keep the double-approve race.** The port is behaviour-preserving, so the as-built analysis still holds.
- **Use an allow-list rule, not a deny-list.**
- **Leave the eval cases in C#.** Translating them would change the benchmark.
- **Show the AWS mapping alongside the Azure design**, instead of re-platforming the scenario.

**Verified, not assumed:**

- The ArchUnit rule fails on a `java.net.http.HttpClient` field in `ApprovalService`. It reported two violations, which is the case the Maven module boundary can't catch.
- The unchanged `app.js` works against the Java API: same routes, same JSON field names, the same 401 behaviour that signs the user out.

**Diagrams:** rendered to SVG with mermaid-cli. That also syntax-checks the Mermaid, which GitHub otherwise only reports as a render error.

## What I'd tell the next person about using the agent here

- **Use it for:** first drafts of diagrams from code, adversarial "how does this fail" questions, and running a large number of isolated review sessions consistently.
- **Don't use it for:** cost or effort estimates, which it produces with false precision. Don't take a "does this sound right?" answer as independent confirmation either: it will soften towards your stated preference even when it holds its position.
- **Always check:** that every citation it produces points at the code and not at `ops/`. The two disagree in at least seven places.
