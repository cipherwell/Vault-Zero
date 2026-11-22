# VaultZero icon source

The launcher mark is a **zero-shaped keyhole**: a fitted ring (the zero of zero-knowledge) merged
into a rounded, slightly tapered stem. It is generated from one geometry script so every asset stays
in sync.

## Files

| File | Purpose |
| --- | --- |
| `make_icon.py` | Single source of truth: writes the Android vector layers, adaptive-icon XML and the SVGs below |
| `vaultzero-icon-square.svg` | Legacy squircle render (used for mipmaps, store icon, F-Droid repo icon) |
| `vaultzero-icon-round.svg` | Circular legacy render (`ic_launcher_round`) |
| `vaultzero-icon-full.svg` | Full-bleed square (512 px store artwork) |
| `vaultzero-icon-glyph.svg` | Glyph-only silhouette (design reference / monochrome check) |
| `icon-preview-sheet.png` | Rendered review sheet: masks, density ladder, monochrome |

## Why it fits any launcher

- **Adaptive layers**: `ic_launcher_background` (full-bleed gradient) + `ic_launcher_foreground`
  (glyph only). The glyph is inside the 66 dp safe circle, so circle, squircle, rounded-square,
  teardrop and OEM masks never clip it.
- **Monochrome layer**: `ic_launcher_monochrome` is a separate single-colour silhouette, so
  Android 13+ themed icons tint correctly instead of reusing the coloured artwork.
- **Legacy fallbacks**: squircle and round PNGs at mdpi–xxxhdpi for launchers and icon packs that
  ignore adaptive icons.
- **No baked-in mask or text**: the mark is vector geometry, legible from 24 px to 512 px.

## Regenerate

```bash
python3 design/icon/make_icon.py
# rasterise (needs librsvg)
for d in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do ...; done
```

The gradient is `#2F6BFF → #0A2366` with a soft corner glow; the glyph is pure white for maximum
contrast on any wallpaper.
