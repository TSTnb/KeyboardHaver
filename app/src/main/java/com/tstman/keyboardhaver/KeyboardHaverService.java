package com.tstman.keyboardhaver;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class KeyboardHaverService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    Map<Integer, PrimeTimeButton> keyFileMap;
    Map<Integer, String> eventNamesByEventCode;
    Map<Integer, Boolean> eventIsSupported;
    Map<Integer, Boolean> isPressed;
    Runtime runtime;
    String device;
    boolean somethingIsHeld;
    int keypressIndex = 0;
    Process inputProcess;
    OutputStream thingToSendTo;
    private final int EV_ABS = 3;
    private final int EV_KEY = 1;
    private final int EV_SYN = 0;
    private final int ABS_MT_SLOT = 0x2f;
    private final int ABS_MT_TRACKING_ID = 0x39;
    private final int ABS_MT_TOUCH_MAJOR = 0x86;
    private final int ABS_MT_PRESSURE = 0x3a;
    private final int ABS_MT_POSITION_X = 0x35;
    private final int ABS_MT_POSITION_Y = 0x36;
    private final int SYN_REPORT = 0;
    private final int BTN_TOUCH = 0x14a;
    private final int DOWN = 1;
    private final int UP = 0;
    byte[] eventBytes;


    public void InitKeyboardHaverService() {
        isPressed = new HashMap<>();
        somethingIsHeld = false;
        runtime = Runtime.getRuntime();
        final int width = 1080;

        Set<PrimeTimeButton> buttons = new HashSet<>();

        int slotIndex = 0;

        buttons.add(new PrimeTimeButton("hard-drop", 62, width - 257, 2031, ++slotIndex));

        buttons.add(new PrimeTimeButton("right", 38, width - 373, 2146, ++slotIndex));
        buttons.add(new PrimeTimeButton("down", 39, width - 256, 2260, ++slotIndex));
        buttons.add(new PrimeTimeButton("left", 40, width - 137, 2145, ++slotIndex));

        buttons.add(new PrimeTimeButton("ccw", 32, width - 750, 2155, ++slotIndex));
        buttons.add(new PrimeTimeButton("cw", 47, width - 948, 2040, ++slotIndex));
        buttons.add(new PrimeTimeButton("hold", 34, width - 949, 1827, ++slotIndex));

        keyFileMap = new HashMap<>();
        buttons.forEach(button -> {
            keyFileMap.put(button.getKeyCode(), button);
            isPressed.put(button.getKeyCode(), false);
        });

        eventNamesByEventCode = new HashMap<>();
        eventNamesByEventCode.put(BTN_TOUCH, "BTN_TOUCH");
        eventNamesByEventCode.put(ABS_MT_SLOT, "ABS_MT_SLOT");
        eventNamesByEventCode.put(ABS_MT_TRACKING_ID, "ABS_MT_TRACKING_ID");
        eventNamesByEventCode.put(ABS_MT_TOUCH_MAJOR, "ABS_MT_TOUCH_MAJOR");
        eventNamesByEventCode.put(ABS_MT_PRESSURE, "ABS_MT_PRESSURE");
        eventNamesByEventCode.put(ABS_MT_POSITION_X, "ABS_MT_POSITION_X");
        eventNamesByEventCode.put(ABS_MT_POSITION_Y, "ABS_MT_POSITION_Y");

        device = getDevice();
        eventIsSupported = getSupportedEvents();
        eventBytes = new byte[16];

        StartInputSendingProcess();
    }

    protected String getDevice() {
        try {
            Process deviceProcess = runtime.exec(new String[]{"su"});
            OutputStreamWriter stdin = new OutputStreamWriter(deviceProcess.getOutputStream());
            stdin.write("search_string=ABS_MT_POSITION_X;"
                    + "picked_device=NO_DEVICE;"
                    + "for index in $(ls /dev/input/event* | sed 's|.*/dev/input/event||g' | sort -g); do"
                    + "  device=/dev/input/event$index;"
                    + "  if getevent -lp $device | grep -q $search_string; then"
                    + "    picked_device=$device;"
                    + "    break;"
                    + "  fi;"
                    + "done;"
                    + "echo $picked_device;"
                    + "exit;\n"
            );
            stdin.flush();
            String device = getOutputString(deviceProcess);
            if (device.equals("NO_DEVICE")) {
                System.out.println("Unable to get device");
                printStuff(deviceProcess);
            }
            return device;
        } catch (IOException ioException) {
            System.out.println("Unable to start the process that detects your touchscreen device: " + ioException.getMessage());
            return null;
        }
    }

    protected String getOutputString(Process process) {
        try {
            process.waitFor();
        } catch (InterruptedException interruptedException) {
            System.out.println("Could not wait for the process to exit: " + interruptedException.getMessage());
            return null;
        }

        BufferedReader processOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder result = new StringBuilder();
        String line;
        try {
            do {
                line = processOutput.readLine();
                if (line != null) {
                    result.append(line);
                }
            } while (line != null);
        } catch (IOException exception) {
            System.out.println("Problem printing stdout of process: " + exception.getMessage());
        }
        if (result.length() == 0) {
            printStuff(process);
        }
        return result.toString();
    }

    protected Map<Integer, Boolean> getSupportedEvents() {
        Map<Integer, Boolean> eventIsSupported = new HashMap<>();
        try {
            Process deviceProcess = runtime.exec(new String[]{"su"});
            OutputStreamWriter stdin = new OutputStreamWriter(deviceProcess.getOutputStream());
            stdin.write("getevent -lp " + device + ";"
                    + "exit;"
                    + "\n"
            );
            stdin.flush();
            String outputString = getOutputString(deviceProcess);
            eventNamesByEventCode.forEach((eventCode, eventName) -> {
                eventIsSupported.put(eventCode, outputString.matches(".* " + eventName + " .*"));
            });
        } catch (IOException ioException) {
            System.out.println("Unable to start the process that gets the event bytes: " + ioException.getMessage());
        }
        return eventIsSupported;
    }

    protected void addSupportedEvent(OutputStream stream, int type, int code, int value) {
        if (eventIsSupported.get(code)) {
            addEvent(stream, type, code, value);
        }
    }

    protected void addEvent(OutputStream stream, int type, int code, int value) {
        byte[] inputBytes = {
                (byte) (type & 0xff),
                (byte) ((type >> 8) & 0xff),
                (byte) (code & 0xff),
                (byte) ((code >> 8) & 0xff),
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 24) & 0xff),
        };
        try {
            stream.write(eventBytes);
            stream.write(inputBytes);
        } catch (IOException ioException) {
            System.out.println("could not write bytes: " + ioException.getMessage());
        }
    }

    protected void WriteUpInputFile(final PrimeTimeButton button, OutputStream stream) {
        addEvent(stream, EV_ABS, ABS_MT_SLOT, button.getSlot());
        addSupportedEvent(stream, EV_ABS, ABS_MT_PRESSURE, 0x00);
        addEvent(stream, EV_ABS, ABS_MT_TRACKING_ID, 0xffffffff);
        if (!somethingIsHeld) {
            addSupportedEvent(stream, EV_KEY, BTN_TOUCH, UP);
        }
        addEvent(stream, EV_SYN, SYN_REPORT, 0);
    }

    protected void StartInputSendingProcess() {
        String[] pipeCommand = new String[]{"su", "-c", "tee " + device + " >/dev/null"};
        try {
            inputProcess = runtime.exec(pipeCommand);
            thingToSendTo = inputProcess.getOutputStream();
        } catch (IOException ioException) {
            System.out.println("Unable to start input-sending process: " + ioException.getMessage());
            System.exit(1);
        }
    }

    protected void WriteInput(final PrimeTimeButton button, OutputStream stream) {
        addEvent(stream, EV_ABS, ABS_MT_SLOT, button.getSlot());
        addEvent(stream, EV_ABS, ABS_MT_TRACKING_ID, keypressIndex);
        addSupportedEvent(stream, EV_ABS, ABS_MT_TOUCH_MAJOR, keypressIndex++);
        addSupportedEvent(stream, EV_ABS, ABS_MT_PRESSURE, button.RandomInt(0x60, 0x90));
        if (!somethingIsHeld) {
            addSupportedEvent(stream, EV_KEY, BTN_TOUCH, DOWN);
        }
        addEvent(stream, EV_ABS, ABS_MT_POSITION_X, button.getXPosition());
        addEvent(stream, EV_ABS, ABS_MT_POSITION_Y, button.getYPosition());
        addEvent(stream, EV_SYN, SYN_REPORT, 0);
    }

    private void printStuff(Process process) {
        BufferedReader output = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        int exitCode = process.exitValue();
        System.out.println("Command is " + process.toString());
        System.out.println("Exit value is " + exitCode);
        String line = "";
        try {
            do {
                line = output.readLine();
                System.out.println(line);
            } while (line != null);
        } catch (IOException exception) {
            System.out.println("Problem printing stderr: " + exception.getMessage());
        }
        output = new BufferedReader(new InputStreamReader(process.getInputStream()));
        try {
            do {
                line = output.readLine();
                System.out.println(line);
            } while (line != null);
        } catch (IOException exception) {
            System.out.println("Problem printing stdout: " + exception.getMessage());
        }
    }

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

    protected void keyPressed(KeyEvent keyEvent) {
        int keyCode = keyEvent.getKeyCode();
        if (isPressed.get(keyCode)) {
            return;
        }
        isPressed.replace(keyCode, true);
        try {
            WriteInput(keyFileMap.get(keyCode), thingToSendTo);
            thingToSendTo.flush();
        } catch (IOException ioException) {
            printStuff(inputProcess);
            System.out.println("Problem sending input: " + ioException.getMessage());
            InitKeyboardHaverService();
        }
    }

    protected void keyReleased(KeyEvent keyEvent) {
        int keyCode = keyEvent.getKeyCode();
        isPressed.replace(keyCode, false);
        somethingIsHeld = isPressed.containsValue(true);
        try {
            WriteUpInputFile(keyFileMap.get(keyCode), thingToSendTo);
            thingToSendTo.flush();
        } catch (IOException ioException) {
            printStuff(inputProcess);
            System.out.println("Problem releasing key: " + ioException.getMessage());
            InitKeyboardHaverService();
        }
    }

    public boolean onKeyEvent(KeyEvent keyEvent) {
        super.onKeyEvent(keyEvent);
        if (!isPressed.containsKey(keyEvent.getKeyCode())) {
            return true;
        }
        int keyEventAction = keyEvent.getAction();
        if (keyEventAction == KeyEvent.ACTION_DOWN) {
            keyPressed(keyEvent);
        } else if (keyEventAction == KeyEvent.ACTION_UP) {
            keyReleased(keyEvent);
        }
        return true;
    }
}
