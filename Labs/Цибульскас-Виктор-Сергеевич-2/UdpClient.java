import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;


public class UdpClient extends JFrame {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 666;
    public static final String LOG_FILE = "client_protocol.log";

    private static final Logger JOURNAL = Logger.getLogger("aipos.udp.client");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort = -1;
    private boolean sessionOpen = false;
    private volatile boolean running = true;
    private Thread receiverThread;

    // Графические компоненты интерфейса
    private final JTextArea screen = new JTextArea();
    private final JTextField field = new JTextField();
    private final JButton sendButton = new JButton("Отправить (End)");

    public UdpClient() throws IOException {
        super("UDP-клиент");

        // Настройка гарантированной кодировки UTF-8 и шрифтов интерфейса
        configureEncodingAndFonts();
        openJournal();

        screen.setEditable(false);
        screen.setLineWrap(true);
        screen.setWrapStyleWord(true);
        screen.setBackground(new Color(248, 249, 252));
        add(new JScrollPane(screen), BorderLayout.CENTER);

        // Отправка по нажатию кнопки в окне
        sendButton.addActionListener(event -> submit());

        // Перехват глобального нажатия клавиши End в фокусе окна
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED || !isFocused()) {
                return false;
            }
            int code = event.getKeyCode();
            boolean endKey = (code == KeyEvent.VK_END);
            // На клавиатурах ноутбуков Mac клавиша End эмулируется через Fn + стрелка вправо
            boolean macEnd = (code == KeyEvent.VK_RIGHT && (event.isMetaDown() || event.isControlDown()));
            if (!endKey && !macEnd) {
                return false;
            }
            submit();
            return true;
        });

        // Отправка также срабатывает при нажатии Enter в поле ввода
        field.addActionListener(event -> submit());

        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.add(field, BorderLayout.CENTER);
        bottom.add(sendButton, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        setSize(720, 480);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeLink();
            }
        });

        // Информационные приветственные сообщения
        say("===============================================================");
        say("  UDP-Клиент");
        say("===============================================================");
        say("1) Подключение: connect <адрес> <порт> (например: connect 127.0.0.1 666 или 6666)");
        say("2) Отправка строки: клавиша [End] на клавиатуре (на Mac: Fn + стрелка вправо)");
        say("   Также строку можно отправить клавишей Enter или кнопкой «Отправить (End)»");
        say("3) Разрыв соединения: disconnect <адрес> <порт>");
        say("4) Протокол событий пишется в UTF-8 файл: " + new File(LOG_FILE).getAbsolutePath());
        say("===============================================================\n");

        setVisible(true);
        field.requestFocusInWindow();
    }

    /**
     * Гарантирует корректное отображение кириллицы в GUI Swing
     */
    private void configureEncodingAndFonts() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        Font sansFont = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        Font monoFont = new Font(Font.MONOSPACED, Font.PLAIN, 13);

        screen.setFont(monoFont);
        field.setFont(sansFont);
        sendButton.setFont(sansFont);
    }

    private static void openJournal() throws IOException {
        FileHandler handler = new FileHandler(LOG_FILE, true);
        handler.setEncoding(StandardCharsets.UTF_8.name());
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                String moment = LocalDateTime.now().format(CLOCK);
                return "[" + moment + "] " + record.getMessage() + System.lineSeparator();
            }
        });
        JOURNAL.setUseParentHandlers(false);
        JOURNAL.setLevel(Level.INFO);
        JOURNAL.addHandler(handler);
    }

    /**
     * Обработка отправки введенного в поле текста.
     * Строка данных отправляется ровно в том виде, в каком введена:
     * без trim() и без удаления каких-либо суффиксов.
     */
    private void submit() {
        String text = field.getText();
        field.setText("");

        if (text.isEmpty()) {
            say("Поле ввода пустое: введите строку для отправки.");
            return;
        }

        // Служебные команды распознаются без учета ведущих/замыкающих пробелов.
        String command = text.trim();
        if (isCommand(command, "connect")) {
            connectTo(command);
            return;
        }
        if (isCommand(command, "disconnect")) {
            disconnectFrom(command);
            return;
        }

        sendText(text);
    }

    /**
     * Отправка строки данных без искажения пробелов и символов.
     */
    private synchronized void sendText(String text) {
        if (!sessionOpen || socket == null || serverAddress == null) {
            say("Ошибка: Сначала выполните подключение: connect <адрес> <порт>");
            return;
        }

        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(bytes, bytes.length, serverAddress, serverPort));

            // Требование 2: передаваемая серверу строка и время передачи строки
            JOURNAL.info("[SEND] -> Сервер " + serverAddress.getHostAddress() + ":" + serverPort +
                    " | Передаваемая строка: \"" + text + "\"");

            say(">>> " + text);
        } catch (IOException error) {
            say("Ошибка отправки датаграммы: " + error.getMessage());
        }
    }

    /**
     * Подключение к серверу (команда connect <адрес> <порт>)
     */
    private void connectTo(String command) {
        Target target;
        try {
            target = parseTarget(command.substring("connect".length()).trim(), DEFAULT_HOST, DEFAULT_PORT);
        } catch (IllegalArgumentException error) {
            say("Ошибка: " + error.getMessage());
            return;
        }

        synchronized (this) {
            if (sessionOpen) {
                say("Соединение уже активно с " + serverAddress.getHostAddress() + ":" + serverPort);
                return;
            }

            DatagramSocket newSocket = null;
            try {
                InetAddress address = InetAddress.getByName(target.host);

                silentClose();
                newSocket = new DatagramSocket();
                newSocket.setSoTimeout(1000);

                serverAddress = address;
                serverPort = target.port;
                socket = newSocket;
                sessionOpen = true;
                running = true;

                say("Подключен к серверу " + target.host + ":" + target.port +
                        " (локальный порт: " + newSocket.getLocalPort() + ")");

                final DatagramSocket activeSocket = newSocket;
                receiverThread = new Thread(() -> readReplies(activeSocket), "udp-receiver");
                receiverThread.setDaemon(true);
                receiverThread.start();

            } catch (IOException error) {
                sessionOpen = false;
                running = false;
                if (newSocket != null && !newSocket.isClosed()) {
                    newSocket.close();
                }
                socket = null;
                say("Подключение не удалось: " + error.getMessage());
            }
        }
    }

    /**
     * Разрыв соединения (команда disconnect <адрес> <порт>).
     * Если адрес и порт не указаны, используется текущее соединение.
     */
    private synchronized void disconnectFrom(String command) {
        if (!sessionOpen || socket == null) {
            say("Нет активного соединения.");
            return;
        }

        String defaultHost = (serverAddress != null) ? serverAddress.getHostAddress() : DEFAULT_HOST;
        int defaultPort = (serverPort > 0) ? serverPort : DEFAULT_PORT;

        Target target;
        try {
            target = parseTarget(command.substring("disconnect".length()).trim(), defaultHost, defaultPort);
        } catch (IllegalArgumentException error) {
            say("Ошибка: " + error.getMessage());
            return;
        }

        InetAddress targetAddress;
        try {
            targetAddress = InetAddress.getByName(target.host);
        } catch (IOException error) {
            say("Не удалось разрешить адрес " + target.host + ": " + error.getMessage());
            return;
        }

        try {
            String message = "disconnect " + target.host + " " + target.port;
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(bytes, bytes.length, targetAddress, target.port));

            // Команда разрыва — тоже передаваемая строка (требование 2)
            JOURNAL.info("[SEND] -> Сервер " + target.host + ":" + target.port +
                    " | Передаваемая строка: \"" + message + "\"");
            say(">>> " + message);

            // Даём приемнику время получить подтверждение и записать его в протокол
            Thread.sleep(200);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (IOException error) {
            say("Ошибка отправки команды disconnect: " + error.getMessage());
        } finally {
            closeLink();
            say("Соединение с сервером разорвано.");
        }
    }

    /**
     * Поток асинхронного приема входящих датаграмм от сервера
     */
    private void readReplies(DatagramSocket activeSocket) {
        byte[] buffer = new byte[2048];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                activeSocket.receive(packet);

                String received = new String(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getLength(),
                        StandardCharsets.UTF_8
                );

                String sender = packet.getAddress().getHostAddress() + ":" + packet.getPort();

                // Требование 3: принимаемая от сервера строка и время приема строки
                JOURNAL.info("[RECV] <- Сервер " + sender + " | Принятая строка: \"" + received + "\"");

                SwingUtilities.invokeLater(() -> say("<<< " + received));

            } catch (SocketTimeoutException ignored) {
                // Таймаут приема — нормальный цикл проверки флага сеанса
            } catch (IOException error) {
                if (!activeSocket.isClosed() && running) {
                    SwingUtilities.invokeLater(() -> say("Ошибка приема данных: " + error.getMessage()));
                }
                break;
            }
        }
    }

    private synchronized void closeLink() {
        if (!sessionOpen) {
            return;
        }
        sessionOpen = false;
        running = false;
        silentClose();
    }

    private void silentClose() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        socket = null;
    }

    private void say(String message) {
        screen.append(message + "\n");
        screen.setCaretPosition(screen.getDocument().getLength());
    }

    /**
     * Проверяет, является ли строка служебной командой с параметрами.
     */
    private static boolean isCommand(String text, String name) {
        if (text.equalsIgnoreCase(name)) {
            return true;
        }
        return text.length() > name.length()
                && text.regionMatches(true, 0, name, 0, name.length())
                && Character.isWhitespace(text.charAt(name.length()));
    }

    /**
     * Разбирает хвост команды connect/disconnect: <адрес> <порт>.
     * Допускается также запись <адрес>:<порт>.
     */
    private static Target parseTarget(String tail, String defaultHost, int defaultPort) {
        String normalized = tail.replace(':', ' ');
        String[] parts = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");

        String host = (parts.length >= 1 && !parts[0].isEmpty()) ? parts[0] : defaultHost;
        int port = defaultPort;
        if (parts.length >= 2) {
            port = parsePort(parts[1]);
        }
        return new Target(host, port);
    }

    private static int parsePort(String value) {
        int port;
        try {
            port = Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("номер порта должен быть числом: " + value);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("порт должен быть в диапазоне 1..65535: " + value);
        }
        return port;
    }

    private static final class Target {
        private final String host;
        private final int port;

        private Target(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    /**
     * Точка входа в программу.
     * По умолчанию СРАЗУ открывается графический интерфейс (GUI)!
     */
    public static void main(String[] args) {
        // Если явно передан аргумент --console, запускаем консольный интерфейс
        if (args.length > 0 && (args[0].equalsIgnoreCase("--console") || args[0].equalsIgnoreCase("-c"))) {
            runConsoleMode();
            return;
        }

        // По умолчанию СРАЗУ запускаем и открываем окно GUI Swing
        SwingUtilities.invokeLater(() -> {
            try {
                new UdpClient();
            } catch (IOException error) {
                System.err.println("Ошибка запуска GUI клиента: " + error.getMessage());
                error.printStackTrace();
            }
        });
    }

    /**
     * Консольный режим (на случай запуска в headless среде без GUI).
     * Поддерживает те же команды connect/disconnect, что и GUI.
     */
    private static void runConsoleMode() {
        DatagramSocket sock = null;
        try {
            openJournal();
            System.out.println("UDP-Клиент запущен в консольном режиме. Введите 'connect 127.0.0.1 666'");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

            sock = new DatagramSocket();
            sock.setSoTimeout(1000);

            InetAddress serverAddress = InetAddress.getByName(DEFAULT_HOST);
            int serverPort = DEFAULT_PORT;
            boolean connected = false;

            while (true) {
                System.out.print("> ");
                String raw = reader.readLine();
                if (raw == null) break;
                if (raw.isEmpty()) continue;

                String command = raw.trim();
                if (command.equalsIgnoreCase("exit")) break;

                if (isCommand(command, "connect")) {
                    Target target;
                    try {
                        target = parseTarget(command.substring("connect".length()).trim(),
                                DEFAULT_HOST, DEFAULT_PORT);
                    } catch (IllegalArgumentException error) {
                        System.out.println("[КЛИЕНТ] Ошибка: " + error.getMessage());
                        continue;
                    }

                    try {
                        serverAddress = InetAddress.getByName(target.host);
                        serverPort = target.port;
                        connected = true;
                        System.out.println("[КЛИЕНТ] Подключено к " + target.host + ":" + target.port);
                    } catch (IOException error) {
                        System.out.println("[КЛИЕНТ] Не удалось разрешить адрес " + target.host +
                                ": " + error.getMessage());
                    }
                    continue;
                }

                if (!connected) {
                    System.out.println("[КЛИЕНТ] Введите connect 127.0.0.1 666");
                    continue;
                }

                if (isCommand(command, "disconnect")) {
                    Target target;
                    try {
                        target = parseTarget(command.substring("disconnect".length()).trim(),
                                serverAddress.getHostAddress(), serverPort);
                    } catch (IllegalArgumentException error) {
                        System.out.println("[КЛИЕНТ] Ошибка: " + error.getMessage());
                        continue;
                    }

                    try {
                        InetAddress targetAddress = InetAddress.getByName(target.host);
                        String message = "disconnect " + target.host + " " + target.port;
                        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
                        sock.send(new DatagramPacket(bytes, bytes.length, targetAddress, target.port));

                        JOURNAL.info("[SEND] -> Сервер " + target.host + ":" + target.port +
                                " | Передаваемая строка: \"" + message + "\"");
                        System.out.println(">>> " + message);
                        receiveConsoleReply(sock);
                    } catch (IOException error) {
                        System.out.println("[КЛИЕНТ] Ошибка отправки disconnect: " + error.getMessage());
                    }
                    break;
                }

                try {
                    // Данные отправляются без trim(), чтобы не искажать поток символов
                    byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
                    sock.send(new DatagramPacket(bytes, bytes.length, serverAddress, serverPort));

                    JOURNAL.info("[SEND] -> Сервер " + serverAddress.getHostAddress() + ":" + serverPort +
                            " | Передаваемая строка: \"" + raw + "\"");
                    System.out.println(">>> " + raw);
                    receiveConsoleReply(sock);
                } catch (IOException error) {
                    System.out.println("[КЛИЕНТ] Ошибка отправки: " + error.getMessage());
                }
            }
        } catch (Exception error) {
            error.printStackTrace();
        } finally {
            if (sock != null && !sock.isClosed()) {
                sock.close();
            }
        }
    }

    private static void receiveConsoleReply(DatagramSocket sock) {
        byte[] buffer = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            sock.receive(packet);
            String received = new String(
                    packet.getData(),
                    packet.getOffset(),
                    packet.getLength(),
                    StandardCharsets.UTF_8
            );
            System.out.println("<<< " + received);
            JOURNAL.info("[RECV] <- Сервер " + packet.getAddress().getHostAddress() + ":" + packet.getPort() +
                    " | Принятая строка: \"" + received + "\"");
        } catch (SocketTimeoutException ignored) {
            System.out.println("(нет немедленного ответа)");
        } catch (IOException error) {
            System.out.println("[КЛИЕНТ] Ошибка приема: " + error.getMessage());
        }
    }
}
