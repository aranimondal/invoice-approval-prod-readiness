# Measurement baseline before the rotation starts

The point of a baseline is to be able to say, three months from now, whether the AI tooling and the new process made us faster or safer. Without one, we'll be arguing from impressions. Each metric below names the exact source and query, so anyone can re-run it. The two I can capture today are filled in. The third can't be captured from what we have, and I explain why rather than guess a number.

## 1. Lead time for changes

**Definition:** the time from a change's first commit to that change running in production.

**Source once we're live:**

```bash
# merged PRs with first-commit and merge timestamps (GitHub CLI)
gh pr list --repo <org>/invoice-approval --state merged --base main --limit 200 \
  --json number,mergedAt,commits \
  --jq '.[] | [.number, (.commits[0].committedDate), .mergedAt] | @tsv'
```

```bash
# production deploys, assuming the deploy job uses a GitHub Actions environment called "production"
gh api "repos/<org>/invoice-approval/deployments?environment=production&per_page=100" \
  --jq '.[] | [.sha, .created_at, .id] | @tsv'
# then each deployment's statuses, to keep only the successful ones
gh api "repos/<org>/invoice-approval/deployments/<id>/statuses" --jq '.[0].state'
```

Join the two on the merge commit SHA. If deploys don't go through a GitHub environment today, that's the first thing to fix. Without it there's no reliable deploy timestamp at all. Lead time is deploy time minus first-commit time. Report the median and p90 per month.

**Captured today:** from `ops/pipeline/run-timings.csv` (20 CI runs, 19 Jan – 2 Feb 2026), measured as a proxy: the first PR run on a branch to the main-branch push that followed its last PR run. The script is [`scripts/metrics/ci_baseline.py`](../../scripts/metrics/ci_baseline.py):

```
python3 scripts/metrics/ci_baseline.py ops/pipeline/run-timings.csv
```

| Change | First PR run | Main push | Hours |
| --- | --- | --- | --- |
| feature/supplier-contacts | #1 01-19 09:12 | #4 01-22 07:34 | 70.4 |
| fix/notification-filter (1st) | #2 01-20 14:55 | #8 01-24 18:17 | 99.4 |
| chore/dependency-bumps | #10 01-26 09:18 | #12 01-28 09:05 | 47.8 |
| fix/approval-audit | #14 01-29 23:20 | #19 02-01 18:14 | 66.9 |
| feature/payex-retry | #15 01-30 06:37 | #16 01-31 09:02 | 26.4 |
| fix/notification-filter (2nd) | #18 02-01 13:17 | #19 02-01 18:14 | 5.0 |

> **Baseline lead time (PR opened → on main): median 57.3 hours, n = 6, range 5–99 h.**
> CI pipeline duration, a fixed part of every change: **median 31.3 minutes**, range 28.4–35.1 min across all 20 runs. The slowest steps are static analysis (8.0 min median), end-to-end tests (3.7 min) and the container build (2.7 min).

Three caveats, because the number is only as good as its assumptions:

- **The merge mapping is an assumption.** The CSV has no merge commits, so I'm treating the next main push as the merge.
- **Main-to-production is invisible in CI.** INC-2047's publisher log shows release 2.15.1 deployed at 08:41 on 28 January, but main CI run #12 only *started* at 09:05. So at least one release went out before its main build had run. That's a process finding in its own right. It also means CI history can't stand in for deploy time.
- **Three of the eight main pushes (#9, #13, #20) had no PR run since the previous main push.** They look like direct pushes to main that bypassed review. Branch protection should make that impossible before the rotation starts.

## 2. Change failure rate

**Definition:** the share of production deployments that cause a Sev-1 or Sev-2 incident, a rollback, or a hotfix.

**Source once we're live:**

- **Numerator:** incidents in the incident tracker whose `caused_by_release` field is set. That field needs adding to the incident template now. Today, linking an incident to a release takes someone reading the timeline.
- **Denominator:** successful production deployments from the GitHub deployments API (the query above).
- Report it monthly. Report the month-end window separately, because that's where our failures cluster.

**Captured today:** from the evidence packs in `ops/incidents/`, which are the only production history in the repository:

| Release | Date | Outcome | Source |
| --- | --- | --- | --- |
| 2.13.4 | ~5 Jan | ran 9 days without incident | INC-2041 README |
| 2.14.0 | 14 Jan | **INC-2041, Sev-2**: socket exhaustion from the PayEx client change | INC-2041 alert + deploy diff |
| 2.14.3 | 16 Jan | no incident in the evidence | INC-2047 recent-changes |
| 2.15.0 | 21 Jan | no incident in the evidence | INC-2047 recent-changes |
| 2.15.1 | 28 Jan | **INC-2047, Sev-2**: event type rename broke the notification subscription filter | INC-2047 alert + Service Bus rule output |

> **Baseline change failure rate: 2 of 5 known releases = 40%.**
> 2.14.1 and 2.14.2 must exist, given the version numbers, but they aren't in the evidence. If they were clean, the rate is 2 of 7 = 29%. So I'm recording the range **29–40%**, from a very small sample.

Whichever end of that range is right, it's well into what DORA calls "low performer" territory for a tier-2 service. And both failures share a cause: a change whose effect on an *integration contract* (the PayEx connection handling, the event type name) wasn't tested. That's what the architecture test, and the contract test proposed in ADR-001, are aimed at.

For context, here's the CI-level failure rate. It isn't the same thing as the change failure rate, and shouldn't be reported as if it were:

- **PR runs:** 2 of 12 failed (17%). Those are #6, which failed at the container scan, and #14, which failed at the end-to-end tests.
- **Main pushes:** 0 of 8 failed.

## 3. Review turnaround

**Definition:** the time from a PR being marked ready for review to its first review (comment, approval or change request), from a human. Bot reviews are excluded.

**Source once we're live:**

```bash
gh api graphql -f query='
query($owner:String!, $repo:String!) {
  repository(owner:$owner, name:$repo) {
    pullRequests(last:100, states:MERGED, baseRefName:"main") {
      nodes {
        number createdAt
        timelineItems(itemTypes:[READY_FOR_REVIEW_EVENT], first:1) { nodes { ... on ReadyForReviewEvent { createdAt } } }
        reviews(first:1) { nodes { submittedAt author { login } } }
      }
    }
  }
}' -F owner=<org> -F repo=invoice-approval
```

Take `ready_for_review` (falling back to `createdAt`) and subtract it from the first human review's `submittedAt`. Report the median and p90, in working hours, split by whether the author is one of the two new engineers. My guess is that's where the waits will be, and I'd like the data to prove me wrong.

**Captured today: not possible, so no number.** `run-timings.csv` records CI runs, not reviews. The upstream GitHub repository has one PR, from a fork, with no reviews, and its only CI run is `action_required`. I'd rather leave this blank than back-fill it from memory or guesswork. The first real data point will be the PR that delivered this work.

## When to take the baseline

Run all three queries in the week before the rotation starts, and save the outputs in `docs/measurement/baseline-YYYY-MM.md`. Re-run them at the end of each month-end window. Look at the trend after three windows, not before: with roughly 6–10 changes a month, a single month moves these numbers by more than any real effect would.
