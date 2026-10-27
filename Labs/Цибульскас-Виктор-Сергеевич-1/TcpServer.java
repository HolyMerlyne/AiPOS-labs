import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpServer {
    public static final int PORT = 8080;
    private static final String STOP_MARK = "#end##";

    public static void main(String[] args) throws IOException {
        ServerSocket gate = new ServerSocket(PORT);
        System.out.println("TCP-сервер запущен, порт " + PORT);
        try {
            while (true) {
                Socket incoming = gate.accept();
                new Thread(new Session(incoming), "client-" + incoming.getPort()).start();
            }
        } finally {
            gate.close();
        }
    }

    static final class Session implements Runnable {
        private final Socket link;

        Session(Socket link) {
            this.link = link;
        }

        @Override
        public void run() {
            String peer = String.valueOf(link.getRemoteSocketAddress());
            System.out.println("Подключение: " + peer);
            try (
                Socket socket = link;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(
                        new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),
                        true)
            ) {
                writer.println("Hello, Student!");
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("save ")) {
                        if (!writeSavedFile(line.substring(5).trim(), reader, writer)) {
                            break;
                        }
                    } else {
                        writer.println(line);
                    }
                }
            } catch (IOException error) {
                System.err.println("Ошибка сеанса: " + error.getMessage());
            }
            System.out.println("Сеанс завершён: " + peer);
        }

        private boolean writeSavedFile(String fileName, BufferedReader reader, PrintWriter writer)
                throws IOException {
            if (!acceptableName(fileName)) {
                writer.println("Ошибка: имя файла недопустимо. Соединение закрывается.");
                return false;
            }

            StringBuilder body = new StringBuilder();
            boolean markerSeen = false;
            String row;
            while ((row = reader.readLine()) != null) {
                int markerAt = row.indexOf(STOP_MARK);
                if (markerAt >= 0) {
                    body.append(row, 0, markerAt);
                    markerSeen = true;
                    break;
                }
                body.append(row).append('\n');
            }
            if (!markerSeen) {
                writer.println("Ошибка: маркер #end## не получен. Соединение закрывается.");
                return false;
            }

            try (FileWriter file = new FileWriter(fileName)) {
                file.write(body.toString());
            } catch (IOException error) {
                writer.println("Ошибка: файл " + fileName + " не создан. Соединение закрывается.");
                return false;
            }

            writer.println("Файл " + fileName + " создан, символов: " + body.length());
            return true;
        }

        private static boolean acceptableName(String fileName) {
            if (fileName.isEmpty() || fileName.length() > 80) {
                return false;
            }
            if (fileName.equals(".") || fileName.equals("..")) {
                return false;
            }
            for (int i = 0; i < fileName.length(); i++) {
                char symbol = fileName.charAt(i);
                if (symbol < 32 || symbol == '/' || symbol == '\\' || symbol == ':') {
                    return false;
                }
            }
            return true;
        }
    }
}
