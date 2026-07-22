package com.smechain.state;

public class InvoiceRecord {
    public String invoiceId;
    public String issuerBusinessId;
    public String counterpartyBusinessId;
    public long amountCents;
    public long issuedAt;
    public long dueDate;
    public InvoiceStatus status;
    public Long acceptedAt;
    public Long deliveredAt;
    public Long paidAt;
    public String memo;
    public String disputeReason;
    public String disputeOutcome;
    public String resolverBusinessId;

    public InvoiceRecord copy() {
        InvoiceRecord r = new InvoiceRecord();
        r.invoiceId = invoiceId;
        r.issuerBusinessId = issuerBusinessId;
        r.counterpartyBusinessId = counterpartyBusinessId;
        r.amountCents = amountCents;
        r.issuedAt = issuedAt;
        r.dueDate = dueDate;
        r.status = status;
        r.acceptedAt = acceptedAt;
        r.deliveredAt = deliveredAt;
        r.paidAt = paidAt;
        r.memo = memo;
        r.disputeReason = disputeReason;
        r.disputeOutcome = disputeOutcome;
        r.resolverBusinessId = resolverBusinessId;
        return r;
    }
}
