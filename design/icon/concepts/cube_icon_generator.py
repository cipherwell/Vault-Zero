#!/usr/bin/env python3
"""Generate VaultZero's icon: a Chainlink-inspired isometric cube.

A blue wireframe cube on a pale blue ground. Three faces carry the product:
  - top     "VZ" monogram, outlined from Inter and laid into the isometric plane
  - left    fingerprint ridges (biometrics)
  - right   padlock (the vault)

The same geometry is emitted as Android vector layers (background, foreground,
monochrome), SVG renders for legacy/store artwork, and a preview raster set.
Everything is built inside Android's 108dp adaptive canvas and the 66dp safe
circle, so no launcher mask clips it.
"""
from pathlib import Path
import math

from fontTools import ttLib
from fontTools.misc.transform import Transform
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
DESIGN = ROOT / "design/icon"
INTER = "/usr/local/share/fonts/i/Inter_VariableFont_opsz,wght.ttf"

# --- isometric cube (vertices in 108dp canvas) ----------------------------
T1 = (54.0, 22.0)
T2 = (82.0, 38.2)
T3 = (54.0, 54.4)
T4 = (26.0, 38.2)
B1 = (54.0, 82.0)
L2 = (26.0, 65.8)
R2 = (82.0, 65.8)

BLUE = "#2A5ADA"
INK = "#1E3A8A"
FACE = "#FFFFFF"
EDGE_W = 4.6

BG_START = "#FFFFFF"
BG_END = "#E6EFFF"


# --- tiny affine path builder ---------------------------------------------
class Path:
    """Collects M/L/C commands, transforming every point by one matrix."""

    def __init__(self, transform: Transform | None = None):
        self.t = transform or Transform()
        self.cmds: list[tuple[str, list[tuple[float, float]]]] = []

    def _p(self, x, y):
        return self.t.transformPoint((x, y))

    def move(self, x, y):
        self.cmds.append(("M", [self._p(x, y)]))
        return self

    def line(self, x, y):
        self.cmds.append(("L", [self._p(x, y)]))
        return self

    def cubic(self, c1x, c1y, c2x, c2y, x, y):
        self.cmds.append(("C", [self._p(c1x, c1y), self._p(c2x, c2y), self._p(x, y)]))
        return self

    def close(self):
        self.cmds.append(("Z", []))
        return self

    def d(self) -> str:
        out = []
        for op, pts in self.cmds:
            if op == "Z":
                out.append("Z")
            elif op == "M":
                out.append(f"M{pts[0][0]:.3f},{pts[0][1]:.3f}")
            elif op == "L":
                out.append(f"L{pts[0][0]:.3f},{pts[0][1]:.3f}")
            else:
                out.append("C" + " ".join(f"{x:.3f},{y:.3f}" for x, y in pts))
        return "".join(out)


KAPPA = 0.5522847498


