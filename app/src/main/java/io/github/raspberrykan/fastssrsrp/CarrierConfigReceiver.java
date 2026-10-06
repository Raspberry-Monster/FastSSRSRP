package io.github.raspberrykan.fastssrsrp;

import static io.github.raspberrykan.fastssrsrp.PrivilegedProcess.TAG;
import static io.github.raspberrykan.fastssrsrp.ShizukuProvider.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.telephony.CarrierConfigManager;
import android.util.Log;

import rikka.shizuku.Shizuku;

public class CarrierConfigReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED
                .equals(intent.getAction())) {
            if (!StatusViewModel.hasRequestedSettings(context)) {
                return;
            }
            if (UserHandle.myUserId() != UserHandle.USER_SYSTEM) {
                return;
            }
            try {
                if (!Shizuku.pingBinder()) {
                    Log.i(TAG, "Shizuku binder not ready; skip this broadcast");
                    return;
                }
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Shizuku permission not granted; authorize FastSSRSRP and retry");
                    return;
                }
                if (needOverride(context)) {
                    startInstrument(context, canPersistent(context));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply carrier config", e);
            }
        }
    }
}
