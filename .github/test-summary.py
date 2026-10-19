#!/usr/bin/env python3
"""Print a Markdown table of JUnit XML results: one row per test, with the reason for fails and skips."""
import glob
import re
import sys
import xml.etree.ElementTree as ET

title, pattern = sys.argv[1], sys.argv[2]
rows, counts = [], {"passed": 0, "FAILED": 0, "skipped": 0}

for path in sorted(glob.glob(pattern)):
    for case in ET.parse(path).getroot().iter("testcase"):
        cls = case.get("classname", "").rsplit(".", 1)[-1]
        problem = case.find("failure")
        if problem is None:
            problem = case.find("error")
        skipped = case.find("skipped")
        if problem is not None:
            result, node = "FAILED", problem
        elif skipped is not None:
            result, node = "skipped", skipped
        else:
            result, node = "passed", None
        counts[result] += 1
        text = "" if node is None else (node.get("message") or node.text or "").strip()
        reason = re.sub(r"^[\w.$]+: ", "", text.splitlines()[0])[:200].replace("|", "\\|") if text else ""
        rows.append(f"| {cls}.{case.get('name')} | {result} | {reason} |")

if not rows:
    print(f"### {title}\n\nNo test results found.")
    sys.exit()

print(f"### {title}: {counts['passed']} passed, {counts['FAILED']} failed, {counts['skipped']} skipped\n")
if counts["skipped"]:
    print(f"> **Note:** {counts['skipped']} test(s) skipped, so not fully verified; see the reasons below.\n")
print("| Test | Result | Reason |\n|---|---|---|")
print("\n".join(rows))
