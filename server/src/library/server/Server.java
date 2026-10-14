package library.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    public static void main(String[] args) {
        new Server().run();
    }

    private void run() {
        Library library = new Library("data");
        System.out.println("Loaded " + library.itemCount() + " items, " + library.memberCount() + " members.");
        try (ServerSocket serverSocket = new ServerSocket(4242)) {
            System.out.println("Server listening on port 4242");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[server] client connected: " + socket.getRemoteSocketAddress());
                Thread t = new Thread(new ClientHandler(library, socket));
                t.setDaemon(true);
                t.start();
            }
        }
        catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}