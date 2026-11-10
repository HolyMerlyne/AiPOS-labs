import java.io.*;
import java.net.*;

public class Server {
    public static final int PORT = 8080;
    private static final String BASE_DIR = ".";
    private static final int BUF_SIZE = 4096;

    public static void main(String[] args) throws IOException {
        DatagramSocket socket = new DatagramSocket(PORT);
        System.out.println("UDP Server started on port " + PORT);
        byte[] buf = new byte[BUF_SIZE];

        try {
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                InetAddress clientAddr = packet.getAddress();
                int clientPort = packet.getPort();
                String command = new String(packet.getData(), 0, packet.getLength(), "UTF-8").trim();

                System.out.println("Received from " + clientAddr + ":" + clientPort + " -> " + command);

                if (command.startsWith("load ")) {
                    handleLoad(socket, clientAddr, clientPort,command.substring("load ".length()).trim());
                } else {
                    sendLine(socket, clientAddr, clientPort, "ERROR: unknown command, expected: load <file>");
                }
            }
        } finally {
            socket.close();
        }
    }

    private static void handleLoad(DatagramSocket socket, InetAddress addr, int port, String fileName) throws IOException {
        if (fileName.isEmpty()) {
            sendLine(socket, addr, port, "ERROR: file name is empty");
            return;
        }

        File file = new File(BASE_DIR, fileName);
        if (!file.exists() || !file.isFile()) {
            sendLine(socket, addr, port, "ERROR: file not found: " + fileName);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sendLine(socket, addr, port, line);
            }
            sendLine(socket, addr, port, "__EOF__");
        } catch (IOException e) {
            sendLine(socket, addr, port, "ERROR: cannot read file: " + e.getMessage());
        }
    }

    private static void sendLine(DatagramSocket socket, InetAddress addr, int port, String text) throws IOException {
        byte[] data = (text + "\n").getBytes("UTF-8");
        DatagramPacket out = new DatagramPacket(data, data.length, addr, port);
        socket.send(out);
    }
}