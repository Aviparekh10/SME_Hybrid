package com.smechain.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smechain.chain.Block;
import com.smechain.chain.Node;
import com.smechain.chain.AlreadyMinedByRaceException;
import com.smechain.chain.PoaConfig;
import com.smechain.chain.Transaction;
import com.smechain.crypto.CanonicalJson;
import com.smechain.crypto.KeyUtil;
import com.smechain.p2p.P2PServer;
import com.smechain.p2p.PeerManager;
import com.smechain.state.StateTransitionException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RestServer {
    private static final int MAX_BODY_BYTES = 1_048_576;

    private final int port;
    private final Node node;
    private final PeerManager peers;
    private final P2PServer p2pServer;
    private final Path dataDir;
    private final PoaConfig config;
    private final PrivateKey myValidatorKey;
    private final PublicKey myValidatorPub;
    private final ObjectMapper mapper = CanonicalJson.MAPPER;
    private final ApiKeyAuth auth;
    private HttpServer server;

    public RestServer(int port, Node node, PeerManager peers, P2PServer p2pServer, Path dataDir,
                      PoaConfig config, PrivateKey myValidatorKey, PublicKey myValidatorPub) throws IOException {
        this.port = port;
        this.node = node;
        this.peers = peers;
        this.p2pServer = p2pServer;
        this.dataDir = dataDir;
        this.config = config;
        this.myValidatorKey = myValidatorKey;
        this.myValidatorPub = myValidatorPub;
        this.auth = new ApiKeyAuth(dataDir);
    }

    public void start() throws Exception {
        server = createServer();
        server.setExecutor(Executors.newFixedThreadPool(24));

        route("GET", "/v1/health", null, ex -> writeJson(ex, 200, Map.of("ok", true)));
        route("GET", "/v1/chain/head", "user", ex -> writeJson(ex, 200, node.getTip()));
        route("GET", "/v1/state/invoices", "user", ex -> writeJson(ex, 200, node.getTipStateCopy().invoices));
        route("GET", "/v1/export/snapshot", "admin", ex -> writeRaw(ex, 200, "application/json", node.exportSnapshotJson()));
        route("GET", "/v1/wallet/nonce", "user", ex -> {
            String businessId = queryParam(ex, "businessId", "");
            if (businessId.isBlank()) throw new IllegalArgumentException("businessId required");
            long nonce = node.getTipStateCopy().senderNonces.getOrDefault(businessId, 0L);
            writeJson(ex, 200, Map.of("businessId", businessId, "expectedNonce", nonce));
        });
        route("POST", "/v1/wallet/register", "user", ex -> {
            Map<String, Object> req = readJsonMap(ex);
            String pubKeyB64 = stringField(req, "pubKeyB64");
            writeJson(ex, 200, Map.of(
                    "businessId", KeyUtil.businessIdFromPubKey(KeyUtil.pubKeyFromB64(pubKeyB64)),
                    "pubKeyB64", pubKeyB64
            ));
        });
        route("POST", "/v1/tx/submit", "user", ex -> {
            Transaction tx = readJson(ex, Transaction.class);
            if (tx.feeMicrounits < 0) throw new IllegalArgumentException("feeMicrounits must be >= 0");
            if (tx.timestampEpochSec <= 0) throw new IllegalArgumentException("invalid timestamp");
            if (tx.type == null) throw new IllegalArgumentException("type required");
            if (tx.senderPubKeyB64 == null || tx.senderPubKeyB64.isBlank()) throw new IllegalArgumentException("senderPubKeyB64 required");
            if (tx.signatureB64 == null || tx.signatureB64.isBlank()) throw new IllegalArgumentException("signatureB64 required");
            node.submitTx(tx);
            p2pServer.broadcastTx(tx);
            writeJson(ex, 200, Map.of("accepted", true, "txId", tx.txId));
        });
        route("POST", "/v1/block/produce", "admin", ex -> {
            int maxTx = Integer.parseInt(queryParam(ex, "maxTx", "200"));
            Block b = node.produceBlock(config, myValidatorKey, myValidatorPub, maxTx);
            p2pServer.broadcastBlock(b);
            writeJson(ex, 200, Map.of("produced", true, "height", b.height, "hash", b.blockHash, "txCount", b.transactions.size()));
        });
        route("POST", "/v1/peers/add", "admin", ex -> {
            Map<String, Object> req = readJsonMap(ex);
            String hostPort = stringField(req, "peer");
            if (!p2pServer.probeHello(hostPort)) {
                writeJson(ex, 400, Map.of("error", "peer_not_responding_to_hello"));
                return;
            }
            peers.addPeer(hostPort);
            writeJson(ex, 200, Map.of("ok", true, "peers", peers.allPeers()));
        });
        route("POST", "/v1/apikeys/issue", "admin", ex -> {
            String apiKey = auth.issueUserKey();
            writeJson(ex, 200, Map.of("apiKey", apiKey, "role", "user"));
        });
        route("POST", "/v1/admin/validators/add", "admin", ex -> {
            Principal principal = (Principal) ex.getAttribute("principal");
            Map<String, Object> req = readJsonMap(ex);
            String pubKeyB64 = stringField(req, "pubKeyB64");
            boolean added = config.addValidator(pubKeyB64);
            logAdminChange(principal, "add_validator", pubKeyB64);
            writeJson(ex, added ? 200 : 400, Map.of("ok", added, "validators", config.snapshotValidators()));
        });
        route("POST", "/v1/admin/validators/remove", "admin", ex -> {
            Principal principal = (Principal) ex.getAttribute("principal");
            Map<String, Object> req = readJsonMap(ex);
            String pubKeyB64 = stringField(req, "pubKeyB64");
            boolean removed = config.removeValidator(pubKeyB64);
            logAdminChange(principal, "remove_validator", pubKeyB64);
            writeJson(ex, removed ? 200 : 400, Map.of("ok", removed, "validators", config.snapshotValidators()));
        });
        route("POST", "/v1/admin/identity/approve", "admin", ex -> {
            Principal principal = (Principal) ex.getAttribute("principal");
            Map<String, Object> req = readJsonMap(ex);
            String businessId = stringField(req, "businessId");
            boolean approved = !(req.get("approved") instanceof Boolean b) || b;
            node.updateIdentityStatus(businessId, approved);
            logAdminChange(principal, approved ? "approve_identity" : "revoke_identity", businessId);
            writeJson(ex, 200, Map.of("businessId", businessId, "approved", approved));
        });

        server.start();
    }

    private HttpServer createServer() throws Exception {
        String keystorePath = System.getProperty("keystore.path");
        String keystorePassword = System.getProperty("keystore.password");
        if (keystorePath == null || keystorePath.isBlank() || keystorePassword == null || keystorePassword.isBlank()) {
            System.err.println("WARNING: TLS disabled. Start with -Dkeystore.path and -Dkeystore.password to enable HTTPS. Local development only.");
            return HttpServer.create(new InetSocketAddress(port), 0);
        }

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(Path.of(keystorePath))) {
            ks.load(in, keystorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keystorePassword.toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(port), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        return httpsServer;
    }

    private void route(String method, String path, String requiredRole, Handler handler) {
        server.createContext(path, wrap(method, path, requiredRole, handler));
    }

    private HttpHandler wrap(String method, String path, String requiredRole, Handler handler) {
        return ex -> {
            try {
                if (!ex.getRequestMethod().equalsIgnoreCase(method)) {
                    writeJson(ex, 405, Map.of("error", "method_not_allowed"));
                    return;
                }
                if (requiredRole != null) {
                    ex.setAttribute("principal", auth.authenticateRequest(ex, requiredRole));
                }
                handler.handle(ex);
            } catch (RateLimitException e) {
                writeJson(ex, 429, Map.of("error", "rate_limited"));
            } catch (AuthException e) {
                writeJson(ex, e.statusCode, Map.of("error", e.getMessage()));
            } catch (StateTransitionException e) {
                writeJson(ex, 400, Map.of("error", e.getMessage()));
            } catch (AlreadyMinedByRaceException e) {
                Block b = e.getCommittedBlock();
                writeJson(ex, 200, Map.of(
                        "produced", false,
                        "alreadyMinedByConcurrentProducer", true,
                        "height", b.height,
                        "hash", b.blockHash,
                        "txCount", b.transactions.size(),
                        "message", e.getMessage()
                ));
            } catch (IllegalStateException e) {
                if (path.equals("/v1/block/produce") && "No transactions in mempool — block production skipped".equals(e.getMessage())) {
                    writeJson(ex, 400, Map.of("error", "no_pending_transactions"));
                } else {
                    writeJson(ex, 400, Map.of("error", e.getMessage()));
                }
            } catch (IllegalArgumentException e) {
                writeJson(ex, 400, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                String errorId = UUID.randomUUID().toString();
                System.err.println("Internal error [" + errorId + "]: " + e.getMessage());
                writeJson(ex, 500, Map.of("error", "server_error", "errorId", errorId));
            } finally {
                ex.close();
            }
        };
    }

    private <T> T readJson(HttpExchange ex, Class<T> cls) throws IOException {
        try (InputStream is = new LimitedInputStream(ex.getRequestBody(), MAX_BODY_BYTES)) {
            return mapper.readValue(is, cls);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(HttpExchange ex) throws IOException {
        try (InputStream is = new LimitedInputStream(ex.getRequestBody(), MAX_BODY_BYTES)) {
            return mapper.readValue(is, Map.class);
        }
    }

    private void writeJson(HttpExchange ex, int code, Object obj) throws IOException {
        byte[] b = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(obj);
        setSecurityHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private void writeRaw(HttpExchange ex, int code, String contentType, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        setSecurityHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private void setSecurityHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "DENY");
        ex.getResponseHeaders().set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
    }

    private String queryParam(HttpExchange ex, String key, String def) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) return def;
        for (String part : q.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return def;
    }

    private String stringField(Map<String, Object> req, String key) {
        Object value = req.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException(key + " required");
        }
        return s;
    }

    private void logAdminChange(Principal principal, String action, String subject) {
        String masked = principal.apiKey.substring(0, Math.min(8, principal.apiKey.length())) + "***";
        System.out.println(Instant.now() + " " + principal.role + " " + masked + " " + action + " " + subject);
        // v2 should implement this via on-chain governance using a dedicated transaction type instead of a live-mutable in-memory admin endpoint.
    }

    private static class LimitedInputStream extends FilterInputStream {
        private final int maxBytes;
        private int bytesRead;

        protected LimitedInputStream(InputStream in, int maxBytes) {
            super(in);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) increment(1);
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int count = super.read(b, off, len);
            if (count > 0) increment(count);
            return count;
        }

        private void increment(int count) throws IOException {
            bytesRead += count;
            if (bytesRead > maxBytes) {
                throw new IOException("request body too large");
            }
        }
    }

    private static class AuthException extends Exception {
        private final int statusCode;

        AuthException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }

    private static class RateLimitException extends Exception {}

    private static class Principal {
        private final String apiKey;
        private final String role;

        Principal(String apiKey, String role) {
            this.apiKey = apiKey;
            this.role = role;
        }
    }

    private final class ApiKeyAuth {
        private final Map<String, String> apiKeys = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
        private final ScheduledExecutorService limiterReset = Executors.newSingleThreadScheduledExecutor();

        private ApiKeyAuth(Path dataDir) throws IOException {
            Files.createDirectories(dataDir);
            Path adminKeyFile = dataDir.resolve("admin.key");
            String adminKey = UUID.randomUUID().toString();
            Files.writeString(adminKeyFile, adminKey, StandardCharsets.UTF_8);
            System.err.println("WARNING: Admin API key written to " + adminKeyFile + " — keep it secret.");
            apiKeys.put(adminKey, "admin");
            limiterReset.scheduleAtFixedRate(requestCounts::clear, 60, 60, TimeUnit.SECONDS);
        }

        private String issueUserKey() {
            String apiKey = UUID.randomUUID().toString();
            apiKeys.put(apiKey, "user");
            return apiKey;
        }

        private Principal authenticateRequest(HttpExchange ex, String requiredRole) throws AuthException, RateLimitException {
            String apiKey = ex.getRequestHeaders().getFirst("X-API-Key");
            if (apiKey == null || apiKey.isBlank()) throw new AuthException(401, "missing_api_key");
            String role = apiKeys.get(apiKey);
            if (role == null) throw new AuthException(401, "invalid_api_key");
            AtomicInteger count = requestCounts.computeIfAbsent(apiKey, ignored -> new AtomicInteger());
            if (count.incrementAndGet() > 100) throw new RateLimitException();
            if ("admin".equals(requiredRole) && !"admin".equals(role)) throw new AuthException(403, "admin_required");
            if ("user".equals(requiredRole) && !("user".equals(role) || "admin".equals(role))) throw new AuthException(403, "user_required");
            return new Principal(apiKey, role);
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange ex) throws Exception;
    }
}
