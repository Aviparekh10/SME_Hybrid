package com.smechain.v2;

import com.smechain.chain.Transaction;
import com.smechain.chain.TxType;

public class BasicComplianceEngine implements ComplianceEngine {
    private final long maxAmountCents;

    public BasicComplianceEngine(long maxAmountCents) {
        this.maxAmountCents = maxAmountCents;
    }

    @Override
    public String check(Transaction tx) {
        // Example checks: reject huge invoices unless KYC attested (simplified).
        if (tx.type == TxType.ISSUE_INVOICE) {
            Object amt = tx.payload.get("amountCents");
            if (amt instanceof Number) {
                Number n = (Number) amt;
                if (n.longValue() > maxAmountCents) {
                    return "amount_exceeds_limit_needs_kyc";
                }
            }
        }
        return null;
    }
}
