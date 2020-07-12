import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

public class KeyEventDemo extends JFrame implements KeyListener {
    JTextField typingArea;
    Map<Integer, String> keyFileMap;
    Map<Integer, Boolean> isPressed;
    Runtime runtime;
    Process inputProcess;
    OutputStream thingToSendTo;
    OutputStreamWriter thingToSendWith;

    public KeyEventDemo(final String name) {
        super(name);
        runtime = Runtime.getRuntime();
        keyFileMap = new HashMap<>();
        keyFileMap.put(83, "ccw");
        keyFileMap.put(68, "cw");
        keyFileMap.put(70, "hold");

        keyFileMap.put(74, "left");
        keyFileMap.put(75, "down");
        keyFileMap.put(76, "right");

        keyFileMap.put(32, "hard-drop");

        isPressed = new HashMap<>();
        isPressed.put(83, false);
        isPressed.put(68, false);
        isPressed.put(70, false);

        isPressed.put(74, false);
        isPressed.put(75, false);
        isPressed.put(76, false);

        isPressed.put(32, false);

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
}
