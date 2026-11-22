#!/usr/bin/env python3
"""Generate VaultZero's icon: Samsung-One-UI-soft key (V bow, Z tip).

Treatment (studied from Samsung's own app icons): a matte squircle with a
vertical gradient and a light wash from the top, a single white glyph with a
*soft, diffuse* shadow, and one restrained accent colour. No sparkles, no hard
offset shadows, no outlines.

The key keeps the initials: the bow is a loop with a V-shaped opening, the bit
is a Z. Geometry fits Android's 66dp safe circle.

Outputs
  app/src/main/res/drawable/ic_launcher_background.xml   vertical gradient
  app/src/main/res/drawable/ic_launcher_key.xml          white key + cyan V
  app/src/main/res/drawable/ic_launcher_foreground.xml   layer-list: blur shadow + key
  app/src/main/res/drawable/ic_launcher_monochrome.xml   themed-icon silhouette
  app/src/main/res/drawable-xxhdpi/ic_launcher_shadow.png  pre-blurred shadow (432px)
  design/icon/*.svg + preview rasters
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
DESIGN = ROOT / "design/icon"

# --- geometry (v1.7.7 proportions) ----------------------------------------
BOW_C = (54.0, 33.5)
BOW_R = 11.5
V_HOLE = ((47.5, 27.5), (60.5, 27.5), (54.0, 41.0))
SHAFT = (54.0, 44.0, 54.0, 68.5)
SHAFT_W = 7.5
Z_PTS = ((47.5, 67.5), (60.5, 67.5), (47.5, 81.5), (60.5, 81.5))
Z_W = 5.5
SCALE = 0.95
OFFSET = (1.7, 2.8)

INK = "#FFFFFF"
ACCENT = "#6FE3FF"
SHADOW = "#071C4A"
BG_TOP = "#5C8DFF"
BG_MID = "#2F6BFF"
BG_BOTTOM = "#1A44C4"


def bow_path() -> str:
    cx, cy = BOW_C
    r = BOW_R
    (x0, y0), (x1, y1), (x2, y2) = V_HOLE
    return (f"M{cx - r},{cy} a{r},{r} 0 1,0 {r * 2},0 "
            f"a{r},{r} 0 1,0 {-r * 2},0 Z "
            f"M{x0},{y0} L{x1},{y1} L{x2},{y2} Z")


def v_path() -> str:
    (x0, y0), (x1, y1), (x2, y2) = V_HOLE
    return f"M{x0},{y0} L{x1},{y1} L{x2},{y2} Z"


def v_patch_path() -> str:
    """V hole in final (scaled) coordinates, used to hide the shadow behind it."""
    def t(p):
        return (54 + (p[0] - 54) * SCALE, 54 + (p[1] - 54) * SCALE)
    (x0, y0), (x1, y1), (x2, y2) = (t(p) for p in V_HOLE)
    return f"M{x0:.2f},{y0:.2f} L{x1:.2f},{y1:.2f} L{x2:.2f},{y2:.2f} Z"


def shaft_path() -> str:
    return f"M{SHAFT[0]},{SHAFT[1]} L{SHAFT[2]},{SHAFT[3]}"


def z_path() -> str:
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = Z_PTS
    return f"M{x0},{y0} L{x1},{y1} L{x2},{y2} L{x3},{y3}"


SCALE_TX = 54 * (1 - SCALE)


def vector_key(comment: str) -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {comment}. V bow, Z tip, inside the 66dp safe circle. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="{v_patch_path()}">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear"
                android:startX="0" android:startY="0" android:endX="0" android:endY="108">
                <item android:color="{BG_TOP}" android:offset="0.0" />
                <item android:color="{BG_MID}" android:offset="0.55" />
                <item android:color="{BG_BOTTOM}" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
    <group android:scaleX="{SCALE}" android:scaleY="{SCALE}" android:pivotX="54" android:pivotY="54">
        <path android:fillColor="{INK}" android:fillType="evenOdd" android:pathData="{bow_path()}" />
        <path android:fillColor="#00000000" android:strokeColor="{INK}"
            android:strokeWidth="{SHAFT_W}" android:strokeLineCap="round"
            android:pathData="{shaft_path()}" />
        <path android:fillColor="#00000000" android:strokeColor="{INK}"
            android:strokeWidth="{Z_W}" android:strokeLineCap="round" android:strokeLineJoin="round"
            android:pathData="{z_path()}" />
    </group>
</vector>
"""


def vector_monochrome() -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Themed-icon monochrome: key silhouette, V as a real hole. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="{v_patch_path()}">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear"
                android:startX="0" android:startY="0" android:endX="0" android:endY="108">
                <item android:color="{BG_TOP}" android:offset="0.0" />
                <item android:color="{BG_MID}" android:offset="0.55" />
                <item android:color="{BG_BOTTOM}" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
    <group android:scaleX="{SCALE}" android:scaleY="{SCALE}" android:pivotX="54" android:pivotY="54">
        <path android:fillColor="{INK}" android:fillType="evenOdd" android:pathData="{bow_path()}" />
        <path android:fillColor="#00000000" android:strokeColor="{INK}"
            android:strokeWidth="{SHAFT_W}" android:strokeLineCap="round"
            android:pathData="{shaft_path()}" />
        <path android:fillColor="#00000000" android:strokeColor="{INK}"
            android:strokeWidth="{Z_W}" android:strokeLineCap="round" android:strokeLineJoin="round"
            android:pathData="{z_path()}" />
    </group>
