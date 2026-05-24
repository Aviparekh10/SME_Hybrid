package com.smechain;

import com.smechain.api.RestServer;
import com.smechain.chain.Node;
import com.smechain.p2p.P2PServer;
import com.smechain.p2p.PeerManager;

import java.nio.file.Path;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Map<String, String> parsed = parseArgs(args);

        int port = Integer.parseInt(parsed.getOrDefault("--port", "8080"));
        int p2pPort = Integer.parseInt(parsed.getOrDefault("--p2pPort", "9000"));
        String dataDir = parsed.getOrDefault("--dataDir", "data/node");
        String peersArg = parsed.getOrDefault("--peers", "");
        List<String> peers = peersArg.isBlank() ? List.of() : Arrays.asList(peersArg.split(","));

        Node node = Node.create(Path.of(dataDir));
        PeerManager peerManager = new PeerManager(peers);

        P2PServer p2pServer = new P2PServer(p2pPort, node, peerManager);
        p2pServer.start();

        RestServer rest = new RestServer(port, node, peerManager);
        rest.start();

        System.out.println("Node running. REST http://localhost:" + port + "  P2P :" + p2pPort);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String k = args[i];
            if (k.startsWith("--")) {
                String v = (i + 1 < args.length && !args[i+1].startsWith("--")) ? args[++i] : "true";
                m.put(k, v);
            }
        }
        return m;
    }
}
