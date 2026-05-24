package com.smechain.crypto;

import java.security.*;
import java.security.spec.*;
import java.util.Base64;

public final class KeyUtil {
    private KeyUtil(){}

    public static KeyPair newEd25519() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String pubKeyToB64(PublicKey pk) {
        return Base64.getEncoder().encodeToString(pk.getEncoded());
    }

    public static String privKeyToB64(PrivateKey sk) {
        return Base64.getEncoder().encodeToString(sk.getEncoded());
    }

    public static PublicKey pubKeyFromB64(String b64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            return kf.generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static PrivateKey privKeyFromB64(String b64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] signEd25519(PrivateKey sk, byte[] msg) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(sk);
            sig.update(msg);
            return sig.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean verifyEd25519(PublicKey pk, byte[] msg, byte[] signature) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(pk);
            sig.update(msg);
            return sig.verify(signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String businessIdFromPubKey(PublicKey pk) {
        // business id = RIPEMD160(SHA256(pubkey)) in Bitcoin style;
        // we keep it simple: SHA256 hex truncated
        String h = HashUtil.toHex(HashUtil.sha256(pk.getEncoded()));
        return h.substring(0, 40);
    }
}
