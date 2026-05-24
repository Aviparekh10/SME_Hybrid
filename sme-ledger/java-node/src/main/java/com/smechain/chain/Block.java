package com.smechain.chain;

import java.util.ArrayList;
import java.util.List;

public class Block {
    public long height;
    public String blockHash;
    public BlockHeader header;
    public List<Transaction> transactions = new ArrayList<>();

    public long cumulativeWork() {
        // work approximation: 16^difficulty
        // (monotonic with difficulty)
        long d = header.difficulty;
        if (d <= 0) return 1;
        // cap to avoid overflow
        long cap = Math.min(d, 15);
        long w = 1;
        for (int i=0;i<cap;i++) w *= 16L;
        return w;
    }
}
