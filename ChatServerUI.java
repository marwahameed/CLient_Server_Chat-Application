package server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A Swing‐based chat server that:
 * 1. Listens on port 12345 
 * 2. Accepts multiple client connections (using a background thread)
 * 3. Reads and handles each client’s messages in its own handler thread
 * 4. Broadcasts messages to all connected clients
 * 5. Silently ignores blank or malformed input
 * 6. Maintains a thread‐safe list of active connections
 * 7. Logs events in the server GUI
 * 8. Closes all resources cleanly when window is closed
 * 9. Uses a simple plain‐text protocol
 */
public class ChatServerUI extends JFrame {
    private static final int SERVER_PORT = 12345;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private final List<ConnectionHandler> activeClients = new CopyOnWriteArrayList<>();

    // Swing components
    private final JTextArea logArea;
    private final JTextField messageField;
    private final JButton broadcastButton;
    private final JLabel statusLabel;

    public ChatServerUI() {
        super("Chat Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(450, 500);
        setLayout(null);
        getContentPane().setBackground(new Color(220, 220, 220)); // light gray

        // ===== Top Instruction Label =====
        JLabel instructionLabel = new JLabel("Type a message and hit Send to broadcast");
        instructionLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        instructionLabel.setBounds(20, 20, 350, 25);
        add(instructionLabel);

        // ===== Message Input Field =====
        messageField = new JTextField();
        messageField.setBounds(20, 50, 300, 30);
        add(messageField);

        // ===== Broadcast Button =====
        broadcastButton = new JButton("Send");
        broadcastButton.setBounds(330, 50, 80, 30);
        add(broadcastButton);

        // ===== Server Status Label =====
        statusLabel = new JLabel("Server not running");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setBounds(20, 90, 250, 20);
        add(statusLabel);

        // ===== Log Text Area =====
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBounds(20, 120, 390, 330);
        add(scrollPane);

        // ===== Action to Broadcast a Server Message =====
        broadcastButton.addActionListener(e -> {
            String text = messageField.getText().trim();
            if (!text.isEmpty()) {
                String outMsg = "Server: " + text;
                appendLog(outMsg);
                distributeToAll(outMsg);
                messageField.setText("");
            }
        });
        messageField.addActionListener(e -> broadcastButton.doClick());

        // Start listening for client connections
        launchServer();

        setLocationRelativeTo(null);
        setVisible(true);
    }

    /** Starts the ServerSocket and spawns a thread to accept connections. */
    private void launchServer() {
        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            statusLabel.setText("Listening on port " + SERVER_PORT);
            appendLog("Server initialized on port " + SERVER_PORT);

            acceptThread = new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket clientSock = serverSocket.accept();
                        appendLog("Client arrived: " + clientSock.getInetAddress());
                        ConnectionHandler handler = new ConnectionHandler(clientSock);
                        activeClients.add(handler);
                        new Thread(handler).start();
                    } catch (IOException ex) {
                        if (serverSocket.isClosed()) {
                            break;
                        }
                        appendLog("Error accepting client: " + ex.getMessage());
                    }
                }
            });
            acceptThread.start();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to launch server:\n" + ex.getMessage(),
                "Startup Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /** Display a message in the server log area. */
    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(text + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /** Sends a text line to every connected client. */
    private void distributeToAll(String msg) {
        for (ConnectionHandler ch : activeClients) {
            ch.sendLine(msg);
        }
    }

    /** Override window‐closing to shut down all sockets and threads cleanly. */
    @Override
    protected void processWindowEvent(WindowEvent we) {
        super.processWindowEvent(we);
        if (we.getID() == WindowEvent.WINDOW_CLOSING) {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException ignored) { }

            // Close each client handler
            for (ConnectionHandler handler : activeClients) {
                handler.shutdown();
            }
            activeClients.clear();
        }
    }

    /** Inner class to handle one client’s incoming messages and disconnection. */
    private class ConnectionHandler implements Runnable {
        private final Socket clientSocket;
        private final BufferedReader inReader;
        private final PrintWriter outWriter;
        private volatile boolean alive = true;

        public ConnectionHandler(Socket socket) throws IOException {
            this.clientSocket = socket;
            this.inReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.outWriter = new PrintWriter(socket.getOutputStream(), true);
        }

        @Override
        public void run() {
            try {
                String line;
                while (alive && (line = inReader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        // ignore blank lines
                        continue;
                    }
                    String clientTag = clientSocket.getInetAddress().toString();
                    appendLog("Client " + clientTag + ": " + line);

                    // Broadcast to everyone except this client
                    String forward = "Client (" + clientTag + "): " + line;
                    for (ConnectionHandler other : activeClients) {
                        if (other != this) {
                            other.sendLine(forward);
                        }
                    }
                }
            } catch (IOException e) {
                appendLog("Lost connection with " + clientSocket.getInetAddress());
            } finally {
                cleanup();
            }
        }

        /** Send a single line (with newline) to the client. */
        public void sendLine(String msg) {
            outWriter.println(msg);
        }

        /** Close this client’s socket/streams and remove from list. */
        public void shutdown() {
            alive = false;
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException ignored) { }
        }

        private void cleanup() {
            alive = false;
            activeClients.remove(this);
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException ignored) { }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatServerUI::new);
    }
}
