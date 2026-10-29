# v1 · Classic home

The original wallpaper, clock, top-right controls and app rows. Default for existing and new installations. The version switch is the only added control.

Baseline source commit: `342e1d096a0a43ad51ff7e5b3fba6bba3bd468cf`.

Select **v1** in the top-right version menu, or set `options=version=v1` in your installation configuration.

The original source is preserved in Git history at the baseline commit; the maintained implementation is shared in `src/`.

## 1.6.3: classic plus Continue watching

V1 now adds one compact Continue watching row to the original home layout. Wallpaper treatment, clock/date, top controls, app sizing/positions and app focus behavior stay as before. Resume cards show a thumbnail, app name and one-line title; numeric playback counters remain only in accessibility descriptions. Up from apps reaches resume cards; further Up reaches Refresh, then the top controls. Down returns toward cards and apps. Watch Next needs TV listings permission and depends on app contributions.

## 1.6.4: manual refresh

Select **Refresh** beside Continue watching to query the TV's shared Watch Next feed again. Existing cards stay visible during the query, the button shows Refreshing while loading, duplicate concurrent requests are ignored, and remote focus returns to the button when complete. Empty/error messages remain available when the provider has no usable entries. Tested on the TV: pressing Refresh produced a fresh provider query and retained button focus afterward.
