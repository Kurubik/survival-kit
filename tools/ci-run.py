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

if len(sys.argv) < 2:
    raise SystemExit("usage: ci-run.py COMMAND [ARG ...]")
lines = collections.deque(maxlen=2000)
process = subprocess.Popen(sys.argv[1:], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace")
for line in process.stdout:
    print(line, end="", flush=True)
    lines.append(line)
status = process.wait()
if status:
    useful = [line for line in lines if re.search(r"(^e: |error:|What went wrong|Execution failed|Could not|FAILURE:|Exception|Caused by:|not found|Error:|^> )", line, re.I)]
    message = "".join(useful[-25:] if useful else list(lines)[-35:])[-6000:]
    message = message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::error title=Verification command exited {status}::{message}")
raise SystemExit(status)
