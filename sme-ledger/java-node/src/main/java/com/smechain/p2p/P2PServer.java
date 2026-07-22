package com.smechain.p2p;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smechain.chain.Block;
import com.smechain.chain.Node;
import com.smechain.chain.PoaConfig;
import com.smechain.chain.Transaction;
import com.smechain.crypto.CanonicalJson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class P2PServer {
    private static final int MAX_P2P_MESSAGE_BYTES = 10_485_760;
    private final int port;
    private final Node node;
    private final PeerManager peers;
    private final PoaConfig config;
    private final String myValidatorPubKeyB64;
    private final ObjectMapper mapper = CanonicalJson.MAPPER;

    private final ExecutorService pool = new ThreadPoolExecutor(
            10, 100, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public P2PServer(int port, Node node, PeerManager peers, PoaConfig config, String myValidatorPubKeyB64) {
        this.port = port;
        this.node = node;
        this.peers = peers;
        this.config = config;
        this.myValidatorPubKeyB64 = myValidatorPubKeyB64;
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
                s.setSoTimeout(30_000);
                pool.submit(() -> handleConn(s));
            }
        } catch (Exception e) {
            System.err.println("P2P listen error: " + e.getMessage());
        }
    }

    private void handleConn(Socket s) {
        try (s;
             InputStream in = s.getInputStream();
             OutputStream out = s.getOutputStream()) {
            boolean authenticated = false;
            String line;
            while ((line = readLine(in)) != null) {
                if (line.isBlank()) continue;
                P2PMessage msg = mapper.readValue(line, P2PMessage.class);
                switch (msg.type) {
                    case "HELLO" -> {
                        String peerPubKey = String.valueOf(msg.body);
                        if (config == null || !config.validatorPubKeysB64.contains(peerPubKey)) {
                            System.err.println("Rejected connection from unauthorized peer");
                            return;
                        }
                        authenticated = true;
                        writeMessage(out, "HELLO", myValidatorPubKeyB64);
                    }
                    case "NEW_TX" -> {
                        if (!authenticated) return;
                        Transaction tx = mapper.convertValue(msg.body, Transaction.class);
                        try { node.submitTx(tx); } catch (Exception ignored) {}
                    }
                    case "NEW_BLOCK" -> {
                        if (!authenticated) return;
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
            s.setSoTimeout(2_000);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            writeMessage(out, "HELLO", myValidatorPubKeyB64);
            String response = readLine(in);
            if (response == null) return;
            P2PMessage hello = mapper.readValue(response, P2PMessage.class);
            if (!"HELLO".equals(hello.type)) return;
            if (!"HELLO".equals(type)) {
                writeMessage(out, type, body);
            }
        } catch (Exception ignored) {}
    }

    private void outboundHelloLoop() {
        while (true) {
            try {
                Thread.sleep(3000);
                broadcast("HELLO", myValidatorPubKeyB64);
            } catch (InterruptedException ignored) {}
        }
    }

    public boolean probeHello(String hostPort) {
        String[] parts = hostPort.split(":");
        if (parts.length != 2) return false;
        String host = parts[0];
        int p = Integer.parseInt(parts[1]);
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, p), 2_000);
            s.setSoTimeout(2_000);
            writeMessage(s.getOutputStream(), "HELLO", myValidatorPubKeyB64);
            String line = readLine(s.getInputStream());
            if (line == null) return false;
            P2PMessage msg = mapper.readValue(line, P2PMessage.class);
            return "HELLO".equals(msg.type);
        } catch (Exception e) {
            return false;
        }
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b == -1) {
                if (baos.size() == 0) return null;
                break;
            }
            if (b == '\n') break;
            baos.write(b);
            if (baos.size() > MAX_P2P_MESSAGE_BYTES) {
                System.err.println("Oversized P2P message rejected");
                throw new IOException("oversized P2P message");
            }
        }
        return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private void writeMessage(OutputStream out, String type, Object body) throws IOException {
        P2PMessage m = new P2PMessage();
        m.type = type;
        m.body = body;
        out.write(mapper.writeValueAsBytes(m));
        out.write('\n');
        out.flush();
    }
}
