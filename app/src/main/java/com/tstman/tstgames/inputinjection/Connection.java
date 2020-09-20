package com.tstman.tstgames.inputinjection;

import android.hardware.input.InputManager;
import android.net.LocalSocket;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import com.tstman.tstgames.Engooden;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

public class Connection implements Runnable {

    volatile Socket socket;
    volatile InputStream inputStream;
    volatile OutputStream outputStream;
    private volatile boolean running;
    Set<Integer> pressedButtons;
    int[] buttonIndices;
    boolean useMultitouch;

    public Connection(Socket socket) {
        this.running = true;
        this.socket = socket;
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


    public void downAtPoint(int x, int y, int slot) {
        final int motionEvent;
        int index = buttonIndices[slot];
        System.out.println("index down: " + index);
        if (index >= 0) {
            index <<= 8;
            motionEvent = MotionEvent.ACTION_POINTER_DOWN | index;
            //motionEvent = MotionEvent.ACTION_POINTER_DOWN | (slot << 8);
        } else {
            motionEvent = MotionEvent.ACTION_DOWN;
        }
        actionAtPoint(x, y, motionEvent);
    }

    public void upAtPoint(int x, int y, int slot) {
        int motionEvent;
        int index = buttonIndices[slot];
        System.out.println("index up: " + index);
        if (pressedButtons.size() > 0) {
            useMultitouch = true;
            //index <<= 8;
            index = 0;
            motionEvent = MotionEvent.ACTION_POINTER_UP | index;
            //motionEvent = MotionEvent.ACTION_POINTER_UP | (slot << 8);
            actionAtPoint(x, y, motionEvent);
        } else {

            System.out.println("(final release)");
            if (useMultitouch) {
                useMultitouch = false;
                motionEvent = MotionEvent.ACTION_DOWN;
                actionAtPoint(x, y, motionEvent);
            }
            motionEvent = MotionEvent.ACTION_UP;
        }
        actionAtPoint(x, y, motionEvent);
    }

    InputManager inputManager;
    Method injectInputEvent;

    public void actionAtPoint(int x, int y, int action) {
        if (inputManager == null) {
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

        long time = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(time, time, action, x, y, 1.0f, 1.0f, 0, 1.0f, 1.0f, 0, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);

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

    protected void doStuff() throws IOException {
        if (!readPassword()) {
            return;
        }
        System.out.println("Client " + socket.getRemoteSocketAddress().toString() + " has successfully authenticated.");
        DataInputStream dataInputStream = new DataInputStream(inputStream);
        pressedButtons = new HashSet<>();
        buttonIndices = new int[8];
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
                pressedButtons.add(slot);
                buttonIndices[slot] = pressedButtons.size() - 2;
                downAtPoint(x, y, slot);
                continue;
            } else if (command == 'u') {
                pressedButtons.remove(slot);
                upAtPoint(x, y, slot);
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
