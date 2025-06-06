package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * This class implements:
 *  Client Side Feature 7: An interactive interface/UI for user interaction
 *  All other client‐side features are delegated to ChatClient.java
 */
public class ChatWindow extends JFrame {
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int SERVER_PORT = 12345;

    private ClientConnection client;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JLabel statusLabel;

    public ChatWindow() {
        super("Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(450, 500);
        setLayout(null);
        getContentPane().setBackground(new Color(12, 84, 163)); // match the blue background

        // ======== Top Label ========
        JLabel promptLabel = new JLabel("Write your text here");
        promptLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        promptLabel.setForeground(Color.WHITE);
        promptLabel.setBounds(20, 20, 200, 25);
        add(promptLabel);

        // ======== Input Field ========
        inputField = new JTextField();
        inputField.setBounds(20, 50, 300, 30);
        add(inputField);

        // ======== Send Button ========
        sendButton = new JButton("Send");
        sendButton.setBounds(330, 50, 80, 30);
        add(sendButton);

        // ======== Connection Status ========
        statusLabel = new JLabel("Not connected");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setBounds(20, 90, 200, 20);
        add(statusLabel);

        // ======== Chat Area ========
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBounds(20, 120, 390, 330);
        add(scrollPane);

        // Attempt initial connection
        setupClient();

        // ========== Action Listeners ==========
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                send();
            }
        });
        inputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                send();
            }
        });

        // ======== Background Thread: Listen for server messages ========
        new Thread(() -> {
            while (true) {
                if (client != null && client.isAlive()) {
                    String line = client.receive();
                    if (line != null && !line.trim().isEmpty()) {
                        chatArea.append("Server: " + line + "\n");
                    }
                } else {
                    attemptReconnect();
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignore) {}
            }
        }).start();

        // Show window
        setVisible(true);
    }

    /**
     * Try to establish initial connection to server.
     * Updates statusLabel to reflect connection.
     */
    private void setupClient() {
        try {
            client = new ClientConnection(SERVER_ADDRESS, SERVER_PORT);
            statusLabel.setText("Connected to: " + SERVER_ADDRESS);
            chatArea.append("Connected to server\n");
        } catch (Exception ex) {
            statusLabel.setText("Unable to connect");
            JOptionPane.showMessageDialog(this, "Cannot connect to server", "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Attempt to reconnect if connection drops.
     */
    private void attemptReconnect() {
        if (client == null || !client.isAlive()) {
            ClientConnection newClient = ClientConnection.reconnect(SERVER_ADDRESS, SERVER_PORT);
            if (newClient != null && newClient.isAlive()) {
                client = newClient;
                statusLabel.setText("Reconnected to: " + SERVER_ADDRESS);
                chatArea.append("Reconnected to server\n");
            }
        }
    }

    /**
     * Send the text typed by the user to the server.
     * Also appends it locally with “Me: ” prefix.
     */
    private void send() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && client != null && client.isAlive()) {
            chatArea.append("Me: " + text + "\n");
            client.send(text);
            inputField.setText("");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatWindow::new);
    }
}
