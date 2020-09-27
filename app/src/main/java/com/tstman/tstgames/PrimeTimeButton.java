package com.tstman.tstgames;

import android.view.MotionEvent;

public class PrimeTimeButton {
    public final String name;
    final int keyCode;
    int slot;
    int xPosition;
    int yPosition;
    public MotionEvent.PointerCoords pointerCoords;
    public MotionEvent.PointerProperties pointerProperties;

    public PrimeTimeButton(final String name, final int keyCode, final int xPosition, final int yPosition, final int slot) {
        this.name = name;
        this.keyCode = keyCode;
        this.xPosition = xPosition;
        this.yPosition = yPosition;
        this.slot = slot;
    }

    public int getSlot() {
        return slot;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public int getXPosition() {
        return xPosition + RandomInt(-10, 10);
    }

    public int getYPosition() {
        return yPosition + RandomInt(-10, 10);
    }

    public int RandomInt(int min, int max) {
        return 0;
        //return min + (int) (Math.random() * (max - min) + 1);
    }

    public PrimeTimeButton(String name, int slot) {
        this.name = name;
        keyCode = 0;
        pointerCoords = new MotionEvent.PointerCoords();
        pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.id = slot;
    }

    public void down(int x, int y) {
        pointerCoords.x = x;
        pointerCoords.y = y;
    }
}
