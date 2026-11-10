import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Client extends JFrame {

    private static final String LOG_FILE = "protocol.txt";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int BUF_SIZE = 4096;
    private static final int RECEIVE_TIMEOUT_MS = 3000;

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;

    private String host;
    private int port;

    private final JLabel statusLabel = new JLabel();
    private final JTextArea logArea = new JTextArea();
    private final JTextField inputField = new JTextField();

    private final Object logLock = new Object();

    private DatagramSocket socket;
    private volatile boolean connected;
    private volatile boolean closed;

    public Client(String host, int port) {
        super("UDP-клиент");
        this.host = host;
        this.port = port;

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        buildGui();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closed = true;
                if (connected) {
                    record("Session closed by user: " + host + ":" + port);
                    connected = false;
                } else {
                    record("Application window closed");
                }
                closeQuietly();
                dispose();
                System.exit(0);
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    private void buildGui() {
        statusLabel.setText("Ожидание команды connect <адрес> <порт> " + "(по умолчанию " + host + ":" + port + ")");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(false);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setPreferredSize(new Dimension(720, 360));

        inputField.setEnabled(true);
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_END) {
                    e.consume();
                    handleInput();
                }
            }
        });

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.add(new JLabel("Строка:"), BorderLayout.WEST);
        inputRow.add(inputField, BorderLayout.CENTER);

        JLabel hint = new JLabel("End - отправить строку; " + "connect <адрес> <порт> - задать сервер");
        hint.setFont(hint.getFont().deriveFont(Font.BOLD));
        hint.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        bottom.add(inputRow);
        bottom.add(hint);

        add(statusLabel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private void handleInput() {
        String text = inputField.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        inputField.setText("");

        if (text.startsWith("connect ")) {
            String[] parts = text.trim().split("\\s+");
            if (parts.length != 3) {
                record("Invalid command: " + text + " (expected: connect <адрес> <порт>)");
                return;
            }
            try {
                String newHost = parts[1];
                int newPort = Integer.parseInt(parts[2]);
                record("Command: connect " + newHost + " " + newPort);
                connectTo(newHost, newPort);
            } catch (NumberFormatException ex) {
                record("Invalid port in command: " + text);
            }
            return;
        }
        sendLine(text);
    }

    private void connectTo(String newHost, int newPort) {
        closeQuietly();
        this.host = newHost;
        this.port = newPort;
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(RECEIVE_TIMEOUT_MS);
            connected = true;
            setStatus("UDP-сеанс с " + host + ":" + port);
            record("Session established: " + host + ":" + port);
        } catch (IOException e) {
            setStatus("Не удалось открыть сокет: " + e.getMessage());
            record("Session failed: " + host + ":" + port + " (" + e.getMessage() + ")");
        }
    }

    private void sendLine(String text) {
        if (!connected || socket == null || socket.isClosed()) {
            record("Cannot send (not connected): " + text);
            return;
        }
        try {
            byte[] data = text.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(
                    data, data.length, InetAddress.getByName(host), port);
            socket.send(packet);
            record("Sent to server: " + text);

            if (text.startsWith("load ")) {
                receiveResponse();
            }
        } catch (IOException e) {
            record("Send error: " + e.getMessage());
        }
    }

    private void receiveResponse() {
        byte[] buf = new byte[BUF_SIZE];
        try {
            while (connected && socket != null && !socket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String line = new String(packet.getData(), 0, packet.getLength(), "UTF-8").trim();
                record("Got from server: " + line);
                if ("__EOF__".equals(line)) {
                    break;
                }
                if (line.startsWith("ERROR")) {
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            record("Receive timeout: no response from " + host + ":" + port);
        } catch (IOException e) {
            record("Receive error: " + e.getMessage());
        }
    }

    private void record(String event) {
        final String line = "[" + LocalDateTime.now().format(FORMATTER) + "] " + event;
        if (SwingUtilities.isEventDispatchThread()) {
            appendToLog(line);
        } else {
            SwingUtilities.invokeLater(() -> appendToLog(line));
        }
        synchronized (logLock) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
                writer.println(line);
            } catch (IOException e) {
                System.err.println("Log error: " + e.getMessage());
            }
        }
    }

    private void appendToLog(String line) {
        logArea.append(line + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void setStatus(final String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    private void closeQuietly() {
        if (socket != null) {
            socket.close();
            socket = null;
        }
        connected = false;
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        final Client frame = new Client(host, port);
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }
}