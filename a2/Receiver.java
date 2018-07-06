import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class Receiver {

    private static final String LOG_FILE_ARRIVE = "arrival.log";

    private static final int PACKET_DATA_SIZE = 500;
    private static final int PACKET_SIZE = 512;
    private static final int WINDOW_SIZE = 10;
    private static final int SeqNumModulo = 32;

    private static PrintWriter arriveWriter;

    private static int sendPort;
    private static int receivePort;
    private static String fileName;

    private static InetAddress hostIa;

    private static DatagramSocket sendSocket, receiveSocket;

    private Receiver() {

    }

    public static void main(String args[]) throws Exception {
        // parse input and do error check
        parseInput(args);

        // init log writer
        initLogger();

        // init udp sockets
        initUdp();

        // wait for packets and close sockets upon receiving EOT
        waitPackets();

        // close logger
        closeLogger();
    }

    private static void parseInput(String[] args) throws Exception {
        if (args.length != 4) {
            throw new Exception("Invalid number of input arguments");
        }

        String hostAddr = args[0];
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
        arriveWriter = new PrintWriter(LOG_FILE_ARRIVE, "UTF-8");
    }

    private static void initUdp() throws SocketException {
        sendSocket = new DatagramSocket();
        receiveSocket = new DatagramSocket(receivePort);
    }

    private static void waitPackets() throws Exception {
        int waitingSeqNum = 0;
        boolean receivedPkt0 = false;

        while (true) {
            byte[] receiveBuffer = new byte[PACKET_SIZE];
            DatagramPacket receiveDp = new DatagramPacket(receiveBuffer, PACKET_SIZE);
            receiveSocket.receive(receiveDp);
            packet p = packet.parseUDPdata(receiveDp.getData());

            if (p.getType() == 1) { // if packet
                int seqNum = p.getSeqNum();

                arriveWriter.println(seqNum);

                // update flag
                if (seqNum == 0) receivedPkt0 = true;

                System.out.println("Receive: " + seqNum);

                int ack;
                if (seqNum == waitingSeqNum) { // if order is correct
                    // set ack and increment waitingSeqNum
                    ack = seqNum;
                    waitingSeqNum++;
                    waitingSeqNum %= SeqNumModulo;
                } else { // otherwise ack last consecutive seq num received
                    ack = (waitingSeqNum + SeqNumModulo - 1) % SeqNumModulo;
                }

                // only send back ack when pkt0 has been received
                if (receivedPkt0) {
                    System.out.println("ACK: " + ack);

                    byte[] udpBytes = packet.createACK(ack).getUDPdata();
                    sendSocket.send(new DatagramPacket(udpBytes, udpBytes.length, hostIa, sendPort));
                }
            } else if (p.getType() == 2) { // if eot
                // send a eot packet back
                byte[] udpBytes = packet.createEOT(p.getSeqNum()).getUDPdata();
                sendSocket.send(new DatagramPacket(udpBytes, udpBytes.length, hostIa, sendPort));

                // close sockets and return
                sendSocket.close();
                receiveSocket.close();
                return;
            } else {
                throw new Exception("Received invalid packet");
            }
        }
    }

    private static void closeLogger() {
        arriveWriter.close();
    }

}