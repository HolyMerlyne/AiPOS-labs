import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;


public class UdpServer {

    public static final int    PORT      = 666;
    public static final String FILES_DIR = "server-files";
    public static final String LOGS_DIR  = "logs";
    private static final Logger LOG = Logger.getLogger(UdpServer.class.getName());

    public static void main(String[] args) throws IOException {
        ensureDirs();
        ensureTestData();
        setupLogger();

        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            LOG.info("UDP server started on port " + PORT);
            System.out.println("UDP server started on port " + PORT);
            
            byte[] buf = new byte[2048];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String request = new String(packet.getData(), 0, packet.getLength(), "UTF-8").trim();
                LOG.info("From " + packet.getAddress() + ":" + packet.getPort() + " → " + request);
                System.out.println("Получено: " + request);

                String response = process(request);
                LOG.info("Response: " + response);
                System.out.println("Ответ: " + response);

                byte[] out = response.getBytes("UTF-8");
                socket.send(new DatagramPacket(out, out.length, packet.getAddress(), packet.getPort()));
            }
        }
    }

    private static void ensureDirs() {
        new File(FILES_DIR).mkdirs();
        new File(LOGS_DIR).mkdirs();
    }

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

    private static String process(String request) {
        if (request.equalsIgnoreCase("quit")) return "Пока!";
        
        String[] parts = request.split("\\s+", 3);
        if (parts.length != 3 || !parts[0].equalsIgnoreCase("find")) {
            return "ERROR: ожидается: find <строка> <имя файла>";
        }

        String searchStr = parts[1];
        String fileName  = parts[2];
        File file = new File(FILES_DIR, fileName);
        
        if (!file.exists() || !file.isFile()) {
            return "ERROR: файл '" + fileName + "' не найден. Сеанс завершён.";
        }

        int count = 0;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.contains(searchStr)) count++;
            }
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }

        if (count == 0) {
            return "RESULT: строка '" + searchStr + "' не найдена. Сеанс завершён.";
        }
        return "RESULT: найдено строк = " + count;
    }

    private static void setupLogger() throws IOException {
        FileHandler fh = new FileHandler(LOGS_DIR + "/udp-server.log", true);
        fh.setFormatter(new SimpleFormatter());
        LOG.addHandler(fh);
        LOG.setLevel(Level.INFO);
    }
}