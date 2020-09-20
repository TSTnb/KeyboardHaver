package com.tstman.tstgames.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import com.tstman.tstgames.PrimeTimeButton;
import com.tstman.tstgames.R;
import com.tstman.tstgames.inputinjection.Client;
import com.tstman.tstgames.view.HUDView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TSTgames extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
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
    private final int ABS_MT_WIDTH_MAJOR = 0x32;
    private final int ABS_MT_PRESSURE = 0x3a;
    private final int ABS_MT_POSITION_X = 0x35;
    private final int ABS_MT_POSITION_Y = 0x36;
    private final int ABS_MT_TOOL_TYPE = 0x37;
    private final int MT_TOOL_FINGER = 0x00;
    private final int SYN_REPORT = 0;
    private final int BTN_TOUCH = 0x14a;
    private final int DOWN = 1;
    private final int UP = 0;
    private PipedWriter pipedWriter;
    byte[] eventBytes;

    private Client inputClient;

    public void InitKeyboardHaverService() {
        isPressed = new HashMap<>();
        somethingIsHeld = false;
        runtime = Runtime.getRuntime();

        Set<PrimeTimeButton> buttons = new HashSet<>();

        int slotIndex = 0;

        buttons.add(new PrimeTimeButton("hard-drop", 62, 840, 2026, ++slotIndex));

        buttons.add(new PrimeTimeButton("right", 38, 737, 2131, ++slotIndex));
        buttons.add(new PrimeTimeButton("down", 39, 838, 2237, ++slotIndex));
        buttons.add(new PrimeTimeButton("left", 40, 943, 2132, ++slotIndex));

        buttons.add(new PrimeTimeButton("ccw", 32, 327, 2190, ++slotIndex));
        buttons.add(new PrimeTimeButton("cw", 47, 142, 2196, ++slotIndex));
        buttons.add(new PrimeTimeButton("hold", 34, 234, 2034, ++slotIndex));

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
        eventNamesByEventCode.put(ABS_MT_WIDTH_MAJOR, "ABS_MT_WIDTH_MAJOR");
        eventNamesByEventCode.put(ABS_MT_PRESSURE, "ABS_MT_PRESSURE");
        eventNamesByEventCode.put(ABS_MT_TOOL_TYPE, "ABS_MT_TOOL_TYPE");
        eventNamesByEventCode.put(ABS_MT_POSITION_X, "ABS_MT_POSITION_X");
        eventNamesByEventCode.put(ABS_MT_POSITION_Y, "ABS_MT_POSITION_Y");

        pipedWriter = new PipedWriter();
        PipedReader pipedReader;
        try {
            pipedReader = new PipedReader(pipedWriter);
        } catch (IOException e) {
            Log.e("tstgames", "Unable to initialized PipedReader", e);
            return;
        }
        inputClient = new Client(pipedReader, pipedWriter);
        new Thread(inputClient).start();

        /*device = getDevice();
        eventIsSupported = getSupportedEvents();
        eventBytes = getEventBytes();

        StartInputSendingProcess();*/
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

    protected byte[] getOutputBytes(Process process) {
        try {
            process.waitFor();
        } catch (InterruptedException interruptedException) {
            System.out.println("Could not wait for the process to exit: " + interruptedException.getMessage());
            return null;
        }

        byte[] buffer = new byte[256];
        try {
            byte[] bytes;
            int bytesRead = process.getInputStream().read(buffer);
            bytes = new byte[bytesRead];
            System.arraycopy(buffer, 0, bytes, 0, bytesRead);
            return bytes;
        } catch (IOException exception) {
            System.out.println("Problem printing stdout of process: " + exception.getMessage());
        }
        return null;
    }

    protected byte[] getEventBytes() {
        try {
            Process deviceProcess = runtime.exec(new String[]{"su"});
            OutputStreamWriter stdin = new OutputStreamWriter(deviceProcess.getOutputStream());
            stdin.write("cat " + device + " &"
                    + "sendevent " + device + " " + EV_ABS + " " + ABS_MT_SLOT + " 0;"
                    + "sendevent " + device + " " + EV_SYN + " " + ABS_MT_TRACKING_ID + " 0;"
                    + "sendevent " + device + " " + EV_ABS + " " + ABS_MT_POSITION_X + " 0;"
                    + "sendevent " + device + " " + EV_ABS + " " + ABS_MT_POSITION_Y + " 0;"
                    + "sendevent " + device + " " + EV_SYN + " " + SYN_REPORT + " 0;"
                    + "sendevent " + device + " " + EV_ABS + " " + ABS_MT_POSITION_X + " 123;"
                    + "sendevent " + device + " " + EV_ABS + " " + ABS_MT_POSITION_Y + " 123;"
                    + "sendevent " + device + " " + EV_SYN + " " + ABS_MT_TRACKING_ID + " -1;"
                    + "sendevent " + device + " " + EV_SYN + " " + SYN_REPORT + " 0;"
                    + "kill -9 %;"
                    + "exit;"
                    + "\n"
            );
            stdin.flush();
            return getEventPadding(deviceProcess);
        } catch (IOException ioException) {
            System.out.println("Unable to start the process that gets the event bytes: " + ioException.getMessage());
            return null;
        }
    }

    protected byte[] getEventPadding(Process process) {
        try {
            process.waitFor();
        } catch (InterruptedException interruptedException) {
            System.out.println("Could not wait for the process to exit: " + interruptedException.getMessage());
            return null;
        }

        String processOutput = new String(getOutputBytes(process), StandardCharsets.UTF_8);
        int positionXIndex = processOutput.indexOf(new String(eventToBytes(EV_ABS, ABS_MT_POSITION_X, 123), StandardCharsets.UTF_8));
        int positionYIndex = processOutput.indexOf(new String(eventToBytes(EV_ABS, ABS_MT_POSITION_Y, 123), StandardCharsets.UTF_8));
        if (positionXIndex == -1 || positionYIndex == -1) {
            System.out.println("Unable to determine how many bytes to pad events with");
        }
        return new byte[positionYIndex - positionXIndex - 8];
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

    protected byte[] eventToBytes(int type, int code, int value) {
        return new byte[]{
                (byte) (type & 0xff),
                (byte) ((type >> 8) & 0xff),
                (byte) (code & 0xff),
                (byte) ((code >> 8) & 0xff),
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 24) & 0xff),
        };
    }

    protected void addEvent(OutputStream stream, int type, int code, int value) {
        try {
            stream.write(eventBytes);
            stream.write(eventToBytes(type, code, value));
        } catch (IOException ioException) {
            System.out.println("could not write bytes: " + ioException.getMessage());
        }
    }

    protected void WriteUpInputFile(final PrimeTimeButton button, OutputStream stream) throws IOException {
        pipedWriter.write('u');
        pipedWriter.write(button.getSlot());
        pipedWriter.write(button.getXPosition());
        pipedWriter.write(button.getYPosition());
        pipedWriter.flush();
        /*
        addEvent(stream, EV_ABS, ABS_MT_SLOT, button.getSlot());
        addSupportedEvent(stream, EV_ABS, ABS_MT_PRESSURE, 0x00);
        addEvent(stream, EV_ABS, ABS_MT_TRACKING_ID, 0xffffffff);
        if (!somethingIsHeld) {
            addSupportedEvent(stream, EV_KEY, BTN_TOUCH, UP);
        }
        addEvent(stream, EV_SYN, SYN_REPORT, 0);*/
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

    protected void WriteInput(final PrimeTimeButton button, OutputStream stream) throws IOException {
        //doThatGesture(button);
        pipedWriter.write('d');
        pipedWriter.write(button.getSlot());
        pipedWriter.write(button.getXPosition());
        pipedWriter.write(button.getYPosition());
        pipedWriter.flush();
        /*
        addEvent(stream, EV_ABS, ABS_MT_SLOT, button.getSlot());
        addEvent(stream, EV_ABS, ABS_MT_TRACKING_ID, keypressIndex);
        addSupportedEvent(stream, EV_ABS, ABS_MT_TOOL_TYPE, MT_TOOL_FINGER);
        addSupportedEvent(stream, EV_ABS, ABS_MT_TOUCH_MAJOR, keypressIndex++);
        addSupportedEvent(stream, EV_ABS, ABS_MT_WIDTH_MAJOR, button.RandomInt(6, 9));
        addSupportedEvent(stream, EV_ABS, ABS_MT_PRESSURE, button.RandomInt(0x60, 0x90));
        if (!somethingIsHeld) {
            addSupportedEvent(stream, EV_KEY, BTN_TOUCH, DOWN);
        }
        addEvent(stream, EV_ABS, ABS_MT_POSITION_X, button.getXPosition());
        addEvent(stream, EV_ABS, ABS_MT_POSITION_Y, button.getYPosition());
        addEvent(stream, EV_SYN, SYN_REPORT, 0);
        */
    }

    private void doThatGesture(PrimeTimeButton button) {
        final int connectionId = (int) getPrivateMember(this, "android.accessibilityservice.AccessibilityService", "mConnectionId");
        final Object accessibilityInteractionClient = callPrivateMethod(
                null,
                "android.view.accessibility.AccessibilityInteractionClient",
                "getInstance",
                new Class[]{},
                new Object[]{}
        );
        final Object accessibilityServiceConnection = callPrivateMethod(
                accessibilityInteractionClient,
                "android.view.accessibility.AccessibilityInteractionClient",
                "getConnection",
                new Class[]{int.class},
                new Object[]{connectionId}
        );
        final Class classy = accessibilityServiceConnection.getClass();
        final String stringy = classy.toString();
        final Object systemSupport = getPrivateMember(accessibilityServiceConnection, "com.android.server.accessibility.AccessibilityServiceConnection", "mSystemSupport");
        dispatchGesture(createClick(button.getXPosition(), button.getYPosition()), new AccessibilityService.GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d("tstgames", "gesture completed");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.d("tstgames", "gesture cancelled");
            }
        }, null);
    }

    protected Object callPrivateMethod(Object object, String className, String methodName, Class[] argumentTypes, Object[] arguments) {
        final Class classReference;
        final Method method;
        Object result = null;
        try {
            classReference = Class.forName(className);
        } catch (ClassNotFoundException exception) {
            Log.w("tstgames", "aw, " + className + " class is not found: " + exception.getMessage());
            return null;
        }

        try {
            method = classReference.getMethod(methodName, argumentTypes);
        } catch (NoSuchMethodException exception) {
            exception.printStackTrace();
            return null;
        }

        try {
            result = method.invoke(object, arguments);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            exception.printStackTrace();
        }
        return result;
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

    private static GestureDescription createClick(float x, float y) {
        // for a single tap a duration of 1 ms is enough
        final int DURATION = 1;

        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        GestureDescription.StrokeDescription clickStroke =
                new GestureDescription.StrokeDescription(clickPath, 0, DURATION);
        GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
        clickBuilder.addStroke(clickStroke);
        return clickBuilder.build();
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
        //AccessibilityNodeInfo root = getRootInActiveWindow();
        ActivityManager am = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        Log.w("tstgames", "lol accessibility event");
    }

    private Activity activity;

    public void setActivity(Activity activity) {
        this.activity = activity;
        doThingy(activity);
    }

    private NotificationManager mNM;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.local_service_started;

    public class LocalBinder extends Binder {
        public TSTgames getService() {
            return TSTgames.this;
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        Log.w("keyboardhaver", "that shared preference was changed");
        if (str.equals("NOTIFICATION_NEEDED")) {
            Log.w("keyboardhaver", "dang a notification is needed");
        }
    }

    public void onCreate() {
        super.onCreate();
        InitKeyboardHaverService();
        if (activity == null) {
            return;
        }

        Log.w("keyboardhaver", "haha it was created");
    }

    public void doThingy(Context context) {
        if (true) {
            return;
        }
        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.w("tstman", "ok, it is set");
        Toast.makeText(getBaseContext(), "onCreate", Toast.LENGTH_LONG).show();
        View mView = new HUDView(getBaseContext());
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                /*WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE*/0,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.RIGHT | Gravity.TOP;
        params.setTitle("Load Average");
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.addView(mView, params);
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
            if (false) {
                thingToSendTo.flush();
            }
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
            if (false) {
                thingToSendTo.flush();
            }
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
