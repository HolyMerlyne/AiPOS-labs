import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Client extends JFrame {
    private static final String LOG_FILE = "udp_client_log.txt";

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private boolean connected = false;

    private JTextArea chatArea;
    private JTextField inputField;
    private PrintWriter logWriter;
    private DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Client() {
        setTitle("UDP Клиент — PgUp для отправки, Enter для connect");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        inputField = new JTextField();
        inputField.setFont(new Font("Monospaced", Font.PLAIN, 14));
        inputField.setPreferredSize(new Dimension(0, 30));
        bottomPanel.add(inputField, BorderLayout.CENTER);

        JButton sendButton = new JButton("Отправить (PgUp)");
        bottomPanel.add(sendButton, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
                    sendMessage();
                }
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    processCommand();
                }
            }
        });

        sendButton.addActionListener(e -> {
            if (inputField.getText().trim().startsWith("connect ")) {
                processCommand();
            } else {
                sendMessage();
            }
        });

        setVisible(true);

        try {
            logWriter = new PrintWriter(new FileWriter(LOG_FILE, true), true);
            chatArea.append("UDP-клиент запущен. Введите 'connect <адрес> <порт>' и нажмите Enter.\n");
            chatArea.append("Для отправки текста нажмите PgUp или кнопку «Отправить».\n\n");
        } catch (IOException e) {
            chatArea.append("Ошибка открытия лог-файла: " + e.getMessage() + "\n");
        }

        inputField.requestFocusInWindow();
    }

    private void processCommand() {
        String text = inputField.getText().trim();
        inputField.setText("");

        if (text.startsWith("connect ")) {
            String[] parts = text.split("\\s+");
            if (parts.length == 3) {
                try {
                    int port = Integer.parseInt(parts[2]);
                    connectToServer(parts[1], port);
                } catch (NumberFormatException e) {
                    chatArea.append("Ошибка: порт должен быть числом.\n");
                }
            } else {
                chatArea.append("Использование: connect <адрес> <порт>\n");
            }
        } else {
            chatArea.append("Неизвестная команда. Доступно: connect <адрес> <порт>\n");
        }
        inputField.requestFocusInWindow();
    }

    private void connectToServer(String host, int port) {
        if (connected) {
            chatArea.append("Вы уже подключены.\n");
            return;
        }
        try {
            socket = new DatagramSocket();
            serverAddress = InetAddress.getByName(host);
            serverPort = port;
            connected = true;

            String startTime = dtf.format(LocalDateTime.now());
            logWriter.println("[" + startTime + "] Соединение установлено с " + host + ":" + port);
            chatArea.append("Подключено к " + host + ":" + port + "\n");

            new Thread(() -> {
                byte[] buf = new byte[2048];
                while (connected) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);
                        String response = new String(packet.getData(), 0, packet.getLength());

                        String recvTime = dtf.format(LocalDateTime.now());
                        logWriter.println("[" + recvTime + "] ПРИНЯТО: " + response.replace("\n", " | "));

                        chatArea.append("Сервер: " + response);
                        if (!response.endsWith("\n")) chatArea.append("\n");

                        if (response.contains("Session terminated") || response.contains("Goodbye")) {
                            disconnect();
                            break;
                        }
                    } catch (IOException ex) {
                        if (connected) {
                            chatArea.append("Соединение потеряно.\n");
                            disconnect();
                        }
                        break;
                    }
                }
            }).start();

        } catch (IOException e) {
            chatArea.append("Ошибка подключения: " + e.getMessage() + "\n");
        }
    }

    private void disconnect() {
        if (!connected) return;
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}

        String endTime = dtf.format(LocalDateTime.now());
        logWriter.println("[" + endTime + "] Соединение разорвано.");
        chatArea.append("--- Соединение закрыто ---\n");
    }

    private void sendMessage() {
        if (!connected) {
            chatArea.append("Нет соединения. Введите 'connect <адрес> <порт>'.\n");
            inputField.setText("");
            return;
        }
        String text = inputField.getText();
        if (text.isEmpty()) return;

        try {
            byte[] data = (text + "\n").getBytes();
            DatagramPacket packet = new DatagramPacket(
                data, data.length, serverAddress, serverPort
            );
            socket.send(packet);
            chatArea.append("Клиент: " + text + "\n");
            inputField.setText("");
        } catch (IOException e) {
            chatArea.append("Ошибка отправки: " + e.getMessage() + "\n");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client::new);
    }
}