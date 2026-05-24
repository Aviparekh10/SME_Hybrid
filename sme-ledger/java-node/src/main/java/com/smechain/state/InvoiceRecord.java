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
}
