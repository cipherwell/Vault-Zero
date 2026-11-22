#!/usr/bin/env python3
"""Generate VaultZero's icon: a living key — V bow, Z tip, flat two-tone depth.

Design language (Google-product flat): one bold silhouette, a flat offset
shadow for depth, a single accent colour (cyan V) and a sparkle for energy.
The key is tilted 6° so it reads as an object rather than a glyph.

The initials are the structure: the bow is a loop with a V-shaped opening and
the bit is a Z. All geometry stays inside Android's 66dp safe circle; the
monochrome layer keeps the V as a real hole so themed icons still read.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
DESIGN = ROOT / "design/icon"

BG_START = "#2F6BFF"
BG_END = "#0A2366"
INK = "#FFFFFF"
SHADOW = "#0B2F7A"
ACCENT = "#6FE3FF"
TILT = 0.0
SCALE = 0.92

BOW_C = (54.0, 35.0)
BOW_R = 13.0
V_HOLE = ((47.0, 28.0), (61.0, 28.0), (54.0, 43.0))
SHAFT = (54.0, 45.0, 54.0, 70.0)
SHAFT_W = 8.0
Z_PTS = ((38.0, 70.0), (70.0, 70.0), (38.0, 83.5), (70.0, 83.5))
Z_W = 6.5
OFFSET = (1.2, 1.7)
SPARKLE_BIG = (65.5, 27.5, 4.0)
SPARKLE_SMALL = (72.5, 35.0, 2.3)


def bow_path() -> str:
    cx, cy = BOW_C
    r = BOW_R
    circle = (f"M{cx - r},{cy} a{r},{r} 0 1,0 {r * 2},0 "
              f"a{r},{r} 0 1,0 {-r * 2},0 Z")
    (x0, y0), (x1, y1), (x2, y2) = V_HOLE
    hole = f"M{x0},{y0} L{x1},{y1} L{x2},{y2} Z"
    return f"{circle} {hole}"


def v_triangle_path() -> str:
    (x0, y0), (x1, y1), (x2, y2) = V_HOLE
    return f"M{x0},{y0} L{x1},{y1} L{x2},{y2} Z"


def shaft_path() -> str:
    return f"M{SHAFT[0]},{SHAFT[1]} L{SHAFT[2]},{SHAFT[3]}"


def z_path() -> str:
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = Z_PTS
    return f"M{x0},{y0} L{x1},{y1} L{x2},{y2} L{x3},{y3}"


def sparkle_path(cx: float, cy: float, r: float, k: float = 0.22) -> str:
    return (f"M{cx},{cy - r} C{cx + k * r},{cy - k * r} {cx + k * r},{cy - k * r} {cx + r},{cy} "
            f"C{cx + k * r},{cy + k * r} {cx + k * r},{cy + k * r} {cx},{cy + r} "
            f"C{cx - k * r},{cy + k * r} {cx - k * r},{cy + k * r} {cx - r},{cy} "
            f"C{cx - k * r},{cy - k * r} {cx - k * r},{cy - k * r} {cx},{cy - r} Z")


def _key_paths(color: str) -> str:
    return (
        f'        <path android:fillColor="{color}" android:fillType="evenOdd" '
        f'android:pathData="{bow_path()}" />\n'
        f'        <path android:fillColor="#00000000" android:strokeColor="{color}" '
        f'android:strokeWidth="{SHAFT_W}" android:strokeLineCap="round" '
        f'android:pathData="{shaft_path()}" />\n'
        f'        <path android:fillColor="#00000000" android:strokeColor="{color}" '
        f'android:strokeWidth="{Z_W}" android:strokeLineCap="round" '
        f'android:strokeLineJoin="round" android:pathData="{z_path()}" />'
    )


def vector_foreground() -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Living key: V bow, Z tip, flat offset shadow, cyan V and sparkles. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group android:rotation="{TILT}" android:scaleX="{SCALE}" android:scaleY="{SCALE}" android:pivotX="54" android:pivotY="54">
    <group android:translateX="{OFFSET[0]}" android:translateY="{OFFSET[1]}">
{_key_paths(SHADOW)}
    </group>
{_key_paths(INK)}
    <path android:fillColor="{ACCENT}" android:pathData="{v_triangle_path()}" />
    <path android:fillColor="{INK}" android:pathData="{sparkle_path(*SPARKLE_BIG)}" />
    <path android:fillColor="{ACCENT}" android:pathData="{sparkle_path(*SPARKLE_SMALL)}" />
    </group>
</vector>
"""


