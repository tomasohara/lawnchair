/*
 * Home switcher: toggles the default home app between the installed Lawnchair builds
 * (e.g., the release and the debug one) with a single tap on its icon.
 *
 * Android only lets privileged callers change the default home app silently, so this goes
 * through Shizuku (https://shizuku.rikka.app), which gives apps the privileges of "adb shell"
 * once it has been started. Without it, the system's home app picker is shown instead.
 * See README.md for the setup.
 *
 * Created with the help of Claude Code using model claude-fable-5-2.
 */

package app.lawnchair.homeswitcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rikka.shizuku.Shizuku;

/** Invisible activity that switches the default home app and then exits. */
public class SwitchActivity extends Activity {

    private static final String TAG = "HomeSwitcher";

    /** How long to wait for Shizuku to respond before falling back to the picker. */
    private static final long SHIZUKU_TIMEOUT_MS = 1500;
    private static final int PERMISSION_REQUEST_CODE = 1;
    /** Number of user ids per Android user (i.e., uid = user * range + app id). */
    private static final int PER_USER_RANGE = 100000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mShizukuTimeout = () -> fallBackToPicker(R.string.shizuku_unavailable);
    private final Shizuku.OnBinderReceivedListener mBinderListener = this::onShizukuAvailable;
    private final Shizuku.OnRequestPermissionResultListener mPermissionListener =
            this::onShizukuPermissionResult;

    /** Home activity to switch to, by package. */
    private ComponentName mTarget;
    private Shizuku.UserServiceArgs mServiceArgs;
    private boolean mServiceBound;
    private boolean mDone;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "shell helper connected");
            switchHome(IShellService.Stub.asInterface(binder));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "shell helper disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Work out which Lawnchair build to switch to
        Map<String, ComponentName> launchers = findLawnchairBuilds();
        String current = getCurrentHomePackage();
        String next = HomeSelector.pickNext(new ArrayList<>(launchers.keySet()), current);
        Log.d(TAG, "launchers=" + launchers.keySet() + " current=" + current + " next=" + next);
        if (next == null) {
            finishWithMessage(getString(R.string.need_two_launchers));
            return;
        }
        mTarget = launchers.get(next);

        // Wait for Shizuku, giving up after a short while
        Shizuku.addRequestPermissionResultListener(mPermissionListener);
        Shizuku.addBinderReceivedListenerSticky(mBinderListener);
        mHandler.postDelayed(mShizukuTimeout, SHIZUKU_TIMEOUT_MS);
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacks(mShizukuTimeout);
        Shizuku.removeBinderReceivedListener(mBinderListener);
        Shizuku.removeRequestPermissionResultListener(mPermissionListener);
        if (mServiceBound) {
            try {
                Shizuku.unbindUserService(mServiceArgs, mConnection, true);
            } catch (RuntimeException e) {
                Log.w(TAG, "problem unbinding shell helper", e);
            }
        }
        super.onDestroy();
    }

    /** Returns the installed Lawnchair builds as a map from package to home activity. */
    private Map<String, ComponentName> findLawnchairBuilds() {
        Map<String, ComponentName> launchers = new LinkedHashMap<>();
        List<ResolveInfo> homes = getPackageManager().queryIntentActivities(
                getHomeIntent(), PackageManager.MATCH_DEFAULT_ONLY);
        List<String> packages = new ArrayList<>();
        Map<String, ComponentName> components = new LinkedHashMap<>();
        for (ResolveInfo info : homes) {
            String packageName = info.activityInfo.packageName;
            if (HomeSelector.isLawnchairBuild(packageName, getPackageName())
                    && !components.containsKey(packageName)) {
                packages.add(packageName);
                components.put(packageName, new ComponentName(packageName, info.activityInfo.name));
            }
        }
        // Use a fixed order, so that repeated taps cycle through the builds
        Collections.sort(packages);
        for (String packageName : packages) {
            launchers.put(packageName, components.get(packageName));
        }
        return launchers;
    }

    /** Returns the package of the default home app (or of the system chooser if none). */
    private String getCurrentHomePackage() {
        ResolveInfo info = getPackageManager().resolveActivity(
                getHomeIntent(), PackageManager.MATCH_DEFAULT_ONLY);
        return (info != null && info.activityInfo != null) ? info.activityInfo.packageName : null;
    }

    private static Intent getHomeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    }

    /** Invoked once Shizuku is known to be running: checks for permission to use it. */
    private void onShizukuAvailable() {
        mHandler.removeCallbacks(mShizukuTimeout);
        if (mDone) {
            return;
        }
        try {
            if (Shizuku.isPreV11()) {
                // Old Shizuku versions use a different permission model
                fallBackToPicker(R.string.shizuku_unavailable);
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindShellHelper();
            } else {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "problem talking to Shizuku", e);
            fallBackToPicker(R.string.shizuku_unavailable);
        }
    }

    private void onShizukuPermissionResult(int requestCode, int grantResult) {
        if (requestCode == PERMISSION_REQUEST_CODE && !mDone) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                bindShellHelper();
            } else {
                fallBackToPicker(R.string.shizuku_denied);
            }
        }
    }

    /** Asks Shizuku to start the helper that runs with shell privileges. */
    private void bindShellHelper() {
        mServiceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(getPackageName(), ShellService.class.getName()))
                .daemon(false)
                .processNameSuffix("shell")
                .version(1);
        try {
            Shizuku.bindUserService(mServiceArgs, mConnection);
            mServiceBound = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "problem starting shell helper", e);
            fallBackToPicker(R.string.shizuku_unavailable);
        }
    }

    /** Switches the default home app via the shell helper, off the main thread. */
    private void switchHome(IShellService service) {
        final String component = mTarget.flattenToString();
        final int userId = Process.myUid() / PER_USER_RANGE;
        new Thread(() -> {
            String output;
            boolean ok;
            try {
                output = service.setHomeActivity(component, userId);
                ok = (output != null) && output.contains("Success");
            } catch (Exception e) {
                output = String.valueOf(e);
                ok = false;
            }
            Log.d(TAG, "set-home-activity " + component + " => " + output);
            final String message = ok
                    ? getString(R.string.switched_to, getAppLabel(mTarget.getPackageName()))
                    : getString(R.string.switch_failed, output);
            final boolean switched = ok;
            mHandler.post(() -> {
                if (switched) {
                    // Show the newly selected home app
                    startActivity(getHomeIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }
                finishWithMessage(message);
            });
        }, "home-switch").start();
    }

    private String getAppLabel(String packageName) {
        String label = packageName;
        try {
            PackageManager pm = getPackageManager();
            label = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "no label for " + packageName, e);
        }
        return label;
    }

    /** Shows the system's home app picker, for when the silent switch is not possible. */
    private void fallBackToPicker(int messageId) {
        if (!mDone) {
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            } catch (RuntimeException e) {
                Log.w(TAG, "problem opening home settings", e);
            }
            finishWithMessage(getString(messageId));
        }
    }

    private void finishWithMessage(String message) {
        if (!mDone) {
            mDone = true;
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
