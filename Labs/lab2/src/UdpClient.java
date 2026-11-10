import java.io.*;
import java.net.*;
import java.util.logging.*;


public class UdpClient {

    public static final int    DEFAULT_PORT = 666;
    public static final String DEFAULT_HOST = "127.0.0.1";
    private static final String LOGS_DIR = "logs";
    private static final int TIMEOUT_MS = 3000;
    private static final Logger LOG = Logger.getLogger(UdpClient.class.getName());

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;

    public static void main(String[] args) throws IOException {
        new File(LOGS_DIR).mkdirs();
        setupLogger();
        new UdpClient().run();
    }

    private void run() throws IOException {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));

        if (!connect(DEFAULT_HOST, DEFAULT_PORT))
            System.out.println("Не удалось настроить UDP. Используйте: connect <адрес> <порт>");

        System.out.println("Введите строку и Enter. 'connect <host> <port>' — сменить сервер, 'quit' — выход.");

        String line;
        while ((line = stdin.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("quit")) break;

            if (trimmed.startsWith("connect ")) {
                String[] parts = trimmed.split("\\s+");
                if (parts.length == 3) {
                    try {
                        close();
                        connect(parts[1], Integer.parseInt(parts[2]));
                    } catch (NumberFormatException e) { System.out.println("Некорректный порт."); }
                } else System.out.println("Использование: connect <адрес> <порт>");
                continue;
            }

            if (socket == null) { System.out.println("Сокет не инициализирован."); continue; }

            byte[] data = trimmed.getBytes("UTF-8");
            socket.send(new DatagramPacket(data, data.length, serverAddress, serverPort));
            LOG.info("SENT => " + trimmed);

            byte[] buf = new byte[4096];
            DatagramPacket inPacket = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(inPacket);
                String response = new String(inPacket.getData(), 0, inPacket.getLength(), "UTF-8");
                System.out.println("SERVER: " + response);
                LOG.info("RECV <= " + response);
            } catch (SocketTimeoutException e) {
                System.out.println("Таймаут ожидания ответа.");
                LOG.warning("Timeout");
            }
        }
        close();
    }

    private boolean connect(String host, int port) {
        try {
            if (socket == null) {
                socket = new DatagramSocket();
                socket.setSoTimeout(TIMEOUT_MS);
            }
            serverAddress = InetAddress.getByName(host);
            serverPort = port;
            System.out.println("UDP настроен на " + host + ":" + port);
            LOG.info("CONNECTED (UDP) to " + host + ":" + port);
            return true;
        } catch (IOException e) {
            System.out.println("Ошибка: " + e.getMessage());
            return false;
        }
    }

    private void close() {
        if (socket != null) { socket.close(); socket = null; LOG.info("DISCONNECT (UDP)"); }
    }

    private static void setupLogger() throws IOException {
        FileHandler fh = new FileHandler(LOGS_DIR + "/udp-client.log", true);
        fh.setFormatter(new SimpleFormatter());
        LOG.addHandler(fh);
        LOG.setLevel(Level.INFO);
    }
}