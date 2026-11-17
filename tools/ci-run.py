#!/usr/bin/env python3
"""Run a verification command with a real exit code and public failure annotation.

Useful when the repository can be pushed over SSH but authenticated CI log download
is not configured. Only command output is examined; environment variables are never
dumped. Full output stays in the regular Actions log.
"""
import collections
import re
import subprocess
import sys
import pathlib
import xml.etree.ElementTree as ET

if len(sys.argv) < 2:
    raise SystemExit("usage: ci-run.py COMMAND [ARG ...]")
lines = collections.deque(maxlen=2000)
process = subprocess.Popen(sys.argv[1:], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace")
for line in process.stdout:
    print(line, end="", flush=True)
    lines.append(line)
status = process.wait()
if status:
    primary = list(dict.fromkeys(line for line in lines if re.search(r"(^e: |error:|Could not resolve|Could not find|Unresolved reference|Cannot access|Type mismatch)", line, re.I)))
    secondary = [line for line in lines if re.search(r"(What went wrong|Execution failed|^FAILURE:|^Caused by:|not found)", line, re.I)]
    message = "".join(primary[:50] + secondary[-8:] if primary else list(lines)[-40:])[:18000]
    message = message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::error title=Verification command exited {status}::{message}")
    reports=pathlib.Path("app/build/outputs/androidTest-results")
    if reports.exists():
        for report in reports.rglob("TEST-*.xml"):
            try:
                for case in ET.parse(report).getroot().iter("testcase"):
                    for failure in list(case.findall("failure"))+list(case.findall("error")):
                        detail=(case.get("name","")+": "+failure.get("message","")+"\n"+(failure.text or ""))[:8000]
                        detail=detail.replace("%","%25").replace("\r","%0D").replace("\n","%0A")
                        print(f"::error title=Android UI test::{detail}")
            except (OSError,ET.ParseError):pass
raise SystemExit(status)
