package com.smechain.chain;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Mempool {
    private final Map<String, Transaction> txs = new ConcurrentHashMap<>();
    private final Map<String, Long> seenAt = new ConcurrentHashMap<>();

    public void add(Transaction tx) {
        txs.put(tx.txId, tx);
        seenAt.put(tx.txId, Instant.now().getEpochSecond());
    }

    public boolean contains(String txId) { return txs.containsKey(txId); }

    public void removeAll(Collection<String> txIds) {
        for (String id : txIds) { txs.remove(id); seenAt.remove(id); }
    }

    public List<Transaction> topByFee(int max) {
        List<Transaction> all = new ArrayList<>(txs.values());
        all.sort((a,b) -> Long.compare(b.feeMicrounits, a.feeMicrounits));
        if (all.size() <= max) return all;
        return all.subList(0, max);
    }

    public int size() { return txs.size(); }
}
