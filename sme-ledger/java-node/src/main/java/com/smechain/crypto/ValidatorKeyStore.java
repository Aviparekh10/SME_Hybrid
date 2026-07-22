package com.smechain.crypto;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

public final class ValidatorKeyStore {
    private ValidatorKeyStore() {}

    public static KeyPair loadFromPkcs12(Path keystorePath, String alias, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(keystorePath)) {
            keyStore.load(in, password.toCharArray());
        }
        String actualAlias = alias;
        if (actualAlias == null || actualAlias.isBlank()) {
            var aliases = keyStore.aliases();
            if (!aliases.hasMoreElements()) {
                throw new IllegalStateException("no aliases found in validator keystore");
            }
            actualAlias = aliases.nextElement();
        }
        KeyStore.Entry entry = keyStore.getEntry(actualAlias, new KeyStore.PasswordProtection(password.toCharArray()));
        if (!(entry instanceof KeyStore.PrivateKeyEntry privateKeyEntry)) {
            throw new IllegalStateException("alias " + actualAlias + " does not contain a private key");
        }
        PrivateKey privateKey = privateKeyEntry.getPrivateKey();
        Certificate certificate = privateKeyEntry.getCertificate();
        PublicKey publicKey = certificate.getPublicKey();
        return new KeyPair(publicKey, privateKey);
    }

    @SuppressWarnings("unchecked")
    public static KeyPair loadOrCreateDevKeyPair(Path dataDir) throws Exception {
        Path keyFile = dataDir.resolve("genesis-keypair.json");
        Files.createDirectories(dataDir);
        if (Files.exists(keyFile)) {
            Map<String, String> kpMap = CanonicalJson.MAPPER.readValue(Files.readString(keyFile, StandardCharsets.UTF_8), Map.class);
            PublicKey pub = KeyUtil.pubKeyFromB64(kpMap.get("pubKeyB64"));
            PrivateKey priv = KeyUtil.privKeyFromB64(kpMap.get("privKeyB64"));
            return new KeyPair(pub, priv);
        }
        KeyPair keyPair = KeyUtil.newEd25519();
        Map<String, String> kpMap = Map.of(
                "pubKeyB64", KeyUtil.pubKeyToB64(keyPair.getPublic()),
                "privKeyB64", KeyUtil.privKeyToB64(keyPair.getPrivate())
        );
        Files.writeString(keyFile, CanonicalJson.MAPPER.writeValueAsString(kpMap), StandardCharsets.UTF_8);
        keyFile.toFile().setReadable(false, false);
        keyFile.toFile().setReadable(true, true);
        keyFile.toFile().setWritable(false, false);
        keyFile.toFile().setWritable(true, true);
        System.err.println("WARNING: Using dev-mode validator key from genesis-keypair.json. This is insecure for production.");
        return keyPair;
    }
}