</vector>
"""


def layer_list() -> str:
    return """<?xml version="1.0" encoding="utf-8"?>
<!-- Soft blurred shadow (bitmap) under the vector key: Samsung-style depth. -->
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:gravity="fill" android:drawable="@drawable/ic_launcher_shadow" />
    <item android:gravity="fill" android:drawable="@drawable/ic_launcher_key" />
</layer-list>
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
            <gradient android:type="linear"
                android:startX="0" android:startY="0" android:endX="0" android:endY="108">
                <item android:color="{BG_TOP}" android:offset="0.0" />
                <item android:color="{BG_MID}" android:offset="0.55" />
                <item android:color="{BG_BOTTOM}" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial"
                android:centerX="32" android:centerY="6" android:gradientRadius="98"
                android:startColor="#38FFFFFF" android:centerColor="#0DFFFFFF"
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


# --- SVG sources (legacy + store artwork, with a real blur) ----------------
def key_svg(color: str) -> str:
    return (f'<path d="{bow_path()}" fill="{color}" fill-rule="evenodd"/>'
            f'<path d="{shaft_path()}" fill="none" stroke="{color}" stroke-width="{SHAFT_W}" stroke-linecap="round"/>'
            f'<path d="{z_path()}" fill="none" stroke="{color}" stroke-width="{Z_W}" stroke-linecap="round" stroke-linejoin="round"/>'
            )


def svg(shape: str) -> str:
    clip = {
        "square": '<rect x="0" y="0" width="108" height="108" rx="24" ry="24"/>',
        "round": '<circle cx="54" cy="54" r="54"/>',
        "full": '<rect x="0" y="0" width="108" height="108"/>',
    }[shape]
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <defs>
    <linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="108">
      <stop offset="0" stop-color="{BG_TOP}"/>
      <stop offset="0.55" stop-color="{BG_MID}"/>
      <stop offset="1" stop-color="{BG_BOTTOM}"/>
    </linearGradient>
    <radialGradient id="toplight" cx="0.3" cy="0.05" r="0.9">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.22"/>
      <stop offset="0.6" stop-color="#FFFFFF" stop-opacity="0.05"/>
      <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
    </radialGradient>
    <filter id="soft" x="-40%" y="-40%" width="180%" height="180%">
      <feGaussianBlur stdDeviation="2.6"/>
    </filter>
    <clipPath id="mask">{clip}</clipPath>
  </defs>
  <g clip-path="url(#mask)">
    <rect width="108" height="108" fill="url(#bg)"/>
    <rect width="108" height="108" fill="url(#toplight)"/>
    <g transform="translate({SCALE_TX:.2f},{SCALE_TX:.2f}) scale({SCALE})">
      <g transform="translate({OFFSET[0]},{OFFSET[1]})" filter="url(#soft)" opacity="0.5">
        {key_svg(SHADOW)}
      </g>
    </g>
    <path d="{v_patch_path()}" fill="url(#bg)"/>
    <g transform="translate({SCALE_TX:.2f},{SCALE_TX:.2f}) scale({SCALE})">
      {key_svg(INK)}
    </g>
  </g>
</svg>
"""


def shadow_svg() -> str:
    """Transparent 108-canvas shadow, rasterised for the adaptive foreground."""
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <defs>
    <filter id="soft" x="-40%" y="-40%" width="180%" height="180%">
      <feGaussianBlur stdDeviation="2.6"/>
    </filter>
  </defs>
  <g transform="translate({SCALE_TX:.2f},{SCALE_TX:.2f}) scale({SCALE})">
    <g transform="translate({OFFSET[0]},{OFFSET[1]})" filter="url(#soft)" opacity="0.5">
      {key_svg(SHADOW)}
    </g>
  </g>
</svg>
"""


def main() -> None:
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    (RES / "drawable-xxhdpi").mkdir(parents=True, exist_ok=True)
    (RES / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
    DESIGN.mkdir(parents=True, exist_ok=True)

    (RES / "drawable/ic_launcher_background.xml").write_text(vector_background())
    (RES / "drawable/ic_launcher_key.xml").write_text(vector_key("Key glyph"))
    (RES / "drawable/ic_launcher_foreground.xml").write_text(layer_list())
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(vector_monochrome())
    (RES / "mipmap-anydpi-v26/ic_launcher.xml").write_text(adaptive())
    (RES / "mipmap-anydpi-v26/ic_launcher_round.xml").write_text(adaptive())

    for shape in ("square", "round", "full"):
        (DESIGN / f"vaultzero-icon-{shape}.svg").write_text(svg(shape))

    # Pre-blurred shadow for the adaptive foreground (432px = 108dp @ 4x).
    shadow_svg_path = DESIGN / "vaultzero-shadow.svg"
    shadow_svg_path.write_text(shadow_svg())
    subprocess.run(
        ["rsvg-convert", "-w", "432", "-h", "432", str(shadow_svg_path),
         "-o", str(RES / "drawable-xxhdpi/ic_launcher_shadow.png")],
        check=True,
    )
    print("wrote Samsung-soft key layers, shadow bitmap and SVGs")


if __name__ == "__main__":
    main()
