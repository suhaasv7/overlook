# Overlook

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

People using an Android TV or Google TV launcher with a directional remote from a couch.

## Product Purpose

Launch installed apps and resume shared viewing activity from one home screen.

## Operating Context

A native Java Android TV launcher installed on the user's TV. V1 is the classic default and remains unchanged. The user selected Scenic dock as the only v2 layout, with smaller elements and a wallpaper gradient that darkens toward the bottom. A persistent newest-first menu selects v2 Scenic dock or v1 Classic home. V2 shows large time/date without visible Overlook branding. See DESIGN.md for the current native visual system.

## Capabilities and Constraints

Preserve existing app order, folders, settings, signing identity and app data. Use generic Android TV / Google TV configuration. V2 reads searchable Watch Next rows with READ_TV_LISTINGS, opens owning-app playback links and displays provider artwork. Current device checks found playable YouTube, Crunchyroll and JioHotstar entries. Netflix and other installed apps may publish no readable entries; never fabricate service history or claim universal support. Text, remote navigation, fallback artwork, permission-denied and empty states must remain functional.

## Brand Commitments

Name: Overlook. User requests a modern Apple TV–inspired v2, while retaining classic v1.

## Evidence on Hand

Current native source, prior TV screenshots/runtime checks, shared thumbnails, app banners and fonts. The compact Scenic dock revision is documented from source; final render verification is pending. The intended new screenshot is .impeccable/review/tv-scenic-compact.png. Older three-layout screenshots do not verify this revision. Review screenshots contain user viewing data and are not for Git or publication. No invented titles, services, viewing histories or subscription data.

## Product Principles

- Resume actual shared content.
- Keep remote navigation predictable.
- Preserve a familiar classic fallback.
- Let content artwork lead without compromising readability.

## Remote review workflow

The user is away from the TV and uses a Tailscale ADB connection. After each installed UI change, capture the actual launcher screen (dismiss the screensaver using Home if needed), inspect it, and show the screenshot inline to the user. Do not substitute a screensaver capture for launcher evidence.

Latest v1 direction: retain classic home styling and add only one quiet Continue watching row. Avoid the v2 dock or extra styling in v1. V2 remains available.

Recent-apps direction: PatchWall hold opens a modern panel with directional-remote app selection, per-app Clear app controls and Clear all. An optional, local ADB-started helper uses actual Android 9 system dismissal controls and verifies task deletion. App data, Home, System UI and Tailscale remain protected. The helper must be restarted after a TV reboot. Short press retains the info overlay.
