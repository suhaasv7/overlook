# Android TV / Google TV

One shared launcher for Android TV and Google TV devices. HDMI inputs appear when the device provides them; streaming sticks do not need a separate version.

Build with `sh build.sh` and install using `sh install.sh <TV_IP> devices/android-google-tv/home.conf`. Customize `home.conf` for your installed services. No personal name, location or device address is required.

Choose **v1 / v2** in the top-right controls. Your selection persists across restarts, and both versions use the same app order, folders and settings. New installations default to v1.

- [v1 · Classic home](versions/v1/README.md)
- [v2 · Continue watching](versions/v2/README.md)

Version folders document layouts and their configuration; shared Java source remains in `src/` so fixes apply to both. These layout names are separate from the APK's release version.

Device-specific cleanup commands are not universal. Inspect a device before disabling any packages. Historical device records are kept in [legacy](../legacy/README.md).
