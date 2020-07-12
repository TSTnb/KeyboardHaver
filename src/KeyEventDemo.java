import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class KeyEventDemo extends JFrame implements KeyListener {
    JTextField typingArea;
    final Map<Integer, String> keyFileMap;
    final Map<Integer, Boolean> isPressed;
    final Runtime runtime;
    Process inputRelay;
    Process inputProcess;
    OutputStream thingToSendTo;
    OutputStreamWriter thingToSendWith;

    public KeyEventDemo(final String name) {
        super(name);
        runtime = Runtime.getRuntime();

        Set<PrimeTimeButton> buttons = new HashSet<>();
        buttons.add(new PrimeTimeButton("hard-drop", 32, 257, 2031));

        buttons.add(new PrimeTimeButton("right", 76, 373, 2146));
        buttons.add(new PrimeTimeButton("down", 75, 256, 2260));
        buttons.add(new PrimeTimeButton("left", 74, 137, 2145));

        buttons.add(new PrimeTimeButton("ccw", 83, 748, 2153));
        buttons.add(new PrimeTimeButton("cw", 68, 948, 2040));
        buttons.add(new PrimeTimeButton("hold", 70, 949, 1827));

        keyFileMap = new HashMap<>();
        isPressed = new HashMap<>();
        buttons.forEach(button -> {
            keyFileMap.put(button.getKeyCode(), button.getName());
            isPressed.put(button.getKeyCode(), false);
        });

        String[] pipeCommand = new String[]{"adb", "shell", "su", "-c", "'cat > /data/fifo/fifo'"};
        try {
            inputProcess = runtime.exec(pipeCommand);
            thingToSendTo = inputProcess.getOutputStream();
            thingToSendWith = new OutputStreamWriter(thingToSendTo);
            /*BufferedReader output = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            int exitCode = process.exitValue();
            String line = "";
            do {
                line = output.readLine();
                System.out.println(line);
            } while (line != null);*/
        } catch (IOException ioException) {
            System.out.println(ioException.getMessage());
            System.exit(1);
        }
    }

    public void keyTyped(KeyEvent event) {
    }

    public void keyPressed(KeyEvent event) {
        int keyCode = event.getKeyCode();
        try {
            if (isPressed.get(keyCode)) {
                return;
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }
        isPressed.replace(keyCode, true);
        try {
            thingToSendWith.write(keyFileMap.get(keyCode) + "\n");
            thingToSendWith.flush();
        } catch (IOException ioException) {
            System.out.println("problem sending thing: " + ioException.getMessage());
        }
    }

    public void keyReleased(KeyEvent event) {
        isPressed.replace(event.getKeyCode(), false);
        typingArea.setText("");
        try {
            thingToSendWith.write("up\n");
            /*isPressed.forEach((keyCode, wellIsIt) -> {
                if (wellIsIt) {
                    try {
                        thingToSendWith.write(keyFileMap.get(keyCode) + "\n");
                    } catch (IOException ioException) {
                        System.out.println("problem sending thing: " + ioException.getMessage());
                    }
                }
            });*/
            thingToSendWith.flush();
        } catch (IOException ioException) {
            System.out.println("problem sending up thing: " + ioException.getMessage());
        }
    }

    private void addStuff() {
        typingArea = new JTextField(20);
        typingArea.addKeyListener(this);
        typingArea.setFocusTraversalKeysEnabled(false);
        getContentPane().add(typingArea, BorderLayout.PAGE_START);
    }

    private static void showStuff() {
        KeyEventDemo frame = new KeyEventDemo("Hey Guys");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addStuff();
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(KeyEventDemo::showStuff);
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
