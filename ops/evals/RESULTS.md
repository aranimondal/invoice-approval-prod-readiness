# Review eval results

Raw rows: [`results.csv`](results.csv). Labels and scoring rules: [`answer-key.md`](answer-key.md). New cases: [`case-11.diff`](cases/case-11.diff) (defect), [`case-12.diff`](cases/case-12.diff) (clean).

## Setup

- **Reviewer:** Claude Sonnet, run as a fresh sub-agent session for every single case. That makes 48 independent sessions, with no context carried between cases.
- **Prompt v1:** the review prompt from `ops/evals/README.md`, word for word. In front of it I put one line, "Do not use any tools. Answer from the text below only.", so the reviewer couldn't go and fetch the repository. That deviates from "nothing else" in the README, and I'm stating it openly. It's a constant across all four runs, so it doesn't affect the comparison.
- **Runs 1–3:** v1, diff only.
- **Run 4, the one change:** the same prompt and model, with the project's AGENTS.md conventions pasted in front of the diff as a context file.

## Results

| Run | Variable | Caught | Missed | False alarms (clean cases) | Catch rate | False-alarm rate |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | baseline | 9 | 0 | 1 of 3 (case-12) | 100% | 33% |
| 2 | baseline | 9 | 0 | 0 of 3 | 100% | 0% |
| 3 | baseline | 9 | 0 | 1 of 3 (case-12) | 100% | 33% |
| **1–3 pooled** | baseline | **27** | **0** | **2 of 9** | **100%** (95% CI 87–100%) | **22%** (95% CI 6–55%) |
| 4 | + AGENTS.md context | 9 | 0 | 1 of 3 (case-12) | 100% (95% CI 70–100%) | 33% (95% CI 6–79%) |

The intervals are Wilson score intervals. I put them in because the point estimates on their own invite exactly the over-reading this exercise is supposed to stop.

## What the numbers say

**The catch rate is at the ceiling, so it can't tell us anything.** Every defect was found in every run, including my case-11, which I expected to be the hardest. This set is too easy for this model to separate one configuration from another on catches. Before trusting this eval as a gate, I'd add harder cases: a defect that needs two files to see, a defect in a test that makes it pass falsely, and a performance regression.

**The false alarms all come from one case.** Both baseline false alarms, and the one in run 4, are case-12. The reviewer asserts at "medium-high" confidence that `GetValue("PayEx:Timeout", …)` will misread a bare `10` as ten days, or that a missing API key fails late. Both statements are true about .NET. Neither is a defect in this diff. In run 2 the same model raised the same points and framed them as "no certain defects, possible issues". So whether a run scores a false alarm comes down to how the model words it, not what it noticed. That's an important thing to know about the reviewer: **the false-alarm rate is a wording lottery on borderline robustness notes.**

**Run 4 changed the kind of output.** It didn't change the scores:

- **Better:** it was the only run that raised the Approver-or-Admin convention on case-01. That was 0 of 3 in the baseline and 1 of 1 with context. On case-05 and case-07 it cited the specific AGENTS.md rule it was enforcing, which makes a finding much faster for a human to check.
- **Noisier:** it raised "missing tests (AGENTS.md says every rule needs tests)" as a numbered finding, often at high confidence, on 8 of 12 cases. The baseline only mentioned missing tests in passing, in 4 of 36 case-runs, and never as a finding with a confidence attached. Some of those are fair. On case-12 it helped tip the run into a false alarm.
- **Wrong because of me:** on case-09 it reported at high confidence that "AGENTS.md contains no demo accounts". That was true of the *trimmed* context I pasted, not of the real file. A context file that's subtly different from the real one produces confident, wrong findings, and the reviewer has no way to know.

## Was the change an improvement?

**No, not on the measured rates.** Catches stayed at 9 of 9, and false alarms went from an average of 0.67 per run to 1. With only 3 clean cases per run, that difference is one case-run and means nothing.

**How confident am I?** Low, and I can say why in numbers:

- A false-alarm rate measured on 3 clean cases can't distinguish 0% from about 70%.
- To detect a real drop in false alarms from about 22% to 5% at the usual 80% power, I'd need roughly 60 clean case-runs per configuration. That's something like 20 clean cases run three times each.
- The catch rate needs harder cases before it can move at all.

**What I'd actually do with this:** keep AGENTS.md in the reviewer's context, because the convention-backed findings are the ones a human reviewer misses most. Add one line to the prompt: "Report missing tests separately from defects." That's the next single-variable experiment, run on a clean set that's at least ten cases bigger.