def rounded_rect(path: Path, x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    path.move(x + r, y)
    path.line(x + w - r, y)
    path.cubic(x + w - r + KAPPA * r, y, x + w, y + r - KAPPA * r, x + w, y + r)
    path.line(x + w, y + h - r)
    path.cubic(x + w, y + h - r + KAPPA * r, x + w - r + KAPPA * r, y + h, x + w - r, y + h)
    path.line(x + r, y + h)
    path.cubic(x + r - KAPPA * r, y + h, x, y + h - r + KAPPA * r, x, y + h - r)
    path.line(x, y + r)
    path.cubic(x, y + r - KAPPA * r, x + r - KAPPA * r, y, x + r, y)
    path.close()
    return path


def circle(path: Path, cx, cy, r):
    path.move(cx + r, cy)
    path.cubic(cx + r, cy + KAPPA * r, cx + KAPPA * r, cy + r, cx, cy + r)
    path.cubic(cx - KAPPA * r, cy + r, cx - r, cy + KAPPA * r, cx - r, cy)
    path.cubic(cx - r, cy - KAPPA * r, cx - KAPPA * r, cy - r, cx, cy - r)
    path.cubic(cx + KAPPA * r, cy - r, cx + r, cy - KAPPA * r, cx + r, cy)
    path.close()
    return path


# --- face transforms ------------------------------------------------------
TOP_M = Transform((T2[0] - T1[0]) / 100, (T2[1] - T1[1]) / 100,
                  (T4[0] - T1[0]) / 100, (T4[1] - T1[1]) / 100, T1[0], T1[1])
LEFT_M = Transform((T3[0] - T4[0]) / 100, (T3[1] - T4[1]) / 100, 0, 0.24, T4[0], T4[1])
RIGHT_M = Transform((T2[0] - T3[0]) / 100, (T2[1] - T3[1]) / 100, 0, 0.24, T3[0], T3[1])


def vz_path() -> str:
    """'VZ' outline from Inter ExtraBold, laid into the top face."""
    font = ttLib.TTFont(INTER)
    font = instantiateVariableFont(font, {"wght": 700, "opsz": 32})
    upem = font["head"].unitsPerEm
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()

    # Lay out V and Z in em units.
    advance = {}
    for ch in "VZ":
        gname = cmap[ord(ch)]
        advance[ch] = glyphs[gname].width
    gap = 0.12 * upem
    total = advance["V"] + gap + advance["Z"]

    # Scale so the run spans ~74 units of the 100-unit face, baseline at v=72.
    face_w = 68.0
    scale_face = face_w / total
    # centre horizontally, baseline at v=72; translation is in face units
    local = Transform(scale_face, 0, 0, -scale_face, (100 - face_w) / 2, 70)

    pen = SVGPathPen(glyphs)
    x = 0.0
    combined = []
    for ch in "VZ":
        gname = cmap[ord(ch)]
        sub_pen = SVGPathPen(glyphs)
        # place glyph: translate by x then apply local (y flip + scale + placement)
        t = Transform().translate(x, 0)
        glyphs[gname].draw(TransformPen(sub_pen, t))
        combined.append(sub_pen.getCommands())
        x += advance[ch] + gap
    # Re-emit combined commands through the face matrix + local placement.
    out = Path(TOP_M.transform(local))
    from fontTools.pens.recordingPen import RecordingPen
    for commands in combined:
        # SVG commands are absolute; replay them into our Path.
        _replay_svg_commands(out, commands)
    font.close()
    return out.d()


def _replay_svg_commands(path: Path, commands: str):
    """Parse the M/L/C/Z subset emitted by SVGPathPen."""
    import re
    tokens = re.findall(r"[MLCZ]|-?\d+\.?\d*", commands)
    i = 0
    cur = (0.0, 0.0)
    while i < len(tokens):
        tok = tokens[i]
        if tok == "M":
            cur = (float(tokens[i + 1]), float(tokens[i + 2]))
            path.move(*cur)
            i += 3
        elif tok == "L":
            cur = (float(tokens[i + 1]), float(tokens[i + 2]))
            path.line(*cur)
            i += 3
        elif tok == "C":
            path.cubic(float(tokens[i + 1]), float(tokens[i + 2]),
                       float(tokens[i + 3]), float(tokens[i + 4]),
                       float(tokens[i + 5]), float(tokens[i + 6]))
            cur = (float(tokens[i + 5]), float(tokens[i + 6]))
            i += 7
        elif tok == "Z":
            path.close()
            i += 1
        else:
            i += 1


def fingerprint_paths() -> list[str]:
    rows = [(20, 80, 10), (14, 86, 28), (10, 90, 46), (16, 84, 64), (26, 74, 82)]
    paths = []
    for x0, x1, y in rows:
        p = Path(LEFT_M).move(x0, y).line(x1, y)
        paths.append(p.d())
    return paths


def padlock_paths() -> tuple[str, str]:
    body = rounded_rect(Path(RIGHT_M), 21, 46, 58, 42, 10).d()
    shackle = Path(RIGHT_M).move(32, 46).line(32, 37)
    # shoulder arc approximated with cubics
    shackle.cubic(32, 27, 40, 19, 50, 19)
    shackle.cubic(60, 19, 68, 27, 68, 37)
    shackle.line(68, 46)
    key = circle(Path(RIGHT_M), 50, 63, 7.0).d()
    key += rounded_rect(Path(RIGHT_M), 46.5, 66, 7, 14, 3.0).d()
    return body, shackle.d(), key


def cube_face_paths() -> list[str]:
    return [
        f"M{T1[0]},{T1[1]} L{T2[0]},{T2[1]} L{T3[0]},{T3[1]} L{T4[0]},{T4[1]} Z",
        f"M{T4[0]},{T4[1]} L{T3[0]},{T3[1]} L{B1[0]},{B1[1]} L{L2[0]},{L2[1]} Z",
        f"M{T3[0]},{T3[1]} L{T2[0]},{T2[1]} L{R2[0]},{R2[1]} L{B1[0]},{B1[1]} Z",
    ]


def cube_edge_paths() -> list[str]:
    edges = [(T1, T2), (T2, R2), (R2, B1), (B1, L2), (L2, T4), (T4, T1),
             (T2, T3), (T3, T4), (T3, B1)]
    return [f"M{a[0]},{a[1]} L{b[0]},{b[1]}" for a, b in edges]


# --- outputs ---------------------------------------------------------------
def vector_layers() -> tuple[str, str, str]:
    faces = "\n".join(
        f'    <path android:fillColor="{FACE}" android:pathData="{d}" />' for d in cube_face_paths()
    )
    edges = "\n".join(
        f'    <path android:fillColor="#00000000" android:strokeColor="{BLUE}" '
        f'android:strokeWidth="{EDGE_W}" android:strokeLineCap="round" '
        f'android:strokeLineJoin="round" android:pathData="{d}" />'
        for d in cube_edge_paths()
    )
    fingerprint = "\n".join(
        f'    <path android:fillColor="#00000000" android:strokeColor="{BLUE}" '
        f'android:strokeWidth="2.6" android:strokeLineCap="round" android:pathData="{d}" />'
        for d in fingerprint_paths()
    )
    body, shackle, key = padlock_paths()
    lock = (
        f'    <path android:fillColor="{BLUE}" android:pathData="{body}" />\n'
        f'    <path android:fillColor="#00000000" android:strokeColor="{BLUE}" '
        f'android:strokeWidth="2.9" android:strokeLineCap="round" android:pathData="{shackle}" />\n'
        f'    <path android:fillColor="#FFFFFF" android:pathData="{key}" />'
    )
    vz = f'    <path android:fillColor="{INK}" android:pathData="{vz_path()}" />'
    foreground = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Isometric cube: VZ monogram on the top face, fingerprint ridges left, padlock right. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{faces}
{edges}
{vz}
{fingerprint}
{lock}
</vector>
"""
    # Monochrome: wireframe + glyphs only, single colour.
    mono = "\n".join(
        f'    <path android:fillColor="#00000000" android:strokeColor="#FFFFFF" '
        f'android:strokeWidth="{EDGE_W}" android:strokeLineCap="round" '
        f'android:strokeLineJoin="round" android:pathData="{d}" />'
        for d in cube_edge_paths()
    )
    mono += "\n" + "\n".join(
        f'    <path android:fillColor="#00000000" android:strokeColor="#FFFFFF" '
        f'android:strokeWidth="2.6" android:strokeLineCap="round" android:pathData="{d}" />'
        for d in fingerprint_paths()
    )
    mono += (
        f'\n    <path android:fillColor="#FFFFFF" android:pathData="{vz_path()}" />'
        f'\n    <path android:fillColor="#FFFFFF" android:pathData="{body}" />'
        f'\n    <path android:fillColor="#00000000" android:strokeColor="#FFFFFF" '
        f'android:strokeWidth="2.9" android:strokeLineCap="round" android:pathData="{shackle}" />'
    )
    monochrome = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Themed-icon monochrome: cube wireframe + face glyphs. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{mono}
</vector>
"""
    background = f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108"
                android:startColor="{BG_START}"
                android:endColor="{BG_END}" />
        </aapt:attr>
    </path>
</vector>
"""
    return foreground, monochrome, background


def svg(shape: str) -> str:
    clip = {
        "square": '<rect x="0" y="0" width="108" height="108" rx="24" ry="24"/>',
        "round": '<circle cx="54" cy="54" r="54"/>',
        "full": '<rect x="0" y="0" width="108" height="108"/>',
    }[shape]
    faces = "".join(
        f'<path d="{d}" fill="{FACE}"/>' for d in cube_face_paths()
    )
    edges = "".join(
        f'<path d="{d}" fill="none" stroke="{BLUE}" stroke-width="{EDGE_W}" '
        f'stroke-linecap="round" stroke-linejoin="round"/>' for d in cube_edge_paths()
    )
    fingerprint = "".join(
        f'<path d="{d}" fill="none" stroke="{BLUE}" stroke-width="2.6" '
        f'stroke-linecap="round"/>' for d in fingerprint_paths()
    )
    body, shackle, key = padlock_paths()
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{BG_START}"/>
      <stop offset="1" stop-color="{BG_END}"/>
    </linearGradient>
    <clipPath id="mask">{clip}</clipPath>
  </defs>
  <g clip-path="url(#mask)">
    <rect width="108" height="108" fill="url(#bg)"/>
    {faces}
    {edges}
    <path d="{vz_path()}" fill="{INK}"/>
    {fingerprint}
    <path d="{body}" fill="{BLUE}"/>
    <path d="{shackle}" fill="none" stroke="{BLUE}" stroke-width="2.9" stroke-linecap="round"/>
    <path d="{key}" fill="#FFFFFF"/>
  </g>
</svg>
"""


def main() -> None:
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    (RES / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
    DESIGN.mkdir(parents=True, exist_ok=True)

    foreground, monochrome, background = vector_layers()
    (RES / "drawable/ic_launcher_foreground.xml").write_text(foreground)
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(monochrome)
    (RES / "drawable/ic_launcher_background.xml").write_text(background)

    adaptive = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""
    (RES / "mipmap-anydpi-v26/ic_launcher.xml").write_text(adaptive)
    (RES / "mipmap-anydpi-v26/ic_launcher_round.xml").write_text(adaptive)

    for shape in ("square", "round", "full"):
        (DESIGN / f"vaultzero-icon-{shape}.svg").write_text(svg(shape))
    print("wrote cube vector layers and SVGs")


if __name__ == "__main__":
    main()
