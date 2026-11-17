#!/usr/bin/env python3
"""Check the actual compiler commands, not just the requested CMake options."""
import json
import pathlib
import re
import shlex

result = {}
for abi in ("arm64-v8a", "x86_64"):
    files = list(pathlib.Path("app/.cxx/Debug").glob(f"*/{abi}/compile_commands.json"))
    if len(files) != 1:
        raise RuntimeError(f"Expected one fresh {abi} native compilation database; found {len(files)}")
    commands = json.loads(files[0].read_text())
    checked = []
    for entry in commands:
        if "/ggml-cpu/" not in entry["file"] and not entry["file"].endswith("/bridge.cpp"):
            continue
        flags = entry.get("arguments") or shlex.split(entry["command"])
        optimization = [flag for flag in flags if re.fullmatch(r"-O(?:[0-3sgz]|fast)", flag)]
        if not optimization or optimization[-1] != "-O3":
            raise RuntimeError(f"Unoptimized inference source: {abi}/{pathlib.Path(entry['file']).name}")
        checked.append(pathlib.Path(entry["file"]).name)
    if not any(name != "bridge.cpp" for name in checked):
        raise RuntimeError(f"No actual CPU kernel compiler commands for {abi}")
    result[abi] = {"effectiveOptimization": "-O3", "verifiedSources": checked}
pathlib.Path("evidence").mkdir(exist_ok=True)
pathlib.Path("evidence/native-build.json").write_text(json.dumps(result, indent=2))
print(json.dumps(result))
