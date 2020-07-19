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
    boolean somethingIsHeld;
    int keypressIndex = 0;
    Process inputProcess;
    OutputStream thingToSendTo;
    OutputStreamWriter thingToSendWith;

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

    protected int RandomInt(int min, int max) {
        return min + (int) (Math.random() * (max - min) + 1);
    }

    protected String BuildUpInputFile(final String device, boolean wasHeld) {
        final String sendEvent = "sendevent " + device;
        final StringBuilder upInput = new StringBuilder();

        //upInput = new StringBuilder(sendEvent + " 3 57 " + 0xffffffff + ";\n");
        //if (!somethingIsHeld) {
        upInput.append(sendEvent).append(" 1 330 0").append(";\n");
        //}
        upInput.append(sendEvent).append(" 0 0 0").append(";\n");
        /*upInput.append(sendEvent).append(" 3 47 0").append(";\n");
        upInput.append(sendEvent).append(" 0 0 0").append(";\n");*/
        return upInput.toString();
    }

    protected String BuildInput(final PrimeTimeButton button, final String device) {
        final String sendEvent = "sendevent " + device;
        final StringBuilder input = new StringBuilder();
        /*input.append(sendEvent).append(" 3 47 1").append(";\n");
        input.append(sendEvent).append(" 3 57 ").append(keypressIndex++).append(";\n");*/
        //if (!somethingIsHeld) {
        input.append(sendEvent).append(" 1 330 1").append(";\n");
        //}
        input.append(sendEvent).append(" 3 53 ").append(button.getXPosition() + RandomInt(-10, 10)).append(";\n");
        input.append(sendEvent).append(" 3 54 ").append(button.getYPosition() + RandomInt(-10, 10)).append(";\n");
        input.append(sendEvent).append(" 0 0 0").append(";\n");

        return input.toString();
    }

    protected String GetDevice() {
        return "/dev/input/event4";
    }

    protected void StartInputSendingProcess() {
        String[] pipeCommand = new String[]{"adb", "shell", "su"};
        try {
            inputProcess = runtime.exec(pipeCommand);
            thingToSendTo = inputProcess.getOutputStream();
            thingToSendWith = new OutputStreamWriter(thingToSendTo);
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
            thingToSendWith.write(BuildInput(keyFileMap.get(keyCode), GetDevice()));
            thingToSendWith.flush();
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
            thingToSendWith.write(BuildUpInputFile(GetDevice(), wasHeld));
            thingToSendWith.flush();
            /*isPressed.forEach((keyCode, wellIsIt) -> {
                if (wellIsIt) {
                    try {
                        thingToSendWith.write(BuildInput(keyFileMap.get(keyCode), GetDevice()));
                        thingToSendWith.flush();
                    } catch (IOException ioException) {
                        System.out.println("problem sending thing: " + ioException.getMessage());
                    }
                }
            });*/
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
            return xPosition;
        }

        public int getYPosition() {
            return yPosition;
        }
    }
}
