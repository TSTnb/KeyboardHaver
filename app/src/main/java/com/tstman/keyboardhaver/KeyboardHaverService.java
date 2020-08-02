package com.tstman.keyboardhaver;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class KeyboardHaverService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {

    public void onInterrupt() {
    }

    public void onServiceConnected() {
        super.onServiceConnected();
    }

    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        Log.w("keyboardhaver", "that shared preference was changed");
        if (str.equals("NOTIFICATION_NEEDED")) {
            Log.w("keyboardhaver", "dang a notification is needed");
        }
    }

    public void onCreate() {
        super.onCreate();
        Log.w("keyboardhaver", "haha it was created");
        InitKeyboardHaverService();
    }

    public void onDestroy() {
        super.onDestroy();
        Log.w("keyboardhaver", "aww it was destroyed");
    }

    public boolean onKeyEvent(KeyEvent keyEvent) {
        super.onKeyEvent(keyEvent);
        return true;
    }
}
