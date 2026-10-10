#!/usr/bin/env python3
"""Fail on any JUnit failure except the documented, pre-existing ones.

Known failures are listed by exact class and test name below. Each one is still
reported as a GitHub warning so it stays visible; any other failure, error or
missing results fails the job.
"""

from __future__ import annotations

import glob
import sys
import xml.etree.ElementTree as ET

# (class name, test name): reason. Remove an entry once the test is fixed.
KNOWN_FAILURES = {
    (
        "app.friendly.assistant.data.ai.mcp.McpOAuthAttemptFenceTest",
        "attempt superseded during settings persistence cannot become current or write the stale token",
    ): "pre-existing on friendly-2.0 before this workflow existed; tracked separately",
}


def main(expected_dirs: list[str]) -> int:
    """expected_dirs: result folders that must exist, e.g. app/build/test-results/testPlayDebugUnitTest."""
    missing = [d for d in expected_dirs if not glob.glob(f"{d}/TEST-*.xml")]
    for d in missing:
        print(f"::error::No test results in {d}; that test task did not run or did not compile.")
    files = sorted(glob.glob("**/build/test-results/test*UnitTest/TEST-*.xml", recursive=True))
    if not files:
        print("::error::No unit test results found; the test tasks did not run.")
        return 1

    total = 0
    known, unexpected = [], []
    for path in files:
        for case in ET.parse(path).getroot().iter("testcase"):
            total += 1
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is None:
                continue
            key = (case.get("classname", ""), case.get("name", ""))
            message = (problem.get("message") or "").splitlines()[0][:300] if problem.get("message") else ""
            entry = f"{key[0]} > {key[1]} ({path.split('/build/')[0]}): {message}"
            (known if key in KNOWN_FAILURES else unexpected).append(entry)

    print(f"{total} test cases in {len(files)} result files")
    for entry in known:
        print(f"::warning title=Known pre-existing test failure::{entry}")
    for entry in unexpected:
        print(f"::error title=Test failure::{entry}")
    if unexpected or missing:
        print(f"{len(unexpected)} unexpected failure(s), {len(missing)} missing result folder(s); {len(known)} known pre-existing failure(s).")
        return 1
    print(f"No unexpected failures; {len(known)} known pre-existing failure(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
