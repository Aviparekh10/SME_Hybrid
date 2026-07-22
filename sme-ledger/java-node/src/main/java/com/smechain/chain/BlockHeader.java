package com.smechain.chain;

import com.smechain.crypto.CanonicalJson;
import com.smechain.crypto.HashUtil;
import com.smechain.crypto.KeyUtil;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class BlockHeader {
    public String prevHash;
    public String merkleRoot;
    public long timestampEpochSec;

    public String validatorPublicKeyB64;
    public String validatorSignature;

    public byte[] canonicalSealBytes() {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("prevHash", prevHash);
            m.put("merkleRoot", merkleRoot);
            m.put("timestampEpochSec", timestampEpochSec);
            m.put("validatorPublicKeyB64", validatorPublicKeyB64);
            return CanonicalJson.MAPPER.writeValueAsBytes(m);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String sealHash() {
        try {
            return HashUtil.toHex(HashUtil.sha256(canonicalSealBytes()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void signHeader(PrivateKey sk) {
        byte[] sig = KeyUtil.signEd25519(sk, canonicalSealBytes());
        this.validatorSignature = Base64.getEncoder().encodeToString(sig);
    }

    public boolean verifyValidatorSignature(Set<String> authorizedPubKeys) {
        try {
            if (validatorPublicKeyB64 == null || validatorPublicKeyB64.isBlank()) return false;
            if (validatorSignature == null || validatorSignature.isBlank()) return false;
            if (authorizedPubKeys == null || !authorizedPubKeys.contains(validatorPublicKeyB64)) return false;
            PublicKey pk = KeyUtil.pubKeyFromB64(validatorPublicKeyB64);
            byte[] sig = Base64.getDecoder().decode(validatorSignature);
            return KeyUtil.verifyEd25519(pk, canonicalSealBytes(), sig);
        } catch (Exception e) {
            return false;
        }
    }

    public String headerHash() {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("prevHash", prevHash);
            m.put("merkleRoot", merkleRoot);
            m.put("timestampEpochSec", timestampEpochSec);
            m.put("validatorPublicKeyB64", validatorPublicKeyB64);
            m.put("validatorSignature", validatorSignature);

            byte[] b = CanonicalJson.MAPPER.writeValueAsBytes(m);
            return HashUtil.toHex(HashUtil.sha256(b));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}