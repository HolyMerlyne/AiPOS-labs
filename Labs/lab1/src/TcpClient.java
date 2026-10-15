import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

/**
 * TCP-клиент, вариант 6.
 * - Отправляет строки серверу.
 * - Ведёт лог-файл: время начала/конца соединения, время приёма строк.
 * - Команда подключения: connect <адрес> <порт>.
 */
public class TcpClient {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static PrintWriter logFile;

    public static void main(String[] args) throws IOException {
        Scanner console = new Scanner(System.in);
        Socket socket = null;
        BufferedReader in = null;
        PrintWriter out = null;

        // Лог-файл в папке logs (создаётся, если нет)
        new File("logs").mkdirs();
        logFile = new PrintWriter(new FileWriter("logs/client.log", true), true);
        log("=== Запуск клиента ===");

        System.out.println("TCP Client (вариант 6)");
        System.out.println("Введите: connect <адрес> <порт>");
        System.out.println("Для отправки строки серверу введите её и нажмите Enter.");
        System.out.println("Для выхода введите: exit");

        while (true) {
            System.out.print("> ");
            if (!console.hasNextLine()) break;
            String line = console.nextLine().trim();

            if (line.isEmpty()) continue;

            if (line.equals("exit")) {
                log("=== Завершение клиента ===");
                break;
            }

            // Команда connect
            if (line.startsWith("connect ")) {
                String[] parts = line.split("\\s+");
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

            try {
                out.println(line);
                System.out.println("Отправлено: " + line);

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

    private static void log(String msg) {
        String time = LocalDateTime.now().format(FMT);
        logFile.println("[" + time + "] " + msg);
    }
}