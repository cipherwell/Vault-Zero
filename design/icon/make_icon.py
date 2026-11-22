#!/usr/bin/env python3
"""Generate VaultZero's icon geometry: Android vector layers + SVG sources.

The mark is a zero-shaped keyhole: a fitted ring (the zero of zero-knowledge)
merged into a rounded stem. It is drawn inside Android's 66dp adaptive safe
zone so no launcher mask can clip it, and the monochrome layer is a separate
single-colour silhouette for themed icons.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
DRAWABLE = RES / "drawable"
MIPMAP_V26 = RES / "mipmap-anydpi-v26"
DESIGN = ROOT / "design/icon"

# --- geometry (108x108 adaptive canvas, safe zone = 66dp circle at 54,54) ---
CX, CY = 54.0, 50.0
OUTER_R = 26.0
INNER_R = 16.5
# Tapered keyhole neck: wide where it meets the ring's hole, narrower at the bottom.
STEM_TOP_W = 13.0
STEM_BOTTOM_W = 10.0
STEM_TOP = 58.5
STEM_BOTTOM = 83.5
STEM_R = STEM_BOTTOM_W / 2

# --- palette ---
GRADIENT_START = "#2F6BFF"
GRADIENT_END = "#0A2366"
GLYPH = "#FFFFFF"


def ring_path() -> str:
    """Annulus drawn as two subpaths (even-odd) so the ring stays one clean shape."""
    return (
        f"M{CX - OUTER_R:.2f},{CY:.2f} "
        f"a{OUTER_R},{OUTER_R} 0 1,0 {OUTER_R * 2},{0} "
        f"a{OUTER_R},{OUTER_R} 0 1,0 {-OUTER_R * 2},{0} Z "
        f"M{CX - INNER_R:.2f},{CY:.2f} "
        f"a{INNER_R},{INNER_R} 0 1,1 {INNER_R * 2},{0} "
        f"a{INNER_R},{INNER_R} 0 1,1 {-INNER_R * 2},{0} Z"
    )


def stem_path() -> str:
    """Tapered, rounded-bottom keyhole neck merging into the ring's hole."""
    top_left = CX - STEM_TOP_W / 2
    top_right = CX + STEM_TOP_W / 2
    bottom_left = CX - STEM_BOTTOM_W / 2
    bottom_right = CX + STEM_BOTTOM_W / 2
    bottom_arc_y = STEM_BOTTOM - STEM_R
    return (
        f"M{top_left:.2f},{STEM_TOP:.2f} "
        f"L{top_right:.2f},{STEM_TOP:.2f} "
        f"L{bottom_right:.2f},{bottom_arc_y:.2f} "
        f"a{STEM_R},{STEM_R} 0 0,1 {-STEM_R},{STEM_R} "
        f"a{STEM_R},{STEM_R} 0 0,1 {-STEM_R},{-STEM_R} "
        f"L{bottom_left:.2f},{bottom_arc_y:.2f} Z"
    )


def glyph_svg(fill: str) -> str:
    return (
        f'<path d="{ring_path()}" fill="{fill}" fill-rule="evenodd"/>\n'
        f'  <path d="{stem_path()}" fill="{fill}"/>'
    )


def background_svg() -> str:
    return (
        '<defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">'
        f'<stop offset="0" stop-color="{GRADIENT_START}"/>'
        f'<stop offset="1" stop-color="{GRADIENT_END}"/>'
        '</linearGradient>'
        '<radialGradient id="glow" cx="0.18" cy="0.14" r="0.78">'
        '<stop offset="0" stop-color="#FFFFFF" stop-opacity="0.10"/>'
        '<stop offset="0.55" stop-color="#FFFFFF" stop-opacity="0.04"/>'
        '<stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>'
        "</radialGradient></defs>\n"
        '  <rect width="108" height="108" fill="url(#bg)"/>\n'
        '  <rect width="108" height="108" fill="url(#glow)"/>'
    )


def masked_svg(shape: str) -> str:
    """Legacy/store renders: `square` (squircle), `round` (circle), `full` (no mask)."""
    if shape == "square":
        clip = '<rect x="0" y="0" width="108" height="108" rx="24" ry="24"/>'
    elif shape == "round":
        clip = '<circle cx="54" cy="54" r="54"/>'
    else:
        clip = '<rect x="0" y="0" width="108" height="108"/>'
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{GRADIENT_START}"/>
      <stop offset="1" stop-color="{GRADIENT_END}"/>
    </linearGradient>
    <clipPath id="mask">{clip}</clipPath>
    <radialGradient id="glow" cx="0.18" cy="0.14" r="0.78">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.10"/>
      <stop offset="0.55" stop-color="#FFFFFF" stop-opacity="0.04"/>
      <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <g clip-path="url(#mask)">
    <rect width="108" height="108" fill="url(#bg)"/>
    <rect width="108" height="108" fill="url(#glow)"/>
    {glyph_svg(GLYPH)}
  </g>
</svg>
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
                android:startColor="{GRADIENT_START}"
                android:endColor="{GRADIENT_END}" />
        </aapt:attr>
    </path>
    <!-- Soft depth glow: keeps the mark from looking flat, invisible to themed icons. -->
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


def vector_glyph(name: str) -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {name}: zero-shaped keyhole inside the 66dp adaptive safe zone. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="{GLYPH}"
        android:fillType="evenOdd"
        android:pathData="{ring_path()}" />
    <path
        android:fillColor="{GLYPH}"
        android:pathData="{stem_path()}" />
</vector>
"""


def adaptive(name: str) -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <!-- Android 13+ themed icons: single-colour silhouette, no background. -->
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""


def main() -> None:
    DRAWABLE.mkdir(parents=True, exist_ok=True)
    MIPMAP_V26.mkdir(parents=True, exist_ok=True)
    DESIGN.mkdir(parents=True, exist_ok=True)

    (DRAWABLE / "ic_launcher_background.xml").write_text(vector_background())
    (DRAWABLE / "ic_launcher_foreground.xml").write_text(vector_glyph("Launcher foreground"))
    (DRAWABLE / "ic_launcher_monochrome.xml").write_text(vector_glyph("Themed-icon monochrome"))
    (MIPMAP_V26 / "ic_launcher.xml").write_text(adaptive("square"))
    (MIPMAP_V26 / "ic_launcher_round.xml").write_text(adaptive("round"))

    (DESIGN / "vaultzero-icon-square.svg").write_text(masked_svg("square"))
    (DESIGN / "vaultzero-icon-round.svg").write_text(masked_svg("round"))
    (DESIGN / "vaultzero-icon-full.svg").write_text(masked_svg("full"))
    (DESIGN / "vaultzero-icon-glyph.svg").write_text(
        f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  {glyph_svg("#000000")}
</svg>
"""
    )
    print("wrote vector layers, adaptive icon XML and SVG sources")


if __name__ == "__main__":
    main()
