package com.smechain.crypto;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;

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
        // RIPEMD160(SHA256(pubkey)) — Bitcoin-style address derivation.
        byte[] sha256 = HashUtil.sha256(pk.getEncoded());
        Digest digest = new RIPEMD160Digest();
        digest.update(sha256, 0, sha256.length);
        byte[] out = new byte[20];
        digest.doFinal(out, 0);
        return HashUtil.toHex(out);
    }
}
