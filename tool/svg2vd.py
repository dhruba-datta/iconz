"""Convert a line-icon SVG (48×48, Arcticons style) to an Android VectorDrawable.

Handles what Arcticons and Iconz icons use: path, line, circle, ellipse, rect, polyline,
polygon and nested g, with styles from <style> classes, presentation attributes and
inline style, and transforms (as VectorDrawable groups). Raises Unsupported for
anything else, so a bad icon is skipped instead of drawn wrong.
"""
import math
import re
import xml.etree.ElementTree as ET

SVG = "{http://www.w3.org/2000/svg}"
SKIP = {"defs", "style", "metadata", "title", "desc", "namedview", "manifest"}
SHAPES = {"path", "line", "circle", "ellipse", "rect", "polyline", "polygon"}
INHERITED = {"fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin",
             "stroke-miterlimit", "fill-rule", "stroke-opacity", "fill-opacity"}
NUM = re.compile(r"[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")
COMMANDS = set("MmZzLlHhVvCcSsQqTtAa")
NAMED = {"white": "FFFFFF", "black": "000000"}


class Unsupported(Exception):
    pass


def fmt(x):
    s = f"{x:.4f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def normalize_path(d):
    """Re-emit SVG path data with explicit separators (Android can't read packed arc flags)."""
    out, cmd, argi, i = [], None, 0, 0
    while i < len(d):
        c = d[i]
        if c in COMMANDS:
            out.append(c)
            cmd, argi = c, 0
            i += 1
        elif c in " ,\t\r\n":
            i += 1
        elif cmd in ("A", "a") and argi % 7 in (3, 4) and c in "01":
            out.append(c)
            argi += 1
            i += 1
        else:
            m = NUM.match(d, i)
            if not m:
                raise Unsupported(f"bad path data near {d[i:i + 12]!r}")
            out.append(m.group())
            argi += 1
            i = m.end()
    return " ".join(out)


def nums(s):
    return [float(x) for x in NUM.findall(s or "")]


def attr(el, name, default=0.0):
    v = el.get(name)
    return float(NUM.match(v.strip()).group()) if v and NUM.match(v.strip()) else default


def shape_path(el, tag):
    if tag == "path":
        return normalize_path(el.get("d", ""))
    if tag == "line":
        return f"M {fmt(attr(el, 'x1'))} {fmt(attr(el, 'y1'))} L {fmt(attr(el, 'x2'))} {fmt(attr(el, 'y2'))}"
    if tag in ("circle", "ellipse"):
        cx, cy = attr(el, "cx"), attr(el, "cy")
        rx = attr(el, "r") if tag == "circle" else attr(el, "rx")
        ry = attr(el, "r") if tag == "circle" else attr(el, "ry")
        if rx <= 0 or ry <= 0:
            return None
        a = f"A {fmt(rx)} {fmt(ry)} 0 1 0"
        return f"M {fmt(cx - rx)} {fmt(cy)} {a} {fmt(cx + rx)} {fmt(cy)} {a} {fmt(cx - rx)} {fmt(cy)} Z"
    if tag == "rect":
        x, y, w, h = attr(el, "x"), attr(el, "y"), attr(el, "width"), attr(el, "height")
        if w <= 0 or h <= 0:
            return None
        rx, ry = el.get("rx"), el.get("ry")
        rx = attr(el, "rx") if rx else (attr(el, "ry") if ry else 0)
        ry = attr(el, "ry") if ry else rx
        rx, ry = min(rx, w / 2), min(ry, h / 2)
        if rx <= 0 or ry <= 0:
            return f"M {fmt(x)} {fmt(y)} H {fmt(x + w)} V {fmt(y + h)} H {fmt(x)} Z"
        a = f"A {fmt(rx)} {fmt(ry)} 0 0 1"
        return (f"M {fmt(x + rx)} {fmt(y)} H {fmt(x + w - rx)} {a} {fmt(x + w)} {fmt(y + ry)} "
                f"V {fmt(y + h - ry)} {a} {fmt(x + w - rx)} {fmt(y + h)} H {fmt(x + rx)} "
                f"{a} {fmt(x)} {fmt(y + h - ry)} V {fmt(y + ry)} {a} {fmt(x + rx)} {fmt(y)} Z")
    if tag in ("polyline", "polygon"):
        p = nums(el.get("points"))
        if len(p) < 4:
            return None
        pts = [f"{fmt(p[i])} {fmt(p[i + 1])}" for i in range(0, len(p) - 1, 2)]
        return "M " + " L ".join(pts) + (" Z" if tag == "polygon" else "")
    raise Unsupported(tag)


def css_rules(root):
    rules = {}
    for st in root.iter(f"{SVG}style"):
        for sel, body in re.findall(r"([^{}]+)\{([^}]*)\}", st.text or ""):
            props = parse_decls(body)
            for s in sel.split(","):
                s = s.strip()
                if s:
                    rules.setdefault(s, {}).update(props)
    return rules


def parse_decls(s):
    out = {}
    for part in (s or "").split(";"):
        if ":" in part:
            k, v = part.split(":", 1)
            out[k.strip().lower()] = v.strip()
    return out


def style_of(el, inherited, rules):
    st = dict(inherited)
    for k in INHERITED | {"opacity"}:
        if el.get(k) is not None:
            st[k] = el.get(k)
    for c in (el.get("class") or "").split():
        st.update(rules.get("." + c, {}))
    if el.get("id") and "#" + el.get("id") in rules:
        st.update(rules["#" + el.get("id")])
    st.update(parse_decls(el.get("style")))
    return st


