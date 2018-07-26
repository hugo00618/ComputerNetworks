import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static List<PKT_LSPDU> topologyDB;
    private static List<Neighbour> neighbours;
    private static Map<Integer, Route> rib;

    private static PrintWriter logger;

    private Router() {

    }

    static class Neighbour {
        int router_id;
        int link_id;

        public Neighbour(int router_id, int link_id) {
            this.router_id = router_id;
            this.link_id = link_id;
        }
    }

    static class Route {
        int next_id;
        int totalCost;

        public Route() {
            next_id = 0;
            totalCost = Integer.MAX_VALUE;
        }
    }

    static interface Sendable {
        public byte[] getUDPdata();

        public void logSend(int routerId);
    }

    static interface Receivable {
        public void logReceive(int routerId);
    }

    static class PKT_INIT implements Sendable {
        private int router_id;

        public PKT_INIT(int router_id) {
            this.router_id = router_id;
        }

        @Override
        public byte[] getUDPdata() {
            ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(router_id);
            return buffer.array();
        }

        @Override
        public void logSend(int routerId) {
            logger.printf("R%d sends a PKT_INIT\n", routerId);
            logger.flush();
        }
    }

    static class PKT_HELLO implements Sendable, Receivable {
        int router_id;
        int link_id;

        public PKT_HELLO(int router_id, int link_id) {
            this.router_id = router_id;
            this.link_id = link_id;
        }

        public PKT_HELLO(byte[] UDPdata) {
            ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            router_id = buffer.getInt();
            link_id = buffer.getInt();
        }

        @Override
        public byte[] getUDPdata() {
            ByteBuffer buffer = ByteBuffer.allocate(8);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(router_id);
            buffer.putInt(link_id);
            return buffer.array();
        }

        @Override
        public void logSend(int routerId) {
            logger.printf("R%d sends a PKT_HELLO: link_id %d\n",
                    routerId,
                    link_id);
            logger.flush();
        }

        @Override
        public void logReceive(int routerId) {
            logger.printf("R%d receives a PKT_HELLO: router_id %d, link_id %d\n",
                    routerId,
                    router_id,
                    link_id);
            logger.flush();
        }
    }

    static class circuit_DB implements Receivable {

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

        @Override
        public void logReceive(int routerId) {
            logger.printf("R%d receives a circuit_DB\n", routerId);
            logger.flush();
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

    static class PKT_LSPDU implements Sendable, Receivable {
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

        @Override
        public void logSend(int routerId) {
            log(true, routerId);
        }

        @Override
        public void logReceive(int routerId) {
            log(false, routerId);
        }

        private void log(boolean isSend, int routerId) {
            logger.printf("R%d %s a LS PDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n",
                    routerId,
                    isSend ? "sends" : "receives",
                    sender,
                    router_id,
                    link_id,
                    cost,
                    via);
            logger.flush();
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

        // send hello to neighbours
        sendHello();

        // wait for packets
        waitPackets();

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

        neighbours = new ArrayList<>();

        rib = new HashMap<>();
        for (int i = 1; i <= NBR_ROUTER; i++) {
            rib.put(i, new Route());
        }

        logger = new PrintWriter("router" + routerId + ".log");
    }

    private static void sendInit() throws Exception {
        sendPacket(new PKT_INIT(routerId));
    }

    private static void waitCircuitDB() throws Exception {
        // receive circuit db and audit
        DatagramPacket dp = receiveDatagramPacket();
        circuitDb = new circuit_DB(dp.getData());
        circuitDb.logReceive(routerId);

        // init lspdus
        topologyDB = new ArrayList<>();
        for (int i = 0; i < circuitDb.nbr_link; i++) {
            link_cost lc = circuitDb.linkcost[i];
            topologyDB.add(new PKT_LSPDU(routerId, 0, lc.link, lc.cost, 0));
        }
        logTopologyDB();
    }

    private static void sendHello() throws Exception {
        for (int i = 0; i < circuitDb.nbr_link; i++) {
            sendPacket(new PKT_HELLO(routerId, circuitDb.linkcost[i].link));
        }
    }

    private static void waitPackets() throws Exception {
        while (true) {
            DatagramPacket dp = receiveDatagramPacket();

            if (dp.getLength() == 8) { // PKT_HELLO
                processHello(new PKT_HELLO(dp.getData()));
            } else { // PKT_LSPDU
                processLspdu(new PKT_LSPDU(dp.getData()));
            }
        }
    }

    private static void processHello(PKT_HELLO packet) throws Exception {
        // audit
        packet.logReceive(routerId);

        // respond with topology db
        for (PKT_LSPDU lspdu : topologyDB) {
            lspdu.sender = routerId;
            lspdu.via = packet.link_id;

            sendPacket(lspdu);
        }

        // add to neighbours
        neighbours.add(new Neighbour(packet.router_id, packet.link_id));

        // update RIB

    }

    private static void processLspdu(PKT_LSPDU packet) throws Exception {
        // audit
        packet.logReceive(routerId);

        // ignore if we already know this link
        for (PKT_LSPDU lspdu : topologyDB) {
            if (lspdu.router_id == packet.router_id &&
                    lspdu.link_id == packet.link_id) {
                return;
            }
        }

        // add new entry to topology db and log
        topologyDB.add(packet);
        logTopologyDB();

        // update RIB

        // send to neighbours
        packet.sender = routerId;
        for (Neighbour neighbour : neighbours) {
            // exclude the sender of PKT_LSPDU
            if (neighbour.link_id != packet.link_id) {
                packet.via = neighbour.link_id;
                sendPacket(packet);
            }
        }
    }

    private static void closeLogger() {
        logger.close();
    }

    /**
     * send packet and audit
     *
     * @param packet
     * @throws Exception
     */
    private static void sendPacket(Sendable packet) throws Exception {
        byte[] udpBytes = packet.getUDPdata();
        socket.send(new DatagramPacket(udpBytes, udpBytes.length, nseIa, nsePort));

        packet.logSend(routerId);
    }

    private static void logTopologyDB() {
        logger.println("# Topology database");
        for (int i = 1; i <= NBR_ROUTER; i++) {
            List<PKT_LSPDU> myLinks = new ArrayList<>();
            for (PKT_LSPDU lspdu : topologyDB) {
                if (lspdu.router_id == i) myLinks.add(lspdu);
            }
            if (myLinks.size() > 0) {
                logger.printf("R%d -> R%d nbr link %d\n",
                        routerId,
                        i,
                        myLinks.size());
                for (PKT_LSPDU lspdu : myLinks) {
                    logger.printf("R%d -> R%d link %d cost %d\n",
                            routerId,
                            i,
                            lspdu.link_id,
                            lspdu.cost);
                }
            }
        }
        logger.flush();
    }

    private static void logRIB() {
        logger.println("# RIB");
        for (int i = 1; i <= NBR_ROUTER; i++) {
            if (i == routerId) {
                logger.printf("R%d -> R%d -> LOCAL, 0\n",
                        routerId,
                        routerId);
            } else {
                Route r = rib.get(i);
                logger.printf("R%d -> R%d -> R%d, %d\n",
                        routerId,
                        i,
                        r.next_id,
                        r.totalCost);
            }
        }
        logger.flush();
    }

    private static DatagramPacket receiveDatagramPacket() throws Exception {
        byte[] receiveBuffer = new byte[PACKET_SIZE];
        DatagramPacket receiveDp = new DatagramPacket(receiveBuffer, PACKET_SIZE);
        socket.receive(receiveDp);
        return receiveDp;
    }

}