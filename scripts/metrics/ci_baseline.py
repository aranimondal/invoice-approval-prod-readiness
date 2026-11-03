#!/usr/bin/env python3
"""Baseline numbers from the CI history in ops/pipeline/run-timings.csv.

Usage:
    python3 scripts/metrics/ci_baseline.py [path/to/run-timings.csv]

Prints:
  * pipeline duration per run (sum of step durations) with median / p90
  * pipeline failure rate, split by PR runs and main pushes
  * a lead-time proxy per branch: first PR run -> first main push after that
    branch's last PR run (assumes that main push is the merge)
  * main pushes with no PR run since the previous main push (possible direct pushes)

Standard library only, so it runs anywhere Python 3.8+ is installed.
"""
import csv
import statistics
import sys
from collections import OrderedDict, defaultdict
from datetime import datetime

DEFAULT_PATH = "ops/pipeline/run-timings.csv"
REUSE_GAP_DAYS = 4


def parse_time(value):
    return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ")


def percentile(values, pct):
    ordered = sorted(values)
    if not ordered:
        return float("nan")
    k = (len(ordered) - 1) * pct / 100.0
    lower = int(k)
    upper = min(lower + 1, len(ordered) - 1)
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (k - lower)


def load_runs(path):
    runs = OrderedDict()
    with open(path, newline="") as handle:
        for row in csv.DictReader(handle):
            number = int(row["run_number"])
            run = runs.setdefault(number, {
                "number": number,
                "started": parse_time(row["started_at"]),
                "branch": row["branch"],
                "event": row["event"],
                "conclusion": row["conclusion"],
                "seconds": 0,
                "steps": [],
            })
            run["seconds"] += int(row["duration_seconds"])
            run["steps"].append(row["step"])
    return list(runs.values())


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_PATH
    runs = load_runs(path)

    print(f"Runs analysed: {len(runs)} ({runs[0]['started']:%Y-%m-%d} to {runs[-1]['started']:%Y-%m-%d})\n")

    durations = [run["seconds"] for run in runs]
    print("Pipeline duration (sum of steps)")
    print(f"  median {statistics.median(durations) / 60:.1f} min, "
          f"mean {statistics.mean(durations) / 60:.1f} min, "
          f"p90 {percentile(durations, 90) / 60:.1f} min, "
          f"min {min(durations) / 60:.1f}, max {max(durations) / 60:.1f}")

    step_totals = defaultdict(list)
    with open(path, newline="") as handle:
        for row in csv.DictReader(handle):
            step_totals[row["step"]].append(int(row["duration_seconds"]))
    slowest = sorted(step_totals.items(), key=lambda item: -statistics.median(item[1]))[:3]
    print("  slowest steps (median): " + ", ".join(
        f"{name} {statistics.median(values) / 60:.1f} min" for name, values in slowest))

    print("\nPipeline failures")
    for label, selector in (("all runs", lambda r: True),
                            ("pull_request", lambda r: r["event"] == "pull_request"),
                            ("push to main", lambda r: r["event"] == "push")):
        subset = [run for run in runs if selector(run)]
        failed = [run for run in subset if run["conclusion"] != "success"]
        rate = 100.0 * len(failed) / len(subset) if subset else 0.0
        names = ", ".join(f"#{run['number']} {run['branch']}" for run in failed) or "none"
        print(f"  {label:<13} {len(failed)}/{len(subset)} = {rate:.0f}%   ({names})")

    print("\nLead-time proxy: first PR run -> next main push after the branch's last PR run")
    main_pushes = [run for run in runs if run["event"] == "push" and run["branch"] == "main"]
    pr_runs_by_branch = OrderedDict()
    for run in runs:
        if run["event"] == "pull_request":
            pr_runs_by_branch.setdefault(run["branch"], []).append(run)

    lead_hours = []
    for branch, branch_runs in pr_runs_by_branch.items():
        # A branch name can be reused after merge (fix/notification-filter is: runs 2-7,
        # then run 18 a week later). Treat a gap of more than REUSE_GAP_DAYS between two
        # PR runs on the same branch as a new change.
        cycles, current = [], []
        for run in branch_runs:
            if current and (run["started"] - current[-1]["started"]).days > REUSE_GAP_DAYS:
                cycles.append(current)
                current = []
            current.append(run)
        if current:
            cycles.append(current)

        for cycle in cycles:
            first, last = cycle[0], cycle[-1]
            merge = next((push for push in main_pushes if push["started"] > last["started"]), None)
            if merge is None:
                print(f"  {branch:<28} no main push after last PR run #{last['number']} (still open?)")
                continue
            hours = (merge["started"] - first["started"]).total_seconds() / 3600
            lead_hours.append(hours)
            print(f"  {branch:<28} #{first['number']:>2} {first['started']:%m-%d %H:%M} -> "
                  f"main #{merge['number']:>2} {merge['started']:%m-%d %H:%M}  = {hours:5.1f} h")

    if lead_hours:
        print(f"  median {statistics.median(lead_hours):.1f} h over {len(lead_hours)} changes "
              f"(min {min(lead_hours):.1f}, max {max(lead_hours):.1f})")

    print("\nMain pushes with no PR run since the previous main push (possible direct pushes)")
    previous = None
    direct = []
    for push in main_pushes:
        window_start = previous["started"] if previous else datetime.min
        had_pr = any(window_start < run["started"] < push["started"]
                     for run in runs if run["event"] == "pull_request")
        if not had_pr:
            direct.append(push)
        previous = push
    print(f"  {len(direct)}/{len(main_pushes)}: " + (", ".join(
        f"#{push['number']} {push['started']:%m-%d %H:%M}" for push in direct) or "none"))


if __name__ == "__main__":
    main()
