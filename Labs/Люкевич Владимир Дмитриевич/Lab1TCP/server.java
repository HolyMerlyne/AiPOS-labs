import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class server {
    public static final int PORT = 8080;
    private static final int GROUP_SIZE = 64;
    private static final int MIN_DIFFERENT = 3;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static JFrame frame;
    private static JTextArea logArea;
    private static JTextField portField;
    private static JButton startButton;
    private static JButton stopButton;
    private static JLabel statusLabel;

    private static ServerSocket serverSocket;
    private static volatile boolean running = false;
    private static Thread acceptThread;

    private static final java.util.List<Socket> activeClients =
            Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            createGUI();
            startServer(); 
        });
    }

    private static void createGUI() {
        frame = new JFrame("Сервер");
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
                frame.dispose();
                System.exit(0);
            }
        });

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
            serverSocket = new ServerSocket(port);
            running = true;
            appendLog("Сервер запущен: " + serverSocket);
            appendLog("Ожидание подключений...");
            statusLabel.setText("Статус: сервер запущен на порту " + port);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            portField.setEnabled(false);

            acceptThread = new Thread(() -> {
                try {
                    while (running) {
                        Socket clientSocket = serverSocket.accept();
                        activeClients.add(clientSocket);
                        appendLog("Новое соединение: " + clientSocket);
                        try {
                            new ClientHandler(clientSocket).start();
                        } catch (IOException e) {
                            appendLog("Ошибка при создании обработчика: " + e.getMessage());
                            try { clientSocket.close(); } catch (IOException ex) { /* ignore */ }
                        }
                    }
                } catch (IOException e) {
                    if (running) {
                        appendLog("Ошибка в accept: " + e.getMessage());
                    }
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();
        } catch (IOException ex) {
            appendLog("Не удалось запустить сервер: " + ex.getMessage());
        }
    }

    private static void stopServer() {
        if (!running) return;
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            appendLog("Ошибка при закрытии сервера: " + e.getMessage());
        }
        synchronized (activeClients) {
            for (Socket s : activeClients) {
                try { s.close(); } catch (IOException e) { /* ignore */ }
            }
            activeClients.clear();
        }
        appendLog("Сервер остановлен.");
        statusLabel.setText("Статус: сервер остановлен");
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        portField.setEnabled(true);
    }

    private static void appendLog(String message) {
        String line = "[" + LocalDateTime.now().format(TIME_FORMAT) + "] " + message;
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream())), true);
        }

        @Override
        public void run() {
            StringBuilder buffer = new StringBuilder();
            String line;

            try {
                while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("END")) {
                        appendLog("[" + socket + "] Получена команда END. Завершение сеанса.");
                        break;
                    }
                    buffer.append(line);
                    while (buffer.length() >= GROUP_SIZE) {
                        String group = buffer.substring(0, GROUP_SIZE);
                        buffer.delete(0, GROUP_SIZE);
                        if (!processGroup(group)) return;
                    }
                }
                if (buffer.length() > 0) {
                    appendLog("[" + socket + "] Обработка остатка буфера ("
                            + buffer.length() + " символов).");
                    processGroup(buffer.toString());
                }
            } catch (IOException e) {
                appendLog("[" + socket + "] Соединение разорвано: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException e) { /* ignore */ }
                activeClients.remove(socket);
                appendLog("[" + socket + "] Клиент отключён.");
            }
        }

        private boolean processGroup(String group) {
            Map<Character, Integer> freq = new LinkedHashMap<>();
            for (int i = 0; i < group.length(); i++) {
                char c = group.charAt(i);
                freq.put(c, freq.getOrDefault(c, 0) + 1);
            }
            if (freq.size() < MIN_DIFFERENT) {
                String errorMsg = "ERROR: less than 3 different symbols";
                appendLog("[" + socket + "] " + errorMsg);
                out.println(errorMsg);
                try { socket.close(); } catch (IOException e) { /* ignore */ }
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
            appendLog("[" + socket + "] Отправка ответа: " + response);
            out.println(response.toString());
            return true;
        }
    }
}