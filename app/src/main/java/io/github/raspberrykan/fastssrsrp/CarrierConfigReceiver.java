package io.github.raspberrykan.fastssrsrp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.CarrierConfigManager;

public class CarrierConfigReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED
                .equals(intent.getAction())) {
            ShizukuProvider.applyIfNeeded(context);
        }
    }
}