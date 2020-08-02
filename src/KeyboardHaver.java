import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class KeyboardHaver extends JFrame implements KeyListener {
    JTextField typingArea;
    final Map<Integer, PrimeTimeButton> keyFileMap;
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
    private final int ABS_MT_POSITION_X = 0x35;
    private final int ABS_MT_POSITION_Y = 0x36;
    private final int SYN_REPORT = 0;
    private final int BTN_TOUCH = 0x14a;
    private final int DOWN = 1;
    private final int UP = 0;
    byte[] eventBytes;

    public KeyboardHaver(final String name) {
        super(name);
        somethingIsHeld = false;
        runtime = Runtime.getRuntime();

        final int width = 1080;
        Set<PrimeTimeButton> buttons = new HashSet<>();
        int slotIndex = 0;
        buttons.add(new PrimeTimeButton("hard-drop", 32, width - 257, 2031, ++slotIndex));

        buttons.add(new PrimeTimeButton("right", 74, width - 373, 2146, ++slotIndex));
        buttons.add(new PrimeTimeButton("down", 75, width - 256, 2260, ++slotIndex));
        buttons.add(new PrimeTimeButton("left", 76, width - 137, 2145, ++slotIndex));

        buttons.add(new PrimeTimeButton("ccw", 68, width - 750, 2155, ++slotIndex));
        buttons.add(new PrimeTimeButton("cw", 83, width - 948, 2040, ++slotIndex));
        buttons.add(new PrimeTimeButton("hold", 70, width - 949, 1827, ++slotIndex));

        keyFileMap = new HashMap<>();
        isPressed = new HashMap<>();
        buttons.forEach(button -> {
            keyFileMap.put(button.getKeyCode(), button);
            isPressed.put(button.getKeyCode(), false);
        });

        device = getDevice();
        if (device.equals("NO_DEVICE")) {
            System.out.println("Unable to get device");
        }
        eventBytes = new byte[16];

        StartInputSendingProcess();
    }

    protected String getDevice() {
        try {
            Process deviceProcess = runtime.exec(new String[]{"adb", "shell"});
            OutputStreamWriter stdin = new OutputStreamWriter(deviceProcess.getOutputStream());
            stdin.write("search_string=ABS_MT_POSITION_X;"
                    + "picked_device=NO_DEVICE;"
                    + "for index in $(ls /dev/input/event* | awk -F'/dev/input/event' '{print $2}' | sort -g); do"
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
        addEvent(stream, EV_ABS, ABS_MT_TRACKING_ID, 0xffffffff);
        if (!somethingIsHeld) {
            addEvent(stream, EV_KEY, BTN_TOUCH, UP);
        }
        addEvent(stream, EV_SYN, SYN_REPORT, 0);
    }

    protected void WriteInput(final PrimeTimeButton button, OutputStream stream) {
        addEvent(stream, EV_ABS, ABS_MT_SLOT, button.getSlot());
        addEvent(stream, EV_ABS, ABS_MT_TRACKING_ID, keypressIndex++);
        if (!somethingIsHeld) {
            addEvent(stream, EV_KEY, BTN_TOUCH, DOWN);
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