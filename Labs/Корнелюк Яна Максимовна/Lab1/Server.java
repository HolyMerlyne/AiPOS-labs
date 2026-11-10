import java.io.*;
import java.net.*;

public class Server {
    public static final int PORT = 8080;
    private static final String BASE_DIR = ".";

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server started: " + serverSocket);
        try {
            while (true) {
                Socket socket = serverSocket.accept();
                try {
                    new ClientHandler(socket).start();
                } catch (IOException e) {
                    socket.close();
                }
            }
        } finally {
            serverSocket.close();
        }
    }

    private static class ClientHandler extends Thread {

        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new BufferedWriter( new OutputStreamWriter(socket.getOutputStream())), true);
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("Received: " + line);

                    if (line.startsWith("load ")) {
                        handleLoad(line.substring("load ".length()).trim());
                        break;
                    } else {
                        out.println("ERROR: unknown command, expected: load <file>");
                    }
                }
            } catch (IOException e) {
                System.err.println("IO error: " + e.getMessage());
            } finally {
                closeQuietly();
                System.out.println("Connection closed: " + socket);
            }
        }

        private void handleLoad(String fileName) {
            if (fileName.isEmpty()) {
                out.println("ERROR: file name is empty");
                return;
            }

            File file = new File(BASE_DIR, fileName);

            if (!file.exists() || !file.isFile()) {
                out.println("ERROR: file not found: " + fileName);
                return;
            }

            try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
                String fileLine;
                while ((fileLine = fileReader.readLine()) != null) {
                    out.println(fileLine);
                }
                out.println("__EOF__");
            } catch (IOException e) {
                out.println("ERROR: cannot read file: " + e.getMessage());
            }
        }

        private void closeQuietly() {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}