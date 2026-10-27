import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class Client {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 8080;
    private static final String LOG_FILE = "protocol.log";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        // --- Исправление кодировки консоли Windows ---
        try {
            new ProcessBuilder("cmd", "/c", "chcp", "65001").inheritIO().start().waitFor();
        } catch (Exception e) { }
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
            System.setErr(new PrintStream(System.err, true, "UTF-8"));
        } catch (Exception e) { }
        // ---------------------------------------------

        Socket socket = null;
        PrintWriter out = null;
        BufferedReader in = null;
        Scanner scanner = null; // Объявляем здесь, чтобы закрыть в finally

        try {
            scanner = new Scanner(System.in, "UTF-8");
            
            logEvent("Попытка автоподключения к " + DEFAULT_HOST + ":" + DEFAULT_PORT);
            socket = new Socket(DEFAULT_HOST, DEFAULT_PORT);
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            
            logEvent("СОЕДИНЕНИЕ НАЧАТО с " + DEFAULT_HOST + ":" + DEFAULT_PORT);
            System.out.println("Подключено к серверу " + DEFAULT_HOST + ":" + DEFAULT_PORT);
            System.out.println("Введите строку и нажмите Enter для отправки.");
            System.out.println("Для отключения введите: disconnect <адрес> <порт>");

            // Убрали finalSocket, оставили только finalIn
            final BufferedReader finalIn = in;
            Thread readerThread = new Thread(() -> {
                try {
                    String serverResponse;
                    while ((serverResponse = finalIn.readLine()) != null) {
                        System.out.println("<<< ОТВЕТ СЕРВЕРА: " + serverResponse);
                        logEvent("ПОЛУЧЕНО ОТ СЕРВЕРА: " + serverResponse);
                        
                        if (serverResponse.contains("ОШИБКА") || serverResponse.contains("разрывается")) {
                            System.out.println("Сервер разорвал соединение. Выход...");
                            System.exit(0);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Соединение с сервером потеряно.");
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            while (true) {
                String userInput = scanner.nextLine();

                if (userInput.startsWith("disconnect")) {
                    logEvent("ОТПРАВЛЕНА КОМАНДА ОТКЛЮЧЕНИЯ");
                    System.out.println("Отключение от сервера...");
                    logEvent("СОЕДИНЕНИЕ ЗАВЕРШЕНО");
                    break;
                }

                logEvent("ОТПРАВЛЕНО СЕРВЕРУ: " + userInput);
                for (char c : userInput.toCharArray()) {
                    out.print(c);
                }
                out.print("\n");
                out.flush();
                System.out.println(">>> Отправлено (" + userInput.length() + " символов). Введите следующую строку:");
            }

        } catch (IOException e) {
            System.out.println("Ошибка подключения: " + e.getMessage());
            logEvent("ОШИБКА ПОДКЛЮЧЕНИЯ: " + e.getMessage());
        } finally {
            try {
                if (socket != null) socket.close();
                if (scanner != null) scanner.close(); // Закрываем сканер (исправление 1)
                logEvent("СОЕДИНЕНИЕ ЗАКРЫТО");
            } catch (IOException e) { }
        }
    }

    private static void logEvent(String message) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter logOut = new PrintWriter(bw)) {
            String time = LocalDateTime.now().format(TIME_FORMAT);
            logOut.println("[" + time + "] " + message);
        } catch (IOException e) {
            System.err.println("Не удалось записать лог: " + e.getMessage());
        }
    }
}