import java.util.Timer;
import java.util.TimerTask;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.InetAddress;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;


public class Sender {

    // Constants
    private static final int max_window_size = 10;

    // Arguments to be initialized
    private static String hostAddressOfEmulator;
    private static int sendDataUdpPort;
    private static int receiveAckUdpPort;
    private static String fileName;
    private static InetAddress emulatorHostAddress;

    // File to output
    static FileWriter seqnumLog;
    static FileWriter ackLog;

    // UDP socket
    static DatagramSocket sendDataSocket;
    static DatagramSocket receiveAckSocket;
    static byte[] packetBytes;
    static ArrayList<DatagramPacket> packetList;

    // Counters
    static int curSeqNum;
    static int packetCounter;

    static Timer timer;

    // Error report
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
                System.err.println("Receving wrong packets.");
                break;
            case 6:
                System.err.println("Error when sending/receiving EOT.");
                break;
            case 7:
                System.err.println("Error when closing files.");
                break;
            case 8:
                System.err.println("Error when Waiting for acknowledgements");
                break;
        }
        System.exit(1);
    }

    private static void setCommandLineArguments(String []args) {
        if (args.length != 4) {
            errorReport(1);
        }
        try {
            hostAddressOfEmulator = args[0];
            sendDataUdpPort = Integer.valueOf(args[1]);
            receiveAckUdpPort = Integer.valueOf(args[2]);
            fileName = args[3];
            emulatorHostAddress = InetAddress.getByName(hostAddressOfEmulator);
        } catch (Exception e) {
            errorReport(1);
        }
    }

    // Clear Exisiting logs
    private static void clearExistingLog() throws Exception {
        PrintWriter seqWriter = new PrintWriter("seqnum.log");
        PrintWriter ackWriter = new PrintWriter("ack.log");
        seqWriter.print("");
        seqWriter.close();
        ackWriter.print("");
        ackWriter.close();
    }

    // Create Writing Logs
    private static void createWritingLogs() {
        try {
            clearExistingLog();
            seqnumLog = new FileWriter("seqnum.log");
            ackLog = new FileWriter("ack.log");
        } catch (Exception e) {
            errorReport(2);
        }
    }

    // Close file
    private static void closeFile() {
        try {
            ackLog.close();
            seqnumLog.close();
        } catch (Exception e) {
            errorReport(7);
        }
    }

    // Initialize UDP
    private static void initializeUDP() {
        try {
            sendDataSocket = new DatagramSocket();
            receiveAckSocket = new DatagramSocket(receiveAckUdpPort);
            packetBytes = new byte[512];
        } catch (Exception e) {
            errorReport(3);
        }
    }

    // Create Packets
    private static void createPacket(String packetData) throws Exception {
        packet p = packet.createPacket(packetCounter++, packetData);    // Create packet p
        byte[] pBytes = p.getUDPdata();                             // Convert packet into Bytes
        // Add the packet to packet list
        packetList.add(new DatagramPacket(pBytes, pBytes.length, emulatorHostAddress, sendDataUdpPort));
    }

    // Initialize Packets
    private static void initializePackets() {
        try {
            packetList = new ArrayList<>();
            int c;
            FileReader file = new FileReader(fileName);
            StringBuilder packetBuilder = new StringBuilder(500);
            while ((c = file.read()) != -1) {
                packetBuilder.append((char)(c));
                if (packetBuilder.length() == 500) {
                    createPacket(packetBuilder.toString());
                    packetBuilder = new StringBuilder(500);
                }
            }
            // Create remaining packet if exist
            if (packetBuilder.length() != 0) createPacket(packetBuilder.toString());
        } catch (Exception e) {
            errorReport(4);
        }
    }

    // Timer for Go Back N Protocal
    private static void startTimer() {
        if (timer != null) {
            timer.cancel();
        }
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    sendNPackets(max_window_size);
                } catch (Exception e) {
                    errorReport(4);
                }
            }
        }, 180);
    }

    // Send N Packets
    private static void sendNPackets(int n) throws Exception {
        startTimer();
        for ( int k = curSeqNum ; k < curSeqNum + n && k < packetCounter; ++k) {
            sendDataSocket.send(packetList.get(k));
            seqnumLog.write( k % 32 + "\n");
        }
    }

    // Recalcualte position in window
    private static void recalculateSlidingWindow(int packetSeqNum) throws Exception
    {
        int curWindowBase = curSeqNum % 32;
        if (curWindowBase > 22) {              // 22 + 10 > 31, seperated window
            if (curWindowBase <= packetSeqNum && packetSeqNum <= 31) {      // First Window
                int ackSkipped = packetSeqNum - curWindowBase;
                curSeqNum += ackSkipped + 1;
                sendNPackets(ackSkipped + 1);
            } else if (0 <= packetSeqNum && packetSeqNum <= curSeqNum + max_window_size - 33) {                                                  // Second Window
                int ackSkipped = 31 - curWindowBase + 1 + packetSeqNum;
                curSeqNum += ackSkipped + 1;
                sendNPackets(ackSkipped + 1);
            }   // Ignore duplicates
        } else if (curWindowBase <= packetSeqNum
                && packetSeqNum <= curWindowBase + max_window_size) {   // base < 22, ignore duplicates
            int ackSkipped = packetSeqNum - curWindowBase;
            curSeqNum += ackSkipped + 1;
            sendNPackets(ackSkipped + 1);
        }
    }

    // Wait for acknowledgement from receiver
    private static void acknowledgementWaiting() {
        try {
            DatagramPacket ack = new DatagramPacket(packetBytes, packetBytes.length);
            for ( ;; ) {
                if (curSeqNum >= packetCounter) break;
                receiveAckSocket.receive(ack);
                packet p = packet.parseUDPdata(ack.getData());
                switch (p.getType()) {
                    case 0 :                        // Recived Ack packet
                        if (p.getSeqNum() == curSeqNum % 32) {      // Correct Order
                            ++curSeqNum;
                            sendNPackets(1);
                        } else {                                    // Out of Order
                            recalculateSlidingWindow(p.getSeqNum());
                        }
                        ackLog.write(p.getSeqNum() + "\n");         // Write to file
                        break;
                    case 1:
                    case 2:
                    default :
                        errorReport(5);
                }
            }
        } catch ( Exception e) {
            errorReport(8);
        }
    }

    // Send EOT and Close UDP connection
    private static void closeConnection() {
        try {
            // Create EOT packet and send EOT packet
            timer.cancel();
            packet eot = packet.createEOT(curSeqNum);
            byte[] eotBytes = eot.getUDPdata();
            DatagramPacket eotPacket = new DatagramPacket(eotBytes, eotBytes.length,
                    emulatorHostAddress, sendDataUdpPort);
            sendDataSocket.send(eotPacket);
            sendDataSocket.close();

            // Wait for EOT acknowledgement
            DatagramPacket ack = new DatagramPacket(packetBytes, packetBytes.length);
            for ( ; ; ) {
                receiveAckSocket.receive(ack);
                packet ackPacket = packet.parseUDPdata(ack.getData());
                if (ackPacket.getType() == 0) {
                    // Write late arriving acks
                    ackLog.write(ackPacket.getSeqNum() + "\n");
                } else if (ackPacket.getType() == 2) {
                    // close receive ack socket connection
                    receiveAckSocket.close();
                    break;
                }
            }
        } catch (Exception e) {
            errorReport(6);
        }
    }

    public static void main(String []args) throws Exception {
        curSeqNum = 0;
        packetCounter = 0;
        setCommandLineArguments(args);  // Set commandline arguments
        createWritingLogs();    //  Create output logs
        initializeUDP();        // Set up UDP
        initializePackets();    // Intialize file data into packets
        sendNPackets(max_window_size);         // Start sending packages
        acknowledgementWaiting();   // Wait for acknowledgement
        closeConnection();          // Send eot and Close connection
        closeFile();                // Close files
    }

}
