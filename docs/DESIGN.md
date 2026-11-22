# Design system — One UI inspired

VaultZero uses a Samsung One UI-inspired language: a calm, high-contrast interface built from two
grounds, one accent, generously rounded containers and a thumb-reach layout. Tokens live in
`app/src/main/java/com/example/ui/theme/`; never hardcode colours or blur values in screens.

## Sources studied

- One UI as a visual style: <https://indexstyle.org/styles/one-ui> — grounds `#FAFAFA` / `#000000`,
  a single blue accent, 12–26 dp rounded containers, ≥24 dp side margins, title-high/controls-low
  split, grouped cards instead of ruled lists.
- One UI kit guidelines: <https://github.com/lavazlife/one-ui-kit-galaxy/blob/main/docs/guidelines.md>
  — 8 dp grid, system-font fallback for Samsung One, true-black dark theme, blue accent, pill
  buttons, 48 dp targets, bottom-weighted navigation.
- One UI 8/8.5 coverage (translucent floating chrome): SamMobile and Phandroid articles.
- The app's own icon/mark and screenshots define the brand; see below.

## Tokens

| Token | Dark | Light |
| --- | --- | --- |
| Ground | `#000000` (true black, OLED) | `#FAFAFA` |
| Card / container | `#121214` | `#FFFFFF` |
| Raised container | `#1C1C1E` | `#F2F2F7` |
| Hairline / outline | `#2C2C2E` | `#E5E5EA` |
| Accent (single blue) | `#4DA6FF` | `#1976D2` |
| Primary text | `#FFFFFF` | `#000000` |
| Secondary text | `#A1A1A6` | `#6B6B70` |
| Danger / warning / success | `#FF453A` / `#FFB74D` / `#4CAF50` | semantic only |

**Shapes:** 10/14/20/26/30 dp ramp; pill (999 dp) for every pressable chip/button; full-width
bottom sheets with 30 dp top corners and a grab handle; grouped list cards use 26 dp.

**Typography:** one neutral system sans (Roboto fallback; Samsung One is not redistributable),
tight tracking on large sizes. Display 34 bold / 30 / 26 / 22; titles 19/16; body 15/14/12.5;
labels 14/12/11/10. Mono (JetBrains Mono) only for keys, codes and addresses.

**Layout:** 20 dp content margins, 8 dp grid, grouped containers with hairline inset dividers and
chevrons. Title high, controls (floating pill nav, header actions) within thumb reach.

**Motion:** short (~150–250 ms) fades/slides; the large `Vault` title collapses to 20 sp past a
~56 px scroll threshold while search/chips stay pinned; reveal/undo animations are brief.

## App icon (v1.7.7)

The mark is a **living key**: the bow (the part you hold) is a loop with a **V-shaped opening**, the
bit is a **Z**, tilted and lit like a product logo rather than a pictogram.

| Spec | Value |
| --- | --- |
| Adaptive canvas | 108 × 108 dp; all glyph geometry inside the 66 dp safe circle (0.95 scale) |
| Background | full-bleed linear gradient `#2F6BFF → #0A2366` (135°) + soft corner glow |
| Foreground | white key (bow Ø 23, shaft 23, bit 16 × 18), flat offset shadow `#0B2F7A` (1.2, 1.7), cyan `#6FE3FF` V, two sparkles |
| Monochrome | separate layer: key silhouette with the V as a real hole + sparkle |
| Legacy | squircle (`ic_launcher`) and round (`ic_launcher_round`) PNGs, mdpi–xxxhdpi |
| Store/social | 512 px full-bleed icon + rebuilt 1024 × 500 feature graphic |
| Source | `design/icon/make_icon.py` (geometry) + `icon-preview-sheet.png` |

Rules learned here: no content outside the safe circle (launchers clip sparkles), letterforms must be
structure (V as the bow opening, Z as the bit), and depth stays flat — an offset shadow, never a
gradient.

## Screen inventory

| Screen | Key elements |
| --- | --- |
| Lock | fingerprint, master-password field, unlock CTA, biometric button, recovery entry |
| Vault list | collapsing title, search, filter chips, grouped card rows, inline 2FA, selection bar, undo pill, FAB replaced by header + |
| Detail sheet | grab handle, masked fields, strength, TOTP, copy/reveal, delete/edit pills |
| Editor | per-type fields, strength meter, save pill, discard-changes guard |
| Generator | live credential card, mode chips, sliders/toggles, passphrase options, save/copy, history |
| Audit | score ring, metric pills, finding cards with FIX |
| Settings | appearance, security controls, recovery/backup, specs, danger zone |
| 2FA dashboard | live codes per login, countdown, copy |

History and current screenshots: [SCREENSHOTS.md](SCREENSHOTS.md).

## Accessibility

- Icon-only actions carry `contentDescription`; chips use selectable/RadioButton semantics.
- Text contrast uses theme tokens (avoid alpha-only fixes).
- Touch targets: nav items and icon buttons at least 40–48 dp where layout allows.
- TalkBack passes are part of the manual QA checklist; see [TESTING.md](TESTING.md).
