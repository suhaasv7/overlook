---
name: Overlook v2 native TV
description: Approved Apple TV–inspired native home layouts
colors:
  backdrop: "#101116"
  artwork-fallback: "#20232B"
  white: "#FFFFFF"
  date: "rgba(255,255,255,0.851)"
  resume-rest: "rgba(255,255,255,0.910)"
  focus-tint: "rgba(42,48,61,0.098)"
  focus-ring: "#B8C6E3"
  dock: "rgba(33,38,48,0.600)"
typography:
  display:
    fontFamily: "Inter SemiBold, MiLan Pro SemiBold, sans-serif-medium"
    fontSize: "92px"
    fontWeight: 600
  date:
    fontFamily: "Inter, MiLan Pro, sans-serif"
    fontSize: "30px"
    fontWeight: 400
  hero-title:
    fontFamily: "sans-serif"
    fontSize: "42px"
    fontWeight: 700
  card-title:
    fontFamily: "sans-serif"
    fontSize: "27px"
    fontWeight: 700
  cinematic-card-title:
    fontFamily: "sans-serif"
    fontSize: "23px"
    fontWeight: 700
  service:
    fontFamily: "sans-serif"
    fontSize: "20px"
    fontWeight: 400
  detail:
    fontFamily: "sans-serif"
    fontSize: "19px"
    fontWeight: 400
rounded:
  resume: "12px"
  card: "16px"
  dock: "28px"
spacing:
  card-gap: "24px"
  safe-edge: "89px"
  header-top: "34px"
components:
  resume:
    backgroundColor: "{colors.resume-rest}"
    textColor: "{colors.backdrop}"
    rounded: "{rounded.resume}"
    width: "180px"
    height: "42px"
  resume-focused:
    backgroundColor: "{colors.white}"
    textColor: "{colors.backdrop}"
    rounded: "{rounded.resume}"
  watch-card:
    backgroundColor: "transparent"
    textColor: "{colors.white}"
    rounded: "{rounded.card}"
    padding: "8px 10px"
---

# Design System: Overlook v2 native TV

## Overview

The approved direction is Apple TV–inspired: artwork leads, white type stays readable from the couch, and directional focus is explicit. Three native layouts live inside v2; classic v1 remains the default fallback. All v2 layouts retain the large time/date header without visible Overlook branding.

**Key Characteristics:**
- Artwork-led viewing content.
- Large, restrained time and date.
- Rounded artwork and clear remote focus.

## Colors

The neutral palette gives provider artwork its own color. Backdrop is the cinematic and artwork-first canvas; artwork-fallback supports missing images. White carries text and focus, with a softer date and translucent resume resting state. Scenic dock uses the translucent dock over wallpaper. Focus-ring is the resume button's cool outline; focus-tint lightly fills focused viewing cards.

## Typography

The header uses bundled Inter Regular/SemiBold, falling back to MiLan Pro and Android sans-serif. Watch Next uses Android sans-serif normal/bold. Frontmatter sizes are baseline design pixels, not Android sp.

The clock uses tabular numerals. Hero titles have two lines in an 840 × 104 box without font padding. Card titles use one line in Cinematic spotlight and two in the other layouts, with end ellipsis. Service and detail are single-line ellipsized text. Continue watching is 44 in Artwork first and 34 in the other layouts; Your apps is 30.

## Layout

All measurements use the native 1920-wide baseline: rendered pixels equal rounded baseline value × display width / 1920. No browser breakpoints apply. Time/date sit at the safe left edge and header top; controls occupy the upper right. Watch Next ends at baseline y=800, ahead of Your apps at y=803 and app tiles beginning at y=830.

| Layout | Watch Next top | Card size | Artwork height | Composition |
| --- | ---: | ---: | ---: | --- |
| Cinematic spotlight | 230 | 390 × 272 | 166 | Dark canvas, 230-high hero, compact viewing rail |
| Artwork first | 300 | 520 × 420 | 275 | Dark canvas, large viewing artwork |
| Scenic dock | 340 | 420 × 360 | 220 | Wallpaper, viewing rail, translucent app dock |

The hero's right-aligned artwork is 1120 × 240 with a dark left-to-right veil. Service, title, metadata and resume action are vertically separated. Scenic dock begins at y=840 and is 214 high. Viewing cards have the frontmatter padding and gap; scroll padding leaves space for focus enlargement.

## Elevation & Depth

Dark veils and translucent surfaces establish depth. Header text has a black shadow; viewing-card focus adds a white 4-baseline-pixel stroke, slight tint, scale 1.035 and translationZ 10 over 180ms. Resume focus uses a 3-pixel cool ring and solid white fill. App tiles retain their native 1.14 scale, upward movement and elevation over 220ms. Disabled system animation scale removes Watch Next focus animation.

## Shapes

Viewing artwork is cropped within rounded clipped containers; owning-app icons remain centered when used as fallback. Viewing cards share the artwork corner radius. Resume is more compactly rounded, while the scenic app dock is broader and softer. Use the actual radii in frontmatter.

## Components

- **Time/date:** prominent in every v2 layout, with localized date formatting and system 12/24-hour time.
- **Hero:** Cinematic spotlight only, populated from actual Watch Next content; focused cards update its artwork and metadata. Resume/Watch next opens the owning app link. Up returns to header controls; Down reaches the rail; horizontal keys stay on the hero action.
- **Viewing rail:** provider artwork, service, title and resume metadata. Up from a cinematic card reaches the hero action; Down reaches apps. The first/last card does not escape horizontally. Permission and empty-state messaging remain readable and navigable.
- **Apps:** existing app order, folders and controls remain; Scenic dock adds the translucent grouping surface.
- **Home screen & layout menu:** Cinematic spotlight, Artwork first, Scenic dock, then v1 Classic home; active choice has a checkmark and layout selection persists. V1 stays classic.

## Do's and Don'ts

- **Do** preserve the large time/date and artwork-led hierarchy across all v2 layouts.
- **Do** keep focus outlines, hero text and app headings from overlapping or clipping.
- **Do** use real provider content and owning-app fallback icons.
- **Don't** add visible Overlook branding to v2 home layouts.
- **Don't** treat absent provider entries as permission to invent viewing history.

Source of truth: `src/com/ansub/tvhome/MainActivity.java` and `src/com/ansub/tvhome/WatchNextShelf.java`. Actual-TV evidence is `.impeccable/review/tv-cinematic.png`, `tv-shelves.png`, `tv-scenic.png` and `tv-version-menu.png`. Finish review accepted source/screenshot fixes for overlap, clipped title and hero focus; this is not a broader runtime-verification claim. Screenshot artifacts contain user viewing data and must not be committed to Git or published.
