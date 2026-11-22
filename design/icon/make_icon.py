#!/usr/bin/env python3
"""Generate VaultZero's icon: a zero with a fingerprint whorl inside.

The launcher mark is the zero (vault + "zero-knowledge") holding a fingerprint
(two ridges and a core, gaps aligned on a lower-left delta) — biometrics and
the app's initial in one silhouette. One geometry source feeds the adaptive
vector layers, the monochrome themed-icon layer, legacy renders and the
wordmark lockup used in store/social artwork.

Everything is authored on Android's 108x108 adaptive canvas and stays inside
the 66dp safe circle (centre 54,54), so launcher masks never clip it.
"""
from pathlib import Path
import math

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
DESIGN = ROOT / "design/icon"

CX, CY = 54.0, 50.0
GRADIENT_START = "#2F6BFF"
GRADIENT_END = "#0A2366"
GLYPH = "#FFFFFF"

ZERO_R = 26.2
ZERO_W = 8.6
# (cx, cy, rx, ry, rotation, gap_start_deg, gap_end_deg, stroke_width)
RIDGES = (
    (52.8, 49.2, 17.6, 16.6, -8, 184.5, 239.5, 5.4),
    (54.2, 50.2, 10.2, 9.6, 12, 180.0, 244.0, 4.6),
)
CORE = (53.6, 50.6, 2.1)


def _circle_path(cx: float, cy: float, r: float) -> str:
    return (f"M{cx - r:.2f},{cy:.2f} a{r},{r} 0 1,0 {r * 2:.2f},0 "
            f"a{r},{r} 0 1,0 {-r * 2:.2f},0")


def _ellipse_arc(cx, cy, rx, ry, rot, gap_start, gap_end) -> str:
    """Arc covering everything except [gap_start, gap_end]."""
    def pt(deg):
        a = math.radians(deg)
        x, y = rx * math.cos(a), ry * math.sin(a)
        r = math.radians(rot)
        return (cx + x * math.cos(r) - y * math.sin(r),
                cy + x * math.sin(r) + y * math.cos(r))

    start = gap_end % 360
    end = gap_start % 360
    sweep = (end - start) % 360
    x0, y0 = pt(start)
    x1, y1 = pt(end)
    large = 1 if sweep > 180 else 0
    return (f"M{x0:.2f},{y0:.2f} A{rx},{ry} {rot} {large} 1 {x1:.2f},{y1:.2f}")


def mark_layers() -> list[tuple[str, float]]:
    """(pathData, strokeWidth); strokeWidth 0 means filled."""
    layers: list[tuple[str, float]] = [(_circle_path(CX, CY, ZERO_R), ZERO_W)]
    for cx, cy, rx, ry, rot, gs, ge, w in RIDGES:
        layers.append((_ellipse_arc(cx, cy, rx, ry, rot, gs, ge), w))
    layers.append((_circle_path(CORE[0], CORE[1], CORE[2]), 0.0))
    return layers


def glyph_svg(fill: str) -> str:
    out = []
    for d, w in mark_layers():
        if w == 0:
            out.append(f'<path d="{d}" fill="{fill}"/>')
        else:
            out.append(f'<path d="{d}" fill="none" stroke="{fill}" stroke-width="{w}" '
                       f'stroke-linecap="round"/>')
    return "\n  ".join(out)


# --- Android vector layers -------------------------------------------------
def vector_glyph(comment: str) -> str:
    paths = []
    for d, w in mark_layers():
        if w == 0:
            paths.append(
                f'    <path\n        android:fillColor="{GLYPH}"\n        android:pathData="{d}" />'
            )
        else:
            paths.append(
                f'    <path\n'
                f'        android:fillColor="#00000000"\n'
                f'        android:strokeColor="{GLYPH}"\n'
                f'        android:strokeWidth="{w}"\n'
                f'        android:strokeLineCap="round"\n'
                f'        android:pathData="{d}" />'
            )
    body = "\n".join(paths)
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {comment}. Zero with a fingerprint whorl, inside the 66dp safe zone. -->
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
                android:startColor="{GRADIENT_START}"
                android:endColor="{GRADIENT_END}" />
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
    <!-- Android 13+ themed icons: glyph-only silhouette. -->
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""


# --- SVG renders -----------------------------------------------------------
def _defs(clip: str) -> str:
    return f"""<defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{GRADIENT_START}"/>
      <stop offset="1" stop-color="{GRADIENT_END}"/>
    </linearGradient>
    <radialGradient id="glow" cx="0.18" cy="0.14" r="0.8">
      <stop offset="0" stop-color="#fff" stop-opacity="0.10"/>
      <stop offset="0.6" stop-color="#fff" stop-opacity="0.03"/>
      <stop offset="1" stop-color="#fff" stop-opacity="0"/>
    </radialGradient>
    <clipPath id="mask">{clip}</clipPath>
  </defs>"""


def masked_svg(shape: str) -> str:
    clip = {
        "square": '<rect x="0" y="0" width="108" height="108" rx="24" ry="24"/>',
        "round": '<circle cx="54" cy="54" r="54"/>',
        "full": '<rect x="0" y="0" width="108" height="108"/>',
    }[shape]
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  {_defs(clip)}
  <g clip-path="url(#mask)">
    <rect width="108" height="108" fill="url(#bg)"/>
    <rect width="108" height="108" fill="url(#glow)"/>
    {glyph_svg(GLYPH)}
  </g>
</svg>
"""


def lockup_svg() -> str:
    """Mark + wordmark: store/social artwork only — text is unreadable at launcher size."""
    clip = '<rect x="0" y="0" width="108" height="108" rx="24" ry="24"/>'
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  {_defs(clip)}
  <g clip-path="url(#mask)">
    <rect width="108" height="108" fill="url(#bg)"/>
    <rect width="108" height="108" fill="url(#glow)"/>
    <g transform="translate(54,45) scale(0.78) translate(-54,-50)">
      {glyph_svg(GLYPH)}
    </g>
    <text x="54" y="94" text-anchor="middle" font-family="Inter" font-size="11.5"
          font-weight="800" fill="#FFFFFF" letter-spacing="1.4">VAULTZERO</text>
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
        vector_glyph("Themed-icon monochrome layer"))
    (RES / "mipmap-anydpi-v26/ic_launcher.xml").write_text(adaptive())
    (RES / "mipmap-anydpi-v26/ic_launcher_round.xml").write_text(adaptive())

    (DESIGN / "vaultzero-icon-square.svg").write_text(masked_svg("square"))
    (DESIGN / "vaultzero-icon-round.svg").write_text(masked_svg("round"))
    (DESIGN / "vaultzero-icon-full.svg").write_text(masked_svg("full"))
    (DESIGN / "vaultzero-icon-lockup.svg").write_text(lockup_svg())
    (DESIGN / "vaultzero-icon-glyph.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">'
        f'{glyph_svg("#000000")}</svg>'
    )
    print("wrote adaptive layers, SVGs, and the wordmark lockup")


if __name__ == "__main__":
    main()
