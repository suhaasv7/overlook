---
name: Overlook v2 Scenic dock
description: Compact native TV home with artwork and bottom-darkened wallpaper
colors:
  artwork-fallback: "#20232B"
  white: "#FFFFFF"
  date: "rgba(255,255,255,0.851)"
  focus-tint: "rgba(42,48,61,0.098)"
  dock: "rgba(33,38,48,0.600)"
  wallpaper-clear: "rgba(10,13,18,0)"
  wallpaper-soft: "rgba(10,13,18,0.094)"
  wallpaper-mid: "rgba(10,13,18,0.439)"
  wallpaper-bottom: "rgba(10,13,18,0.902)"
typography:
  display:
    fontFamily: "Inter SemiBold, MiLan Pro SemiBold, sans-serif-medium"
    fontSize: "82px"
    fontWeight: 600
  date:
    fontFamily: "Inter, MiLan Pro, sans-serif"
    fontSize: "27px"
    fontWeight: 400
  app-heading:
    fontFamily: "Inter SemiBold, MiLan Pro SemiBold, sans-serif-medium"
    fontSize: "27px"
    fontWeight: 600
  shelf-heading:
    fontFamily: "sans-serif"
    fontSize: "30px"
    fontWeight: 700
  card-title:
    fontFamily: "sans-serif"
    fontSize: "24px"
    fontWeight: 700
  service-detail:
    fontFamily: "sans-serif"
    fontSize: "18px"
    fontWeight: 400
rounded:
  card: "16px"
  dock: "28px"
  header-control: "29px"
spacing:
  card-gap: "22px"
  safe-edge: "89px"
  header-top: "34px"
  control-gap: "28px"
components:
  watch-card:
    backgroundColor: "transparent"
    textColor: "{colors.white}"
    rounded: "{rounded.card}"
    padding: "7px 9px"
    width: "376px"
    height: "332px"
  header-control:
    rounded: "{rounded.header-control}"
    height: "58px"
  scenic-dock:
    backgroundColor: "{colors.dock}"
    rounded: "{rounded.dock}"
    height: "194px"
---

# Design System: Overlook v2 Scenic dock

## Overview

The current approved direction is a compact Apple TV–inspired Scenic dock. Wallpaper leads the upper screen and darkens toward the bottom behind viewing content and apps. V2 contains this single layout, with large time/date and no visible Overlook branding. V1 remains classic and unchanged by this design revision.

**Key Characteristics:**
- Scenic wallpaper with a bottom-darkening veil.
- Compact artwork cards and centered app tiles.
- Large time/date and explicit remote focus.

## Colors

White text and focused outlines sit over dark translucent surfaces. Artwork-fallback supports missing images, focus-tint lightly fills focused cards, and dock groups apps over wallpaper. The v2 wallpaper overlay runs top-to-bottom through wallpaper-clear, wallpaper-soft, wallpaper-mid and wallpaper-bottom, layered over the existing wallpaper shade.

## Typography

The header uses bundled Inter Regular/SemiBold, falling back to MiLan Pro and Android sans-serif. Watch Next uses Android sans-serif normal/bold. Frontmatter sizes are baseline design pixels, not Android sp. The clock uses tabular numerals and system 12/24-hour formatting; dates are localized.

Continue watching uses shelf-heading; Your apps uses app-heading. Card titles occupy two lines in a 62-high box without font padding, with end ellipsis. Service and resume detail are single-line ellipsized text. Permission/empty-state text is 22.

## Layout

Native rendered pixels equal rounded baseline value × display width / 1920. There are no browser breakpoints. Time/date begin at the safe left edge and header top; controls sit upper right. Icon-only controls are 58 square with 30 icons. Text controls size to content with 22 horizontal padding.

Watch Next starts at y=340 and ends at y=800. Cards use frontmatter dimensions, padding and gap; artwork height is 198. Scroll padding is 8 horizontally and 10 vertically. Your apps sits at y=803. App tiles start at y=860; each tile width is 90% of its computed classic width, keeping the existing gaps, with the full row centered. Tile height remains 9/16 of width. The dock starts at y=840, extends 18 beyond the app row on each side, and uses the frontmatter height. This is tile-width reduction, not a 90%-viewport row.

## Elevation & Depth

The top-to-bottom wallpaper veil and translucent dock establish depth without a solid v2 backdrop. Header text retains black shadows. Focused viewing cards gain a white 4-pixel stroke, focus-tint fill, scale 1.035 and translationZ 10 over 180ms; system-disabled animation scale removes that animation. App focus retains scale 1.14, translationY −6 and translationZ 26 over 220ms. Header controls focus to white with dark foreground, scale 1.08 and translationZ 12 over 220ms. Values are baseline design pixels.

## Shapes

Viewing artwork is cropped inside rounded clipped containers. Owning-app fallback icons remain centered. Card and artwork corners share the card radius; the dock is softly rounded and header controls are pills.

## Components

- **Time/date:** prominent in v2, without a logo or greeting competing with the clock.
- **Viewing rail:** actual provider artwork, service, title and resume metadata. Up reaches header controls; Down reaches apps; first/last cards contain horizontal navigation. Clicking opens the owning-app playback link. Permission and empty states remain navigable.
- **Apps:** compact centered tiles preserve existing order, folders and controls, grouped by the translucent scenic dock.
- **Home screen & layout menu:** v2 Scenic dock first, then v1 Classic home, with a checkmark on the active version. There is no cinematic hero or alternate v2 layout selection.

## Do's and Don'ts

- **Do** preserve the time/date and bottom-darkened scenic wallpaper.
- **Do** keep card text and remote-focus outlines readable and unclipped.
- **Do** use real provider content and owning-app fallback icons.
- **Don't** add visible Overlook branding to v2 home.
- **Don't** restore the removed cinematic hero or artwork-first layout.
- **Don't** invent viewing history for providers without readable entries.

Source of truth: `src/com/ansub/tvhome/MainActivity.java` and `src/com/ansub/tvhome/WatchNextShelf.java`. This revision documents current source; final render verification is pending. The intended new review artifact is `.impeccable/review/tv-scenic-compact.png`; its capture has not been verified here. Older three-layout screenshots describe the previous build and are not evidence of this revision. Screenshot artifacts contain user viewing data and must not be committed to Git or published.

## Compact render review

The final 1.6.1 native source and screenshots `tv-scenic-compact.png` and `tv-scenic-compact-menu.png` were independently reviewed and accepted with no concrete findings. They show readable compact captions, the two-entry version menu and the bottom-darkening wallpaper. This is screenshot/source evidence from the connected TV, not a claim about all Android TV firmware.

## App dock heading

1.6.2 removes the Your apps label; the dock shows app icons without a section heading.

## V1 classic + resume (1.6.3)

V1 retains its original clock/date, app positions/sizes, controls, wallpaper treatment and focus behavior. The only new surface is a resume row at y420 in 1920×1080 design coordinates: 340×260 cards, 180px image, app name and one-line 22px title. No visible playback counters. V2 uses the same quiet captions with its existing Scenic styling. Actual-device screenshots are required after each installed UI change and must be shown to the user.
