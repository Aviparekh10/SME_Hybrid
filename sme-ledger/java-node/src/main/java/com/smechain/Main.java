package com.smechain;

import com.smechain.api.RestServer;
import com.smechain.chain.PoaConfig;
import com.smechain.chain.PoaScheduler;
import com.smechain.chain.Node;
import com.smechain.crypto.KeyUtil;
import com.smechain.crypto.ValidatorKeyStore;
import com.smechain.p2p.P2PServer;
import com.smechain.p2p.PeerManager;

import java.security.KeyPair;
import java.nio.file.Path;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Map<String, String> parsed = parseArgs(args);

        int port = Integer.parseInt(parsed.getOrDefault("--port", "8080"));
        int p2pPort = Integer.parseInt(parsed.getOrDefault("--p2pPort", "9000"));
        String dataDir = parsed.getOrDefault("--dataDir", "data/node");
        String peersArg = parsed.getOrDefault("--peers", "");
        long blockInterval = Long.parseLong(parsed.getOrDefault("--blockInterval", "5"));
        String validatorsArg = parsed.getOrDefault("--validators", "");
        String validatorKeystore = parsed.getOrDefault("--validatorKeystore", "");
        String validatorAlias = parsed.getOrDefault("--validatorAlias", "");
        List<String> peers = peersArg.isBlank() ? List.of() : Arrays.asList(peersArg.split(","));
        Path dataPath = Path.of(dataDir);

        KeyPair validatorKeyPair;
        List<String> validators;
        if (!validatorKeystore.isBlank()) {
            String password = System.getenv("SMECHAIN_KEYSTORE_PASSWORD");
            if (password == null || password.isBlank()) {
                throw new IllegalStateException("SMECHAIN_KEYSTORE_PASSWORD must be set when using --validatorKeystore");
            }
            validatorKeyPair = ValidatorKeyStore.loadFromPkcs12(Path.of(validatorKeystore), validatorAlias, password);
        } else {
            System.err.println("WARNING: No validator PKCS12 keystore configured. Falling back to dev-mode key storage.");
            validatorKeyPair = ValidatorKeyStore.loadOrCreateDevKeyPair(dataPath);
        }

        String myPubKeyB64 = KeyUtil.pubKeyToB64(validatorKeyPair.getPublic());
        if (validatorsArg.isBlank()) {
            System.err.println("WARNING: --validators not provided. Using single-validator dev mode. Production must configure validators explicitly.");
            validators = List.of(myPubKeyB64);
        } else {
            validators = Arrays.stream(validatorsArg.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        }
        if (!validators.contains(myPubKeyB64)) {
            throw new IllegalStateException("local validator public key is not present in --validators");
        }
        PoaConfig poaConfig = new PoaConfig(validators, blockInterval);

        Node node = Node.create(dataPath, poaConfig, validatorKeyPair);
        PeerManager peerManager = new PeerManager(peers);

        P2PServer p2pServer = new P2PServer(p2pPort, node, peerManager, poaConfig, myPubKeyB64);
        p2pServer.start();

        RestServer rest = new RestServer(port, node, peerManager, p2pServer, dataPath, poaConfig, validatorKeyPair.getPrivate(), validatorKeyPair.getPublic());
        rest.start();

        PoaScheduler scheduler = new PoaScheduler(node, p2pServer, poaConfig, validatorKeyPair.getPrivate(), validatorKeyPair.getPublic());
        scheduler.start();

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
