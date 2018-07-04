import java.io.FileWriter;
import java.net.InetAddress;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Receiver {

    private static String hostNameOfEmulator;
    private static int receiveDataUdpPort;
    private static int sendAckUdpPort;
    private static String fileName;
    private static InetAddress emulatorHostAddress;

    // File to output
    private static FileWriter dataFile;
    private static FileWriter arrivalLog;

    // UDP Socket
    private static DatagramSocket receiveDataUdpSocket;
    private static DatagramSocket sendAckUdpSocket;
    private static byte[] packetBytes;

    // Error reporting
    private static void errorReport(int error) {
        switch (error) {
            case 1:
                System.err.println("Incorrect command line arguments.");
                break;
            case 2:
                System.err.println("Can not create log files.");
                break;
            case 3:
                System.err.println("UDP initialization error.");
                break;
            case 4:
                System.err.println("Packets initialization error.");
                break;
            case 5:
                System.err.println("Receving wrong packets TYPES.");
                break;
            case 6:
                System.err.println("Error while executing packets received.");
                break;
        }
		System.exit(1);
    }

    // read and set commandline arguments
    private static void setCommandLineArguments(String []args) {
        if (args.length != 4) {         // Need 4 arguments
            errorReport(1);
        }
        try {
            hostNameOfEmulator = args[0];
            sendAckUdpPort = Integer.valueOf(args[1]);
            receiveDataUdpPort = Integer.valueOf(args[2]);
            fileName = args[3];
            emulatorHostAddress = InetAddress.getByName(hostNameOfEmulator);
        } catch (Exception e) {
            errorReport(1);
        }
    }

    // Clear file if file existed
    private static void clearExistingLog() throws Exception {
        PrintWriter fileWriter = new PrintWriter(fileName);
        PrintWriter ackWriter = new PrintWriter("arrival.log");
        fileWriter.print("");
        fileWriter.close();
		ackWriter.print("");
		ackWriter.close();
    }

    // Create files
    private static void createWritingLogs() {
        try {
            clearExistingLog();                         // Clear
            dataFile = new FileWriter(fileName);        // Create file
            arrivalLog = new FileWriter("arrival.log");     // Create arrival log
        } catch (Exception e) {
            errorReport(2);
        }
    }

    // Close files
    private static void closeFiles() throws Exception {
        dataFile.close();
        arrivalLog.close();
    }

    // Initialize UDP sockets
    private static void initializeUDP() {
        try {
            receiveDataUdpSocket = new DatagramSocket(receiveDataUdpPort);
            sendAckUdpSocket = new DatagramSocket();
            packetBytes = new byte[512];
        } catch (Exception e) {
            errorReport(3);
        }
    }

    // send Acknowledgement for the packet recieved from the sender
    private static void sendAcknowledgement(int ackSeqNum, DatagramPacket ackData) throws Exception {
        packet ackPacket = packet.createACK(ackSeqNum);
        byte[] ackBytes = ackPacket.getUDPdata();
        ackData = new DatagramPacket(ackBytes, ackBytes.length,emulatorHostAddress, sendAckUdpPort);
		sendAckUdpSocket.send(ackData);
    }

    // Send EOT ack to sender to end connection
    private static void sendEOTAcknowledgement(int ackSeqNum) throws Exception {
        packet eotPacket = packet.createEOT(ackSeqNum);
        byte[] eotBytes = eotPacket.getUDPdata();
		sendAckUdpSocket.send(
            new DatagramPacket(eotBytes, eotBytes.length,emulatorHostAddress, sendAckUdpPort)
            );
    }

    // Open port to receive data from sender and send ack back to sender
    private static void acknowledgementWaiting() {
        int curSeqNum = 0;
        DatagramPacket ackData = null;
        DatagramPacket dp = new DatagramPacket(packetBytes, packetBytes.length);
        Boolean eotSend = false;
        try {
            while (!eotSend) {
                receiveDataUdpSocket.receive(dp);                       // Wait for data
                packet p = packet.parseUDPdata(packetBytes);
                int receivedSeqPacket = p.getSeqNum();
                arrivalLog.write( receivedSeqPacket + "\n" );
                switch (p.getType()) {
                    case 1 :                               // Data type
                        if (receivedSeqPacket == curSeqNum) {               // Correct seq packet received
                            sendAcknowledgement(curSeqNum, ackData);        // Send acknowledegement to sender
                            ++curSeqNum;
                            curSeqNum %= 32;                              // Increment seq num
                            String s = new String(p.getData());
                            dataFile.write(s);                              // Write to file
                        } else if (ackData != null) {                       // Wrong seq received
                            sendAckUdpSocket.send(ackData);                 // Resend previous ack        
                        }
                        break;
                    case 2 :                                // EOT type
                        sendEOTAcknowledgement(curSeqNum);                  // Send Ack
                        eotSend = true;                                     // Stop looping
                        break;
                    case 0 :
                    default :
                        errorReport(5);
                }
            }
        } catch (Exception e) {
            errorReport(6);
        }
    }

    public static void main(String [] args) throws Exception {
        setCommandLineArguments(args);          // Read command line arguments
        createWritingLogs();                    // create file and log
        initializeUDP();                        // intialize udp sockets
        acknowledgementWaiting();               // wait for data and send acknowledgements
        closeFiles();                           // Close files
    }
}