import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class KeyboardHaver extends JFrame implements KeyListener {
    JTextField typingArea;
    final Map<Integer, PrimeTimeButton> keyFileMap;
    Map<Integer, String> eventNamesByEventCode;
    Map<Integer, Boolean> eventIsSupported;
    final Map<Integer, Boolean> isPressed;
    final Runtime runtime;
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
    byte[] eventBytes;

    public KeyboardHaver(final String name) {
        super(name);
        somethingIsHeld = false;
        runtime = Runtime.getRuntime();

        Set<PrimeTimeButton> buttons = new HashSet<>();
        int slotIndex = 0;

        buttons.add(new PrimeTimeButton("ccw", 90, 750, 2155, ++slotIndex));
        buttons.add(new PrimeTimeButton("cw", 88, 948, 2040, ++slotIndex));
        buttons.add(new PrimeTimeButton("hold", 67, 949, 1827, ++slotIndex));

        buttons.add(new PrimeTimeButton("hard-drop", 32, 257, 2031, ++slotIndex));

        buttons.add(new PrimeTimeButton("left", 37, 137, 2145, ++slotIndex));
        buttons.add(new PrimeTimeButton("right", 39, 373, 2146, ++slotIndex));
        buttons.add(new PrimeTimeButton("down", 40, 256, 2260, ++slotIndex));

        keyFileMap = new HashMap<>();
        isPressed = new HashMap<>();
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

        device = getDevice();
        eventIsSupported = getSupportedEvents();
        if (device.equals("NO_DEVICE")) {
            System.out.println("Unable to get device");
        }
        eventBytes = getEventBytes();
        ;

        StartInputSendingProcess();
    }

    protected String getDevice() {
        try {
            Process deviceProcess = runtime.exec(new String[]{"adb", "shell"});
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
            return getOutputString(deviceProcess);
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

    protected byte[] getEventBytes() {
        try {
            Process deviceProcess = runtime.exec(new String[]{"adb", "shell", "su"});
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
            Process deviceProcess = runtime.exec(new String[]{"adb", "shell", "su"});
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

    protected void WriteUpInputFile(final PrimeTimeButton button, OutputStream stream) {
        addEvent(stream, EV_ABS, ABS_MT_SLOT, button.getSlot());
        addSupportedEvent(stream, EV_ABS, ABS_MT_PRESSURE, 0x00);
        addEvent(stream, EV_ABS, ABS_MT_TRACKING_ID, 0xffffffff);
        if (!somethingIsHeld) {
            addSupportedEvent(stream, EV_KEY, BTN_TOUCH, UP);
        }
        addEvent(stream, EV_SYN, SYN_REPORT, 0);
    }

    protected void WriteInput(final PrimeTimeButton button, OutputStream stream) {
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
    }

    protected void StartInputSendingProcess() {
        String[] pipeCommand = new String[]{"adb", "shell", "su", "-c", "'tee " + device + " >/dev/null'"};
        try {
            inputProcess = runtime.exec(pipeCommand);
            thingToSendTo = inputProcess.getOutputStream();
        } catch (IOException ioException) {
            System.out.println("Unable to start input-sending process: " + ioException.getMessage());
            System.exit(1);
        }
    }

    public void keyTyped(KeyEvent event) {
    }

    public void keyPressed(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (!isPressed.containsKey(keyCode)) {
            return;
        }
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
        }
    }

    public void keyReleased(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (!isPressed.containsKey(keyCode)) {
            return;
        }
        isPressed.replace(keyCode, false);
        somethingIsHeld = isPressed.containsValue(true);
        typingArea.setText("");
        try {
            WriteUpInputFile(keyFileMap.get(keyCode), thingToSendTo);
            thingToSendTo.flush();
        } catch (IOException ioException) {
            printStuff(inputProcess);
            System.out.println("Problem releasing key: " + ioException.getMessage());
        }
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

    private void addStuff() {
        typingArea = new JTextField(20);
        typingArea.addKeyListener(this);
        typingArea.setFocusTraversalKeysEnabled(false);
        getContentPane().add(typingArea, BorderLayout.PAGE_START);
    }

    private static void showStuff() {
        KeyboardHaver frame = new KeyboardHaver("Plug in phone before starting this, restart if phone gets disconnected");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addStuff();
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(KeyboardHaver::showStuff);
    }
}
