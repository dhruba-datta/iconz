#!/usr/bin/env python3
"""Render every icon onto one dark sheet: ../local/previews/icons.png (macOS Quick Look).

    python3 tool/preview_icons.py
"""
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DRAWABLES = ROOT / "app" / "src" / "main" / "res" / "drawable"
OUT = ROOT.parent / "local" / "previews"

files = sorted(f for f in DRAWABLES.glob("*.xml") if f.name != "ic_launcher_foreground.xml")
cols, cell = 6, 110
rows = (len(files) + cols - 1) // cols
svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{cols * cell}" height="{rows * cell + 10}">',
       '<rect width="100%" height="100%" fill="#0E1116"/>']
for i, f in enumerate(files):
    paths = re.findall(r'pathData="([^"]+)"', f.read_text())
    x, y = (i % cols) * cell + 15, (i // cols) * cell + 5
    g = "".join(f'<path d="{d}"/>' for d in paths)
    svg.append(f'<g transform="translate({x},{y}) scale(1.67)" fill="none" stroke="#fff" stroke-width="2.2" '
               f'stroke-linecap="round" stroke-linejoin="round">{g}</g>'
               f'<text x="{x + 40}" y="{y + 100}" fill="#9AA4B2" font-size="11" text-anchor="middle" '
               f'font-family="Helvetica">{f.stem}</text>')
svg.append("</svg>")

OUT.mkdir(parents=True, exist_ok=True)
src = OUT / "icons.svg"
src.write_text("\n".join(svg))
subprocess.run(["qlmanage", "-t", "-s", str(cols * cell), "-o", str(OUT), str(src)], check=True, capture_output=True)
(OUT / "icons.svg.png").replace(OUT / "icons.png")
print(f"{len(files)} icons → {OUT / 'icons.png'}")
