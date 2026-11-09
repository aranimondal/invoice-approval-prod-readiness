# AI augmentation log

This is a record of where an AI agent was used in this work, what it was asked, what came back, and what I kept or threw away. It's written to be checkable: prompts are verbatim where quoted in full and marked "abridged" otherwise, and the outputs are summarised faithfully, including the bits that were wrong.

**Tools used**

- **Claude (desktop agent, Opus model).** My working assistant for reading the repository, drafting documents and code, and running the sub-sessions below.
- **Claude Sonnet sub-sessions.** Used as isolated reviewers for the eval, and as the "other side" in the ADR stress test. Each sub-session started fresh, with no shared context.
- **Deviation from the brief, the Mermaid MCP:** it wasn't available in my environment. The agent wrote the Mermaid text directly; I validated and rendered it to SVG with mermaid-cli (the renderer the MCP wraps), and CI checks the docs against the `.mmd` sources.

## 1. Reading the codebase and the as-built diagrams

**Prompt (sub-session, read-only shell on `backend/` only; docs, `ops/`, README and AGENTS.md off limits):**

> You are helping me document a codebase as it is built today. Read ONLY the source under backend (the main and test Java, application.yml, the pom.xml files) and backend/invoice-api/src/main/resources/static. Do NOT read docs/, ops/, README, AGENTS.md or any .mmd/.md file. Those describe plans or earlier analysis, and I want an independent read of the code. Produce: 1. A Mermaid container diagram (flowchart) of what runs today. 2. A Mermaid sequence diagram of the approve path, from the browser click to the response. 3. A citation table: every node and arrow → file + class/method. 4. A separate list of everything you inferred rather than read directly, each with your confidence. 5. Anything in the code that looks like a production risk (concurrency, auth, persistence).

**What came back:** a correct container diagram and approve-path sequence, a full citation table, seven inferred items with confidence, and a risk list. It found the check-then-act race on its own, plus sessions that never expire and the prefix-matched login path.

**What I changed:** it described the race but not its cost (a double payment once PayEx is wired in); one of its risks was wrong (a null password gives a 401, not a 500); it treated the delete bypass as structure, not as an audit problem; and it folded error mapping into the controllers. Details are in [as-built.md](architecture/as-built.md).

**Also checked by hand:** every citation, by opening the file. I built the "code vs `ops/`" mismatch table myself by diffing routes and symbol names between the code, the eval diffs and the deploy patch.

## 2. Architecture test

The agent wrote the ArchUnit allow-list from my rule statement ("the domain must not reference web, storage or transport code"). I chose an allow-list over a deny-list. The red/green proof is in PR #1 (see [as-built](architecture/as-built.md#the-architecture-test)).

## 3. ADR-001 stress test

First run (2026-11-03): all four prompts went to fresh Sonnet sub-sessions with an identical context paragraph (platform, volume, PayEx timeout behaviour, SOC 2, team). A fresh session hasn't seen its own first answer, so on 2026-11-09 I re-ran prompts 1–3 as follow-ups to the agent's own prompt-0 answer. That transcript is in [ai-log/adr-001-stress-test-rerun-2026-11-09.md](ai-log/adr-001-stress-test-rerun-2026-11-09.md), and ADR-001 reports both runs. The table below is the first run.

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

## 6. The Java service

**Why Java:** the team that owns the rotation works in Java, Spring Boot and Quarkus. The reasoning, and the counter-argument, are in [ADR-002](decisions/ADR-002-java-spring-boot-stack.md).

**What the agent did:** wrote the two Maven modules, the ArchUnit rule, the MockMvc contract test and the Maven wrapper. It ran the full build on JDK 21: 18 tests green (10 contract, 7 domain, 1 architecture). It also started the jar and drove it with curl: sign in, list, approve.

**What I decided:**

- **Keep the double-approve race visible**, so the as-built analysis describes what runs.
- **Use an allow-list rule, not a deny-list.**
- **Leave the eval cases exactly as the workshop gave them.** Translating them would change the benchmark.
- **Show the AWS mapping alongside the Azure design**, instead of re-platforming the scenario.

**Verified:**

- The ArchUnit rule fails on a `java.net.http.HttpClient` field in `ApprovalService`. It reported two violations, which is the case the Maven module boundary can't catch.
- The static `app.js` works against the API: same routes, same JSON field names, the same 401 behaviour that signs the user out.

**Diagrams:** rendered to SVG with mermaid-cli. That also syntax-checks the Mermaid, which GitHub otherwise only reports as a render error.

## 7. Reviewer pass

I reviewed the repository the way an assessor would, and fixed what I found:

- **The diagrams weren't traceable to their source.** Each picture now sits next to its exact Mermaid source and a Mermaid Live link. `scripts/diagrams/sync_diagrams.py` generates those sections from the `.mmd` files, and CI fails if a document drifts.
- **`e2e/` was empty, although AGENTS.md mandates page-object tests.** I added two Playwright tests (approve plus reject, and a wrong password) with page objects and role-based locators. They run in a separate CI job against the real Spring Boot app, on Linux and Windows.
- **A strict-marker pass by a separate agent** found overclaims (the architecture test's scope, the INV-41521 evidence), a stress test that wasn't a real follow-up, and missing AI context in SUBMISSION. All fixed, and the stress test re-run.

## What I'd tell the next person about using the agent here

- **Use it for:** first drafts of diagrams from code, adversarial "how does this fail" questions, and running a large number of isolated review sessions consistently.
- **Don't use it for:** cost or effort estimates, which it produces with false precision. Don't take a "does this sound right?" answer as independent confirmation either: it will soften towards your stated preference even when it holds its position.
- **Always check:** that every citation it produces points at the code and not at `ops/`. The two disagree in at least seven places.
