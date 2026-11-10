import java.io.*;
import java.net.*;
import java.util.*;

public class TCPServer {
    public static final int PORT = 666;

    public static void main(String[] args) {
        System.out.println("TCP Server started on port " + PORT);
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());   
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true); 
        } catch (IOException e) {
            System.err.println("Error initializing streams: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        StringBuilder buffer = new StringBuilder();
        
        try {
            String line;
            while ((line = in.readLine()) != null) {
                
                if (line.trim().equalsIgnoreCase("disconnect")) {
                    System.out.println("Client requested disconnect.");
                    break;
                }

                buffer.append(line);
                
                while (buffer.length() >= 64) {
                    String group = buffer.substring(0, 64);
                    buffer.delete(0, 64);
                    
                    Map<Character, Integer> charCount = new LinkedHashMap<>();
                    for (char c : group.toCharArray()) {
                        charCount.put(c, charCount.getOrDefault(c, 0) + 1);
                    }
                    
                    if (charCount.size() < 3) {
                        String errorMsg = "Error: less than 3 different characters in the group. Disconnecting.";
                        System.out.println("Error for client. Disconnecting.");
                        out.println(errorMsg);
                        return;
                    }
                    
                    StringBuilder response = new StringBuilder();
                    for (Map.Entry<Character, Integer> entry : charCount.entrySet()) {
                        response.append(entry.getKey()).append("-").append(entry.getValue()).append(" ");
                    }
                    
                    out.println(response.toString().trim());
                }
            }
        } catch (IOException e) {
            System.out.println("Client disconnected abruptly or IO error: " + e.getMessage());
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                System.out.println("Connection closed for client.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}