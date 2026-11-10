import java.io.*;
import java.net.*;
import java.util.*;


public class TcpServer {
    public static final int PORT = 6666;

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("TCP Server запущен на порту " + PORT);
        System.out.println("Ожидание подключения...");

        while (true) {
            Socket client = serverSocket.accept();
            System.out.println("Клиент подключён: " + client.getRemoteSocketAddress());
            new Thread(new ClientHandler(client)).start();
        }
    }
}

class ClientHandler implements Runnable {
    private final Socket socket;

    ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
             PrintWriter out = new PrintWriter(
                    new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), "UTF-8")), true)) {

            out.println("Hello, Student!");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Получено (" + line.length() + " симв.): " + line);

                // Обрабатываем строку ЦЕЛИКОМ — любой длины
                if (!processGroup(line, out)) {
                    return; // разрыв
                }
                out.println("OK: обработано " + line.length() + " символов");
            }

        } catch (IOException e) {
            System.err.println("Ошибка при работе с клиентом: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("Клиент отключён.");
        }
    }

    /**
     * Обрабатывает строку символов.
     * Возвращает true — можно продолжать, false — разрыв.
     */
    private boolean processGroup(String group, PrintWriter out) {
        Map<Character, Integer> counts = new LinkedHashMap<>();
        for (char c : group.toCharArray()) {
            counts.merge(c, 1, Integer::sum);
        }

        // < 3 уникальных символов — разрыв
        if (counts.size() < 3) {
            out.println("ERROR: меньше 3 различных символов. Соединение закрывается.");
            System.out.println("Разрыв: <3 уникальных символов");
            return false;
        }

        // Формируем статистику
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Character, Integer> e : counts.entrySet()) {
            char c = e.getKey();
            String sym = (c == '\n') ? "\\n"
                       : (c == '\r') ? "\\r"
                       : (c == '\t') ? "\\t"
                       : String.valueOf(c);
            sb.append("<").append(sym).append("-").append(e.getValue()).append("> ");
        }

        System.out.println("Отправлено: " + sb);
        out.println(sb.toString());
        return true;
    }
}