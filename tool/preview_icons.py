#!/usr/bin/env python3
"""Render the Bangladesh icons onto one dark sheet: ../local/previews/icons.png.

    python3 tool/preview_icons.py

Draws from the SVG sources (icons/svg/, or the Arcticons checkout in ../local/arcticons for
icons marked "source": "arcticons"), with macOS Quick Look. Also copied to branding/icons.png
for the README.
"""
import json
import re
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT.parent / "local" / "previews"
ARCTICONS = ROOT.parent / "local" / "arcticons" / "icons" / "white"

icons = sorted(json.loads((ROOT / "icons" / "icons.json").read_text())["icons"], key=lambda i: i["label"].lower())
cols, cell = 7, 130
rows = (len(icons) + cols - 1) // cols
cells = []
for i, icon in enumerate(icons):
    src = ARCTICONS / f"{icon['drawable']}.svg" if icon.get("source") == "arcticons" else ROOT / "icons" / "svg" / f"{icon['drawable']}.svg"
    inner = re.sub(r"^.*?<svg[^>]*>", "", src.read_text(), flags=re.S).rsplit("</svg>", 1)[0]
    x, y = (i % cols) * cell, (i // cols) * cell
    cells.append(f'<g transform="translate({x + 23},{y + 8}) scale(1.75)"><svg width="48" height="48" viewBox="0 0 48 48">{inner}</svg></g>'
                 f'<text x="{x + cell / 2}" y="{y + 115}" fill="#9AA4B2" font-size="11" text-anchor="middle" font-family="Helvetica">{icon["label"]}</text>')
w, h = cols * cell, rows * cell
side = max(w, h)  # Quick Look thumbnails are square
svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{side}" height="{side}"><rect width="100%" height="100%" fill="#0E1116"/>'
       + "".join(cells) + "</svg>")
OUT.mkdir(parents=True, exist_ok=True)
(OUT / "icons.svg").write_text(svg)
subprocess.run(["qlmanage", "-t", "-s", str(side), "-o", str(OUT), str(OUT / "icons.svg")], check=True, capture_output=True)
(OUT / "icons.svg.png").replace(OUT / "icons.png")
subprocess.run(["magick", str(OUT / "icons.png"), "-crop", f"{w}x{h}+0+0", "+repage", str(OUT / "icons.png")], check=False)
shutil.copy(OUT / "icons.png", ROOT / "branding" / "icons.png")
print(f"{len(icons)} icons → {OUT / 'icons.png'}")
