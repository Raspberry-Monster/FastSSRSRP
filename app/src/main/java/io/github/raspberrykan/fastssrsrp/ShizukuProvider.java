package io.github.raspberrykan.fastssrsrp;

import static io.github.raspberrykan.fastssrsrp.PrivilegedProcess.TAG;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.IActivityManager;
import android.app.UiAutomationConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.system.Os;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import org.lsposed.hiddenapibypass.LSPass;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;

public class ShizukuProvider extends rikka.shizuku.ShizukuProvider {
    static {
        LSPass.setHiddenApiExemptions("");
    }

    @Override
    public Bundle call(@NonNull String method, String arg, Bundle extras) {
        if (UserHandle.myUserId() != UserHandle.USER_SYSTEM) {
            return new Bundle();
        }

        int sdkUid = Process.toSdkSandboxUid(Os.getuid());
        int callingUid = Binder.getCallingUid();

        if (callingUid != sdkUid
                && callingUid != Process.SHELL_UID
                && callingUid != Process.ROOT_UID) {
            return new Bundle();
        }

        if ("activate".equals(method)) {
            if (callingUid != Process.SHELL_UID && callingUid != Process.ROOT_UID) {
                throw new SecurityException("Activation requires shell or root");
            }
            requestActivation(getContext());
            var result = new Bundle();
            result.putString("status", "Activation requested; check logcat for the result");
            return result;
        }

        if (METHOD_GET_BINDER.equals(method)
                && callingUid == sdkUid
                && extras != null) {
            IBinder binder = extras.getBinder("binder");

            if (binder != null) {
                Shizuku.addBinderReceivedListenerSticky(
                    new Shizuku.OnBinderReceivedListener() {
                        @Override
                        public void onBinderReceived() {
                            Shizuku.removeBinderReceivedListener(this);
                            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                                startShellPermissionDelegate(binder, sdkUid);
                                }
                            }
                    });
            }

            return new Bundle();
        }

        return super.call(method, arg, extras);
    }

    private static void requestActivation(Context context) {
        var handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            Log.i(TAG, "Activation requested; waiting for Shizuku binder");
            var listener = new Shizuku.OnBinderReceivedListener() {
                private boolean finished;
                private final Runnable timeout = () -> {
                    if (finished) return;
                    cleanup();
                    Log.w(TAG, "Activation timed out; start Shizuku/Sui and retry");
                };

                private void cleanup() {
                    finished = true;
                    handler.removeCallbacks(timeout);
                    Shizuku.removeBinderReceivedListener(this);
                }

                @Override
                public void onBinderReceived() {
                    handler.post(() -> {
                        if (finished) return;
                        cleanup();
                        applyIfNeeded(context);
                    });
                }

                private void start() {
                    handler.postDelayed(timeout, 5000);
                    Shizuku.addBinderReceivedListenerSticky(this);
                }
            };
            listener.start();
        });
    }

    public static void applyIfNeeded(Context context) {
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
        private static void startShellPermissionDelegate(IBinder binder, int sdkUid) {
        try {
            var activity = ServiceManager.getService(Context.ACTIVITY_SERVICE);
            var am = IActivityManager.Stub.asInterface(new ShizukuBinderWrapper(activity));
            am.startDelegateShellPermissionIdentity(sdkUid, null);
            var data = Parcel.obtain();
            binder.transact(1, data, null, 0);
            data.recycle();
            am.stopDelegateShellPermissionIdentity();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private static void startInstrument(Context context, boolean sdkSandbox) {
        try {
            var binder = ServiceManager.getService(Context.ACTIVITY_SERVICE);
            var am = IActivityManager.Stub.asInterface(new ShizukuBinderWrapper(binder));
            var name = new ComponentName(context, PrivilegedProcess.class);
            var flags = ActivityManager.INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS;
            if (sdkSandbox) {
                flags |= ActivityManager.INSTR_FLAG_INSTRUMENT_SDK_SANDBOX;
            } else {
                flags |= ActivityManager.INSTR_FLAG_NO_RESTART;
            }
            var args = new Bundle();
            args.putInt("pid", Process.myPid());
            var connection = new UiAutomationConnection();
            am.startInstrumentation(name, null, flags, args, null, connection, 0, null);
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private static boolean needOverride(Context context) {
        var cm = context.getSystemService(CarrierConfigManager.class);
        var sm = context.getSystemService(SubscriptionManager.class);
        try {
            var list = sm.getActiveSubscriptionInfoList();
            if (list == null || list.isEmpty()) {
                return true;
            }
            for (var subinfo : list) {
                var subId = subinfo.getSubscriptionId();
                var bundle = cm.getConfigForSubId(subId, "raspberrykan_config_version");
                if (bundle.getInt("raspberrykan_config_version", 0) != BuildConfig.VERSION_CODE) {
                    return true;
                }
            }
            Log.i(TAG, "no need to override carrier config");
            return false;
        } catch (SecurityException e) {
            return true;
        }
    }

    @SuppressLint("PrivateApi")
    private static boolean canPersistent(Context context) {
        try {
            var phone = context.createPackageContext("com.android.phone",
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            var clazz = phone.getClassLoader().loadClass("com.android.phone.CarrierConfigLoader");
            try {
                clazz.getDeclaredMethod("isSystemApp");
            } catch (NoSuchMethodException e) {
                return true;
            }
            clazz.getDeclaredMethod("secureOverrideConfig", PersistableBundle.class, boolean.class);
            try {
                clazz.getDeclaredMethod("isSdkSandboxUidInternal", int.class);
                return false;
            } catch (NoSuchMethodException e) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
