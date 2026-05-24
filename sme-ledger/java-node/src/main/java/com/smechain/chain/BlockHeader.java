package com.smechain.chain;

import com.smechain.crypto.CanonicalJson;
import com.smechain.crypto.HashUtil;

import java.util.Map;

public class BlockHeader {
    public String prevHash;
    public String merkleRoot;
    public long timestampEpochSec;
    public long difficulty; // leading zero bits approximation by hex zeros (v1 simplification)
    public long nonce;

    public String headerHash() {
        try {
            Map<String,Object> m = Map.of(
                    "prevHash", prevHash,
                    "merkleRoot", merkleRoot,
                    "timestampEpochSec", timestampEpochSec,
                    "difficulty", difficulty,
                    "nonce", nonce
            );
            byte[] b = CanonicalJson.MAPPER.writeValueAsBytes(m);
            return HashUtil.toHex(HashUtil.sha256(b));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean meetsDifficulty(String hashHex) {
        // difficulty is number of leading hex zeros required (simple, fast)
        int zeros = (int) difficulty;
        if (zeros <= 0) return true;
        for (int i=0;i<zeros && i<hashHex.length();i++) {
            if (hashHex.charAt(i) != '0') return false;
        }
        return true;
    }
}
