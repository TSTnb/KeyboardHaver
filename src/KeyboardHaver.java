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
    Process inputProcess;
    OutputStream thingToSendTo;
    OutputStreamWriter thingToSendWith;

    public KeyboardHaver(final String name) {
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
            keyFileMap.put(button.getKeyCode(), button);
            isPressed.put(button.getKeyCode(), false);
        });

        StartInputSendingProcess();
    }

    protected int RandomInt(int min, int max) {
        return min + (int) (Math.random() * (max - min) + 1);
    }

    protected String BuildUpInputFile(final String device) {
        final String sendEvent = "sendevent " + device;
        return "" +
                sendEvent + " 1 330 0" + ";\n" +
                sendEvent + " 0 0 0" + ";\n";
    }

    protected String BuildInput(final PrimeTimeButton button, final String device) {
        final String sendEvent = "sendevent " + device;
        return "" +
                sendEvent + " 3 53 " + (button.getXPosition() + RandomInt(-10, 10)) + ";\n" +
                sendEvent + " 3 54 " + (button.getYPosition() + RandomInt(-10, 10)) + ";\n" +
                sendEvent + " 1 330 1" + ";\n" +
                sendEvent + " 0 0 0" + ";\n";
    }

    protected String BuildInputFile(final PrimeTimeButton button, final String device) {
        final String sendEvent = "sendevent " + device;
        return "" +
                sendEvent + " 3 53 " + (button.getXPosition() + RandomInt(-10, 10)) + ";\n" +
                sendEvent + " 3 54 " + (button.getYPosition() + RandomInt(-10, 10)) + ";\n" +
                sendEvent + " 1 330 1" + ";\n" +
                sendEvent + " 0 0 0" + ";\n";
    }

    protected String GetDevice() {
        return "/dev/input/event4";
    }

    protected String BuildWriteInputFile(final String inputFileContents, final String name) {
        final String inputFile = INPUT_DIR + "/" + name;
        String inputCat = "<<INPUT_FILE cat > " + inputFile + ";\n";
        return "" +
                inputCat +
                inputFileContents +
                "INPUT_FILE\n" +
                "chmod +x " + inputFile + "\n";
    }

    protected String BuildWriteInputFilesCommand() {
        final String device = GetDevice();
        final StringBuilder commandBuilder = new StringBuilder();

        commandBuilder.append("input_dir=" + INPUT_DIR + ";\n");
        commandBuilder.append("if [[ ! -d $input_dir ]]; then" + "\n");
        commandBuilder.append("  rm -rf $input_dir;" + "\n");
        commandBuilder.append("  mkdir -p $input_dir;" + "\n");
        commandBuilder.append("fi;" + "\n");
        commandBuilder.append("cd $input_dir;" + "\n");

        keyFileMap.forEach((keyCode, button) -> commandBuilder.append(BuildWriteInputFile(BuildInputFile(button, device), button.getName())));
        commandBuilder.append(BuildWriteInputFile(BuildUpInputFile(device), "up"));
        return commandBuilder.toString();
    }

    protected String RelayCommand() {
        return "" +
                "fifo_dir=" + FIFO_DIR + ";" + "\n" +
                "if [[ ! -d $fifo_dir ]]; then" + "\n" +
                "  rm -rf $fifo_dir;" + "\n" +
                "  mkdir -p $fifo_dir;" + "\n" +
                "fi;" + "\n" +
                "cd $fifo_dir;" + "\n" +
                "fifo=fifo;" + "\n" +
                "if [[ ! -p $fifo ]]; then" + "\n" +
                "  rm -rf $fifo;" + "\n" +
                "  mkfifo $fifo;" + "\n" +
                "fi;" + "\n" +
                "while true; do" + "\n" +
                "  while IFS= read -r command; do" + "\n" +
                "    inputs/${command};" + "\n" +
                "  done;" + "\n" +
                "done <$fifo;";
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
            PrimeTimeButton button = keyFileMap.get(keyCode);
            thingToSendWith.write(BuildInputFile(button, GetDevice()));
            thingToSendWith.flush();
        } catch (IOException ioException) {
            printStuff(inputProcess);
            System.out.println("Problem sending input: " + ioException.getMessage());
        }
    }

    public void keyReleased(KeyEvent event) {
        int keyCode = event.getKeyCode();
        isPressed.replace(keyCode, false);
        typingArea.setText("");
        try {
            thingToSendWith.write(BuildUpInputFile(GetDevice()));
            /*isPressed.forEach((keyCode, wellIsIt) -> {
                if (wellIsIt) {
                    try {
                        thingToSendWith.write(keyFileMap.get(keyCode).getName() + "\n");
                    } catch (IOException ioException) {
                        System.out.println("problem sending thing: " + ioException.getMessage());
                    }
                }
            });*/
            thingToSendWith.flush();
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
