package com.tstman.tstgames.service;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.inputmethodservice.InputMethodService;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;

import com.tstman.tstgames.Engooden;
import com.tstman.tstgames.R;
import com.tstman.tstgames.view.HUDView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ScreenPresser extends InputMethodService {

    private HUDView masterView;

    public void onCreate() {
        super.onCreate();
        /*try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        downAtPoint(994, 914);
        upAtPoint(994, 914);
        Log.w("tstgames", "yep it did the thing");*/
    }

    public void setMasterView(HUDView masterView) {
        this.masterView = masterView;
    }

    public void downAtPoint(int x, int y) {
        actionAtPoint(x, y, MotionEvent.ACTION_DOWN);
    }

    public void upAtPoint(int x, int y) {
        actionAtPoint(x, y, MotionEvent.ACTION_UP);
    }

    InputManager inputManager;
    Method injectInputEvent;

    public void actionAtPoint(int x, int y, int action) {
        Engooden.Companion.make();

        if (inputManager == null) {
            try {
                inputManager = (InputManager) InputManager.class.getDeclaredMethod("getInstance", new Class[0]).invoke((Object) null, new Object[0]);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                return;
            } catch (InvocationTargetException e) {
                e.printStackTrace();
                return;
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                return;
            }

            //Make MotionEvent.obtain() method accessible
            try {
                MotionEvent.class.getDeclaredMethod("obtain", new Class[0]).setAccessible(true);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                return;
            }

            try {
                injectInputEvent = InputManager.class.getMethod("injectInputEvent", new Class[]{InputEvent.class, Integer.TYPE});
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                return;
            }
        }


        long time = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(time, time, action, x, y, 1.0f, 1.0f, 0, 1.0f, 1.0f, 0, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        InputConnection inputConnection = getCurrentInputConnection();
        //masterView.dispatchTouchEvent(event);
        //masterView.performContextClick(x, y);
        //injectInputEvent(event);
        //masterView.dispatchTouchEventOnParent(event);
        try {
            injectInputEvent.invoke(inputManager, new Object[]{event, Integer.valueOf(0)});
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    private void injectInputEvent(InputEvent event) {
        try {
            getInjectInputEvent().invoke(getInputManager(), event, 0);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private static Method getInjectInputEvent() throws NoSuchMethodException {

        Class<InputManager> cl = InputManager.class;
        Method method = cl.getDeclaredMethod("injectInputEvent", InputEvent.class, int.class);
        method.setAccessible(true);
        return method;
    }

    @SuppressLint("DiscouragedPrivateApi")
    private static InputManager getInputManager() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<InputManager> cl = InputManager.class;
        Method method = cl.getDeclaredMethod("getInstance");
        method.setAccessible(true);
        return (InputManager) method.invoke(cl);
    }

    private NotificationManager mNM;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.local_service_started;

    public class LocalBinder extends Binder {
        public ScreenPresser getService() {
            return ScreenPresser.this;
        }
    }
}
