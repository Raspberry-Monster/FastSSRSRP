# FastSSRSRP

FastSSRSRP adjusts Android's 5G NR SS-RSRP signal strength thresholds to better reflect real-world 5G signal characteristics. It changes how measured signal strength is mapped to signal levels; it does not boost reception.

The app has no launcher icon or activity.

## Requirements

- Android 14 or later.
- Shizuku or Sui running, with FastSSRSRP authorized.
- ADB for initial activation after installation.

## Installation

1. Install the APK.
2. Connect the device to a computer with ADB and enable USB debugging.
3. Ensure Shizuku or Sui is running and FastSSRSRP is authorized.
4. Run the following commands to clear the app's stopped state and request a configuration check:

   ```sh
   adb shell cmd package unstop --user 0 io.github.raspberrykan.fastssrsrp
   adb shell content call --user 0 --uri content://io.github.raspberrykan.fastssrsrp.shizuku --method activate
   ```

5. Check the result in Logcat:

   ```sh
   adb logcat -d -s raspberrykan:I
   ```

The `activate` method requires an APK built with this activation entry point. It waits up to five seconds for Shizuku/Sui before checking the configuration. If authorization is missing, grant it and run the activation command again.

The command response only confirms that activation was requested. Look for `overrideConfig succeeded` or `no need to override carrier config` in the logs. The `unstop` command alone does not trigger a configuration check. Later carrier configuration change broadcasts also trigger checks automatically.

Repeat the activation steps after reinstalling or force-stopping the app. These commands target the device's primary user.

## Acknowledgments

The inspiration and most of the code for this project come from [vvb2060/Ims](https://github.com/vvb2060/Ims). Many thanks to vvb2060 for his excellent work!
