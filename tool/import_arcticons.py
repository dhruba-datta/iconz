#!/usr/bin/env python3
"""Import the Arcticons line icons (CC BY-SA 4.0) into Iconz.

    git clone --depth 1 --filter=blob:none --sparse https://github.com/Arcticons-Team/Arcticons.git ../local/arcticons
    (cd ../local/arcticons && git sparse-checkout set icons/white app/src/main/assets)
    python3 tool/import_arcticons.py [path to that checkout]
    python3 tool/build_icons.py

Converts icons/white/*.svg to VectorDrawables in app/src/main/res/drawable, except names
Iconz draws itself (icons/svg/), and writes icons/arcticons/: the app list (appfilter.tsv),
calendar entries, the list of imported drawables and the source commit. Re-running it
replaces the previous import.
"""
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import svg2vd  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
DRAWABLES = ROOT / "app" / "src" / "main" / "res" / "drawable"
OUT = ROOT / "icons" / "arcticons"
OWN = ROOT / "icons" / "svg"
JAVA_KEYWORDS = set(
    "abstract assert boolean break byte case catch char class const continue default do double else enum "
    "extends final finally float for goto if implements import instanceof int interface long native new "
    "package private protected public return short static strictfp super switch synchronized this throw "
    "throws transient try void volatile while true false null".split()
)


def main():
    src = Path(sys.argv[1] if len(sys.argv) > 1 else ROOT.parent / "local" / "arcticons").resolve()
    svgs = sorted((src / "icons" / "white").glob("*.svg"))
    if not svgs:
        sys.exit(f"no icons in {src}/icons/white")
    own = {p.stem for p in OWN.glob("*.svg")}

    listed = OUT / "drawables.txt"
    if listed.exists():
        for name in listed.read_text().split():
            (DRAWABLES / f"{name}.xml").unlink(missing_ok=True)

    imported, skipped = [], []
    for f in svgs:
        name = f.stem
        if name in own:
            continue
        if not re.fullmatch(r"[a-z_][a-z0-9_]*", name) or name in JAVA_KEYWORDS:
            skipped.append(f"{name} (name)")
            continue
        try:
            xml = svg2vd.convert(f.read_text(encoding="utf-8"))
        except (svg2vd.Unsupported, ValueError) as e:
            skipped.append(f"{name} ({e})")
            continue
        (DRAWABLES / f"{name}.xml").write_text(xml)
        imported.append(name)
    have = set(imported) | own

    appfilter = (src / "app" / "src" / "main" / "assets" / "appfilter.xml").read_text(encoding="utf-8")
    rows, seen = [], set()
    for comp, drawable in re.findall(r'<item\s+component="ComponentInfo\{([^}]+)\}"\s+drawable="([^"]+)"', appfilter):
        if drawable in have and "/" in comp and comp not in seen:
            seen.add(comp)
            rows.append(f"{comp}\t{drawable}")
    calendars = []
    for comp, prefix in re.findall(r'<calendar\s+component="ComponentInfo\{([^}]+)\}"\s+prefix="([^"]+)"', appfilter):
        if all(f"{prefix}{d}" in have for d in range(1, 32)):
            calendars.append(f"{comp}\t{prefix}")

    commit = subprocess.run(["git", "-C", str(src), "log", "-1", "--format=%H %cs"],
                            capture_output=True, text=True).stdout.strip()
    OUT.mkdir(parents=True, exist_ok=True)
    listed.write_text("\n".join(imported) + "\n")
    (OUT / "appfilter.tsv").write_text("\n".join(rows) + "\n")
    (OUT / "calendars.tsv").write_text("\n".join(calendars) + "\n")
    (OUT / "SOURCE.md").write_text(
        "# Arcticons\n\n"
        "Most Iconz icons come from [Arcticons](https://github.com/Arcticons-Team/Arcticons) by Donnnno and\n"
        "the Arcticons contributors, licensed [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).\n"
        "They were converted from SVG to Android VectorDrawables by `tool/import_arcticons.py`;\n"
        "the drawings themselves are unchanged. The app list (`appfilter.tsv`) comes from\n"
        "the same repository.\n\n"
        f"Imported from commit `{commit}`: {len(imported)} icons, {len(rows)} app entries,\n"
        f"{len(calendars)} calendar entries.\n"
    )
    print(f"imported {len(imported)} icons ({len(own)} drawn by Iconz instead), {len(rows)} app entries, "
          f"{len(calendars)} calendars; skipped {len(skipped)}: {', '.join(skipped[:10])}")


if __name__ == "__main__":
    main()
