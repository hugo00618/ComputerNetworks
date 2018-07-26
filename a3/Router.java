import java.nio.*;
import java.io.*;
import java.net.*;
import java.util.*;

class Edge {
    int value;
    Boolean visited;

    public Edge() {
        this.value = Integer.MAX_VALUE;
        this.visited = false;
    }
}

class Route
{
    int my_neighbor = -1;
    int link;
    int cost;

    public Route(int link, int cost)
    {
        this.link = link;
        this.cost = cost;
    }
}

class pkt_HELLO
{
     int router_id; /* id of the router who sends the HELLO PDU */
     int link_id; /* id of the link through which it is sent */

    public pkt_HELLO( int router_id,  int link_id) {
        this.router_id = router_id;
        this.link_id = link_id;
    }

    public byte[] getDataInByte() {
        ByteBuffer b = ByteBuffer.allocate(8);
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(this.router_id);
        b.putInt(this.link_id);
        return b.array();
    }
}

class pkt_LSPDU
{
     int sender; /* sender of the LS PDU */
     int router_id; /* router id */
     int link_id; /* link id */
     int cost; /* cost of the link */
     int via; /* id of the link through which the LS PDU is sent */

    public pkt_LSPDU( int sender,  int router_id,  int link_id, int cost,  int via) {
        this.sender = sender;
        this.router_id = router_id;
        this.link_id = link_id;
        this.cost = cost;
        this.via = via;
    } 

    public byte[] getDataInByte() {
        ByteBuffer b = ByteBuffer.allocate(20);
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(this.sender);
        b.putInt(this.router_id);
        b.putInt(this.link_id);
        b.putInt(this.cost);
        b.putInt(this.via);
        return b.array();
    }

}

class pkt_INIT
{
     int router_id; /* id of the router that send the INIT PDU */

    public pkt_INIT( int router_id) {
        this.router_id = router_id;
    }

    public byte[] getDataInByte() {
        ByteBuffer b = ByteBuffer.allocate(4);
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(this.router_id);
        return b.array();
    }

}

class link_cost
{
     int link; /* link id */
     int cost; /* associated cost */

    public link_cost( int link,  int cost) {
        this.link = link;
        this.cost = cost;
    }
}

class circuit_DB
{
    static final int nbr_router = 5;
    int nbr_link; /* number of links attached to a router */
    link_cost linkcost[] = new link_cost[nbr_router];
    /* we assume that at most NBR_ROUTER links are attached to each router */
    public circuit_DB() { 

    }
}

class router 
{
    private static final int HELLO_PACKET_SIZE = 8;
    private static final int LSPDU_PACKET_SIZE = 20;
    private static final int NBR_ROUTER = 5;

    private static int routerId, routerPort;
    private static int emulatorPort;
    private static InetAddress emulatorHost;
    private static PrintWriter logFile;
    private static circuit_DB c_db;
    
    private static DatagramSocket routerSocket;

    private static int routing[];
    private static int minPath[][]; 
    private static ArrayList<Route> routes;
    private static ArrayList<pkt_LSPDU> lspduList;
    private static HashMap<Integer, Integer> routingMap;