def vector_monochrome() -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Themed-icon monochrome: key silhouette with the V as a real hole. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group android:rotation="{TILT}" android:scaleX="{SCALE}" android:scaleY="{SCALE}" android:pivotX="54" android:pivotY="54">
{_key_paths(INK)}
    <path android:fillColor="{INK}" android:pathData="{sparkle_path(*SPARKLE_BIG)}" />
    </group>
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
                android:centerX="21"
                android:centerY="15"
                android:gradientRadius="86"
                android:startColor="#29FFFFFF"
                android:centerColor="#08FFFFFF"
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
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""


def glyph_svg(monochrome: bool = False) -> str:
    parts = [f'<g transform="translate({OFFSET[0]},{OFFSET[1]})" fill="{SHADOW}" stroke="{SHADOW}">'
             f'<path d="{bow_path()}" fill-rule="evenodd" stroke="none"/>'
             f'<path d="{shaft_path()}" fill="none" stroke-width="{SHAFT_W}" stroke-linecap="round"/>'
             f'<path d="{z_path()}" fill="none" stroke-width="{Z_W}" stroke-linecap="round" stroke-linejoin="round"/>'
             f'</g>']
    if monochrome:
        parts = []
    parts.append(
        f'<g fill="{INK}" stroke="{INK}">'
        f'<path d="{bow_path()}" fill-rule="evenodd" stroke="none"/>'
        f'<path d="{shaft_path()}" fill="none" stroke-width="{SHAFT_W}" stroke-linecap="round"/>'
        f'<path d="{z_path()}" fill="none" stroke-width="{Z_W}" stroke-linecap="round" stroke-linejoin="round"/>'
        "</g>"
    )
    if not monochrome:
        parts.append(f'<path d="{v_triangle_path()}" fill="{ACCENT}"/>')
    parts.append(f'<path d="{sparkle_path(*SPARKLE_BIG)}" fill="{INK}"/>')
    if not monochrome:
        parts.append(f'<path d="{sparkle_path(*SPARKLE_SMALL)}" fill="{ACCENT}"/>')
    return "\n".join(parts)


def svg(shape: str, monochrome: bool = False) -> str:
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
    <radialGradient id="glow" cx="0.19" cy="0.14" r="0.86">
      <stop offset="0" stop-color="#fff" stop-opacity="0.16"/>
      <stop offset="0.55" stop-color="#fff" stop-opacity="0.04"/>
      <stop offset="1" stop-color="#fff" stop-opacity="0"/>
    </radialGradient>
    <clipPath id="mask">{clip}</clipPath>
  </defs>
  <g clip-path="url(#mask)">
    <rect width="108" height="108" fill="url(#bg)"/>
    <rect width="108" height="108" fill="url(#glow)"/>
    <g transform="translate(4.32,4.32) scale({SCALE})">
{glyph_svg(monochrome)}
    </g>
  </g>
</svg>
"""


def main() -> None:
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    (RES / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
    DESIGN.mkdir(parents=True, exist_ok=True)

    (RES / "drawable/ic_launcher_background.xml").write_text(vector_background())
    (RES / "drawable/ic_launcher_foreground.xml").write_text(vector_foreground())
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(vector_monochrome())
    (RES / "mipmap-anydpi-v26/ic_launcher.xml").write_text(adaptive())
    (RES / "mipmap-anydpi-v26/ic_launcher_round.xml").write_text(adaptive())

    for shape in ("square", "round", "full"):
        (DESIGN / f"vaultzero-icon-{shape}.svg").write_text(svg(shape))
    (DESIGN / "vaultzero-icon-glyph.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">\n'
        f'{glyph_svg(monochrome=True)}\n</svg>'
    )
    print("wrote living-key vector layers and SVGs")


if __name__ == "__main__":
    main()
