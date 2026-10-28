# Overlook

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

People using an Android TV or Google TV launcher with a directional remote from a couch.

## Product Purpose

Launch installed apps and resume shared viewing activity from one home screen.

## Operating Context

A native Java Android TV launcher. Home is installed on the user's TV. V1 is the classic default; a persistent version control selects v2. The user approved and the native implementation now includes all three Apple TV–inspired v2 layouts: Cinematic spotlight, Artwork first and Scenic dock. A combined newest-first menu selects these layouts or v1 Classic home and saves the layout choice. All v2 layouts show large time/date without visible Overlook branding. See DESIGN.md for the implemented visual system.

## Capabilities and Constraints

Preserve existing app order, folders, settings, signing identity and app data. Use generic Android TV / Google TV configuration. V2 reads searchable Watch Next rows with READ_TV_LISTINGS, opens owning-app playback links and displays provider artwork. Current device checks found playable YouTube, Crunchyroll and JioHotstar entries. Netflix and other installed apps may publish no readable entries; never fabricate service history or claim universal support. Text, remote navigation, fallback artwork, permission-denied and empty states must remain functional.

## Brand Commitments

Name: Overlook. User requests a modern Apple TV–inspired v2, while retaining classic v1.

## Evidence on Hand

Actual TV screenshots and prior runtime checks, shared thumbnails, existing app banners and fonts. Finish review inspected native source and TV screenshots and accepted fixes for overlap, clipped title and hero focus; it does not establish broader runtime coverage. Review screenshots in .impeccable/review contain user viewing data and are not for Git or publication. No invented titles, services, viewing histories or subscription data.

## Product Principles

- Resume actual shared content.
- Keep remote navigation predictable.
- Preserve a familiar classic fallback.
- Let content artwork lead without compromising readability.
