package com.smechain.chain;

import java.util.ArrayList;
import java.util.List;

public class Block {
    public long height;
    public String blockHash;
    public BlockHeader header;
    public List<Transaction> transactions = new ArrayList<>();

    public long cumulativeWork() {
        return height + 1;
    }
}