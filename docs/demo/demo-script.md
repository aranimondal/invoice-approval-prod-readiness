# Demo video talk track (target 9 minutes)

Record with Teams or OBS, at 1080p, as MP4. Before you start, have these tabs open in this order:

1. The GitHub repo home
2. The merged PR, showing its checks
3. `docs/architecture/as-built.md`
4. `docs/architecture/target-state.md`
5. `ArchitectureTests.cs`
6. ADR-001
7. The autonomy policy
8. `ops/evals/RESULTS.md`
9. The risk audit
10. The baseline plan

Also have a terminal open at the repo root. Speak in your own words. The lines below are a guide to *what* to say, not a script to read aloud. Glance at them, look away, and say it.

---

## 0:00 – 0:45 · Who I am and what I was asked

**Show:** the repo home page and the README table.

**Say:** "I've just been made senior engineer on the Invoice Approval service. It's going tier-2 in January, with 24x7 on-call, an SLO, and a new payment integration with PayEx. The brief was the four things I'd want to own before that happens: a decision record, an autonomy policy, a working eval and a measurement plan. Everything's in this repo. The table here maps each deliverable to a file."

**Tip:** say one sentence about the trap you noticed early. "The first thing I noticed is that `ops/` describes the *planned* system, not the code. I kept those two strictly apart."

## 0:45 – 2:30 · As-built diagram and the correction that mattered

**Show:** `as-built.md`. Scroll through the container diagram, then the sequence diagram, then the citation table.

**Say:**

- "Every box cites a file and a symbol. The list underneath separates what was read from what was inferred."
- "I had an agent draft this first. It was decent, and it even refused to draw PayEx, because AGENTS.md says not to trust `ops/`."
- Point at the `GetById — returns the live object` step in the sequence. "This is the correction that mattered. The draft treated `Update` as the commit point. But the repository hands back the shared object, and the service mutates it before calling `Update`, so two concurrent approvals both pass the pending check. Today that's harmless. Once an approval triggers a payment, it's a double payment. And the INC-2041 logs show the same invoice approved twice, by two people, both returning 200."
- Scroll to the "code vs ops" table. "Seven places where the evidence packs and the code disagree. If you read an incident pack as a description of the code, you'll get it wrong."

## 2:30 – 3:15 · Target state

**Show:** `target-state.md`, the container diagram.

**Say:** "Dashed orange is what doesn't exist yet: the SQL tables, the dispatcher, the PayEx client, reconciliation. Service Bus is grey: it's provisioned, and deliberately unused in v1. The ADR explains why."

## 3:15 – 4:15 · The architecture test, running

**Show:** `ArchitectureTests.cs` for about ten seconds. Then switch to the terminal and run:

```
dotnet test backend/InvoiceApp.sln --filter "FullyQualifiedName~ArchitectureTests"
```

**Say:**

- "One rule: the Domain project can't reference web, storage or transport code. I picked that one because the outbox design depends on it."
- While it runs: "It reads the compiled assembly's references. No extra packages."
- Show it passing. Then switch to the PR's commit list and show the red CI run from the deliberate violation, followed by the green revert. "And here's proof that it fails when it should."

## 4:15 – 6:00 · ADR-001: exactly-once to PayEx

**Show:** ADR-001. Scroll slowly.

**Say:**

- "First, honesty: across an ambiguous timeout, exactly-once isn't something you can guarantee. What you build is at-least-once, plus PayEx-side dedupe, plus a way to find out when you don't know."
- "The evidence is what decided it. In INC-2041, the inline PayEx call exhausted sockets at month-end. Latency went from 120 milliseconds to 18 seconds, and people kept clicking approve. So the inline option has already failed, in our environment, in the exact window we care about."
- "Decision: a transactional outbox, a dispatcher with leases, the key `invoiceId:approvalVersion` because partial approvals are coming, an Unknown state that we look up before re-sending, and reconciliation as a report that never sends."
- Scroll to the stress tests. "I pushed the agent three ways. When I said I preferred the cheap option, it held its position, but it softened its wording towards me. The 3 a.m. question was the most useful: it found its own missing lease expiry. Its cost numbers were made up, so I redid them line by line."
- Scroll to **Where the agent was wrong or useless**. Read out one point, for example the invented cost figures or the missing HttpClient point.
- Scroll to "What would make me revisit". Mention the PayEx idempotency answer as the blocker.

## 6:00 – 6:45 · Autonomy policy

**Show:** the policy, the levels table and the task table.

**Say:** "I chose dependency bumps, because release 2.15.1 bundled four package bumps with the schema change that caused INC-2047, and that made rollback harder. The main control is one dependency family per PR and nothing else, enforced by CI. Day one is L2 at most: the agent proposes and a human merges. Two of six engineers are new, and auto-merge only makes sense if whoever's on call can confidently revert a bump. Promotion needs 20 clean PRs over 8 weeks. Demotion is immediate."

## 6:45 – 8:00 · Review eval

**Show:** `RESULTS.md`, the table, then `case-11.diff` briefly.

**Say:**

- "Twelve cases: the ten given, plus my two. Case-11 is a retry loop that re-posts a payment after a timeout. Case-12 is the clean fix for INC-2041. Each case ran in a fresh session, three times, then once more with AGENTS.md as context."
- "Caught 27 of 27, so the catch rate is at the ceiling and tells us nothing. False alarms were 2 of 9, both on case-12, where the model calls a config-parsing edge case a defect. In another run it said the same thing but framed it as 'no certain defects'. So the false-alarm rate is really about how the model words its findings."
- "Adding AGENTS.md didn't change the scores. It did catch the Approver-or-Admin convention, and it added noise about missing tests. I'm not claiming an improvement. With 3 clean cases, a false-alarm rate can't tell 0% from 70%. I'd need about 60 clean case-runs."
- If you have time, add the context-file lesson: "My own trimmed context file made it confidently wrong once."

## 8:00 – 8:45 · Risk audit and baseline

**Show:** the risk audit's legs table, then the baseline plan's two callout boxes.

**Say:**

- "The ops README in this copy doesn't have the agent-setup section, so I audited the setup the repo actually configures. All three legs are present: logs with supplier data, untrusted text in those logs, and a Playwright browser that can go anywhere. I'm removing the exfiltration leg. Triage sessions get no network and no tokens. Sessions that can push get no logs. The origin allow-list is a guardrail, not the control. Playwright's own docs say so."
- "Baseline: lead time from PR to main is a median of 57 hours across six changes. CI is about 31 minutes. Change failure rate is 2 out of 5 known releases. And two findings fell out of measuring: one release deployed before its main build had even started, and 3 of 8 main pushes had no PR. Review turnaround I couldn't measure, so I've left it blank rather than guess."

## 8:45 – 9:15 · Close

**Say:** "If I had one more week: get PayEx's answer on idempotency, add ten harder eval cases, and turn on branch protection. The AI augmentation log shows exactly what the agent did and where I overrode it. Thanks."

---

## Recording tips

- Do one dry run with a timer. If you're over 10 minutes, shorten the eval section first.
- Zoom the browser to 125% so the text is readable in the recording.
- Keep the PAT and any terminal history containing tokens off screen.
- Upload the MP4 (OneDrive, SharePoint or YouTube unlisted) and paste the link into `SUBMISSION.md` item 8.
