import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Router {

    private static final int NBR_ROUTER = 5;
    private static final int PACKET_SIZE = 512;

    private static int routerId;
    private static String nseHost;
    private static int nsePort;
    private static int routerPort;

    private static InetAddress nseIa;
    private static DatagramSocket socket;

    private static circuit_DB circuitDb;

    private static PrintWriter logger;

    private Router() {

    }

    static abstract class Packet {
        public abstract byte[] getUDPdata();
    }

    static class PKT_INIT extends Packet {
        private int routerId;

        public PKT_INIT(int routerId) {
            this.routerId = routerId;
        }

        @Override
        public byte[] getUDPdata() {
            ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(routerId);
            return buffer.array();
        }

    }

    static class circuit_DB {

        int nbr_link;
        link_cost linkcost[];

        public circuit_DB(int nbr_link, link_cost linkcost[]) {
            this.nbr_link = nbr_link;
            this.linkcost = linkcost;
        }

        public static circuit_DB parseUDPdata(byte[] UDPdata) {
            ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            int my_nbr_link = buffer.getInt();

            link_cost my_linkcost[] = new link_cost[NBR_ROUTER];
            for (int i = 0; i < my_nbr_link; i++) {
                int myLink = buffer.getInt();
                int myCost = buffer.getInt();
                my_linkcost[i] = new link_cost(myLink, myCost);
            }

            return new circuit_DB(my_nbr_link, my_linkcost);
        }
    }

    static class link_cost {
        int link;
        int cost;

        public link_cost(int link, int cost) {
            this.link = link;
            this.cost = cost;
        }
    }

    public static void main(String[] args) throws Exception {
        // read and validate input arguments
        validateInput(args);

        // initialize
        init();

        // send init to nse
        sendInit();

        // wait for circuitDB
        waitCircuitDB();
    }

    private static void validateInput(String[] args) throws Exception {
        if (args.length != 4) {
            throw new Exception("Invalid number of input arguments");
        }

        try {
            routerId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new Exception("Invalid input type for args[0]", e);
        }

        nseHost = args[1];

        try {
            nsePort = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new Exception("Invalid input type for args[2]", e);
        }

        try {
            routerPort = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            throw new Exception("Invalid input type for args[3]", e);
        }
    }

    private static void init() throws Exception {
        nseIa = InetAddress.getByName(nseHost);
        socket = new DatagramSocket(routerPort);

        logger = new PrintWriter("router" + routerId + ".log");
    }

    private static void sendInit() throws Exception {
        Packet packet = new PKT_INIT(routerId);
        byte[] udpBytes = packet.getUDPdata();
        socket.send(new DatagramPacket(udpBytes, udpBytes.length, nseIa, nsePort));

        // audit
        logger.printf("R%d sends a INIT to NSE\n", routerId);
    }

    private static void waitCircuitDB() throws Exception {
        byte[] receiveBuffer = new byte[PACKET_SIZE];
        DatagramPacket receiveDp = new DatagramPacket(receiveBuffer, PACKET_SIZE);
        socket.receive(receiveDp);
        circuitDb = circuit_DB.parseUDPdata(receiveDp.getData());

        // audit
        logger.printf("R%d receives a circuit_DB from NSE\n", routerId);
    }

    private static void log(boolean isSend, String pktType, int senderId) {
        // R1  receives  an  LS PDU:  sender  3,  router_id  7,  link_id  7,  cost  5,  via  2 
        logger.printf("R%d %s a %s: sender %d, router_id 7, link_id 7, cost 5, via 2\n",
                routerId,
                isSend ? "sends" : "receives",
                pktType,
                senderId);
    }

}