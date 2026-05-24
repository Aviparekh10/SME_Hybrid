package com.smechain.p2p;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smechain.chain.Block;
import com.smechain.chain.Node;
import com.smechain.chain.Transaction;
import com.smechain.crypto.CanonicalJson;

import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;

public class P2PServer {
    private final int port;
    private final Node node;
    private final PeerManager peers;
    private final ObjectMapper mapper = CanonicalJson.MAPPER;

    private final ExecutorService pool = Executors.newCachedThreadPool();

    public P2PServer(int port, Node node, PeerManager peers) {
        this.port = port;
        this.node = node;
        this.peers = peers;
    }

    public void start() {
        pool.submit(this::listenLoop);
        // outbound dial to known peers
        pool.submit(this::outboundHelloLoop);
    }

    private void listenLoop() {
        try (ServerSocket server = new ServerSocket(port)) {
            while (true) {
                Socket s = server.accept();
                pool.submit(() -> handleConn(s));
            }
        } catch (Exception e) {
            System.err.println("P2P listen error: " + e.getMessage());
        }
    }

    private void handleConn(Socket s) {
        try (s;
             BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                P2PMessage msg = mapper.readValue(line, P2PMessage.class);
                switch (msg.type) {
                    case "HELLO" -> {
                        // no-op
                    }
                    case "NEW_TX" -> {
                        Transaction tx = mapper.convertValue(msg.body, Transaction.class);
                        try { node.submitTx(tx); } catch (Exception ignored) {}
                    }
                    case "NEW_BLOCK" -> {
                        Block b = mapper.convertValue(msg.body, Block.class);
                        node.tryAddBlock(b);
                    }
                    default -> {}
                }
            }
        } catch (Exception ignored) {}
    }

    public void broadcastTx(Transaction tx) {
        broadcast("NEW_TX", tx);
    }

    public void broadcastBlock(Block b) {
        broadcast("NEW_BLOCK", b);
    }

    private void broadcast(String type, Object body) {
        Set<String> ps = peers.allPeers();
        for (String hp : ps) {
            pool.submit(() -> sendOne(hp, type, body));
        }
    }

    private void sendOne(String hostPort, String type, Object body) {
        String[] parts = hostPort.split(":");
        if (parts.length != 2) return;
        String host = parts[0];
        int p = Integer.parseInt(parts[1]);
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, p), 800);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
            P2PMessage m = new P2PMessage();
            m.type = type;
            m.body = body;
            bw.write(mapper.writeValueAsString(m));
            bw.write("\n");
            bw.flush();
        } catch (Exception ignored) {}
    }

    private void outboundHelloLoop() {
        // periodic keepalive / peer ping
        while (true) {
            try {
                Thread.sleep(3000);
                broadcast("HELLO", "hi");
            } catch (InterruptedException ignored) {}
        }
    }
}
