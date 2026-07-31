package com.smechain.chain;

@Deprecated
public final class PowMiner {
    private PowMiner() {}

    public static Block mineNext(Block tip, java.util.List<Transaction> txs, long difficulty, long height) {
        throw new UnsupportedOperationException(
                "PoW mining is disabled. SMEChain now uses Proof of Authority."
        );
    }
}