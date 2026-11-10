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

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = Server.PORT;

    private String host;
    private int port;

    private final JLabel statusLabel = new JLabel();
    private final JTextArea logArea = new JTextArea();
    private final JTextField inputField = new JTextField();

    private final Object logLock = new Object();

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean closed;
    private volatile boolean serverClosed;

    public Client(String host, int port) {
        super("TCP-клиент");
        this.host = host;
        this.port = port;

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        buildGui();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closed = true;
                if (socket != null && !socket.isClosed()) {
                    record("Connection closed by user: " + socket.getRemoteSocketAddress());
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

        JLabel hint = new JLabel("End - отправить строку; " + "connect <адрес> <порт> - подключиться к серверу");
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

    //Обработка нажатия End
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
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new BufferedWriter( new OutputStreamWriter(socket.getOutputStream())), true);

            setStatus("Подключено к " + socket.getRemoteSocketAddress());
            record("Connection established: " + host + ":" + port);
            startReader();
        } catch (IOException e) {
            setStatus("Не удалось подключиться к " + host + ":" + port + " - " + e.getMessage());
            record("Connection failed: " + host + ":" + port +  " (" + e.getMessage() + ")");
        }
    }

    private void sendLine(String text) {
        if (out == null) {
            record("Cannot send (not connected): " + text);
            return;
        }
        record("Sent to server: " + text);
        out.println(text);
    }

    private void startReader() {
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String str;
                    while ((str = in.readLine()) != null) {
                        record("Got from server: " + str);
                    }
                    serverClosed = true;
                } catch (IOException e) {
                    serverClosed = true;
                    record("Connection error: " + e.getMessage());
                } finally {
                    closeQuietly();
                    if (!closed) {
                        setStatus("Соединение закрыто сервером");
                    }
                }
            }
        });
        reader.setDaemon(true);
        reader.start();
    }

    private void record(String event) {
        final String line = "[" + LocalDateTime.now().format(FORMATTER) + "] " + event;
        if (SwingUtilities.isEventDispatchThread()) {
            appendToLog(line);
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    appendToLog(line);
                }
            });
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
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(text);
            }
        });
    }

    private void closeQuietly() {
    Socket s = socket;
    if (s == null) {
        return;
    }
    boolean wasOpen = !s.isClosed();
    String addr = s.getRemoteSocketAddress() != null
                  ? s.getRemoteSocketAddress().toString()
                  : "unknown";
    try {
        s.close();
    } catch (IOException ignored) {
    } finally {
        socket = null;
        in = null;
        out = null;
        if (wasOpen) {
            if (serverClosed) {
                record("Connection closed by server: " + addr);
            } else if (!closed) {
                record("Connection closed: " + addr);
            }
        }
        serverClosed = false;
    }
}

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        final Client frame = new Client(host, port);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                frame.setVisible(true);
            }
        });
    }
}