# Autonomy policy: dependency bumps

**Scope:** Maven dependencies and plugins in `backend/` and npm packages in `e2e/` for the Invoice Approval service.
**Owner:** service owner. **Reviewed:** every quarter, or after any incident a dependency change contributed to.

**Why this workflow:** of the three on offer, it's the one where agent output goes straight to production, and we already have evidence of it going wrong. Release 2.15.1 bundled four package bumps with the schema rename that caused INC-2047, so rolling back the bumps meant rolling back everything.

## Levels

| Level | What the agent may do | What a human does |
| --- | --- | --- |
| **L0** | Nothing | Everything |
| **L1 — advise** | Read the repo and advisories, and write a report or an upgrade note | Decides, makes the change, opens the PR |
| **L2 — propose** | Open a PR on its own branch with the bump and a filled-in description | Reviews, approves and merges |
| **L3 — merge on green** | Open the PR and let auto-merge complete once all required checks pass | Is notified, and can revert. Spot-checks one in five |
| **L4 — ship** | Merge and deploy without a human in the loop | Not used for this service. Tier-2 with a payment integration doesn't qualify |

## Which task sits where

| Task | Level today | Why |
| --- | --- | --- |
| Weekly scan for outdated or vulnerable packages (Maven versions plugin, OWASP dependency-check, `npm audit`) | L3 | Read-only |
| Patch bumps of test-only packages (JUnit, AssertJ, ArchUnit, Surefire, Playwright) | L2 | CI fully proves them. First candidate for L3 |
| Patch bumps of runtime packages (Spring Boot BOM, cloud SDKs, JDBC driver) | L2 | They reach production |
| Minor-version bumps of runtime packages | L2, reviewer from the four established engineers | Behaviour changes hide in minors |
| Major bumps, or anything touching auth, serialization, the HTTP client or the SQL client | L1 | INC-2041 started with an HTTP client change |
| Critical CVE fix inside the month-end window | L1; the incident commander decides | A risk call isn't the agent's to make |

## Controls that make those levels safe

1. **One dependency family per PR, and nothing else in it.** A CI check fails the PR if it touches anything except build manifests (`pom.xml`, the Maven wrapper properties, `package*.json`). That's the direct lesson from 2.15.1.
2. **The full pipeline is required:** every stage, including the architecture rule and the end-to-end tests, even though the pipeline takes about 31 minutes (see the [baseline](../measurement/baseline-plan.md)).
3. **A mandatory PR description:** old → new version, release notes link, changed transitive dependencies, and "what could break". If any of these is missing, the PR stays a draft.
4. **A narrow token:** scoped to this repo, with write access to branches and PRs only. No `workflow` scope (so it can't edit CI), no admin. Branch protection applies to the bot.
5. **Month-end freeze:** the agent can't merge from the third-last working day to the first working day of the next month.
6. **Kill switch:** anyone on the rota can turn off bot auto-merge with a repository variable, without asking.

## Evidence needed to move a task up a level

Going from L2 to L3 for one row of the table needs all of these, measured over at least 8 weeks:

- at least 20 agent PRs in that row merged with no human change to the diff;
- zero reverts, and zero incidents where the change was a contributing factor;
- the PR description was accurate in every one of a sample of 10;
- a median time from opening to merge under 2 working days. That shows reviewers are keeping up, not rubber-stamping a backlog.

Going from L1 to L2 needs 5 consecutive upgrade notes that a human followed without finding anything the note missed.

**Who approves:** the service owner plus one other engineer who's been on the codebase longest, recorded as a PR against this file. **Demotion is immediate and needs no approval.** Any revert or incident linked to an agent PR drops that row one level, and the next on-call engineer does it.

## Day one of the rotation

**The team runs at L2 at most, with the read-only scan at L3.**

Two of the six engineers are new to the codebase, and each will own a week of on-call. L3 is only safe when whoever's on call can confidently tell a bad bump apart from everything else that changed, and revert it. We aren't there yet. We also have no history of agent PRs, so the promotion evidence can't exist yet. I expect the test-only row to earn L3 first, around the end of the first quarter.
