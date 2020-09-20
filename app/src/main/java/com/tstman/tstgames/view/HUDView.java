package com.tstman.tstgames.view;

import android.content.Context;
import android.graphics.Canvas;
import android.hardware.input.InputManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.View;

import com.tstman.tstgames.Engooden;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class HUDView extends View {

    private float completionLevel;
    Object inputStage;
    Object viewRootImpl;
    Object inputEventReceiver;
    Method onInputEvent;

    protected void setThatParent() {
        Engooden.Companion.make();
        viewRootImpl = getPrivateMember(this, "android.view.View", "mParent");
        inputEventReceiver = getPrivateMember(viewRootImpl, "android.view.ViewRootImpl", "mInputEventReceiver");

        final Class inputEventReceiverClass;
        final String inputEventReceiverClassName = "android.view.ViewRootImpl$WindowInputEventReceiver";
        try {
            inputEventReceiverClass = Class.forName(inputEventReceiverClassName);
        } catch (ClassNotFoundException exception) {
            Log.w("tstgames", "aw, " + inputEventReceiverClassName + " class is not found: " + exception.getMessage());
            return;
        }

        try {
            onInputEvent = inputEventReceiverClass.getMethod("onInputEvent", InputEvent.class);
        } catch (NoSuchMethodException exception) {
            exception.printStackTrace();
            return;
        }
    }

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

    public HUDView(Context context) {
        super(context);
    }

    public HUDView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HUDView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCompletionLevel(float completionLevel) {
        this.completionLevel = completionLevel;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action != 2) {
            Log.i("tstgames", "action: " + (action & 0xff));
            Log.i("tstgames", "index: " + Integer.toBinaryString(event.getActionIndex()));
        }
        if (onInputEvent == null) {
            setThatParent();
        }
        boolean whatItWouldReturn = super.onTouchEvent(event);
        return false;
    }

    public void dispatchTouchEventOnParent(MotionEvent event) {
        if (onInputEvent == null) {
            return;
        }
        try {
            onInputEvent.invoke(inputEventReceiver, event);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            exception.printStackTrace();
        }
    }
}
