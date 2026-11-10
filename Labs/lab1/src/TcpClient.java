import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;


public class TcpClient {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int GROUP_SIZE = 64;   // размер группы по заданию

    private static PrintWriter logFile;

    public static void main(String[] args) throws IOException {
        Scanner console = new Scanner(System.in);
        Socket socket = null;
        BufferedReader in = null;
        PrintWriter out = null;

        new File("logs").mkdirs();
        logFile = new PrintWriter(new FileWriter("logs/client.log", true), true);
        log("=== Запуск клиента ===");

        System.out.println("TCP Client (вариант 6)");
        System.out.println("Введите: connect <адрес> <порт>");
        System.out.println("Каждая строка добивается пробелами до 64 символов.");
        System.out.println("Для выхода введите: exit");

        while (true) {
            System.out.print("> ");
            if (!console.hasNextLine()) break;
            String line = console.nextLine();

            if (line.equals("exit")) {
                log("=== Завершение клиента ===");
                break;
            }

            // Команда connect
            if (line.startsWith("connect ")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length != 3) {
                    System.out.println("Использование: connect <адрес> <порт>");
                    continue;
                }
                String host = parts[1];
                int port;
                try {
                    port = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    System.out.println("Порт должен быть числом.");
                    continue;
                }

                try {
                    socket = new Socket(host, port);
                    in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    out = new PrintWriter(
                            new BufferedWriter(
                                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8")), true);

                    String startTime = LocalDateTime.now().format(FMT);
                    System.out.println("Соединение установлено с " + host + ":" + port);
                    System.out.println("Время начала соединения: " + startTime);
                    log("Начало соединения: " + host + ":" + port + " в " + startTime);

                    // Приветствие сервера
                    String greeting = in.readLine();
                    if (greeting != null) {
                        System.out.println("Сервер: " + greeting);
                        log("Принято: " + greeting + " в " + LocalDateTime.now().format(FMT));
                    }
                } catch (IOException e) {
                    System.out.println("Не удалось подключиться: " + e.getMessage());
                    log("Ошибка подключения: " + e.getMessage());
                }
                continue;
            }

            // Отправка строки
            if (socket == null || socket.isClosed()) {
                System.out.println("Сначала подключитесь командой connect <адрес> <порт>");
                continue;
            }

            if (line.isEmpty()) continue;

            try {
                // Добиваем строку пробелами до 64 символов
                String toSend = padToGroupSize(line, GROUP_SIZE);

                System.out.println("Отправлено (" + toSend.length() + " симв.): " + line);
                log("ОТПРАВЛЕНО (" + line.length() + " симв.): " + line);

                out.println(toSend);

                // Читаем ответ сервера
                String response = in.readLine();
                if (response != null) {
                    String time = LocalDateTime.now().format(FMT);
                    System.out.println("Сервер: " + response);
                    log("Принято: " + response + " в " + time);

                    if (response.startsWith("ERROR")) {
                        System.out.println("Сервер разорвал соединение.");
                        log("Разрыв соединения по инициативе сервера в " + time);
                        socket.close();
                        socket = null;
                    }
                } else {
                    System.out.println("Сервер закрыл соединение.");
                    log("Сервер закрыл соединение в " + LocalDateTime.now().format(FMT));
                    socket.close();
                    socket = null;
                }
            } catch (IOException e) {
                System.out.println("Ошибка при обмене: " + e.getMessage());
                log("Ошибка обмена: " + e.getMessage());
                try { socket.close(); } catch (IOException ignored) {}
                socket = null;
            }
        }

        if (socket != null && !socket.isClosed()) {
            log("Конец соединения в " + LocalDateTime.now().format(FMT));
            socket.close();
        }
        logFile.close();
        console.close();
    }

    /**
     * Добивает строку пробелами до нужного размера.
     * Если строка длиннее — обрезает до GROUP_SIZE.
     */
    private static String padToGroupSize(String s, int size) {
        if (s.length() >= size) {
            return s.substring(0, size);
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < size) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static void log(String msg) {
        String time = LocalDateTime.now().format(FMT);
        logFile.println("[" + time + "] " + msg);
    }
}