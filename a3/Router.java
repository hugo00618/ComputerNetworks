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

    static class PKT_HELLO extends Packet {
        int routerId;
        int linkId;

        public PKT_HELLO(int routerId, int linkId) {
            this.routerId = routerId;
            this.linkId = linkId;
        }

        @Override
        public byte[] getUDPdata() {
            ByteBuffer buffer = ByteBuffer.allocate(8);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(routerId);
            buffer.putInt(linkId);
            return buffer.array();
        }
    }

    static class circuit_DB {

        int nbr_link;
        link_cost linkcost[];

        public circuit_DB(byte[] UDPdata) {
            ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            nbr_link = buffer.getInt();

            linkcost = new link_cost[NBR_ROUTER];
            for (int i = 0; i < nbr_link; i++) {
                int myLink = buffer.getInt();
                int myCost = buffer.getInt();
                linkcost[i] = new link_cost(myLink, myCost);
            }
        }

        public circuit_DB(int nbr_link, link_cost linkcost[]) {
            this.nbr_link = nbr_link;
            this.linkcost = linkcost;
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

    static class PKT_LSPDU extends Packet {
        int sender;
        int router_id;
        int link_id;
        int cost;
        int via;

        public PKT_LSPDU(int sender, int router_id, int link_id, int cost, int via) {
            init(sender, router_id, link_id, cost, via);
        }

        public PKT_LSPDU(byte[] UDPdata) {
            ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            init(buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getInt());
        }

        private void init(int sender, int router_id, int link_id, int cost, int via) {
            this.sender = sender;
            this.router_id = router_id;
            this.link_id = link_id;
            this.cost = cost;
            this.via = via;
        }

        @Override
        public byte[] getUDPdata() {
            ByteBuffer buffer = ByteBuffer.allocate(20);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(sender);
            buffer.putInt(router_id);
            buffer.putInt(link_id);
            buffer.putInt(cost);
            buffer.putInt(via);
            return buffer.array();
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

        // send hello to neighbour
        sendHello();

        // wait for lspdu
        waitLsPdu();

        // close logger
        closeLogger();
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
        sendPacket(new PKT_INIT(routerId));

        // audit
        logger.printf("R%d sends a PKT_INIT to NSE\n", routerId);
    }

    private static void waitCircuitDB() throws Exception {
        DatagramPacket dp = receiveDatagramPacket();
        circuitDb = new circuit_DB(dp.getData());

        // audit
        logger.printf("R%d receives a circuit_DB from NSE\n", routerId);
    }

    private static void sendHello() throws Exception {
        for (int i = 0; i < circuitDb.nbr_link; i++) {
            link_cost lc = circuitDb.linkcost[i];
            sendPacket(new PKT_HELLO(routerId, lc.link));

            // audit
            logger.printf("R%d sends a PKT_HELLO to NSE: link_id %d\n",
                    routerId,
                    lc.link);
        }
    }

    private static void waitLsPdu() throws Exception {
        while (true) {
            DatagramPacket dp = receiveDatagramPacket();
            PKT_LSPDU pktLspdu = new PKT_LSPDU(dp.getData());

            System.out.println(pktLspdu.router_id);
        }
    }

    private static void closeLogger() {
        logger.close();
    }

    private static void sendPacket(Packet packet) throws Exception {
        byte[] udpBytes = packet.getUDPdata();
        socket.send(new DatagramPacket(udpBytes, udpBytes.length, nseIa, nsePort));
    }

    private static DatagramPacket receiveDatagramPacket() throws Exception {
        byte[] receiveBuffer = new byte[PACKET_SIZE];
        DatagramPacket receiveDp = new DatagramPacket(receiveBuffer, PACKET_SIZE);
        socket.receive(receiveDp);
        return receiveDp;
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