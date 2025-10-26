import java.net.*;
import java.io.*;
import java.util.*;
public class GroupChat
{
    private static final String TERMINATE = "Exit";
    static String name;
    static volatile boolean finished = false;
    public static void main(String[] args)
    {
        if (args.length != 2)
            System.out.println("Two arguments required: <multicast-host> <port-number>");
        else
        {
            // CHANGES HAVE BEEN DONE HERE: Added validation for multicast address and port
            try
            {
                InetAddress group = InetAddress.getByName(args[0]);
                if (!group.isMulticastAddress()) {
                    System.out.println("Error: Invalid multicast address");
                    return;
                }

                int port = Integer.parseInt(args[1]);
                if (port < 1024 || port > 65535) {
                    System.out.println("Error: Port number must be between 1024 and 65535");
                    return;
                }

                Scanner sc = new Scanner(System.in);
                System.out.print("Enter your name: ");
                name = sc.nextLine();

                MulticastSocket socket = new MulticastSocket(port);

                // CHANGES HAVE BEEN DONE HERE: Set proper TTL value
                socket.setTimeToLive(1); // Changed from 0 to 1 for local subnet

                socket.joinGroup(group);
                Thread t = new Thread(new ReadThread(socket,group,port));
                t.start();

                System.out.println("Start typing messages...\n");
                try {
                    while(true)
                    {
                        String message = sc.nextLine();
                        if(message.equalsIgnoreCase(GroupChat.TERMINATE))
                        {
                            finished = true;
                            // CHANGES HAVE BEEN DONE HERE: Proper cleanup
                            socket.leaveGroup(group);
                            socket.close();
                            sc.close(); // Added scanner close
                            break;
                        }
                        message = name + ": " + message;
                        byte[] buffer = message.getBytes();
                        DatagramPacket datagram = new DatagramPacket(buffer,buffer.length,group,port);
                        socket.send(datagram);
                    }
                } finally {
                    if (!socket.isClosed()) {
                        socket.leaveGroup(group);
                        socket.close();
                    }
                    sc.close();
                }
            }
            catch(UnknownHostException uhe) {
                System.out.println("Error: Invalid multicast host");
                uhe.printStackTrace();
            }
            catch(NumberFormatException nfe) {
                System.out.println("Error: Port must be a number");
                nfe.printStackTrace();
            }
            catch(SocketException se)
            {
                System.out.println("Error creating socket");
                se.printStackTrace();
            }
            catch(IOException ie)
            {
                System.out.println("Error reading/writing from/to socket");
                ie.printStackTrace();
            }
        }
    }
}

class ReadThread implements Runnable
{
    private final MulticastSocket socket; // CHANGES HAVE BEEN DONE HERE: Made final
    private final InetAddress group;
    private final int port;
    private static final int MAX_LEN = 1000;

    ReadThread(MulticastSocket socket, InetAddress group, int port)
    {
        this.socket = socket;
        this.group = group;
        this.port = port;
    }

    @Override
    public void run()
    {
        while(!GroupChat.finished)
        {
            byte[] buffer = new byte[ReadThread.MAX_LEN];
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length, group, port);
            try
            {
                socket.receive(datagram);
                String message = new String(buffer, 0, datagram.getLength(), "UTF-8");
                if(!message.startsWith(GroupChat.name))
                    System.out.println(message);
            }
            catch(SocketException se) {
                if (!GroupChat.finished) {
                    System.out.println("Socket error: " + se.getMessage());
                }
            }
            catch(IOException e)
            {
                if (!GroupChat.finished) {
                    System.out.println("Error reading message: " + e.getMessage());
                }
            }
        }
    }
}