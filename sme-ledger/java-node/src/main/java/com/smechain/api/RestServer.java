package com.smechain.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smechain.chain.*;
import com.smechain.crypto.CanonicalJson;
import com.smechain.crypto.KeyUtil;
import com.smechain.p2p.PeerManager;
import com.smechain.state.StateTransitionException;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.time.Instant;
import java.util.*;

public class RestServer {
    private final int port;
    private final Node node;
    private final PeerManager peers;
    private final ObjectMapper mapper = CanonicalJson.MAPPER;
    private HttpServer server;

    public RestServer(int port, Node node, PeerManager peers) {
        this.port = port;
        this.node = node;
        this.peers = peers;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        route("GET", "/health", ex -> writeJson(ex, 200, Map.of("ok", true)));
        route("GET", "/chain/head", ex -> writeJson(ex, 200, node.getTip()));
        route("GET", "/state/invoices", ex -> writeJson(ex, 200, node.getTipStateCopy().invoices));
        route("GET", "/export/snapshot", ex -> {
            String json = node.exportSnapshotJson();
            writeRaw(ex, 200, "application/json", json);
        });

        route("POST", "/wallet/new", ex -> {
            KeyPair kp = KeyUtil.newEd25519();
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("businessId", KeyUtil.businessIdFromPubKey(kp.getPublic()));
            out.put("pubKeyB64", KeyUtil.pubKeyToB64(kp.getPublic()));
            out.put("privKeyB64", KeyUtil.privKeyToB64(kp.getPrivate()));
            writeJson(ex, 200, out);
        });

        route("POST", "/tx/submit", ex -> {
            Transaction tx = readJson(ex, Transaction.class);
            node.submitTx(tx);
            writeJson(ex, 200, Map.of("accepted", true, "txId", tx.txId));
        });

        // Convenience endpoints that build+sign txs from priv key
        route("POST", "/tx/issueInvoice", ex -> {
            Map<String,Object> req = readJsonMap(ex);
            String priv = (String) req.get("issuerPrivKeyB64");
            String counterparty = (String) req.get("counterpartyBusinessId");
            long amountCents = ((Number)req.get("amountCents")).longValue();
            long dueDate = ((Number)req.get("dueDateEpochSec")).longValue();
            String memo = (String) req.getOrDefault("memo", "");
            long fee = ((Number)req.getOrDefault("feeMicrounits", 1000)).longValue();

            var sk = KeyUtil.privKeyFromB64(priv);
            var pk = KeyUtil.pubKeyFromB64((String) req.getOrDefault("issuerPubKeyB64", "")); // optional
            if (pk == null || ((String) req.getOrDefault("issuerPubKeyB64","")).isBlank()) {
                // derive pk from sk not trivial without keeping it; require pubkey for convenience not mandatory in /tx/submit path
                // For simplicity, create from provided pubkey.
                // In practice you'd store wallet keys locally.
                throw new IllegalArgumentException("issuerPubKeyB64 required for this helper endpoint");
            }

            String invoiceId = com.smechain.crypto.HashUtil.sha256Hex(
                    KeyUtil.businessIdFromPubKey(pk) + ":" + counterparty + ":" + amountCents + ":" + dueDate + ":" + Instant.now().toEpochMilli()
            );

            Transaction tx = Transaction.unsigned(
                    TxType.ISSUE_INVOICE,
                    Map.of(
                            "invoiceId", invoiceId,
                            "counterpartyBusinessId", counterparty,
                            "amountCents", amountCents,
                            "dueDateEpochSec", dueDate,
                            "memo", memo
                    ),
                    Instant.now().getEpochSecond(),
                    fee,
                    pk
            );
            tx.sign(sk);

            node.submitTx(tx);
            writeJson(ex, 200, Map.of("accepted", true, "txId", tx.txId, "invoiceId", invoiceId));
        });

        route("POST", "/tx/simple", ex -> {
            Map<String,Object> req = readJsonMap(ex);
            String priv = (String) req.get("privKeyB64");
            String pub = (String) req.get("pubKeyB64");
            String invoiceId = (String) req.get("invoiceId");
            String type = (String) req.get("type");
            long fee = ((Number)req.getOrDefault("feeMicrounits", 1000)).longValue();
            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("invoiceId", invoiceId);
            if (req.containsKey("reason")) payload.put("reason", req.get("reason"));
            if (req.containsKey("outcome")) payload.put("outcome", req.get("outcome"));

            var sk = KeyUtil.privKeyFromB64(priv);
            var pk = KeyUtil.pubKeyFromB64(pub);

            Transaction tx = Transaction.unsigned(
                    TxType.valueOf(type),
                    payload,
                    Instant.now().getEpochSecond(),
                    fee,
                    pk
            );
            tx.sign(sk);
            node.submitTx(tx);
            writeJson(ex, 200, Map.of("accepted", true, "txId", tx.txId));
        });

        route("POST", "/mine", ex -> {
            int maxTx = Integer.parseInt(queryParam(ex, "maxTx", "200"));
            Block b = node.mineBlock(maxTx);
            writeJson(ex, 200, Map.of("mined", true, "height", b.height, "hash", b.blockHash, "txCount", b.transactions.size()));
        });

        route("POST", "/peers/add", ex -> {
            Map<String,Object> req = readJsonMap(ex);
            String hostPort = (String) req.get("peer");
            peers.addPeer(hostPort);
            writeJson(ex, 200, Map.of("ok", true, "peers", peers.allPeers()));
        });

        server.start();
    }

    private void route(String method, String path, Handler h) {
        server.createContext(path, ex -> {
            try {
                if (!ex.getRequestMethod().equalsIgnoreCase(method)) {
                    writeJson(ex, 405, Map.of("error", "method_not_allowed"));
                    return;
                }
                h.handle(ex);
            } catch (StateTransitionException e) {
                writeJson(ex, 400, Map.of("error", e.getMessage()));
            } catch (IllegalArgumentException e) {
                writeJson(ex, 400, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                writeJson(ex, 500, Map.of("error", "server_error", "detail", e.getMessage()));
            } finally {
                ex.close();
            }
        });
    }

    private <T> T readJson(HttpExchange ex, Class<T> cls) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return mapper.readValue(is, cls);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> readJsonMap(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return mapper.readValue(is, Map.class);
        }
    }

    private void writeJson(HttpExchange ex, int code, Object obj) throws IOException {
        byte[] b = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(obj);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private void writeRaw(HttpExchange ex, int code, String contentType, String body) throws IOException {
        byte[] b = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private String queryParam(HttpExchange ex, String key, String def) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) return def;
        for (String part : q.split("&")) {
            String[] kv = part.split("=");
            if (kv.length == 2 && kv[0].equals(key)) return kv[1];
        }
        return def;
    }

    @FunctionalInterface
    private interface Handler { void handle(HttpExchange ex) throws Exception; }
}
