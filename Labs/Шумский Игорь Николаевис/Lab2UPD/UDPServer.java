import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UDPServer {
    public static final int PORT = 666;
    
    private static Map<SocketAddress, StringBuilder> clientBuffers = new ConcurrentHashMap<>();
    private static Set<SocketAddress> disconnectedClients = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        System.out.println("UDP Server started on port " + PORT);
        
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String data = new String(packet.getData(), 0, packet.getLength());
                SocketAddress clientAddr = packet.getSocketAddress();

                if (disconnectedClients.contains(clientAddr)) {
                    continue; 
                }

                if (data.trim().equalsIgnoreCase("disconnect")) {
                    clientBuffers.remove(clientAddr);
                    disconnectedClients.add(clientAddr);
                    System.out.println("Client disconnected logically: " + clientAddr);
                    continue;
                }

                StringBuilder sb = clientBuffers.computeIfAbsent(clientAddr, k -> new StringBuilder());
                sb.append(data);

                while (sb.length() >= 64) {
                    String group = sb.substring(0, 64);
                    sb.delete(0, 64);

                    Map<Character, Integer> charCount = new LinkedHashMap<>();
                    for (char c : group.toCharArray()) {
                        charCount.put(c, charCount.getOrDefault(c, 0) + 1);
                    }

                    String response;
                    if (charCount.size() < 3) {
                        response = "Error: less than 3 different characters in the group. Disconnecting.";
                        System.out.println("Error for client " + clientAddr + ". Disconnecting.");
                        
                        clientBuffers.remove(clientAddr);
                        disconnectedClients.add(clientAddr);
                    } else {
                        StringBuilder respBuilder = new StringBuilder();
                        for (Map.Entry<Character, Integer> entry : charCount.entrySet()) {
                            respBuilder.append(entry.getKey()).append("-").append(entry.getValue()).append(" ");
                        }
                        response = respBuilder.toString().trim();
                    }

                    byte[] respBytes = response.getBytes();
                    DatagramPacket respPacket = new DatagramPacket(respBytes, respBytes.length, clientAddr);
                    socket.send(respPacket);
                }
            }
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}