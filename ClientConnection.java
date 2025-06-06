package client;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encapsulates the socket, reader, and writer for a chat client:
 * 1. Connects to the server at given host/port
 * 2. Sends text messages
 * 3. Receives plain‐text lines (with a timeout)
 * 4. Allows clean shutdown of the socket
 * 5. Supports a static reconnect helper
 */
public class ClientConnection {
    private final Socket sock;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);

    /**
     * Opens a socket to the chat server, sets a 5-second read timeout,
     * and initializes I/O streams.
     */
    public ClientConnection(String host, int port) throws IOException {
        this.sock = new Socket(host, port);
        this.sock.setSoTimeout(5000);
        this.reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        this.writer = new PrintWriter(sock.getOutputStream(), true);
        isConnected.set(true);
    }

    /** Returns true if the socket is open and still connected. */
    public boolean isAlive() {
        return isConnected.get() && sock != null && sock.isConnected() && !sock.isClosed();
    }

    /** Sends a line (with newline) to the server. */
    public void send(String message) {
        if (isAlive()) {
            writer.println(message);
        }
    }

    /**
     * Attempts to read one line from the server.
     * If it times out or an I/O error occurs, returns null.
     */
    public String receive() {
        try {
            return reader.readLine();
        } catch (SocketTimeoutException e) {
            // No message arrived in 5 seconds; that’s fine
            return null;
        } catch (IOException e) {
            isConnected.set(false);
            return null;
        }
    }

    /** Closes socket and marks connection as dead. */
    public void close() {
        isConnected.set(false);
        try {
            if (sock != null && !sock.isClosed()) {
                sock.close();
            }
        } catch (IOException ignored) { }
    }

    /**
     * Static helper to establish a new connection if the previous one died.
     * Returns null if reconnection fails.
     */
    public static ClientConnection reconnect(String host, int port) {
        try {
            return new ClientConnection(host, port);
        } catch (IOException e) {
            return null;
        }
    }
}
