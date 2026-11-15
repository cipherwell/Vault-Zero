# Design system — One UI inspired

VaultZero's interface follows Samsung's One UI design language (One UI 7–8.5 era):
a calm, high-contrast interface built from two grounds, one accent, generously
rounded containers and a thumb-reach layout. This file records the tokens so the
app stays coherent as it grows.

## Sources studied

- One UI design principles & component guidance (community One UI kit):
  <https://github.com/lavazlife/one-ui-kit-galaxy/blob/main/docs/guidelines.md>
  — 8dp grid, system-font fallback for Samsung One, true-black dark theme, blue
  accent, pill buttons, 48dp touch targets, bottom-weighted navigation.
- Visual analysis of One UI as a style:
  <https://indexstyle.org/styles/one-ui> — grounds `#FAFAFA` / `#000000`, a single
  blue accent, 12–26dp rounded containers, ≥24dp side margins, title high /
  controls low screen split, grouped cards instead of ruled lists.
- One UI 8 / 8.5 coverage (glassier floating chrome direction):
  <https://www.sammobile.com/news/samsung-one-ui-8-everything-to-know/> and
  <https://www.sammobile.com/news/complete-samsung-one-ui-8-guide-galaxy/>
- SamMobile One UI 8.5 beta reporting on translucent ("liquid glass") surfaces:
  <https://phandroid.com/2025/12/10/one-ui-8-5-beta-shows-samsung-copying-apples-glassy-iphone-look/>

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

**Shapes** — 10 / 14 / 20 / 26 / 30dp; pill (999dp) for every pressable chip or
button; bottom sheets are full-width with 30dp top corners and a grab handle.

**Type** — one neutral system sans (Roboto on Android) for everything; Samsung
One is not redistributable, and One UI's own guidelines specify a system-font
fallback. Scale: 34 bold display (screen titles), 26 bold section, 22 bold card
title, 16 medium row title, 14 body, 12.5 caption, 11 label; tight negative
tracking on large sizes. Monospace (JetBrains Mono) only for keys, codes and
addresses.

**Layout** — 20dp content margins, 8dp grid, grouped list containers with
hairline inset dividers and chevrons, title in the upper area, controls
(floating pill bottom navigation, header actions) within thumb reach.

**Appearance** — System / Light / Dark selector in Settings, persisted; system bar
icon appearance follows the resolved theme.

## Interaction patterns

- **Collapsing large title** (vault list): the 34sp `Vault` title and its status
  line shrink/fade to a compact 20sp bar once the list scrolls (~56px threshold),
  while the search field and filter chips stay pinned. One UI's "expanded /
  collapsed, never in between" title rule.
- **Grouped sections**: when Group-by-type is on, each type renders a section
  label above its own rounded card (One UI-style grouped settings list).
- **Bottom sheets**: item detail is a full-width sheet with a grab handle, 30dp
  top corners, a dim scrim that also dismisses on tap (separate sibling, so sheet
  buttons keep their clicks).
- **Haptics**: long-press enters multi-select with `HapticFeedbackType.LongPress`.
- **Toast**: status/copy feedback is a neutral `surfaceContainerHighest` capsule
  with a blue check, not a bordered neon pill.
