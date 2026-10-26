import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class client {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 8080;

    private static PrintWriter logWriter;
    private static Socket socket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static String currentHost;
    private static int currentPort;
    private static volatile boolean isConnected = false;

    private static JFrame frame;
    private static JTextField hostField;
    private static JTextField portField;
    private static JTextArea logArea;
    private static JTextField inputField;
    private static JButton connectButton;
    private static JButton disconnectButton;
    private static JButton sendButton;
    private static JLabel statusLabel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(client::start);
    }

    private static void start() {
        createGUI();

        try {
            logWriter = new PrintWriter(new BufferedWriter(
                    new FileWriter("client_protocol.log", true)), true);
            log("=== Запуск клиента ===");
        } catch (IOException e) {
            log("Не удалось открыть файл протокола: " + e.getMessage());
        }

        log("Попытка автоматического подключения к " + DEFAULT_HOST + ":" + DEFAULT_PORT + "...");
        try {
            connect(DEFAULT_HOST, DEFAULT_PORT);
            startServerReaderThread();
        } catch (IOException e) {
            log("Автоматическое подключение не удалось: " + e.getMessage());
            log("Используйте кнопку 'Подключиться' для подключения.");
        }
    }

    private static void createGUI() {
        frame = new JFrame("Клиент");
        frame.setSize(900, 650);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Адрес:"));
        hostField = new JTextField(DEFAULT_HOST, 12);
        top.add(hostField);
        top.add(new JLabel("Порт:"));
        portField = new JTextField(String.valueOf(DEFAULT_PORT), 6);
        top.add(portField);
        connectButton = new JButton("Подключиться");
        disconnectButton = new JButton("Отключиться");
        disconnectButton.setEnabled(false);
        top.add(connectButton);
        top.add(disconnectButton);
        frame.add(top, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        frame.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JPanel inputRow = new JPanel(new BorderLayout());
        inputField = new JTextField();
        inputField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        sendButton = new JButton("Отправить");
        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendButton, BorderLayout.EAST);
        bottom.add(inputRow, BorderLayout.CENTER);
        statusLabel = new JLabel("Статус: не подключено");
        bottom.add(statusLabel, BorderLayout.SOUTH);
        frame.add(bottom, BorderLayout.SOUTH);

        connectButton.addActionListener(e -> doConnect());
        disconnectButton.addActionListener(e -> doDisconnect());
        sendButton.addActionListener(e -> doSend());
        inputField.addActionListener(e -> doSend());

        inputField.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "sendMessage");
        inputField.getActionMap().put("sendMessage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doSend();
            }
        });

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isConnected) disconnect();
                if (logWriter != null) logWriter.close();
                frame.dispose();
                System.exit(0);
            }
        });

        frame.setVisible(true);
    }

    private static void doConnect() {
        if (isConnected) {
            log("Соединение уже установлено. Сначала отключитесь.");
            return;
        }
        try {
            String host = hostField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            connect(host, port);
            startServerReaderThread();
        } catch (NumberFormatException ex) {
            log("Порт должен быть числом.");
        } catch (IOException ex) {
            log("Ошибка подключения: " + ex.getMessage());
        }
    }

    private static void doDisconnect() {
        if (!isConnected) {
            log("Нет активного соединения.");
            return;
        }
        disconnect();
    }

    private static void doSend() {
        String line = inputField.getText();
        if (line.isEmpty()) return;

        try {
            if (line.startsWith("connect ")) {
                String[] parts = line.split("\\s+");
                if (parts.length != 3) {
                    log("Использование: connect <адрес> <порт>");
                    return;
                }
                if (isConnected) {
                    log("Соединение уже установлено. Сначала disconnect.");
                    return;
                }
                int port = Integer.parseInt(parts[2]);
                connect(parts[1], port);
                hostField.setText(parts[1]);
                portField.setText(String.valueOf(port));
                startServerReaderThread();
                inputField.setText("");
                return;
            }
            if (line.startsWith("disconnect ")) {
                String[] parts = line.split("\\s+");
                if (parts.length != 3) {
                    log("Использование: disconnect <адрес> <порт>");
                    return;
                }
                if (!isConnected) {
                    log("Нет активного соединения.");
                    return;
                }
                int port = Integer.parseInt(parts[2]);
                if (!parts[1].equals(currentHost) || port != currentPort) {
                    log("Указанный адрес/порт не совпадает с текущим соединением ("
                            + currentHost + ":" + currentPort + ")");
                    return;
                }
                disconnect();
                inputField.setText("");
                return;
            }
        } catch (NumberFormatException ex) {
            log("Порт должен быть числом.");
            return;
        } catch (IOException ex) {
            log("Ошибка: " + ex.getMessage());
            disconnect();
            return;
        }

        if (!isConnected) {
            log("Нет активного соединения. Используйте: connect <адрес> <порт> или кнопку 'Подключиться'.");
            return;
        }

        out.println(line);
        log("[Отправлено]: " + line);
        log("Отправлено на сервер: " + line);
        inputField.setText("");

        if ("END".equalsIgnoreCase(line)) {
            log("Завершение сеанса по команде END...");
            disconnect();
        }
    }

    private static void connect(String host, int port) throws IOException {
        InetAddress addr = InetAddress.getByName(host);
        socket = new Socket(addr, port);
        currentHost = host;
        currentPort = port;
        isConnected = true;

        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream())), true);

        String startTime = LocalDateTime.now().format(TIME_FORMAT);
        log("Подключено к " + host + ":" + port + " в " + startTime);
        log("Начало соединения с " + host + ":" + port + " [" + startTime + "]");
        updateUIState();
    }

    private static void startServerReaderThread() {
        final BufferedReader reader = in;
        if (reader == null) return;

        Thread serverReader = new Thread(() -> {
            try {
                String serverLine;
                while ((serverLine = reader.readLine()) != null) {
                    String time = LocalDateTime.now().format(TIME_FORMAT);
                    log("[СЕРВЕР " + time + "]: " + serverLine);
                    log("Получено от сервера [" + time + "]: " + serverLine);
                }
            } catch (IOException e) {
                if (isConnected) {
                    log("Соединение с сервером разорвано.");
                    log("Соединение разорвано: " + e.getMessage());
                    disconnect();
                }
            }
        });
        serverReader.setDaemon(true);
        serverReader.start();
    }

    private static synchronized void disconnect() {
        if (!isConnected) return;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                String endTime = LocalDateTime.now().format(TIME_FORMAT);
                log("Соединение закрыто в " + endTime);
                log("Окончание соединения с " + currentHost + ":" + currentPort
                        + " [" + endTime + "]");
            }
        } catch (IOException e) {
            log("Ошибка при закрытии соединения: " + e.getMessage());
        } finally {
            socket = null;
            in = null;
            out = null;
            isConnected = false;
            updateUIState();
        }
    }

    private static void updateUIState() {
        SwingUtilities.invokeLater(() -> {
            if (isConnected) {
                statusLabel.setText("Статус: подключено к " + currentHost + ":" + currentPort);
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(true);
                hostField.setEnabled(false);
                portField.setEnabled(false);
            } else {
                statusLabel.setText("Статус: не подключено");
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
                hostField.setEnabled(true);
                portField.setEnabled(true);
            }
        });
    }

    private static void log(String message) {
        String timestamped = "[" + LocalDateTime.now().format(TIME_FORMAT) + "] " + message;
        if (logWriter != null) {
            synchronized (client.class) {
                logWriter.println(timestamped);
            }
        }
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}