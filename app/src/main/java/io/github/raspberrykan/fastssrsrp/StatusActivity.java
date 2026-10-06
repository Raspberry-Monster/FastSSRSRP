package io.github.raspberrykan.fastssrsrp;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserManager;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.snackbar.Snackbar;

import io.github.raspberrykan.fastssrsrp.databinding.ActivityStatusBinding;
import rikka.shizuku.Shizuku;

public class StatusActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION = 1;
    private ActivityStatusBinding binding;
    private StatusViewModel model;
    private boolean permissionPending;

    private final Shizuku.OnBinderReceivedListener binderReceived = this::refreshOnMainThread;
    private final Shizuku.OnBinderDeadListener binderDead = this::refreshOnMainThread;
    private final Shizuku.OnRequestPermissionResultListener permissionResult =
            (requestCode, grantResult) -> {
                if (requestCode != REQUEST_PERMISSION) return;
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    permissionPending = false;
                    refreshStatus();
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        showMessage(R.string.permission_denied);
                    }
                });
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityStatusBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());

        model = new ViewModelProvider(this).get(StatusViewModel.class);
        binding.authorize.setOnClickListener(view -> requestAuthorization());
        binding.apply.setOnClickListener(view -> model.applySettings());
        model.getApplyState().observe(this, state -> {
            binding.progress.setVisibility(
                    state == StatusViewModel.ApplyState.REQUESTING ? View.VISIBLE : View.GONE);
            int message = switch (state) {
                case IDLE -> 0;
                case REQUESTING -> R.string.applying;
                case REQUESTED -> R.string.apply_requested;
                case FAILED -> R.string.apply_failed;
                case PRIMARY_USER_ONLY -> R.string.primary_user_only;
            };
            binding.applyStatus.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
            if (message != 0) binding.applyStatus.setText(message);
            refreshStatus();
        });

        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        Shizuku.addRequestPermissionResultListener(permissionResult);
    }

    @Override
    protected void onResume() {
        super.onResume();
        permissionPending = false;
        refreshStatus();
    }

    private void refreshOnMainThread() {
        runOnUiThread(() -> {
            if (!isDestroyed() && !isFinishing()) refreshStatus();
        });
    }

    private void refreshStatus() {
        boolean connected = Shizuku.pingBinder();
        boolean granted = false;
        try {
            granted = connected && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException e) {
            connected = false;
        }
        boolean primaryUser = getSystemService(UserManager.class).isSystemUser();
        boolean applying = model.getApplyState().getValue() == StatusViewModel.ApplyState.REQUESTING;
        binding.connectionStatus.setText(connected ? R.string.connected : R.string.disconnected);
        binding.permissionStatus.setText(!connected ? R.string.unavailable
                : granted ? R.string.granted : R.string.not_granted);
        binding.accessHint.setText(!primaryUser ? R.string.primary_user_only
                : !connected ? R.string.start_shizuku
                : granted ? R.string.ready_hint : R.string.authorize_hint);
        binding.authorize.setText(granted ? R.string.authorized : R.string.authorize);
        binding.authorize.setEnabled(primaryUser && connected && !granted && !permissionPending);
        binding.apply.setEnabled(primaryUser && connected && granted && !applying);
    }

    private void requestAuthorization() {
        try {
            if (!Shizuku.pingBinder()) {
                refreshStatus();
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshStatus();
                return;
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                showMessage(R.string.permission_denied);
                return;
            }
            permissionPending = true;
            refreshStatus();
            Shizuku.requestPermission(REQUEST_PERMISSION);
        } catch (RuntimeException e) {
            permissionPending = false;
            refreshStatus();
            showMessage(R.string.permission_error);
        }
    }

    private void showMessage(int message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived);
        Shizuku.removeBinderDeadListener(binderDead);
        Shizuku.removeRequestPermissionResultListener(permissionResult);
        super.onDestroy();
    }
}
