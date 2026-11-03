# Autonomy policy: dependency bumps

**Scope:** Maven dependencies and plugins in `backend/` and npm packages in `e2e/` for the Invoice Approval service.
**Owner:** service owner. **Reviewed:** every quarter, or after any incident a dependency change contributed to.

I picked dependency bumps over log triage or incident summaries because it's the workflow where an agent's output goes straight to production, and where we already have evidence of it going wrong. Release 2.15.1 shipped "four patch-level package upgrades" in the same release as the event-schema rename that caused INC-2047. Because of that bundling, the INC-2047 pack warns that "a rollback affects everything else in the release". The bumps themselves were probably fine. Bundling them with a feature change is what made the incident harder to get out of.

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
| Scan for outdated or vulnerable packages (`./backend/mvnw -f backend/pom.xml versions:display-dependency-updates`, OWASP `dependency-check`, `npm audit`) and post a weekly summary | L3 | Read-only, no production impact |
| Patch bumps of test-only packages (`junit-jupiter`, `assertj-core`, `archunit-junit5`, `maven-surefire-plugin`, `@playwright/test`) | L2 | CI alone fully proves these. First candidate for L3 |
| Patch bumps of runtime packages (the Spring Boot BOM, `com.azure:*` / `software.amazon.awssdk:*`, the JDBC driver) | L2 | Reach production. Need a reviewer who knows the runtime path |
| Minor-version bumps of runtime packages | L2, reviewer must be one of the four engineers who've been on the codebase longest | Behaviour changes ship in minors more often than people expect |
| Major bumps, or anything touching auth, serialization, HTTP client handling, or the SQL client | L1 | The agent writes the upgrade note and a human does the work. INC-2041 started with an HTTP client change |
| Critical CVE fix during the month-end window | L1, and the incident commander decides | Fixing a CVE during freeze is a risk call, and a risk call isn't the agent's to make |

## Controls that make those levels safe

1. **One dependency family per PR, and nothing else in it.** A CI check fails the PR if it touches files other than `pom.xml` files, `.mvn/wrapper/maven-wrapper.properties`, `package.json` and `package-lock.json`. That's the direct lesson from 2.15.1.
2. **The full pipeline is required:** build, unit, integration, the architecture test, secret scan, vulnerability scan, container scan and end-to-end. No skipped stages on bot PRs, even though the pipeline takes about 31 minutes (median of the last 20 runs, see the [baseline](../measurement/baseline-plan.md)).
3. **The PR description is mandatory:** old → new version, a link to the release notes, any transitive packages that changed, and a one-line "what could break". If the agent can't fill one of these in, the PR stays a draft.
4. **The agent's token is scoped to this repo.** It gets contents write on non-default branches and pull-request write. No `workflow` scope, so it can't edit CI, and no admin. Branch protection on `main` applies to the bot like anyone else.
5. **Freeze windows.** No merges by the agent, at any level, from the third-last working day to the first working day of the next month. Bot PRs can open, but they wait.
6. **Kill switch.** Anyone on the rota can turn off auto-merge for bot PRs, by repository variable, without asking.

## Evidence needed to move a task up a level

Going from L2 to L3 for one row of the table needs all of these, measured over at least 8 weeks:

- at least 20 agent PRs in that row merged with no human change to the diff;
- zero reverts, and zero incidents where the change was a contributing factor;
- the PR description was accurate in every one of a sample of 10;
- median time from PR opened to merged under 2 working days, so we know the reviewers are actually keeping up rather than rubber-stamping a backlog.

Going from L1 to L2 needs 5 consecutive upgrade notes that a human followed without finding anything the note missed.

**Who approves:** the service owner plus one other engineer who's been on the codebase longest, recorded as a PR against this file. **Demotion is immediate and needs no approval.** Any revert or incident linked to an agent PR drops that row one level, and the next on-call engineer does it.

## Day one of the rotation

**The team runs at L2 at most, with the read-only scan at L3.**

Two of the six engineers are new to the codebase. Over a six-week cycle, each will be the on-call engineer for one week, and possibly the one who has to judge whether a bump caused a 3 a.m. alert. L3 only makes sense if whoever is around can revert a bump confidently and tell it apart from everything else that changed. We don't have that yet. The other blocker is data: we have no history of agent PRs on this repo, so the L3 evidence above can't exist yet. I'd expect the test-only patch row to be the first to earn L3, around the end of the first quarter.
