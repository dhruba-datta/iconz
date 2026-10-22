#!/usr/bin/env python3
"""List the phone's apps that Iconz has no icon mapping for, over USB (adb).

    python3 tool/scan_phone.py            # writes missing.txt, then:
    python3 tool/build_icons.py --merge missing.txt

The app itself never reads which apps are installed (that, plus downloading APKs, is
what made Play Protect call it harmful). Instead the phone is asked from the Mac:
turn on USB debugging (Settings → Developer options), plug it in, allow the Mac.

Each line of missing.txt is "<drawable or -> <package/activity>". A drawable is filled in
when the app's package is already listed in icons.json; "-" means no icon is drawn yet.
"""
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SDK = os.environ.get("ANDROID_HOME", "/opt/homebrew/share/android-commandlinetools")
ADB = shutil.which("adb") or f"{SDK}/platform-tools/adb"


def launcher_activities():
    out = subprocess.run(
        [ADB, "shell", "cmd", "package", "query-activities", "--brief",
         "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER"],
        capture_output=True, text=True,
    )
    if out.returncode != 0:
        sys.exit(f"adb failed (is the phone plugged in with USB debugging on?)\n{out.stderr.strip()}")
    found = set()
    for token in out.stdout.split():
        if "/" not in token or token.count("/") != 1:
            continue
        pkg, act = token.split("/")
        if act.startswith("."):
            act = pkg + act
        if pkg and act and pkg != "app.iconz":
            found.add(f"{pkg}/{act}")
    return sorted(found)


def main():
    icons = json.loads((ROOT / "icons" / "icons.json").read_text())["icons"]
    appfilter = (ROOT / "app" / "src" / "main" / "res" / "xml" / "appfilter.xml").read_text()
    mapped = set(re.findall(r'ComponentInfo\{([^}]+)\}', appfilter))
    by_package = {p: i["drawable"] for i in icons for p in i["packages"]}
    apps = [c for c in launcher_activities() if c not in mapped]
    ready = [c for c in apps if c.split("/")[0] in by_package]
    lines = ["# Iconz missing icons, from scan_phone.py", "# <drawable or -> <package/activity>"]
    lines += [f"{by_package[c.split('/')[0]]} {c}" for c in ready]
    lines += [f"- {c}" for c in apps if c not in ready]
    out = ROOT / "missing.txt"
    out.write_text("\n".join(lines) + "\n")
    print(f"{len(apps)} apps without an icon mapping; {len(ready)} are already drawn → {out.name}")
    print("Next: python3 tool/build_icons.py --merge missing.txt")


if __name__ == "__main__":
    main()
