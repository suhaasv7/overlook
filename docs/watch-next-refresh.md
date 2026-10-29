# Continue watching refresh

Overlook reads Android TV's shared Watch Next provider, as nielsvanvelzen/tv-launcher does. It does not read streaming-account history directly.

- Query globally by last_engagement_time_utc_millis descending, with provider ID as a stable tie-breaker. Do not interleave apps: that moves older titles ahead of newer activity.
- Keep up to 30 playable entries after filtering. Preserve provider artwork and owning-app links.
- Observe provider changes while Home is active, debounce bursts by 350 ms, and unregister while paused.
- Queue another read when a change arrives during a query.
- Restore focus by provider item identity, rather than by card position.
- Manual Refresh compares the displayed entries, metadata, artwork URLs and playback positions. It reports whether anything changed instead of implying a service-account sync occurred.

Android defines engagement time differently for Continue (last watched), Next (previous episode watched), and New (episode release date). The row retains those provider timestamps; it cannot infer unreported viewing history. Netflix and other services only appear when they publish readable entries.

References:
- https://github.com/nielsvanvelzen/tv-launcher/blob/master/app/src/main/kotlin/nl/ndat/tvlauncher/data/resolver/ChannelResolver.kt
- https://developer.android.com/reference/android/media/tv/TvContract.WatchNextPrograms#COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS
- https://github.com/spocky/miproja1/issues/409

Validation for 1.6.5: local Java/Android build passed; signing certificate matched the installed app; update installed without clearing data; real TV query returned 30 playable entries; a second manual read compared equal and displayed the unchanged-feed message. Remote navigation to Refresh and focus retention passed. Screenshots are kept outside Git because they contain personal viewing data. A new upstream streaming-app publication was not available during validation, so the observer's live-update path still needs that end-to-end check.
