# FastSSRSRP

FastSSRSRP adjusts Android's 5G NR SS-RSRP signal strength thresholds to better reflect real-world 5G signal characteristics. It changes how measured signal strength is mapped to signal levels; it does not boost reception.

The app includes a Material 3 screen with separate buttons for Shizuku authorization and applying signal settings. Signal thresholds are defined in the code, not edited in the UI.

## Requirements

- Android 14 or later.
- Shizuku or Sui running, with FastSSRSRP authorized.

## Installation

1. Install the APK.
2. Start Shizuku or Sui.
3. Open FastSSRSRP from the launcher and tap **Authorize Shizuku**.
4. Once access is granted, tap **Apply settings**.

Opening the app handles initial activation; no ADB activation command is required. Connecting or granting authorization alone does not apply settings. Use the device's primary user.

The UI confirms that a settings request was started, not that the carrier configuration was successfully updated. To inspect the result with ADB:

```sh
adb logcat -d -s raspberrykan:I
```

Look for `overrideConfig succeeded` in the logs. After the first accepted settings request, carrier configuration change broadcasts can check and reapply the configuration. If the app is force-stopped, open it again from the launcher.

## Acknowledgments

The inspiration and most of the code for this project come from [vvb2060/Ims](https://github.com/vvb2060/Ims). Many thanks to vvb2060 for his excellent work!
