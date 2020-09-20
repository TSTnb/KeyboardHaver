package com.tstman.tstgames;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethod;
//import android.inputmethodservice.IInputMethodWrapper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceFragmentCompat;

import com.tstman.tstgames.service.ScreenPresser;
import com.tstman.tstgames.service.TSTgames;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

public class SettingsActivity extends AppCompatActivity implements View.OnTouchListener {

    protected Object getPrivateMember(Object object, String className, String fieldName) {
        final Class classReference;
        final Field field;
        final Object member;

        try {
            classReference = Class.forName(className);
        } catch (ClassNotFoundException exception) {
            Log.w("tstgames", "aw, " + className + " class is not found: " + exception.getMessage());
            return null;
        }

        try {
            field = classReference.getDeclaredField(fieldName);
            field.setAccessible(true);
        } catch (NoSuchFieldException exception) {
            Log.w("tstgames", "aw, " + fieldName + " field is not found: " + exception.getMessage());
            return null;
        }
        try {
            member = field.get(object);
        } catch (IllegalAccessException exception) {
            Log.w("tstgames", "aw, could not get that " + fieldName + " field: " + exception.getMessage());
            return null;
        }
        return member;
    }

    private TSTgames tstGames;
    private ServiceConnection tstGamesConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Engooden.Companion.make();
            final Object accessibilityService = getPrivateMember(service, "android.accessibilityservice.AccessibilityService$IAccessibilityServiceClientWrapper", "mCallback");
            tstGames = (TSTgames) getPrivateMember(accessibilityService, "android.accessibilityservice.AccessibilityService$2", "this$0");


            ActivityCompat.requestPermissions(SettingsActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 5005);
            if (!Settings.canDrawOverlays(tstGames)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 5004);
            } else {
                tstGames.setActivity(SettingsActivity.this);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            tstGames = null;
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 5004) {
            tstGames.setActivity(SettingsActivity.this);
        } else if (requestCode == 5005) {

        }
    }

    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        //Loads Shared preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

//Setup a shared preference listener for hpwAddress and restart transport
        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            }
        };

        prefs.registerOnSharedPreferenceChangeListener(listener);

        bindService(new Intent(SettingsActivity.this, TSTgames.class), tstGamesConnection, Context.BIND_AUTO_CREATE);
        Log.w("tstgames", "aight");
    }

    private boolean didTheThingAlready = false;

    /**
     * Called when a drag event is dispatched to a view. This allows listeners
     * to get a chance to override base View behavior.
     *
     * @param v     The View that received the drag event.
     * @param event The {@link DragEvent} object for the drag event.
     * @return {@code true} if the drag event was handled successfully, or {@code false}
     * if the drag event was not handled. Note that {@code false} will trigger the View
     * to call its  handler.
     */
    //@Override
    public boolean onDrag(View v, DragEvent event) {
        return false;
    }

    /**
     * Called when a touch event is dispatched to a view. This allows listeners to
     * get a chance to respond before the target view.
     *
     * @param v     The view the touch event has been dispatched to.
     * @param event The MotionEvent object containing full information about
     *              the event.
     * @return True if the listener has consumed the event, false otherwise.
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
        }
    }
}