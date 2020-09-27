package com.tstman.tstgames.inputinjection;

import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import com.tstman.tstgames.PrimeTimeButton;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Connection implements Runnable {

    volatile Socket socket;
    private volatile boolean running;
    InputStream inputStream;
    OutputStream outputStream;
    List<Integer> pressedButtons;
    Map<Integer, Boolean> isPressed;
    Map<Integer, PrimeTimeButton> buttons;
    int[] buttonIndices;

    public Connection(Socket socket) {
        this.running = true;
        this.socket = socket;
    }


    public void downAtPoint(int x, int y, int slot) {
        if (!pressedButtons.contains(slot)) {
            pressedButtons.add(0, slot);
        }
        isPressed.put(slot, true);
        buttonIndices[slot] = pressedButtons.size() - 2;
        int index = buttonIndices[slot];
        buttons.get(slot).down(x, y);
        final int motionEvent;
        System.out.println("index down: " + slot);
        if (index >= 0) {
            index <<= 8;
            motionEvent = MotionEvent.ACTION_POINTER_DOWN | index;
        } else {
            motionEvent = MotionEvent.ACTION_DOWN;
        }
        actionAtPoint(motionEvent);
    }

    public void upAtPoint(int slot) {
        int motionEvent;
        int index = buttonIndices[slot];
        System.out.println("index up: " + slot);
        isPressed.remove(slot);
        if (isPressed.size() > 1) {
            index <<= 8;
            motionEvent = MotionEvent.ACTION_POINTER_UP | index;
            actionAtPoint(motionEvent);
            //pressedButtons.remove((Object)slot);
        } else {
            System.out.println("(final release)");
            motionEvent = MotionEvent.ACTION_UP;
            actionAtPoint(motionEvent);
            pressedButtons = new ArrayList<>();
        }
    }

    InputManager inputManager;
    Method injectInputEvent;

    protected void initReflectiveMethods() {
        try {
            inputManager = (InputManager) InputManager.class.getDeclaredMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
            return;
        }

        //Make MotionEvent.obtain() method accessible
            /*try {
                MotionEvent.class.getDeclaredMethod("obtain").setAccessible(true);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                return;
            }*/

        try {
            injectInputEvent = InputManager.class.getMethod("injectInputEvent", InputEvent.class, Integer.TYPE);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return;
        }
    }

    public void actionAtPoint(int action) {
        long time = SystemClock.uptimeMillis();
        int size = pressedButtons.size();
        final int[] index = {-1};
        MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[size];
        MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[size];
        pressedButtons.forEach(pressedButton -> {
            index[0]++;
            PrimeTimeButton button = buttons.get(pressedButton);
            System.out.print(button.name + ", ");
            pointerProperties[index[0]] = button.pointerProperties;
            pointerCoords[index[0]] = button.pointerCoords;
        });
        System.out.print("\n");
        MotionEvent event = MotionEvent.obtain(time, time, action, size, pointerProperties, pointerCoords, 0, 0, 1.0f, 1.0f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);

        try {
            injectInputEvent.invoke(inputManager, event, 0);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    final static String secretPhrase = "no one will ever guess this!";

    protected boolean readPassword() throws IOException {
        if (!running) {
            return false;
        }
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        OutputStreamWriter bufferedWriter = new OutputStreamWriter(outputStream);
        String receiveMessage, sendMessage;
        boolean correctPassword = false;
        do {
            receiveMessage = bufferedReader.readLine();
            if (receiveMessage.equals(secretPhrase)) {
                sendMessage = "correct";
                correctPassword = true;
            } else {
                sendMessage = "incorrect";
            }
            bufferedWriter.write(sendMessage + "\n");
            bufferedWriter.flush();
        } while (running && !correctPassword);
        return correctPassword;
    }

    protected void startServer() {
        try {
            inputStream = socket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
            running = false;
            return;
        }
        try {
            outputStream = socket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
            running = false;
        }
        Log.i("tstgames-server", "New connection to " + Server.socketFilename);
    }

    protected void initButtons() {
        buttonIndices = new int[8];
        pressedButtons = new ArrayList<>();

        buttons = new HashMap<>();
        int slotIndex = 0;

        buttons.put(++slotIndex, new PrimeTimeButton("hard-drop", slotIndex));
        buttons.put(++slotIndex, new PrimeTimeButton("down", slotIndex));

        buttons.put(++slotIndex, new PrimeTimeButton("right", slotIndex));
        buttons.put(++slotIndex, new PrimeTimeButton("left", slotIndex));

        buttons.put(++slotIndex, new PrimeTimeButton("ccw", slotIndex));
        buttons.put(++slotIndex, new PrimeTimeButton("cw", slotIndex));

        buttons.put(++slotIndex, new PrimeTimeButton("hold", slotIndex));
    }

    protected void doStuff() throws IOException {
        startServer();
        if (!readPassword()) {
            return;
        }
        System.out.println("Client " + socket.getRemoteSocketAddress().toString() + " has successfully authenticated.");
        DataInputStream dataInputStream = new DataInputStream(inputStream);
        initButtons();
        initReflectiveMethods();
        byte command;
        int slot, x, y;
        do {
            command = dataInputStream.readByte();
            if (command == 'e') {
                running = false;
                System.out.println("Received exit command, exiting...");
                continue;
            }
            slot = dataInputStream.readInt();
            x = dataInputStream.readInt();
            y = dataInputStream.readInt();
            if (command == 'd') {
                downAtPoint(x, y, slot);
                continue;
            } else if (command == 'u') {
                upAtPoint(slot);
                continue;
            }
            System.out.println("Unexpected command " + command + "," + x + "," + y);
            running = false;
        } while (running);
    }

    public void run() {
        try {
            doStuff();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
