import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class UDPClientGUI extends JFrame {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 666;
    private static final String LOG_FILE = "client_log.txt";

    private DatagramSocket socket;
    private InetAddress address;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    private volatile boolean isDisconnected = false;

    private JTextArea outputArea;
    private JTextArea inputArea;
    private JLabel statusLabel;

    public UDPClientGUI() {
        setTitle("UDP Client (Variant 1: Home Key to Send)");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });

        statusLabel = new JLabel("Статус: Подключение...", JLabel.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setBackground(new Color(240, 240, 240));
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder("Ответы сервера и логи"));

        inputArea = new JTextArea(5, 40);
        inputArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createTitledBorder("Введите текст и нажмите HOME для отправки"));

        JButton disconnectBtn = new JButton("Disconnect");
        disconnectBtn.addActionListener(e -> disconnect());

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.add(inputScroll, BorderLayout.CENTER);
        bottomPanel.add(disconnectBtn, BorderLayout.EAST);

        setLayout(new BorderLayout(10, 10));
        add(statusLabel, BorderLayout.NORTH);
        add(outputScroll, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        InputMap inputMap = inputArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = inputArea.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), "sendAction");
        actionMap.put("sendAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendData();
            }
        });

        connectToServer();
    }

    private void connectToServer() {
        logEvent("Connection started to " + DEFAULT_HOST + ":" + DEFAULT_PORT);
        try {
            socket = new DatagramSocket();
            address = InetAddress.getByName(DEFAULT_HOST);
            statusLabel.setText("Статус: Подключено к " + DEFAULT_HOST + ":" + DEFAULT_PORT);
            statusLabel.setForeground(Color.GREEN);
            logEvent("Connection established successfully.");
            startReceiverThread();
        } catch (Exception e) {
            statusLabel.setText("Статус: Ошибка подключения!");
            statusLabel.setForeground(Color.RED);
            logEvent("Connection failed: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Не удалось подключиться к серверу:\n" + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startReceiverThread() {
        Thread receiverThread = new Thread(() -> {
            byte[] buffer = new byte[2048];
            while (socket != null && !socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    String response = new String(packet.getData(), 0, packet.getLength());
                    logEvent("Received from server: \"" + response + "\"");
                    
                    SwingUtilities.invokeLater(() -> {
                        outputArea.append("Server: " + response + "\n");
                        outputArea.setCaretPosition(outputArea.getDocument().getLength());
                        
                        if (response.toLowerCase().contains("disconnecting")) {
                            isDisconnected = true;
                            statusLabel.setText("Статус: Сервер разорвал соединение");
                            statusLabel.setForeground(Color.RED);
                            inputArea.setEditable(false);
                            
                            if (socket != null && !socket.isClosed()) {
                                socket.close();
                            }
                            
                            JOptionPane.showMessageDialog(UDPClientGUI.this, 
                                "Сервер разорвал соединение (менее 3 уникальных символов в группе).", 
                                "Внимание", JOptionPane.WARNING_MESSAGE);
                        }
                    });
                } catch (SocketException e) {
                    break; 
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    private void sendData() {
        if (isDisconnected) {
            return; 
        }

        String text = inputArea.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        if (text.trim().equalsIgnoreCase("disconnect")) {
            logEvent("Command: disconnect");
            try {
                byte[] discBytes = "disconnect".getBytes();
                socket.send(new DatagramPacket(discBytes, discBytes.length, address, DEFAULT_PORT));
            } catch (IOException e) {
                e.printStackTrace();
            }
            disconnect();
            return;
        }

        try {
            byte[] msgBytes = text.getBytes();
            DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length, address, DEFAULT_PORT);
            socket.send(packet);
            
            logEvent("Sent to server: \"" + text + "\"");
            outputArea.append("Me (Home): " + text + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
            inputArea.setText(""); 
            
        } catch (IOException e) {
            logEvent("Send error: " + e.getMessage());
        }
    }

    private void disconnect() {
        logEvent("Connection ended.");
        statusLabel.setText("Статус: Отключено");
        statusLabel.setForeground(Color.GRAY);
        isDisconnected = true;
        
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        dispose();
        System.exit(0);
    }

    private void logEvent(String message) {
        String timestampedMessage = "[" + sdf.format(new Date()) + "] " + message;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            writer.write(timestampedMessage);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Failed to write to log file.");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UDPClientGUI client = new UDPClientGUI();
            client.setVisible(true);
        });
    }
}