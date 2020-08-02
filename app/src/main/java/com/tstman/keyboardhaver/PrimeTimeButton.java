package com.tstman.keyboardhaver;

public class PrimeTimeButton {
    final String name;
    final int keyCode;
    final int xPosition;
    final int yPosition;
    final int slot;

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

    protected int RandomInt(int min, int max) {
        return min + (int) (Math.random() * (max - min) + 1);
    }
}
