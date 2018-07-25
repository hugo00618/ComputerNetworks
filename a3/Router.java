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
            logger.printf("R%d sends a PKT_INIT to NSE\n", routerId);
        }
    }

    static class PKT_HELLO implements Sendable {
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
            logger.printf("R%d sends a PKT_HELLO to NSE: link_id %d\n",
                    routerId,
                    link_id);
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
            logger.printf("R%d receives a circuit_DB from NSE\n", routerId);
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

        logger = new PrintWriter("router" + routerId + ".log");
    }

    private static void sendInit() throws Exception {
        Sendable packet = new PKT_INIT(routerId);
        sendPacket(packet);
        packet.logSend(routerId);
    }

    private static void waitCircuitDB() throws Exception {
        DatagramPacket dp = receiveDatagramPacket();
        circuitDb = new circuit_DB(dp.getData());
        circuitDb.logReceive(routerId);
    }

    private static void sendHello() throws Exception {
        for (int i = 0; i < circuitDb.nbr_link; i++) {
            Sendable packet = new PKT_HELLO(routerId, circuitDb.linkcost[i].link);
            sendPacket(packet);
            packet.logSend(routerId);
        }
    }

    private static void waitPackets() throws Exception {
        while (true) {
            DatagramPacket dp = receiveDatagramPacket();

            if (dp.getLength() == 8) { // PKT_HELLO
                PKT_HELLO pktHello = new PKT_HELLO(dp.getData());
                System.out.println("Hello from " + pktHello.router_id);
            } else { // PKT_LSPDU
                PKT_LSPDU pktLspdu = new PKT_LSPDU(dp.getData());
            }
        }
    }

    private static void closeLogger() {
        logger.close();
    }

    private static void sendPacket(Sendable packet) throws Exception {
        byte[] udpBytes = packet.getUDPdata();
        socket.send(new DatagramPacket(udpBytes, udpBytes.length, nseIa, nsePort));
    }

    private static DatagramPacket receiveDatagramPacket() throws Exception {
        byte[] receiveBuffer = new byte[PACKET_SIZE];
        DatagramPacket receiveDp = new DatagramPacket(receiveBuffer, PACKET_SIZE);
        socket.receive(receiveDp);
        return receiveDp;
    }

}