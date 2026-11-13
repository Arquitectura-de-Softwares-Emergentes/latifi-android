---
name: LatiFi Wallet
description: A calm navy money app with a lime pulse of life, built as a token layer over Material 3.
colors:
  navy: "#0B1F3A"
  navy-700: "#14407A"
  lime: "#C6FF3D"
  lime-soft: "#D8FF7A"
  green: "#0A7D5A"
  ink: "#101828"
  ink-variant: "#566174"
  surface: "#F5F7FA"
  background: "#FFFFFF"
  info-container: "#E8EFF9"
  success-container: "#E6F4EF"
  warning: "#B45309"
  warning-container: "#FEF3E2"
  error: "#C62828"
  error-container: "#FDECEC"
  lime-tint: "#F3FFD1"
  on-hero-variant: "#C9D6EA"
  dark-background: "#0B0F14"
  dark-surface: "#171C24"
  dark-hero-start: "#163C6E"
typography:
  display:
    fontFamily: "Sora, sans-serif"
    fontSize: "32sp"
    fontWeight: 700
    lineHeight: "40sp"
  headline:
    fontFamily: "Sora, sans-serif"
    fontSize: "24sp"
    fontWeight: 600
    lineHeight: "32sp"
  title:
    fontFamily: "Sora, sans-serif"
    fontSize: "18sp"
    fontWeight: 600
    lineHeight: "24sp"
  body:
    fontFamily: "Inter, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  label:
    fontFamily: "Inter, sans-serif"
    fontSize: "14sp"
    fontWeight: 600
    lineHeight: "21sp"
rounded:
  field: "8dp"
  chip: "12dp"
  card: "16dp"
  hero: "24dp"
  sheet: "28dp"
  button: "999dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  lg: "16dp"
  xl: "24dp"
  xxl: "32dp"
components:
  button-primary:
    backgroundColor: "{colors.navy}"
    textColor: "{colors.background}"
    rounded: "{rounded.button}"
    height: "48dp"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
    padding: "16dp"
  balance-hero:
    backgroundColor: "{colors.navy}"
    textColor: "{colors.background}"
    rounded: "{rounded.hero}"
    padding: "24dp"
---

# Design System: LatiFi Wallet

## Overview

LatiFi Wallet is an Operate surface on Android: people check a balance, request or fund a small loan and sign each step with a wallet. The personality comes from the landing page. A deep navy carries trust, lime is the single burst of energy, and soft tinted cards give each status a color without shouting. Material 3 governs structure, navigation and interaction. Brand lives in the token layer on top of it (`ui/theme`) and in a small set of components (`ui/components`). Dynamic color is never used, because wallpaper colors would break the contrast table.

The mark is the web favicon: a lime pulse line with two dots on a navy rounded square. It is the launcher icon, the splash, the connect screen logo and the faint watermark inside the balance hero.

## Colors

- **Navy** `#0B1F3A` and **Navy 700** `#14407A` form the hero gradient and the light theme primary button. White text on them passes 10:1 or better.
- **Lime** `#C6FF3D` is the accent on navy (icons, the reputation ring on the hero, the pulse) and the primary button fill in dark theme. It is never body text on white.
- **Lime soft** `#D8FF7A` fills the selected tab pill in light theme, with navy icons on it (14:1).
- **Green** `#0A7D5A` is success and the "Trusted" level. Warning amber, error red and info navy 700 each come with a tinted container, an icon and a word.
- **Surface tiers**: background, surface (cards) and an outline for decorative borders. Cards tinted by tone use the container of that tone, so text keeps the normal on-surface color.
- **Reputation levels** have their own ring colors: New slate, Building blue, Trusted green, Excellent green in light and lime in dark. All hold 3:1 against the card surface.
- Dark theme is its own scheme, not an inversion: a near black background, a lifted navy hero (`#163C6E` to `#0B1F3A`), lime for primary actions and for the selected state.

Rules: color never carries meaning alone. A status shows an icon, a word and a color. Contrast of every pair, light and dark, is held by `ThemeContrastTest` and `BrandTokensContrastTest`.

## Typography

Sora for headings and amounts, Inter for body and labels, both as variable fonts. Six sizes (32, 24, 18, 16, 14, 12 sp) and four weights. Line height is 1.5 times the size on body, label and caption. Amounts use tabular figures so columns align. All sizes are `sp`; layouts reflow at 200 percent and switch the bottom bar for a drawer from font scale 1.3.

## Layout

A 4dp base scale (4, 8, 16, 24, 32, 48). Screens use 16dp side padding and 16dp between cards, with edge to edge content and window insets applied. Every target is at least 48dp. Home reads top to bottom: greeting, balance hero, role card (reputation ring or open requests), next installment card, the single primary button, then the quiet actions.

## Elevation & Depth

Flat first. Cards are a hairline border on a tinted or neutral surface and cast no shadow. The hero is distinguished by its gradient, not by a shadow. Only floating things (sheets, menus) rise.

## Shapes

Field 8dp, chip 12dp, card 16dp, hero 24dp, sheet 28dp, buttons and pills fully round. Icon chips are 12dp rounded tiles.

## Components

- **Buttons** (primary, tonal, secondary, tertiary): 48dp minimum, fully rounded, press scale 0.97 with a spring release and a haptic tick. One primary per screen.
- **LatiFiCard**: neutral or tinted by tone (info, success, warning, danger, accent lime). Clickable cards share the press scale.
- **IconChip**: a decorative icon on a tone container, always next to words.
- **BalanceHero**: navy gradient, white amount with count-up, soft blue secondary text, lime accents.
- **ReputationRing**: a 270 degree gauge that sweeps to the score, with the number or the pulse in the middle.
- **Status and reputation chips**: icon, word and color pair.
- **SuccessBadge**: green disc, a check that draws itself, a small lime and green burst, one confirming haptic.
- **Skeleton**: card placeholders with a soft shimmer band.
- **Empty states**: a small brand illustration, a title, one sentence and one action.
- **Navigation**: Material navigation suite. The selected tab shows a lime pill and a navy icon that grows slightly.

## Motion

All motion is defined in `LatiFiMotion` (durations 120, 200, 320, 480 and 900 ms for hero moments, three easings, springs) and reaches the screen only through `rememberMotion()`. When the system animation scale is 0 the gate returns snaps and empty transitions, so nothing moves. A unit test fails the build if a file starts its own animation without the gate. Authored moments: count-up of the balance, the reputation ring sweep, the staggered entrance of the first six list items, press scale, the tab icon settle, fade through between tabs, shared axis between screens (which also feeds the predictive back gesture), the success check, the pulse drawing itself on the connect screen and the splash.

## Do's and Don'ts

Do:
- Take colors from `LatiFiTheme.colors` and Material roles, never raw hex in a screen.
- Pair every semantic color with an icon and words.
- Route every animation through `rememberMotion()`.
- Keep one primary button per screen.
- Keep both languages of every string in sync.

Don't:
- Use dynamic color or a drop shadow on cards.
- Put lime text on a light surface.
- Name the chain or use wallet jargon in the interface.
- Add a Lottie or Rive dependency for something a spring or a path can do.
