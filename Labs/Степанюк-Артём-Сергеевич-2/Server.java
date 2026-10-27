import java.io.*;
import java.net.*;
import java.util.Arrays;

public class Server {
    private static class ClientHandler implements Runnable {
        private final DatagramSocket s;
        private final DatagramPacket request;

        public ClientHandler(DatagramSocket s, DatagramPacket request) {
            this.s = s;
            this.request = request;
        }

        @Override
        public void run() {
            InetAddress address = request.getAddress();
            int clientPort = request.getPort();
            try {
                String str = new String(request.getData(), 0, request.getLength());
                if (str.startsWith("load ")) {
                    String filename = str.substring(5).trim();

                    File file = new File(filename);

                    if (!file.exists()) {
                        send(address, clientPort, "File not found");
                        return;
                    }

                    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = reader.readLine()) != null)
                            send(address, clientPort, line);
                    } catch (IOException ex) {
                        send(address, clientPort, "Read error: " + ex.getMessage());
                    }
                } else
                    send(address, clientPort, str);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            } finally {
                System.out.println("closing: " + address + ":" + clientPort);
            }
        }

        private void send(InetAddress address, int port, String text) throws IOException {
            byte[] data = text.getBytes();
            s.send(new DatagramPacket(data, data.length, address, port));
        }

    }

    public static final int PORT = 666;

    public static void main(String[] args) throws IOException {
        DatagramSocket s = new DatagramSocket(PORT);
        System.out.println("Started: " + s);
        
        try {
        byte[] buffer = new byte[65507];
        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            s.receive(packet);
            byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
            DatagramPacket request = new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort());
            new Thread(new ClientHandler(s, request)).start();
            }
        } finally {
            s.close();
        } 
    }
}
