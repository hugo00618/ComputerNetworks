import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Sender {

    private static final String LOG_FILE_SEQ = "seqnum.log";
    private static final String LOG_FILE_ACK = "ack.log";

    private static final int PACKET_DATA_SIZE = 500;
    private static final int PACKET_SIZE = 512;
    private static final int WINDOW_SIZE = 10;

    private static final Logger seqLogger = Logger.getLogger(Sender.class.getName());
    private static final Logger ackLogger = Logger.getLogger(Sender.class.getName());

    private static String hostAddr;
    private static int sendPort;
    private static int receivePort;
    private static String fileName;

    private static InetAddress hostIa;

    private static DatagramSocket sendSocket, receiveSocket;

    private static List<DatagramPacket> packets;

    private static int windowBase;

    private Sender() {

    }

    public static void main(String[] args) throws Exception {
        // parse input and do error check
        parseInput(args);

        // init loggers
        initLogger();

        // create packets
        initPackets();

        // init udp sockets
        initUdp();

        // start sending
        windowBase = 0;
        send();
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
        FileHandler seqFh = new FileHandler(LOG_FILE_SEQ);
        seqFh.setFormatter(new SimpleFormatter());
        seqLogger.addHandler(seqFh);

        FileHandler ackFh = new FileHandler(LOG_FILE_ACK);
        ackFh.setFormatter(new SimpleFormatter());
        ackLogger.addHandler(ackFh);
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

    private static void send() throws IOException {
        // TODO: start timer

        for (int i = 0; i < WINDOW_SIZE && windowBase + i < packets.size(); i++) {
            sendSocket.send(packets.get(windowBase + i));
            seqLogger.info(String.valueOf(windowBase + i));
        }
    }

}