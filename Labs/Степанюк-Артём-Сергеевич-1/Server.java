import java.io.*;
import java.net.*;

public class Server {
    private static class ClientHandler implements Runnable {
        private final Socket s;

        public ClientHandler(Socket s) {
            this.s = s;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream())), true);
                String str;

                while ((str = in.readLine()) != null)
                    if (str.startsWith("load ")) {
                        String filename = str.substring(5).trim();

                        File file = new File(filename);

                        if (!file.exists()) {
                            out.println("File not found");
                            break;
                        }

                        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                            String line;
                            while ((line = reader.readLine()) != null)
                                out.println(line);
                        } catch (IOException ex) {
                            out.println("Read error: " + ex.getMessage());
                            break;
                        }
                    } else
                        out.println(str);
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                } finally {
                    try {
                        System.out.println("closing: " + s);
                        s.close();
                    } catch (IOException e) {
                        System.out.println(e.getMessage());
                    }
                }
            }
        }

    public static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        ServerSocket s = new ServerSocket(PORT);
        System.out.println("Started: " + s);
        
        try {
        while (true) {
            Socket socket = s.accept();
            new Thread(new ClientHandler(socket)).start();
            }
        } finally {
            s.close();
        } 
    }
}