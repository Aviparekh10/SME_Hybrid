package com.smechain.state;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class State {
    public Map<String, InvoiceRecord> invoices = new ConcurrentHashMap<>();
    public Map<String, Long> senderNonces = new ConcurrentHashMap<>();

    public State copy() {
        State s = new State();
        for (Map.Entry<String, InvoiceRecord> entry : invoices.entrySet()) {
            s.invoices.put(entry.getKey(), entry.getValue().copy());
        }
        s.senderNonces.putAll(senderNonces);
        return s;
    }
}
