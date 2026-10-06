package io.github.raspberrykan.fastssrsrp;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/** Retains the pending request and its feedback across Activity recreation. */
public class StatusViewModel extends AndroidViewModel {
    enum ApplyState { IDLE, REQUESTING, REQUESTED, FAILED, PRIMARY_USER_ONLY }

    private final MutableLiveData<ApplyState> applyState =
            new MutableLiveData<>(ApplyState.IDLE);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public StatusViewModel(@NonNull Application application) {
        super(application);
    }

    LiveData<ApplyState> getApplyState() {
        return applyState;
    }

    static boolean hasRequestedSettings(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("apply_requested", false);
    }

    void applySettings() {
        if (applyState.getValue() == ApplyState.REQUESTING) return;
        Application context = getApplication();
        if (!context.getSystemService(UserManager.class).isSystemUser()) {
            applyState.setValue(ApplyState.PRIMARY_USER_ONLY);
            return;
        }
        applyState.setValue(ApplyState.REQUESTING);
        executor.execute(() -> {
            boolean started = false;
            try {
                if (Shizuku.pingBinder()
                        && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    started = ShizukuProvider.startInstrument(
                            context, ShizukuProvider.canPersistent(context));
                    if (started) {
                        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                                .edit().putBoolean("apply_requested", true).apply();
                    }
                }
            } catch (Exception e) {
                Log.e(PrivilegedProcess.TAG, "Failed to request signal settings", e);
            }
            applyState.postValue(started ? ApplyState.REQUESTED : ApplyState.FAILED);
        });
    }

    @Override
    protected void onCleared() {
        executor.shutdown();
    }
}
