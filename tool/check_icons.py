#!/usr/bin/env python3
"""Check every icon before a release; exits non-zero on any problem.

    python3 tool/check_icons.py

Android reads a vector's path data only when the icon is drawn, so a bad path crashes the
launcher instead of failing the build. This checks each path's commands and argument
counts the way Android's parser needs them, and that every icon named in appfilter.xml and
drawable.xml exists.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}
TOKEN = re.compile(r"[MmZzLlHhVvCcSsQqTtAa]|[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")


def path_error(d):
    toks = TOKEN.findall(d)
    if re.sub(r"[\s,]", "", d) != "".join(toks):
        return "characters Android can't read"
    if not toks or toks[0] not in "Mm":
        return "doesn't start with M"
    i = 0
    while i < len(toks):
        cmd = toks[i]
        i += 1
        args = []
        while i < len(toks) and toks[i] not in ARGS and toks[i].upper() not in ARGS:
            args.append(toks[i])
            i += 1
        need = ARGS[cmd.upper()]
        if need == 0 and args:
            return "numbers after Z"
        if need and (not args or len(args) % need):
            return f"{cmd} has {len(args)} numbers"
        if cmd in "Aa" and any(args[k] not in ("0", "1") for j in range(0, len(args), 7) for k in (j + 3, j + 4)):
            return "arc flag isn't 0 or 1"
    return None


def main():
    problems, paths = [], 0
    drawables = {p.stem for p in (RES / "drawable").glob("*.xml")}
    for f in sorted((RES / "drawable").glob("*.xml")):
        for d in re.findall(r'pathData="([^"]*)"', f.read_text()):
            paths += 1
            err = path_error(d)
            if err:
                problems.append(f"{f.name}: {err}")
    for name in ("appfilter", "drawable"):
        text = (RES / "xml" / f"{name}.xml").read_text()
        for d in sorted(set(re.findall(r'drawable="([^"]+)"', text)) - drawables):
            problems.append(f"{name}.xml names missing icon {d}")
        for p in set(re.findall(r'prefix="([^"]+)"', text)):
            problems += [f"calendar icon {p}{n} missing" for n in range(1, 32) if f"{p}{n}" not in drawables]
    if problems:
        print("\n".join(problems[:50]))
        sys.exit(f"✗ {len(problems)} problems")
    print(f"✓ {len(drawables)} icons, {paths} paths, all references found")


if __name__ == "__main__":
    main()
