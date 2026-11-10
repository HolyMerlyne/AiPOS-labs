import javax.swing.*;
import javax.swing.Timer; // <-- ЯВНЫЙ ИМПОРТ ДЛЯ УСТРАНЕНИЯ КОНФЛИКТА
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class UDPServer {
    public static final int PORT = 8080;
    private static final int GROUP_SIZE = 64;
    private static final int MIN_DIFFERENT = 3;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();

    // GUI
    private static JFrame frame;
    private static JTextArea logArea;
    private static JTextField portField;
    private static JButton startButton;
    private static JButton stopButton;
    private static JLabel statusLabel;

    private static DatagramSocket serverSocket;
    private static volatile boolean running = false;
    private static Thread receiverThread;
    private static Timer statusTimer;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            createGUI();
            startServer(); // Сохраняем исходное поведение — автостарт на PORT
        });
    }

    private static void createGUI() {
        frame = new JFrame("UDP Сервер");
        frame.setSize(900, 650);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Порт:"));
        portField = new JTextField(String.valueOf(PORT), 6);
        top.add(portField);
        startButton = new JButton("Запустить сервер");
        stopButton = new JButton("Остановить сервер");
        stopButton.setEnabled(false);
        top.add(startButton);
        top.add(stopButton);
        frame.add(top, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        frame.add(new JScrollPane(logArea), BorderLayout.CENTER);

        statusLabel = new JLabel("Статус: сервер остановлен");
        frame.add(statusLabel, BorderLayout.SOUTH);

        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (running) stopServer();
                if (statusTimer != null) statusTimer.stop();
                frame.dispose();
                System.exit(0);
            }
        });

        // Периодически обновляем счётчик сессий
        statusTimer = new Timer(1000, e -> updateStatus());
        statusTimer.start();

        frame.setVisible(true);
    }

    private static void startServer() {
        if (running) {
            appendLog("Сервер уже запущен.");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            appendLog("Порт должен быть числом.");
            return;
        }
        try {
            serverSocket = new DatagramSocket(port);
            running = true;
            appendLog("UDP сервер запущен на порту " + port);
            appendLog("Ожидание датаграмм...");
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            portField.setEnabled(false);
            updateStatus();

            receiverThread = new Thread(() -> receiveLoop(serverSocket));
            receiverThread.setDaemon(true);
            receiverThread.start();
        } catch (IOException ex) {
            appendLog("Не удалось запустить сервер: " + ex.getMessage());
        }
    }

    private static void receiveLoop(DatagramSocket sock) {
        byte[] buf = new byte[4096];
        while (running && !sock.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                sock.receive(packet);
            } catch (SocketException e) {
                break;
            } catch (IOException e) {
                if (running) {
                    appendLog("Ошибка приёма: " + e.getMessage());
                }
                continue;
            }

            String received = new String(packet.getData(), 0, packet.getLength());
            InetAddress clientAddr = packet.getAddress();
            int clientPort = packet.getPort();
            String key = clientAddr.getHostAddress() + ":" + clientPort;

            appendLog("[" + key + "] Получено: " + received);

            if (received.equalsIgnoreCase("END")) {
                appendLog("[" + key + "] Получена команда END. Завершение сеанса.");
                sessions.remove(key);
                updateStatus();
                continue;
            }

            ClientSession session = sessions.computeIfAbsent(key,
                    k -> new ClientSession(clientAddr, clientPort));
            session.addData(received, sock);
        }
    }

    private static void stopServer() {
        if (!running) return;
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception e) {
            appendLog("Ошибка при закрытии сервера: " + e.getMessage());
        }
        sessions.clear();
        appendLog("Сервер остановлен.");
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        portField.setEnabled(true);
        updateStatus();
    }

    private static void updateStatus() {
        SwingUtilities.invokeLater(() -> {
            if (running && serverSocket != null && !serverSocket.isClosed()) {
                statusLabel.setText("Статус: сервер запущен на порту "
                        + serverSocket.getLocalPort()
                        + " | Активных сессий: " + sessions.size());
            } else {
                statusLabel.setText("Статус: сервер остановлен");
            }
        });
    }

    private static void appendLog(String message) {
        String line = "[" + LocalDateTime.now().format(TIME_FORMAT) + "] " + message;
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private static class ClientSession {
        private final InetAddress address;
        private final int port;
        private final StringBuilder buffer = new StringBuilder();
        private boolean alive = true;

        public ClientSession(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }

        public synchronized void addData(String data, DatagramSocket socket) {
            if (!alive) return;

            buffer.append(data);

            while (buffer.length() >= GROUP_SIZE) {
                String group = buffer.substring(0, GROUP_SIZE);
                buffer.delete(0, GROUP_SIZE);

                if (!processGroup(group, socket)) {
                    alive = false;
                    return;
                }
            }
        }

        private boolean processGroup(String group, DatagramSocket socket) {
            Map<Character, Integer> freq = new LinkedHashMap<>();
            for (int i = 0; i < group.length(); i++) {
                char c = group.charAt(i);
                freq.put(c, freq.getOrDefault(c, 0) + 1);
            }

            if (freq.size() < MIN_DIFFERENT) {
                String errorMsg = "ERROR: less than 3 different symbols";
                appendLog("[" + address.getHostAddress() + ":" + port + "] " + errorMsg);
                send(socket, errorMsg);
                return false;
            }

            StringBuilder response = new StringBuilder();
            boolean first = true;
            for (Map.Entry<Character, Integer> e : freq.entrySet()) {
                if (!first) response.append(", ");
                response.append("<").append(e.getKey())
                        .append("-").append(e.getValue()).append(">");
                first = false;
            }

            appendLog("[" + address.getHostAddress() + ":" + port + "] Отправка ответа: " + response);
            send(socket, response.toString());
            return true;
        }

        private void send(DatagramSocket socket, String message) {
            try {
                byte[] data = message.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                socket.send(packet);
            } catch (IOException e) {
                appendLog("Ошибка отправки: " + e.getMessage());
            }
        }
    }
}