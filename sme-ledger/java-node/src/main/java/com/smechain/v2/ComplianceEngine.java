package com.smechain.v2;

import com.smechain.chain.Transaction;

public interface ComplianceEngine {
    // Returns null if allowed, otherwise reason string
    String check(Transaction tx);
}
