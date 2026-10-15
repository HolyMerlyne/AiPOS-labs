import java.io.*;
import java.net.*;
import java.util.*;

/**
 * TCP-сервер, вариант 6.
 * Принимает группы по 64 символа, присылает клиенту статистику:
 * <символ-количество> для каждого символа в группе.
 * Если уникальных символов < 3 — разрыв соединения.
 */
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

            char[] buffer = new char[64];
            int read;

            while ((read = readFully(in, buffer)) > 0) {
                String group = new String(buffer, 0, read);

                // Подсчёт вхождений каждого символа
                Map<Character, Integer> counts = new LinkedHashMap<>();
                for (char c : group.toCharArray()) {
                    counts.merge(c, 1, Integer::sum);
                }

                // Если уникальных символов < 3 — разрыв
                if (counts.size() < 3) {
                    out.println("ERROR: в группе меньше 3 различных символов. Соединение закрывается.");
                    System.out.println("Разрыв: <3 уникальных символов");
                    break;
                }

                // Формируем строку статистики
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
            }

        } catch (IOException e) {
            System.err.println("Ошибка при работе с клиентом: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            System.out.println("Клиент отключён.");
        }
    }

    /** Читает ровно buffer.length символов или до конца потока. */
    private static int readFully(Reader in, char[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int r = in.read(buffer, total, buffer.length - total);
            if (r == -1) break;
            total += r;
        }
        return total;
    }
}