    // Parse byte into Hello packet
    private static pkt_HELLO parese_pkt_HELLO(byte[] data) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(data);
		b.order(ByteOrder.LITTLE_ENDIAN);
		int router = b.getInt();
		int link = b.getInt();
        pkt_HELLO pkt = new pkt_HELLO( router, link );
		return pkt;
    }

    // Parse byte into LSPDU packet
    private static pkt_LSPDU parese_pkt_LSPDU(byte[] data) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(data);
		b.order(ByteOrder.LITTLE_ENDIAN);
		int sender = b.getInt(); /* sender of the LS PDU */
        int router_id = b.getInt(); /* router id */
        int link_id = b.getInt(); /* link id */
        int cost = b.getInt(); /* cost of the link */
        int via = b.getInt();
        pkt_LSPDU pkt = new pkt_LSPDU( sender, router_id, link_id, cost, via );
		return pkt;
    }

    // Parse byte into circuit_db
    private static circuit_DB parese_DB(byte[] data) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(data);
		b.order(ByteOrder.LITTLE_ENDIAN);
        circuit_DB db = new circuit_DB();
        db.nbr_link = b.getInt();
        for (int i = 0; i < db.nbr_link; ++i) {
            int link = b.getInt();
            int cost = b.getInt();
            db.linkcost[i] = new link_cost(link, cost);
        }
		return db;
    }

    // Clear used file and create new one
    private static void clearLogfile() throws Exception {
        logFile = new PrintWriter("router" + routerId + ".log");
        logFile.print("");
        logFile.close();
        logFile = new PrintWriter("router" + routerId + ".log");
    }

    // set and check commandline arguments 
    private static void setCommandLineArguments(String[] argv) throws Exception {
        if (argv.length != 4) throw new Exception();
        routerId = Integer.valueOf(argv[0]);
        emulatorHost = InetAddress.getByName(argv[1]);
        emulatorPort = Integer.valueOf(argv[2]);
        routerPort = Integer.valueOf(argv[3]);
    }

    // Initilize member fields
    private static void initialize() throws Exception {
        routing = new int[NBR_ROUTER];
        minPath = new int[NBR_ROUTER][NBR_ROUTER];
        routerSocket = new DatagramSocket(routerPort);
        routes = new ArrayList<Route>();
        lspduList = new ArrayList<pkt_LSPDU>();
        routingMap = new HashMap<Integer, Integer> ();
        for (int i = 0; i < NBR_ROUTER; ++i) {
            routing[i] = -1;
            for (int j = 0; j < NBR_ROUTER; ++j) {
                minPath[i][j] = 0;
            }
        }
        sendInit();                                         // send init packet
    }

    // Send init packet to emulator
    private static void sendInit() throws Exception {
        pkt_INIT init = new pkt_INIT(routerId);
        byte[] pkt = init.getDataInByte();
        routerSocket.send(new DatagramPacket(pkt, pkt.length, emulatorHost, emulatorPort));
        logFile.printf("R%d sended a INIT: router_id %d \n", routerId, init.router_id);
		logFile.flush();
    }

    // Send hello packet
    private static void sendHello() throws Exception {
        int links = c_db.nbr_link;
        for (int i = 0; i < links; ++i) {
            pkt_HELLO hello = new pkt_HELLO(routerId, c_db.linkcost[i].link);
            byte[] pkt = hello.getDataInByte();
		    routerSocket.send(new DatagramPacket(pkt, pkt.length, emulatorHost, emulatorPort));
            logFile.printf("R%d sended a HELLO to router_id %d, link_id %d \n", 
                routerId, hello.router_id, hello.link_id);
		    logFile.flush();
        }
    }

    // Send LSPDU packet
    private static void sendLSPDUPacket(pkt_LSPDU pktLSPDU) throws Exception {
        byte[] pkt = pktLSPDU.getDataInByte();
        routerSocket.send(new DatagramPacket(pkt, pkt.length, emulatorHost, emulatorPort));
        logFile.printf("R%d sended a LSPDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n",
            routerId, pktLSPDU.sender, pktLSPDU.router_id, pktLSPDU.link_id, pktLSPDU.cost, pktLSPDU.via);
		logFile.flush();
    }

    // Get circuit database from emulator
    private static void receiveCircuitDB() throws Exception {
        byte[] pkt_bytes = new byte[512];
        DatagramPacket pkt = new DatagramPacket(pkt_bytes, pkt_bytes.length);
        routerSocket.receive(pkt);
        c_db = parese_DB(pkt_bytes);
        int links = c_db.nbr_link;
        for (int i = 0; i < links; ++i) {
            link_cost link = c_db.linkcost[i];
            pkt_LSPDU lspdu = new pkt_LSPDU(0, routerId, link.link, link.cost, 0);
            lspduList.add(lspdu);                               // Save lspdu packets
            Route route = new Route(link.link, link.cost);
            routes.add(route);                                  // Add routings
            printTopology();                                    // Print tology database
        }
        sendHello();                                            // send hello
    }

    // Recevied a hello packet
    private static void receiveHelloPacket(byte[] pkt_bytes) throws Exception {
        pkt_HELLO pkt = parese_pkt_HELLO(pkt_bytes);
        int router_id = pkt.router_id;
        int link_id = pkt.link_id;

        // Handle hello packets received
        for (int i = 0; i < lspduList.size(); ++i) {
            lspduList.get(i).sender = router_id; lspduList.get(i).via = link_id;
            sendLSPDUPacket(lspduList.get(i));
        }

        // recalculate shortest path in routing table
        for (int i = 0; i < routes.size(); ++i) {
            if (routes.get(i).link == link_id) {
                routes.get(i).my_neighbor = router_id;
                routingMap.put(link_id, router_id);
                if (routerId != routes.get(i).my_neighbor
                    && minPath[routes.get(i).my_neighbor - 1][routerId - 1] != routes.get(i).cost) {
                    minPath[routes.get(i).my_neighbor - 1][routerId - 1] = routes.get(i).cost;
                    minPath[routerId - 1][routes.get(i).my_neighbor - 1] = routes.get(i).cost;
                    dijkstraAlgorithm();
                }
            }
        }
        logFile.printf("R%d received a HELLO: router_id %d, link_id %d\n ", routerId, router_id, link_id);
        logFile.flush();
    }

    // Recevied LSDPU packet
    private static void receiveLSDPUPacket(byte[] pkt_bytes) throws Exception {
        pkt_LSPDU pkt = parese_pkt_LSPDU(pkt_bytes);
        int router_id = pkt.router_id;
        int link_id = pkt.link_id;
        int list_size = lspduList.size();

        for (int i = 0; i < list_size; ++i) {
            // Check if known
            if ( link_id == lspduList.get(i).link_id && lspduList.get(i).router_id == router_id) return;
        }

        logFile.printf("R%d received a LSPDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n",
            routerId, pkt.sender, pkt.router_id, pkt.link_id, pkt.cost, pkt.via);
        logFile.flush();
        
        lspduList.add(pkt);                 // Add to list

        // Recalculate shortest path if needed
        if (!routingMap.containsKey(link_id)) {
            routingMap.put(link_id, router_id);
        } else if (router_id != routingMap.get(link_id)) {
            int id = routingMap.get(link_id);
            minPath[router_id - 1][id - 1] = minPath[id - 1][router_id - 1] = pkt.cost;
            dijkstraAlgorithm();
        }

        // Send lspdu packet to all routing path
        for (int i = 0; i < routes.size(); ++i) {
            if (link_id != routes.get(i).link) {
                pkt.router_id = routerId; pkt.via = routes.get(i).link;
                sendLSPDUPacket(pkt);
            }
        }

        logFile.printf(" # Topology database \n");
        logFile.flush();
        printTopology();
    }

    // Receive loop to receive packets from other routes
    private static void receivePackets() throws Exception {
        for ( ; ; ) {
            byte[] pkt_bytes = new byte[512];
            DatagramPacket pkt = new DatagramPacket(pkt_bytes, pkt_bytes.length);
            routerSocket.receive(pkt);
            switch (pkt.getLength()) {
                case HELLO_PACKET_SIZE:
                    receiveHelloPacket(pkt_bytes); 
                    break;
                case LSPDU_PACKET_SIZE:
                    receiveLSDPUPacket(pkt_bytes);
                    break;
                default:
                    break; 
            }                                                  // Receive packets
        }
    }

    // dijkstra's Algorithm inspired by dijkstra's algorithm teached in CS341
    private static void dijkstraAlgorithm() throws Exception {
        ArrayList<Edge> graph = new ArrayList<Edge>();
        for (int i = 0; i < NBR_ROUTER; ++i) {
            Edge edge = new Edge();
            graph.add(edge);
        }

        int routerIndex = routerId - 1;
        graph.get(routerIndex).value = 0;
        //graph.get(routerIndex).visited = true;

        int edgeVisited = 0;

        while (edgeVisited < graph.size()) {
            int minIndex = 0;
            int minValue = Integer.MAX_VALUE;
            for (int i = 0; i < graph.size(); ++i) {
                Edge e = graph.get(i);
                if (!e.visited && e.value <= minValue) {
                    minValue = e.value;
                    minIndex = i;
                }
            }
            Edge u = graph.get(minIndex);
            u.visited = true;
            for (int i = 0; i < graph.size(); ++i) {
                Edge e = graph.get(i);
                if (minPath[minIndex][i] == 0 || e.visited || u.value == Integer.MAX_VALUE) continue;
                if ( u.value + minPath[minIndex][i] < e.value ) {
                    e.value = u.value + minPath[minIndex][i];
                    routing[i] = routing[minIndex] == -1 ? i : routing[minIndex];
                }
            }
            edgeVisited++;
        }

        // Print Rib
        logFile.printf(" # RIB \n");
        logFile.flush();
        for (int i = 0; i < graph.size(); ++i) {
            Edge e = graph.get(i);
            int destination = routing[i] == -1 ? 0 : routing[i] + 1;    // 0 means no routing, inifinite
            if (routerId == i + 1) {                            // Local
                logFile.printf("R%d -> R%d -> local, %d \n", routerId, i + 1, e.value);    
            } else {                                            // to router out
                logFile.printf("R%d -> R%d -> R%d, %d \n", routerId, i + 1, destination, e.value);   
            }
            logFile.flush();
        }
    }

    // Print topology database
    private static void printTopology() throws Exception {
        for (int i = 0; i < NBR_ROUTER; ++i) {
            int count = 0;
            for (int j = 0; j < lspduList.size(); ++j) {
                if (lspduList.get(j).router_id - 1 == i) count++;
            }
            logFile.printf("R%d -> R%d nbr link %d \n", routerId, i + 1 ,count);    // Print entry count
            logFile.flush();
            for (int j = 0; j < lspduList.size(); ++j) {
                if (lspduList.get(j).router_id - 1 == i) {              // Print routing cost and link
                    logFile.printf("R%d -> R%d, link %d, cost %d \n",
                        lspduList.get(j).sender, 
                        lspduList.get(j).router_id, 
                        lspduList.get(j).link_id,
                        lspduList.get(j).cost);
					logFile.flush();
                }
            }
        }
    }

    public static void main(String argv[]) throws Exception {
        setCommandLineArguments(argv);                      // Read command line arguments
        clearLogfile();                                     // Create and clear file
        initialize();                                       // Initialize member fields
        receiveCircuitDB();                                 // receive db
        receivePackets();                                   // receive packets
    } 
}