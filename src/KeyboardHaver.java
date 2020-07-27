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
    private final static String FIFO_DIR = "/data/fifo";
    private final static String INPUT_DIR = FIFO_DIR + "/inputs";
    JTextField typingArea;
    final Map<Integer, PrimeTimeButton> keyFileMap;
    final Map<Integer, Boolean> isPressed;
    final Runtime runtime;
    final String device = "/dev/input/event4";
    boolean somethingIsHeld;
    int keypressIndex = 0;
    Process inputProcess;
    OutputStream thingToSendTo;
    private final int EV_ABS = 3;
    private final int EV_KEY = 1;
    private final int EV_SYN = 0;
    private final int ABS_MT_SLOT = 47;
    private final int ABS_MT_WIDTH_MAJOR = 50;
    private final int ABS_MT_TRACKING_ID = 57;
    private final int ABS_MT_POSITION_X = 53;
    private final int ABS_MT_POSITION_Y = 54;
    private final int SYN_REPORT = 0;
    private final int BTN_TOUCH = 330;
    private final int DOWN = 1;
    private final int UP = 0;

    public KeyboardHaver(final String name) {
        super(name);
        somethingIsHeld = false;
        runtime = Runtime.getRuntime();

        final int width = 1080;
        Set<PrimeTimeButton> buttons = new HashSet<>();
        buttons.add(new PrimeTimeButton("hard-drop", 32, width - 257, 2031));

        buttons.add(new PrimeTimeButton("right", 74, width - 373, 2146));
        buttons.add(new PrimeTimeButton("down", 75, width - 256, 2260));
        buttons.add(new PrimeTimeButton("left", 76, width - 137, 2145));

        buttons.add(new PrimeTimeButton("ccw", 68, width - 750, 2155));
        buttons.add(new PrimeTimeButton("cw", 83, width - 948, 2040));
        buttons.add(new PrimeTimeButton("hold", 70, width - 949, 1827));

        keyFileMap = new HashMap<>();
        isPressed = new HashMap<>();
        buttons.forEach(button -> {
            keyFileMap.put(button.getKeyCode(), button);
            isPressed.put(button.getKeyCode(), false);
        });

        StartInputSendingProcess();
    }

    protected void addEvent(OutputStream stream, int type, int code, int value) {
        byte[] bytes = {
                (byte) 0xd7,
                (byte) 0xb6,
                (byte) 0x1b,
                (byte) 0x5f,
                (byte) 0x00,
                (byte) 0x00,
                (byte) 0x00,
                (byte) 0x00,
                (byte) 0x83,
                (byte) 0xff,
                (byte) 0x0c,
                (byte) 0x00,
                (byte) 0x00,
                (byte) 0x00,
                (byte) 0x00,
                (byte) 0x00,
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
            stream.write(bytes);
        } catch (IOException ioException) {
            System.out.println("could not write bytes: " + ioException.getMessage());
        }
    }

    protected void WriteUpInputFile(boolean wasHeld, OutputStream stream) throws IOException {
        addEvent(stream, EV_ABS, ABS_MT_TRACKING_ID, 0xffffffff);
        if (!somethingIsHeld) {
            addEvent(stream, EV_KEY, BTN_TOUCH, UP);
        }
        addEvent(stream, EV_SYN, SYN_REPORT, 0);
        addEvent(stream, EV_ABS, ABS_MT_SLOT, 0);
        addEvent(stream, EV_SYN, SYN_REPORT, 0);
    }


    protected void WriteInput(final PrimeTimeButton button, OutputStream stream) throws IOException {
        addEvent(stream, EV_ABS, ABS_MT_SLOT, 1);
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
        isPressed.replace(event.getKeyCode(), false);
        boolean wasHeld = somethingIsHeld;
        somethingIsHeld = isPressed.containsValue(true);
        typingArea.setText("");
        try {
            WriteUpInputFile(wasHeld, thingToSendTo);
            isPressed.forEach((keyCode, wellIsIt) -> {
                if (wellIsIt) {
                    try {
                        WriteInput(keyFileMap.get(keyCode), thingToSendTo);
                    } catch (IOException ioException) {
                        System.out.println("problem sending thing: " + ioException.getMessage());
                    }
                }
            });
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

    private static class PrimeTimeButton {
        final String name;
        final int keyCode;
        final int xPosition;
        final int yPosition;

        public PrimeTimeButton(String name, int keyCode, int xPosition, int yPosition) {
            this.name = name;
            this.keyCode = keyCode;
            this.xPosition = xPosition;
            this.yPosition = yPosition;
        }

        public String getName() {
            return name;
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
}
