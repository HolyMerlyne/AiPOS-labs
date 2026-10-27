import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class UdpServer {

    public static final int DEFAULT_PORT = 666;
    public static final int BUFFER_SIZE = 2048;
    public static final int CHUNK_SIZE = ChecksumUtil.CHUNK_SIZE;
    private static final String ASCII_ERROR =
            "ERROR: допускаются только ASCII-символы (коды 0..127). Датаграмма не обработана.";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final DatagramSocket socket;
    private volatile boolean running = true;

    // Хранилище буферов входящего потока для каждого клиента (IP + порт)
    private final Map<SocketAddress, StringBuilder> clientBuffers = new ConcurrentHashMap<>();

    public UdpServer(int port) throws IOException {
        this.socket = new DatagramSocket(port);
        log("UDP-сервер успешно запущен на порту " + port);
        log("Ожидание входящих датаграмм (проверка на 127.0.0.1:" + port + ")...");
    }

    public void start() {
        byte[] receiveBuffer = new byte[BUFFER_SIZE];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(packet);

                // Декодируем принятые байты строго в кодировке UTF-8
                String receivedText = new String(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getLength(),
                        StandardCharsets.UTF_8
                );

                SocketAddress clientAddress = packet.getSocketAddress();
                InetAddress clientIp = packet.getAddress();
                int clientPort = packet.getPort();

                log(String.format("Принята датаграмма от [%s]: \"%s\" (длина: %d байт)",
                        clientAddress, receivedText, packet.getLength()));

                // По условию задания поток должен состоять только из ASCII-символов.
                // Не-ASCII датаграмма не попадает в буфер и не участвует в подсчете.
                if (!ChecksumUtil.isAscii(receivedText)) {
                    log(String.format("Отклонена датаграмма от [%s]: обнаружены не-ASCII символы", clientAddress));
                    sendDatagram(ASCII_ERROR, clientIp, clientPort);
                    continue;
                }

                String trimmed = receivedText.trim();

                // 1. Обработка команды разрыва соединения: disconnect <адрес> <порт>
                if (trimmed.equalsIgnoreCase("disconnect") ||
                    trimmed.toLowerCase().startsWith("disconnect ")) {
                    handleDisconnect(clientAddress, clientIp, clientPort, trimmed);
                    continue;
                }

                // 2. Обработка символьного потока по Варианту 3
                processClientStream(clientAddress, clientIp, clientPort, receivedText);

            } catch (IOException error) {
                if (!running) {
                    log("Сервер остановлен.");
                    break;
                }
                log("Ошибка ввода-вывода сокета: " + error.getMessage());
            }
        }
    }

    /**
     * Накапливание потока символов и отправка контрольной суммы для каждых 10 символов
     */
    private void processClientStream(SocketAddress clientKey, InetAddress clientIp, int clientPort, String text)
            throws IOException {

        StringBuilder buffer = clientBuffers.computeIfAbsent(clientKey, k -> new StringBuilder());
        buffer.append(text);

        log(String.format("Буфер клиента [%s]: длина %d симв., содержимое: \"%s\"",
                clientKey, buffer.length(), buffer.toString()));

        // Пока в буфере накопилось 10 или более символов — обрабатываем каждую 10-символьную цепочку
        while (buffer.length() >= CHUNK_SIZE) {
            String chunk = buffer.substring(0, CHUNK_SIZE);
            buffer.delete(0, CHUNK_SIZE);

            int checksum = ChecksumUtil.calculateAsciiChecksum(chunk);

            String response = String.format("CHECKSUM: %d | Chunk: \"%s\" | Formula: %s",
                    checksum, chunk, ChecksumUtil.formatCalculationDetails(chunk));

            log(String.format("Отправка ответа клиенту [%s]: %s", clientKey, response));
            sendDatagram(response, clientIp, clientPort);
        }

        // Если в буфере остался хвост менее 10 символов — он ожидает следующей порции данных
        if (buffer.length() > 0) {
            log(String.format("В буфере клиента [%s] ожидает дозаполнения: %d симв. (\"%s\")",
                    clientKey, buffer.length(), buffer.toString()));
        }
    }

    /**
     * Обработка команды disconnect <адрес> <порт>
     */
    private void handleDisconnect(SocketAddress clientAddress, InetAddress clientIp, int clientPort, String cmd)
            throws IOException {
        log(String.format("Получена команда разрыва соединения от [%s]: \"%s\"", clientAddress, cmd));
        StringBuilder remaining = clientBuffers.remove(clientAddress);
        String info = (remaining != null && remaining.length() > 0)
                ? " (необработанный остаток буфера сброшен: \"" + remaining + "\")"
                : "";

        String ackMessage = "DISCONNECTED: Сессия успешно закрыта по запросу" + info;
        sendDatagram(ackMessage, clientIp, clientPort);
        log(String.format("Сессия клиента [%s] закрыта.", clientAddress));
    }

    /**
     * Отправка датаграммы клиенту в UTF-8
     */
    private void sendDatagram(String message, InetAddress targetIp, int targetPort) throws IOException {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket responsePacket = new DatagramPacket(data, data.length, targetIp, targetPort);
        socket.send(responsePacket);
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        log("Сервер завершил работу.");
    }

    private static void log(String message) {
        String timestamp = LocalDateTime.now().format(CLOCK);
        System.out.println("[" + timestamp + "] [UDP-SERVER] " + message);
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException error) {
                System.err.println("Некорректный порт: " + args[0] + ". Используется порт по умолчанию: " + DEFAULT_PORT);
            }
        }

        try {
            final UdpServer server = new UdpServer(port);
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            server.start();
        } catch (IOException error) {
            System.err.println("Ошибка запуска сервера: " + error.getMessage());
            if (port < 1024) {
                System.err.println("Подсказка: на macOS/Linux порты < 1024 требуют прав root (sudo) либо укажите непривилегированный порт, например 6666: java UdpServer 6666");
            }
        }
    }
}
