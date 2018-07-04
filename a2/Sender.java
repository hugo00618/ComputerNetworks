import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Sender {

    private static final String LOG_FILE_SEQ = "seqnum.log";
    private static final String LOG_FILE_ACK = "ack.log";

    private static final int PACKET_DATA_SIZE = 500;
    private static final int PACKET_SIZE = 512;
    private static final int WINDOW_SIZE = 10;
    private static final int SeqNumModulo = 32;

    private static PrintWriter seqWriter, ackWriter;

    private static String hostAddr;
    private static int sendPort;
    private static int receivePort;
    private static String fileName;

    private static InetAddress hostIa;

    private static DatagramSocket sendSocket, receiveSocket;

    private static List<DatagramPacket> packets;

    private static Timer timer;

    private static int windowBase;
    private static int sentHi;

    private Sender() {

    }

    public static void main(String[] args) throws Exception {
        // parse input and do error check
        parseInput(args);

        // init log writer
        initLogger();

        // create packets
        initPackets();

        // init udp sockets
        initUdp();

        // start sending
        windowBase = 0;
        sentHi = -1;
        sendWindow();

        // wait for all acks
        waitAck();

        // send EOT and close connection
        closeConnection();

        // close log writer
        closeLogger();
    }

    private static void parseInput(String[] args) throws Exception {
        if (args.length != 4) {
            throw new Exception("Invalid number of input arguments");
        }

        hostAddr = args[0];
        hostIa = InetAddress.getByName(hostAddr);

        try {
            sendPort = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new Exception("Invalid input type for args[1]");
        }

        try {
            receivePort = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new Exception("Invalid input type for args[2]");
        }

        fileName = args[3];
    }

    private static void initLogger() throws IOException {
        seqWriter = new PrintWriter(LOG_FILE_SEQ, "UTF-8");
        ackWriter = new PrintWriter(LOG_FILE_ACK, "UTF-8");
    }

    private static void initPackets() throws Exception {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(fileName));

            StringBuilder sb = new StringBuilder();
            packets = new ArrayList<>();

            int readChar;
            int seqNum = 0;
            int charCount = 0;
            // chunk file into packets
            while ((readChar = reader.read()) != -1) {
                if (charCount == PACKET_DATA_SIZE) {
                    byte[] udpBytes = packet.createPacket(seqNum, sb.toString()).getUDPdata();
                    packets.add(new DatagramPacket(udpBytes, udpBytes.length, hostIa, sendPort));
                    charCount = 0;
                    sb = new StringBuilder();
                    seqNum++;
                }
                sb.append((char) readChar);
                charCount++;
            }

            // add remaining packet if applicable
            if (charCount > 0) {
                byte[] udpBytes = packet.createPacket(seqNum, sb.toString()).getUDPdata();
                packets.add(new DatagramPacket(udpBytes, udpBytes.length, hostIa, sendPort));
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (reader != null) reader.close();
        }
    }

    private static void initUdp() throws SocketException {
        sendSocket = new DatagramSocket();
        receiveSocket = new DatagramSocket(receivePort);
    }

    /**
     * send packets in the current window frame
     * @throws IOException
     */
    private static void sendWindow() throws IOException {
        // start timer
        if (timer != null) timer.cancel();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    System.out.println("timeout");
                    sentHi = windowBase - 1;
                    sendWindow();
                } catch (IOException e) {
                    // swallow
                }
            }
        }, 10);

        for (int i = 0; i < WINDOW_SIZE; i++) {
            sendSingle(windowBase + i);
        }
    }

    /**
     * send single packet
     * @param idx packet index
     * @throws IOException
     */
    private static void sendSingle(int idx) throws IOException {
        // if idx out of bound or if idx is already sent
        if (idx >= packets.size() || idx <= sentHi) {
            return;
        }

        // send and audit
        System.out.println("Send idx: " + idx);
        sendSocket.send(packets.get(idx));
        sentHi = idx;
        seqWriter.println(idx % SeqNumModulo);
    }

    private static void waitAck() throws Exception {
        while (windowBase < packets.size()) {
            packet receivePacket = waitForPacket();

            if (receivePacket.getType() == 0) { // if received an ACK packet
                int seqNum = receivePacket.getSeqNum();
                ackWriter.println(seqNum);
                System.out.println("Ack seq: " + seqNum);
                // if in correct order, send next packet
                if (seqNum >= windowBase % SeqNumModulo) {
                    windowBase = seqNum + 1;
                    System.out.println("windowBase: " + windowBase);
                    sendWindow();
                }
            } else {
                throw new Exception("Received invalid packet");
            }
        }
    }

    /**
     * sends EOT and close both send and receive sockets
     * @throws Exception
     */
    private static void closeConnection() throws Exception {
        if (timer != null) timer.cancel();

        // send EOT and close send socket
        byte[] udpBytes = packet.createEOT(packets.size()).getUDPdata();
        sendSocket.send(new DatagramPacket(udpBytes, udpBytes.length, hostIa, sendPort));
        sendSocket.close();

        // wait for EOT's ACK and close receive socket
        packet receivePacket = waitForPacket();
        if (receivePacket.getType() == 2) { // if received EOT packet
            receiveSocket.close();
        } else {
            throw new Exception("Received invalid packet");
        }
    }

    private static packet waitForPacket() throws Exception {
        byte[] receiveBuffer = new byte[PACKET_SIZE];
        DatagramPacket receiveDp = new DatagramPacket(receiveBuffer, PACKET_SIZE);
        receiveSocket.receive(receiveDp);
        return packet.parseUDPdata(receiveDp.getData());
    }

    private static void closeLogger() {
        seqWriter.close();
        ackWriter.close();
    }
}