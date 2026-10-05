package io.github.raspberrykan.fastssrsrp;

import static rikka.shizuku.ShizukuProvider.METHOD_GET_BINDER;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.app.IActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.permission.PermissionManager;
import android.system.Os;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import rikka.shizuku.ShizukuBinderWrapper;

public class PrivilegedProcess extends Instrumentation {
    static final String TAG = "raspberrykan";

    @Override
    public void onCreate(Bundle arguments) {
        var context = getContext();
        if (Process.isSdkSandbox()) {
            var extras = makeExtras(context);
            var cr = getContext().getContentResolver();
            cr.call(BuildConfig.APPLICATION_ID + ".shizuku", METHOD_GET_BINDER, null, extras);
        } else if (arguments.getInt("pid", 0) == Process.myPid()) {
            var binder = ServiceManager.getService(Context.ACTIVITY_SERVICE);
            var am = IActivityManager.Stub.asInterface(new ShizukuBinderWrapper(binder));
            try {
                am.startDelegateShellPermissionIdentity(Os.getuid(), null);
                grantPermission(context);
                overrideConfig(context, false);
                am.stopDelegateShellPermissionIdentity();
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
            finish(0, new Bundle());
        } else {
            finish(0, new Bundle());
        }
    }

    private Bundle makeExtras(Context context) {
        var binder = new Binder() {
            @Override
            protected boolean onTransact(int code, @NonNull Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == 1) {
                    try {
                        grantPermission(context);
                        overrideConfig(context, true);
                    } catch (Exception e) {
                        Log.e(TAG, Log.getStackTraceString(e));
                    }
                    var handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(() -> finish(0, new Bundle()), 1000);
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        };
        var extras = new Bundle();
        extras.putBinder("binder", binder);
        return extras;
    }

    @SuppressLint("MissingPermission")
    private static void grantPermission(Context context) {
        var pm = context.getSystemService(PermissionManager.class);
        pm.grantRuntimePermission(BuildConfig.APPLICATION_ID,
                android.Manifest.permission.READ_PHONE_STATE, Process.myUserHandle());
    }

    @SuppressLint("MissingPermission")
    private static void overrideConfig(Context context, boolean persistent) {
        var cm = context.getSystemService(CarrierConfigManager.class);
        var sm = context.getSystemService(SubscriptionManager.class);
        var values = getConfig();
        for (var subId : sm.getActiveSubscriptionIdList()) {
            values.putInt("raspberrykan_config_version", BuildConfig.VERSION_CODE);
            try {
                cm.overrideConfig(subId, values, persistent);
            } catch (SecurityException e) {
                Log.w(TAG, "overrideConfig failed for subId " + subId, e);
                if (persistent) {
                    persistent = false;
                    cm.overrideConfig(subId, values, persistent);
                }
            }
            var bundle = cm.getConfigForSubId(subId, "raspberrykan_config_version");
            if (bundle.getInt("raspberrykan_config_version", 0) == BuildConfig.VERSION_CODE) {
                Log.i(TAG, "overrideConfig succeeded for subId " + subId + ", persistent=" + persistent);
            } else {
                Log.e(TAG, "overrideConfig failed for subId " + subId + ", persistent=" + persistent);
            }
        }
    }

    private static PersistableBundle getConfig() {
        var bundle = new PersistableBundle();
        bundle.putIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY,
                // Boundaries: [-140 dBm, -44 dBm]
                new int[]{
                        -128, /* SIGNAL_STRENGTH_POOR */
                        -115, /* SIGNAL_STRENGTH_MODERATE */
                        -105, /* SIGNAL_STRENGTH_GOOD */
                        -95,  /* SIGNAL_STRENGTH_GREAT */
                });
        return bundle;
    }
}
