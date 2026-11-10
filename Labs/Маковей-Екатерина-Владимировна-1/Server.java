import java.io.*;
import java.net.*;

public class Server {
    public static final int PORT = 8080;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен на порту " + PORT + ". Ожидание клиента...");
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Подключился клиент: " + clientSocket.getInetAddress());
                // Запускаем отдельный поток для каждого клиента
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            out.println("Hello, Student! Сервер варианта 5 ждет 64 символа.");
            
            char[] buffer = new char[64];
            int count = 0;
            int ch;

            // Читаем по одному символу
            while ((ch = in.read()) != -1) {
                buffer[count++] = (char) ch;

                // Как только набралось 64 символа
                if (count == 64) {
                    // Считаем частоту символов
                    java.util.Map<Character, Integer> freq = new java.util.HashMap<>();
                    for (char c : buffer) {
                        freq.put(c, freq.getOrDefault(c, 0) + 1);
                    }

                    // Проверяем условие: если уникальных символов меньше 3
                    if (freq.size() < 3) {
                        out.println("ОШИБКА: В группе менее 3 уникальных символов. Соединение разрывается.");
                        break; // Выходим из цикла, сокет закроется автоматически
                    }

                    // Формируем ответ по заданию
                    StringBuilder response = new StringBuilder("<<");
                    boolean first = true;
                    for (java.util.Map.Entry<Character, Integer> entry : freq.entrySet()) {
                        if (!first) response.append(", ");
                        // Если символ - пробел или перенос строки, заменяем для красоты вывода
                        String sym = String.valueOf(entry.getKey());
                        if (sym.equals(" ")) sym = "[пробел]";
                        if (sym.equals("\n") || sym.equals("\r")) sym = "[enter]";
                        
                        response.append(sym).append("-").append(entry.getValue());
                        first = false;
                    }
                    response.append(">>");

                    out.println(response.toString());
                    count = 0; // Сбрасываем счетчик для следующей группы
                }
            }
        } catch (IOException e) {
            System.out.println("Клиент отключился.");
        } finally {
            
            try { socket.close(); } catch (IOException e) { }
        }
    }
}