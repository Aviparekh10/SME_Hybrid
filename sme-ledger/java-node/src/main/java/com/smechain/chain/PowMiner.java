package com.smechain.chain;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public final class PowMiner {
    private PowMiner(){}

    public static Block mineNext(Block tip, List<Transaction> txs, long difficulty, long height) {
        Block b = new Block();
        b.height = height;
        b.transactions = txs;

        BlockHeader h = new BlockHeader();
        h.prevHash = tip == null ? "0".repeat(64) : tip.blockHash;
        h.timestampEpochSec = Instant.now().getEpochSecond();
        h.difficulty = difficulty;

        List<String> txids = txs.stream().map(t -> t.txId).collect(Collectors.toList());
        h.merkleRoot = Merkle.merkleRoot(txids);

        long nonce = 0;
        while (true) {
            h.nonce = nonce++;
            String hh = h.headerHash();
            if (h.meetsDifficulty(hh)) {
                b.header = h;
                b.blockHash = hh;
                return b;
            }
            // Very small yield to avoid monopolizing CPU in some environments
            if ((nonce & 0x3FFFF) == 0) Thread.yield();
        }
    }
}