def color(v):
    v = (v or "").strip().lower()
    if v in ("", "none", "transparent"):
        return None
    if v in NAMED:
        return "#FF" + NAMED[v]
    m = re.fullmatch(r"#([0-9a-f]{3}|[0-9a-f]{6})", v)
    if m:
        h = m.group(1)
        return "#FF" + ("".join(ch * 2 for ch in h) if len(h) == 3 else h).upper()
    return "#FFFFFFFF"  # currentColor, rgb(), … : line icons are white


def transform_groups(t):
    """SVG transform list → list of VectorDrawable group attribute dicts, outermost first."""
    groups = []
    for name, args in re.findall(r"(matrix|translate|scale|rotate|skewX|skewY)\s*\(([^)]*)\)", t or ""):
        a = nums(args)
        if name == "translate":
            groups.append({"translateX": a[0], "translateY": a[1] if len(a) > 1 else 0})
        elif name == "scale":
            groups.append({"scaleX": a[0], "scaleY": a[1] if len(a) > 1 else a[0]})
        elif name == "rotate":
            g = {"rotation": a[0]}
            if len(a) >= 3:
                g.update(pivotX=a[1], pivotY=a[2])
            groups.append(g)
        elif name == "matrix":
            groups += matrix_groups(*a[:6])
        else:
            raise Unsupported(name)
    return groups


def matrix_groups(a, b, c, d, e, f):
    # M = R(θ1)·S(sx, sy)·R(θ2) (2×2 SVD), applied as an outer group (scale, rotate,
    # translate, which VectorDrawable applies in that order) around an inner rotation.
    if abs(b) < 1e-9 and abs(c) < 1e-9:
        return [{"scaleX": a, "scaleY": d, "translateX": e, "translateY": f}]
    E, F = (a + d) / 2, (a - d) / 2
    G, H = (b + c) / 2, (b - c) / 2
    Q, R = math.hypot(E, H), math.hypot(F, G)
    sx, sy = Q + R, Q - R
    a1, a2 = math.atan2(G, F), math.atan2(H, E)
    t1, t2 = (a2 - a1) / 2, (a2 + a1) / 2
    outer = {"scaleX": sx, "scaleY": sy, "rotation": math.degrees(t2), "translateX": e, "translateY": f}
    inner = {"rotation": math.degrees(t1)}
    return [outer, inner] if abs(t1) > 1e-9 else [outer]


def convert(svg_text, size_dp=192):
    root = ET.fromstring(svg_text)
    vb = nums(root.get("viewBox")) or [0, 0, attr(root, "width", 48), attr(root, "height", 48)]
    if len(vb) != 4 or vb[2] <= 0:
        raise Unsupported("viewBox")
    rules = css_rules(root)
    body = []

    def emit(el, inherited, depth):
        tag = el.tag.replace(SVG, "")
        if tag in SKIP or tag.startswith("{"):  # editor metadata (Inkscape, Illustrator)
            return
        st = style_of(el, inherited, rules)
        groups = transform_groups(el.get("transform"))
        pad = "    " * depth
        for g in groups:
            body.append(pad + "<group " + " ".join(f'android:{k}="{fmt(v)}"' for k, v in g.items()) + ">")
            pad += "    "
        if tag in ("g", "svg", "a"):
            for child in el:
                emit(child, {k: v for k, v in st.items() if k in INHERITED}, depth + len(groups) + (tag != "svg"))
        elif tag in SHAPES:
            d = shape_path(el, tag)
            if d:
                body.append(pad + path_xml(d, st))
        else:
            raise Unsupported(tag)
        for _ in groups:
            pad = pad[:-4]
            body.append(pad + "</group>")

    for child in root:
        emit(child, {}, 1)
    if not body:
        raise Unsupported("empty")
    head = (f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size_dp}dp" android:height="{size_dp}dp"\n'
            f'    android:viewportWidth="{fmt(vb[2])}" android:viewportHeight="{fmt(vb[3])}">')
    if vb[0] or vb[1]:
        body = ["    " + f'<group android:translateX="{fmt(-vb[0])}" android:translateY="{fmt(-vb[1])}">']\
            + ["    " + b for b in body] + ["    </group>"]
    return head + "\n" + "\n".join(body) + "\n</vector>\n"


def path_xml(d, st):
    a = [f'android:pathData="{d}"']
    op = float(st.get("opacity", 1) or 1)
    fill = color(st.get("fill", "#000"))
    stroke = color(st.get("stroke", "none"))
    if fill:
        a.append(f'android:fillColor="{fill}"')
        fa = op * float(st.get("fill-opacity", 1) or 1)
        if fa < 1:
            a.append(f'android:fillAlpha="{fmt(fa)}"')
        if st.get("fill-rule", "").strip() == "evenodd":
            a.append('android:fillType="evenOdd"')
    if stroke:
        a.append(f'android:strokeColor="{stroke}"')
        w = st.get("stroke-width", "1")
        a.append(f'android:strokeWidth="{fmt(float(NUM.match(w).group()) if NUM.match(w) else 1)}"')
        cap = st.get("stroke-linecap", "butt").strip()
        join = st.get("stroke-linejoin", "miter").strip()
        if cap in ("round", "square"):
            a.append(f'android:strokeLineCap="{cap}"')
        if join in ("round", "bevel"):
            a.append(f'android:strokeLineJoin="{join}"')
        if st.get("stroke-miterlimit"):
            a.append(f'android:strokeMiterLimit="{fmt(float(st["stroke-miterlimit"]))}"')
        sa = op * float(st.get("stroke-opacity", 1) or 1)
        if sa < 1:
            a.append(f'android:strokeAlpha="{fmt(sa)}"')
    if not fill and not stroke:
        return "<!-- invisible shape -->"
    return "<path " + " ".join(a) + " />"
