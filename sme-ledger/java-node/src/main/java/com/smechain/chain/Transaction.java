package com.smechain.chain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smechain.crypto.CanonicalJson;
import com.smechain.crypto.HashUtil;
import com.smechain.crypto.KeyUtil;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Transaction {
    public String txId;
    public TxType type;
    public long timestampEpochSec;
    public long feeMicrounits;

    // sender identity
    public String senderPubKeyB64;
    public String signatureB64;

    // typed payload (kept as a JSON map for flexible v1/v2 evolution)
    public Map<String, Object> payload;

    public static Transaction unsigned(TxType type, Map<String, Object> payload, long ts, long feeMicrounits, PublicKey sender) {
        Transaction tx = new Transaction();
        tx.type = type;
        tx.payload = payload;
        tx.timestampEpochSec = ts;
        tx.feeMicrounits = feeMicrounits;
        tx.senderPubKeyB64 = java.util.Base64.getEncoder().encodeToString(sender.getEncoded());
        tx.txId = tx.computeTxId();
        return tx;
    }

    public void sign(PrivateKey sk) {
        byte[] msg = canonicalBytesForSigning();
        byte[] sig = KeyUtil.signEd25519(sk, msg);
        this.signatureB64 = Base64.getEncoder().encodeToString(sig);
        this.txId = computeTxId(); // include signature for uniqueness
    }

    public boolean verifySignature() {
        try {
            PublicKey pk = KeyUtil.pubKeyFromB64(senderPubKeyB64);
            byte[] msg = canonicalBytesForSigning();
            byte[] sig = Base64.getDecoder().decode(signatureB64);
            return KeyUtil.verifyEd25519(pk, msg, sig);
        } catch (Exception e) {
            return false;
        }
    }

    public String senderBusinessId() {
        PublicKey pk = KeyUtil.pubKeyFromB64(senderPubKeyB64);
        return KeyUtil.businessIdFromPubKey(pk);
    }

    private byte[] canonicalBytesForSigning() {
        // Do NOT include signature itself.
        try {
            Map<String,Object> m = Map.of(
                    "type", type.toString(),
                    "timestampEpochSec", timestampEpochSec,
                    "feeMicrounits", feeMicrounits,
                    "senderPubKeyB64", senderPubKeyB64,
                    "payload", payload
            );
            return CanonicalJson.MAPPER.writeValueAsBytes(m);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String computeTxId() {
        try {
            // txid includes signature if present
            byte[] b = CanonicalJson.MAPPER.writeValueAsBytes(this);
            return HashUtil.toHex(HashUtil.sha256(b));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
