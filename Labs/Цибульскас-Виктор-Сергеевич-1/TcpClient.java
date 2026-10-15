import java.awt.BorderLayout;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.Instant;
import java.time.ZoneId;
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

public class TcpClient extends JFrame {
    private static final Logger JOURNAL = Logger.getLogger("aipos.tcp.client");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private Socket socket;
    private PrintWriter outgoing;
    private boolean sessionOpen;

    private final JTextArea screen = new JTextArea();
    private final JTextField field = new JTextField();

    public TcpClient() throws IOException {
        super("TCP-клиент, вариант 11");
        openJournal();

        screen.setEditable(false);
        screen.setLineWrap(true);
        add(new JScrollPane(screen), BorderLayout.CENTER);

        JButton sendButton = new JButton("Отправить");
        sendButton.addActionListener(event -> submit());
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED || !isFocused()) {
                return false;
            }
            int code = event.getKeyCode();
            boolean pageUp = code == KeyEvent.VK_PAGE_UP;
            boolean upInField = code == KeyEvent.VK_UP && event.getComponent() == field;
            if (!pageUp && !upInField) {
                return false;
            }
            submit();
            return true;
        });

        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.add(field, BorderLayout.CENTER);
        bottom.add(sendButton, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        setSize(620, 420);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                closeLink();
            }
        });

        say("Подключение: connect <адрес> <порт>");
        say("Отправка строки: PgUp. На MacBook это Fn + стрелка вверх");
        say("Можно также нажать стрелку вверх в поле ввода или кнопку «Отправить»");
        say("Журнал сеанса пишется в файл protocol.log");
        setVisible(true);
        field.requestFocusInWindow();
    }

    private static void openJournal() throws IOException {
        FileHandler handler = new FileHandler("protocol.log", true);
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                String moment = CLOCK.format(Instant.ofEpochMilli(record.getMillis()));
                return moment + "  " + record.getMessage() + System.lineSeparator();
            }
        });
        JOURNAL.setUseParentHandlers(false);
        JOURNAL.setLevel(Level.INFO);
        JOURNAL.addHandler(handler);
    }

    private void submit() {
        String text = field.getText();
        field.setText("");
        if (text.isEmpty()) {
            say("Поле пустое: сначала введите строку");
            return;
        }
        if (text.equals("connect") || text.startsWith("connect ")) {
            connectTo(text);
            return;
        }
        synchronized (this) {
            if (outgoing == null) {
                say("Сначала выполните: connect <адрес> <порт>");
                return;
            }
            outgoing.println(text);
        }
        say(">>> " + text);
    }

    private void connectTo(String command) {
        String tail = command.substring("connect".length()).trim().replace(':', ' ');
        String[] parts = tail.isEmpty() ? new String[0] : tail.split("\\s+");
        if (parts.length != 2) {
            say("Формат команды: connect <адрес> <порт>");
            return;
        }
        final String host = parts[0];
        final int port;
        try {
            port = Integer.parseInt(parts[1]);
            if (port < 1 || port > 65535) {
                say("Порт должен быть в диапазоне 1..65535");
                return;
            }
        } catch (NumberFormatException error) {
            say("Порт должен быть числом");
            return;
        }

        synchronized (this) {
            if (outgoing != null) {
                say("Соединение уже установлено");
                return;
            }
            try {
                socket = new Socket(host, port);
                outgoing = new PrintWriter(
                        new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),
                        true);
                sessionOpen = true;
                JOURNAL.info("CONNECTION START " + host + ":" + port);
                say("Подключен к " + host + ":" + port);
                Thread reader = new Thread(this::readReplies, "tcp-reader");
                reader.setDaemon(true);
                reader.start();
            } catch (IOException error) {
                say("Подключение не удалось: " + error.getMessage());
                silentClose();
            }
        }
    }

    private void readReplies() {
        Socket current;
        synchronized (this) {
            current = socket;
        }
        if (current == null) {
            return;
        }
        try {
            BufferedReader incoming = new BufferedReader(
                    new InputStreamReader(current.getInputStream()));
            String line;
            while ((line = incoming.readLine()) != null) {
                final String received = line;
                JOURNAL.info("RECV " + received);
                SwingUtilities.invokeLater(() -> say("<<< " + received));
            }
            SwingUtilities.invokeLater(() -> say("Соединение закрыто"));
        } catch (IOException error) {
            SwingUtilities.invokeLater(() -> say("Соединение закрыто"));
        }
        closeLink();
    }

    private synchronized void closeLink() {
        if (!sessionOpen) {
            return;
        }
        sessionOpen = false;
        JOURNAL.info("CONNECTION END");
        silentClose();
    }

    private void silentClose() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        socket = null;
        outgoing = null;
    }

    private void say(String message) {
        screen.append(message + "\n");
        screen.setCaretPosition(screen.getDocument().getLength());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new TcpClient();
            } catch (IOException error) {
                error.printStackTrace();
            }
        });
    }
}
