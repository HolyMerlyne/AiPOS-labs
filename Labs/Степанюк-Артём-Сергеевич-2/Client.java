import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Client extends JFrame {

    private static final String LOG_FILE = "protocol.txt";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = Server.PORT;

    private final String host;
    private final int port;

    private final JLabel statusLabel = new JLabel();
    private final JTextArea logArea = new JTextArea();
    private final JTextField inputField = new JTextField();

    private final Object logLock = new Object();

    private DatagramSocket socket;
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
                record("Connection closed");
                closed = true;
                closeQuietly();
                dispose();
                System.exit(0);
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    private void buildGui() {
        statusLabel.setText("Подключение к " + host + ":" + port + " ...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(false);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setPreferredSize(new Dimension(720, 360));

        inputField.setEnabled(false);

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
                    e.consume();
                    sendLine();
                }
            }
        });

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.add(new JLabel("Строка:"), BorderLayout.WEST);
        inputRow.add(inputField, BorderLayout.CENTER);

        JLabel hint = new JLabel("введённая строка отсылается серверу по нажатию клавиши PgDn");
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

    public void connect() {
        try {
            socket = new DatagramSocket();
            socket.connect(InetAddress.getByName(host), port);

            setStatus("Подключено к " + socket.getRemoteSocketAddress() + " (адрес по умолчанию)");
            record("Connection established");
            startReader();

            inputField.setEnabled(true);
            inputField.requestFocusInWindow();
        } catch (IOException e) {
            setStatus("Не удалось подключиться к " + host + ":" + port + " - " + e.getMessage());
        }
    }

    private void sendLine() {
        String text = inputField.getText();
        if (text == null || text.isEmpty() || socket == null) {
            return;
        }
        inputField.setText("");
        record("Sent to server: " + text);
        try {
            byte[] data = text.getBytes();
            socket.send(new DatagramPacket(data, data.length));
        } catch (IOException e) {
            record("Send error: " + e.getMessage());
        }
    }

    private void startReader() {
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] buffer = new byte[65507];
                    while (true) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        String str = new String(packet.getData(), 0, packet.getLength());

                        if (str.equals("\u0000")) {
                            record("Connection closed by server");
                            return;
                        }
                        
                        record("Got from server: " + str);
                    }
                } catch (IOException e) {
                    record("Connection closed: " + e.getMessage());
                } finally {
                    closeQuietly();
                    if (!closed) {
                        setStatus("Соединение закрыто сервером");
                        inputField.setEnabled(false);
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
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
  
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
                frame.connect();
            }
        });
    }
}
