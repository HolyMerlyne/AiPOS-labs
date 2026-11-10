import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;


public class TcpClient {

    public static final int    DEFAULT_PORT = 666;
    public static final String DEFAULT_HOST = "127.0.0.1";
    private static final String LOGS_DIR = "logs";
    private static final Logger LOG = Logger.getLogger(TcpClient.class.getName());
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public static void main(String[] args) throws IOException {
        new File(LOGS_DIR).mkdirs();
        setupLogger();
        new TcpClient().run();
    }

    private void run() throws IOException {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));

        if (!connect(DEFAULT_HOST, DEFAULT_PORT)) {
            System.out.println("Не удалось автоматически подключиться. Используйте: connect <адрес> <порт>");
        }

        System.out.println("Введите строку (Enter — отправить), 'connect <host> <port>' — подключиться, 'quit' — выход.");

        String line;
        while ((line = stdin.readLine()) != null) {
            String trimmed = line.trim();

            if (trimmed.equalsIgnoreCase("quit")) { close(); break; }

            if (trimmed.startsWith("connect ")) {
                String[] parts = trimmed.split("\\s+");
                if (parts.length != 3) { System.out.println("Использование: connect <адрес> <порт>"); continue; }
                try {
                    int port = Integer.parseInt(parts[2]);
                    close();
                    connect(parts[1], port);
                } catch (NumberFormatException e) { System.out.println("Некорректный порт."); }
                continue;
            }

            if (socket == null || socket.isClosed()) {
                System.out.println("Нет соединения. Используйте: connect <адрес> <порт>");
                continue;
            }

            out.println(trimmed);
            LOG.info("SENT => " + trimmed);

            try {
                String response = in.readLine();
                if (response == null) { System.out.println("Сервер закрыл соединение."); close(); continue; }
                System.out.println("SERVER: " + response);
                LOG.info("RECV <= " + response);
                if (response.contains("Сеанс завершён") || response.equals("Пока!")) close();
            } catch (IOException e) {
                System.out.println("Ошибка приёма: " + e.getMessage());
                close();
            }
        }
        close();
    }

    private boolean connect(String host, int port) {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 3000);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            LOG.info("CONNECTED to " + host + ":" + port + " at " + TS.format(new Date()));
            System.out.println("Соединение с " + host + ":" + port + " установлено.");

            String hello = in.readLine();
            if (hello != null) { System.out.println("SERVER: " + hello); LOG.info("RECV <= " + hello); }
            return true;
        } catch (IOException e) {
            System.out.println("Ошибка подключения: " + e.getMessage());
            LOG.warning("CONNECT FAILED to " + host + ":" + port + " — " + e.getMessage());
            close();
            return false;
        }
    }

    private void close() {
        if (socket != null && !socket.isClosed()) {
            LOG.info("DISCONNECT at " + TS.format(new Date()));
            try { socket.close(); } catch (IOException ignored) {}
        }
        socket = null;
    }

    private static void setupLogger() throws IOException {
        FileHandler fh = new FileHandler(LOGS_DIR + "/client.log", true);
        fh.setFormatter(new SimpleFormatter());
        LOG.addHandler(fh);
        LOG.setLevel(Level.INFO);
    }
}