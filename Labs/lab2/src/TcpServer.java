import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;


public class TcpServer {

    public static final int PORT = 666;
    public static final String FILES_DIR  = "server-files";
    public static final String LOGS_DIR   = "logs";
    private static final Logger LOG = Logger.getLogger(TcpServer.class.getName());

    public static void main(String[] args) throws IOException {
        ensureDirs();
        ensureTestData();
        setupLogger();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            LOG.info("TCP server started on port " + PORT);
            System.out.println("TCP server started on port " + PORT);
            while (true) {
                Socket client = serverSocket.accept();
                LOG.info("Client connected: " + client.getRemoteSocketAddress());
                new Thread(new ClientHandler(client), "client-" + client.getPort()).start();
            }
        }
    }

    private static void ensureDirs() {
        new File(FILES_DIR).mkdirs();
        new File(LOGS_DIR).mkdirs();
    }

    /** Создаёт test1.txt и test2.txt, если их нет — чтобы пример сразу работал. */
    private static void ensureTestData() throws IOException {
        File t1 = new File(FILES_DIR, "test1.txt");
        if (!t1.exists()) {
            Files.write(t1.toPath(), Arrays.asList(
                    "Hello world",
                    "This is the first test file",
                    "Hello again",
                    "Another line with hello inside",
                    "Last line"
            ));
        }
        File t2 = new File(FILES_DIR, "test2.txt");
        if (!t2.exists()) {
            Files.write(t2.toPath(), Arrays.asList(
                    "apple banana cherry",
                    "dog cat bird",
                    "apple pie",
                    "no matches here"
            ));
        }
    }

    private static void setupLogger() throws IOException {
        FileHandler fh = new FileHandler(LOGS_DIR + "/server.log", true);
        fh.setFormatter(new SimpleFormatter());
        LOG.addHandler(fh);
        LOG.setLevel(Level.INFO);
    }

    /** Обработчик одного клиента. */
    private static class ClientHandler implements Runnable {
        private final Socket socket;

        ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "UTF-8"));
                 PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)) {

                out.println("Hello, Student! Введите: find <строка> <имя файла>");

                String line;
                while ((line = in.readLine()) != null) {
                    LOG.info("From client: " + line);
                    String trimmed = line.trim();

                    if (trimmed.equalsIgnoreCase("quit") || trimmed.equalsIgnoreCase("END")) {
                        out.println("Пока!");
                        break;
                    }

                    String[] parts = trimmed.split("\\s+", 3);
                    if (parts.length != 3 || !parts[0].equalsIgnoreCase("find")) {
                        out.println("ERROR: ожидается команда формата: find <строка> <имя файла>");
                        continue;
                    }

                    String searchStr = parts[1];
                    String fileName  = parts[2];
                    File file = new File(FILES_DIR, fileName);

                    if (!file.exists() || !file.isFile()) {
                        out.println("ERROR: файл '" + fileName + "' не найден. Сеанс завершён.");
                        LOG.warning("File not found: " + fileName);
                        break;
                    }

                    int count = 0;
                    try (BufferedReader fr = new BufferedReader(
                            new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                        String fl;
                        while ((fl = fr.readLine()) != null) {
                            if (fl.contains(searchStr)) count++;
                        }
                    }

                    if (count == 0) {
                        out.println("RESULT: строка '" + searchStr + "' не найдена. Сеанс завершён.");
                        LOG.info("No matches for '" + searchStr + "' in " + fileName);
                        break;
                    } else {
                        out.println("RESULT: найдено строк = " + count);
                        LOG.info("Found " + count + " lines with '" + searchStr + "'");
                    }
                }
            } catch (IOException e) {
                LOG.warning("Client I/O error: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
                LOG.info("Client disconnected");
            }
        }
    }
}