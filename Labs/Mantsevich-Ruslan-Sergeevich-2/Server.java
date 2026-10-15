import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    public static final int PORT = 666;

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            System.out.println("UDP-сервер запущен на порту " + PORT);

            Map<String, StringBuilder> buffers = new ConcurrentHashMap<>();
            byte[] buf = new byte[2048];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String key = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                String data = new String(packet.getData(), 0, packet.getLength());

                StringBuilder buffer = buffers.computeIfAbsent(key, k -> new StringBuilder());
                buffer.append(data);

                String content = buffer.toString();
                int newlineIdx;
                StringBuilder answers = new StringBuilder();
                boolean terminate = false;

                while ((newlineIdx = content.indexOf('\n')) != -1) {
                    String line = content.substring(0, newlineIdx).trim();
                    content = content.substring(newlineIdx + 1);

                    if (line.isEmpty()) continue;

                    System.out.println("Получено от " + key + ": " + line);

                    if (line.startsWith("find ")) {
                        String[] parts = line.substring(5).trim().split("\\s+");
                        if (parts.length < 2) {
                            answers.append("Usage: find <string> <filename>\n");
                            continue;
                        }

                        String searchStr = parts[0];
                        String filename = parts[1];

                        File file = new File(filename);
                        if (!file.exists() || !file.isFile()) {
                            answers.append("File not found. Session terminated.\n");
                            terminate = true;
                            break;
                        }

                        int count = 0;
                        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
                            String fileLine;
                            while ((fileLine = fileReader.readLine()) != null) {
                                if (fileLine.contains(searchStr)) {
                                    count++;
                                }
                            }
                        }

                        if (count == 0) {
                            answers.append("Not found. Session terminated.\n");
                            terminate = true;
                            break;
                        } else {
                            answers.append("Found: ").append(count).append("\n");
                        }
                    } else {
                        answers.append("Echo: ").append(line).append("\n");
                    }
                }

                buffer.setLength(0);
                buffer.append(content);

                if (answers.length() > 0) {
                    byte[] response = answers.toString().getBytes();
                    DatagramPacket responsePacket = new DatagramPacket(
                        response, response.length,
                        packet.getAddress(), packet.getPort()
                    );
                    socket.send(responsePacket);
                    System.out.println("Отправлено " + key + ":\n" + answers);
                }

                if (terminate) {
                    buffers.remove(key);
                    System.out.println("Сеанс " + key + " завершён.");
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка сервера: " + e.getMessage());
        }
    }
}