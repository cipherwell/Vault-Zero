#!/usr/bin/env python3
"""Generate VaultZero's icon: a key whose bow is a V and whose tip is a Z.

A classic key silhouette carries the initials:
  - the bow (the part you hold) is a round loop with a **V-shaped opening**
  - the bit/tip is drawn as a **Z**

Everything is vector geometry on Android's 108dp adaptive canvas, kept inside
the 66dp safe circle, with a separate monochrome layer for themed icons. The
same source emits the adaptive layers, legacy squircle/round renders and the
512px store artwork.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
DESIGN = ROOT / "design/icon"

BG_START = "#2F6BFF"
BG_END = "#0A2366"
GLYPH = "#FFFFFF"

# --- geometry (fits y=21..86.5, x=39..69 inside the 66dp safe circle) -----
BOW_C = (54.0, 36.0)
BOW_R = 15.0
V_HOLE = ((46.0, 28.0), (62.0, 28.0), (54.0, 45.0))
SHAFT_TOP = 50.0
SHAFT_BOTTOM = 68.0
SHAFT_W = 9.0
Z_TOP = 68.0
Z_BOTTOM = 83.0
Z_LEFT = 41.0
Z_RIGHT = 67.0
Z_W = 7.0


def bow_path() -> str:
    cx, cy = BOW_C
    r = BOW_R
    circle = (f"M{cx - r},{cy} a{r},{r} 0 1,0 {r * 2},0 "
              f"a{r},{r} 0 1,0 {-r * 2},0 Z")
    (x0, y0), (x1, y1), (x2, y2) = V_HOLE
    hole = f"M{x0},{y0} L{x1},{y1} L{x2},{y2} Z"
    return f"{circle} {hole}"


def shaft_path() -> str:
    return f"M{BOW_C[0]},{SHAFT_TOP} L{BOW_C[0]},{SHAFT_BOTTOM}"


def z_path() -> str:
    return (f"M{Z_LEFT},{Z_TOP} L{Z_RIGHT},{Z_TOP} "
            f"L{Z_LEFT},{Z_BOTTOM} L{Z_RIGHT},{Z_BOTTOM}")


# --- Android vector layers -------------------------------------------------
def vector_glyph(comment: str, monochrome: bool = False) -> str:
    color = GLYPH
    body = f"""    <path
        android:fillColor="{color}"
        android:fillType="evenOdd"
        android:pathData="{bow_path()}" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="{color}"
        android:strokeWidth="{SHAFT_W}"
        android:strokeLineCap="round"
        android:pathData="{shaft_path()}" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="{color}"
        android:strokeWidth="{Z_W}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="{z_path()}" />"""
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {comment}. Key: V-shaped bow opening, Z-shaped tip, inside the 66dp safe zone. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{body}
</vector>
"""


def vector_background() -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
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
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="19"
                android:centerY="15"
                android:gradientRadius="84"
                android:startColor="#1AFFFFFF"
                android:centerColor="#06FFFFFF"
                android:endColor="#00FFFFFF" />
        </aapt:attr>
    </path>
</vector>
"""


def adaptive() -> str:
    return """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <!-- Android 13+ themed icons: single-colour silhouette. -->
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""


# --- SVG renders -----------------------------------------------------------
def glyph_svg() -> str:
    return f"""    <path d="{bow_path()}" fill="{GLYPH}" fill-rule="evenodd"/>
    <path d="{shaft_path()}" fill="none" stroke="{GLYPH}" stroke-width="{SHAFT_W}" stroke-linecap="round"/>
    <path d="{z_path()}" fill="none" stroke="{GLYPH}" stroke-width="{Z_W}" stroke-linecap="round" stroke-linejoin="round"/>"""


def svg(shape: str) -> str:
    clip = {
        "square": '<rect x="0" y="0" width="108" height="108" rx="24" ry="24"/>',
        "round": '<circle cx="54" cy="54" r="54"/>',
        "full": '<rect x="0" y="0" width="108" height="108"/>',
    }[shape]
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{BG_START}"/>
      <stop offset="1" stop-color="{BG_END}"/>
    </linearGradient>
    <radialGradient id="glow" cx="0.18" cy="0.14" r="0.8">
      <stop offset="0" stop-color="#fff" stop-opacity="0.10"/>
      <stop offset="0.6" stop-color="#fff" stop-opacity="0.03"/>
      <stop offset="1" stop-color="#fff" stop-opacity="0"/>
    </radialGradient>
    <clipPath id="mask">{clip}</clipPath>
  </defs>
  <g clip-path="url(#mask)">
    <rect width="108" height="108" fill="url(#bg)"/>
    <rect width="108" height="108" fill="url(#glow)"/>
{glyph_svg()}
  </g>
</svg>
"""


def main() -> None:
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    (RES / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
    DESIGN.mkdir(parents=True, exist_ok=True)

    (RES / "drawable/ic_launcher_background.xml").write_text(vector_background())
    (RES / "drawable/ic_launcher_foreground.xml").write_text(
        vector_glyph("Launcher foreground"))
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(
        vector_glyph("Themed-icon monochrome layer", monochrome=True))
    (RES / "mipmap-anydpi-v26/ic_launcher.xml").write_text(adaptive())
    (RES / "mipmap-anydpi-v26/ic_launcher_round.xml").write_text(adaptive())

    for shape in ("square", "round", "full"):
        (DESIGN / f"vaultzero-icon-{shape}.svg").write_text(svg(shape))
    (DESIGN / "vaultzero-icon-glyph.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">\n'
        f'{glyph_svg().replace(GLYPH, "#000000")}\n</svg>'
    )
    print("wrote key vector layers and SVGs")


if __name__ == "__main__":
    main()
