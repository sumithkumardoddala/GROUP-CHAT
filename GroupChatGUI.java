import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class GroupChatGUI extends JFrame {

    private static final String TERMINATE = "Exit";
    static String name;
    static volatile boolean finished = false;

    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;

    private MulticastSocket socket;
    private InetAddress group;
    private int port;

    public GroupChatGUI() {
        // --- GUI Setup ---
        setTitle("Group Chat");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // We'll handle closing manually
        setLocationRelativeTo(null); // Center the window

        // Use a more modern look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.out.println("Error setting Look and Feel: " + e.getMessage());
        }

        // --- Components ---
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        messageField = new JTextField();
        messageField.setFont(new Font("SansSerif", Font.PLAIN, 14));

        sendButton = new JButton("Send");

        // --- Layout ---
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(messageField, BorderLayout.CENTER);
        southPanel.add(sendButton, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        // --- Event Listeners ---
        ActionListener sendMessageAction = e -> sendMessage();
        sendButton.addActionListener(sendMessageAction);
        messageField.addActionListener(sendMessageAction);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    /**
     * Initializes networking components and starts the reading thread.
     * @param host The multicast host address.
     * @param portNum The port number.
     * @param username The user's name.
     * @return true if initialization is successful, false otherwise.
     */
    private boolean initNetwork(String host, int portNum, String username) {
        try {
            group = InetAddress.getByName(host);
            if (!group.isMulticastAddress()) {
                showError("Invalid Multicast Address", "The provided host is not a valid multicast address.");
                return false;
            }
            port = portNum;
            name = username;

            socket = new MulticastSocket(port);
            socket.setTimeToLive(1); // For local subnet
            socket.joinGroup(group);

            // Start a thread to read messages from the group
            Thread readerThread = new Thread(new ReadThread(socket, group, port, chatArea));
            readerThread.start();

            // Announce user joining
            sendMessage(name + " has joined the chat.", false);

            return true;
        } catch (UnknownHostException e) {
            showError("Invalid Host", "The multicast host could not be found.");
        } catch (IOException e) {
            showError("Network Error", "An error occurred while setting up the network connection: " + e.getMessage());
        }
        return false;
    }

    /**
     * Sends the message from the message field.
     */
    private void sendMessage() {
        String message = messageField.getText();
        if (message != null && !message.trim().isEmpty()) {
            sendMessage(name + ": " + message, true);
            messageField.setText("");
        }
    }

    /**
     * Sends a message to the multicast group.
     * @param message The string message to send.
     * @param displayInChat If true, the message is also appended to the sender's chat area.
     */
    private void sendMessage(String message, boolean displayInChat) {
        try {
            if (displayInChat) {
                chatArea.append(message + "\n");
            }
            byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length, group, port);
            socket.send(datagram);
        } catch (IOException e) {
            // If the socket is closed, we don't need to show an error.
            if (!socket.isClosed()) {
                showError("Send Error", "Failed to send message: " + e.getMessage());
            }
        }
    }

    /**
     * Gracefully shuts down the application.
     */
    private void shutdown() {
        int choice = JOptionPane.showConfirmDialog(this, "Are you sure you want to exit?", "Exit Confirmation", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            finished = true;
            try {
                if (socket != null && !socket.isClosed()) {
                    sendMessage(name + " has left the chat.", false);
                    socket.leaveGroup(group);
                    socket.close();
                }
            } catch (IOException e) {
                // Log error if needed, but the application is closing anyway.
                System.out.println("Error during shutdown: " + e.getMessage());
            } finally {
                System.exit(0);
            }
        }
    }

    /**
     * Displays an error message in a dialog box.
     * @param title The title of the dialog box.
     * @param message The error message to display.
     */
    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        // --- Get User Input ---
        String host = JOptionPane.showInputDialog(null, "Enter multicast host:", "239.0.0.0");
        if (host == null || host.trim().isEmpty()) return;

        String portStr = JOptionPane.showInputDialog(null, "Enter port number (1024-65535):", "1234");
        if (portStr == null || portStr.trim().isEmpty()) return;

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1024 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "Invalid port number.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String username = JOptionPane.showInputDialog(null, "Enter your name:", "User" + (int)(Math.random() * 1000));
        if (username == null || username.trim().isEmpty()) return;

        // --- Start Application ---
        SwingUtilities.invokeLater(() -> {
            GroupChatGUI gui = new GroupChatGUI();
            if (gui.initNetwork(host, port, username)) {
                gui.setVisible(true);
            } else {
                // If network init fails, the error is shown and we can exit.
                System.exit(1);
            }
        });
    }
}

/**
 * A runnable class that continuously reads messages from a multicast socket
 * and appends them to a JTextArea.
 */
class ReadThread implements Runnable {
    private final MulticastSocket socket;
    private final InetAddress group;
    private final int port;
    private final JTextArea chatArea;
    private static final int MAX_LEN = 1000;

    ReadThread(MulticastSocket socket, InetAddress group, int port, JTextArea chatArea) {
        this.socket = socket;
        this.group = group;
        this.port = port;
        this.chatArea = chatArea;
    }

    @Override
    public void run() {
        while (!GroupChatGUI.finished) {
            byte[] buffer = new byte[MAX_LEN];
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length, group, port);
            try {
                socket.receive(datagram);
                String message = new String(buffer, 0, datagram.getLength(), StandardCharsets.UTF_8);

                // Update the GUI on the Event Dispatch Thread
                SwingUtilities.invokeLater(() -> chatArea.append(message + "\n"));

            } catch (SocketException se) {
                // This exception is expected when the socket is closed during shutdown
                if (!GroupChatGUI.finished) {
                    System.out.println("Socket closed unexpectedly.");
                }
            } catch (IOException e) {
                if (!GroupChatGUI.finished) {
                    System.out.println("Error reading from socket: " + e.getMessage());
                }
            }
        }
    }
}
