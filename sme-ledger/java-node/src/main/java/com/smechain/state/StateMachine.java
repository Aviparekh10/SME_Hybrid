package com.smechain.state;

import com.smechain.chain.Transaction;
import com.smechain.chain.TxType;

import java.util.Map;

public final class StateMachine {
    private StateMachine(){}

    public static void apply(State state, Transaction tx) throws StateTransitionException {
        switch (tx.type) {
            case ISSUE_INVOICE -> applyIssue(state, tx);
            case ACCEPT_INVOICE -> applyAccept(state, tx);
            case CONFIRM_DELIVERY -> applyConfirm(state, tx);
            case PAY_INVOICE -> applyPay(state, tx);
            case CANCEL_INVOICE -> applyCancel(state, tx);
            case OPEN_DISPUTE -> applyOpenDispute(state, tx);
            case RESOLVE_DISPUTE -> applyResolveDispute(state, tx);
            default -> { /* ignore for v1 state */ }
        }
    }

    private static void applyIssue(State st, Transaction tx) throws StateTransitionException {
        Map<String,Object> p = tx.payload;
        String invoiceId = (String)p.get("invoiceId");
        if (invoiceId == null || invoiceId.isBlank()) throw new StateTransitionException("invoiceId required");
        if (st.invoices.containsKey(invoiceId)) throw new StateTransitionException("invoiceId already exists");

        String counterparty = (String)p.get("counterpartyBusinessId");
        Number amount = (Number)p.get("amountCents");
        Number due = (Number)p.get("dueDateEpochSec");
        String memo = (String)p.getOrDefault("memo","");

        if (counterparty == null || counterparty.isBlank()) throw new StateTransitionException("counterpartyBusinessId required");
        if (amount == null || amount.longValue() <= 0) throw new StateTransitionException("amountCents must be > 0");
        if (due == null || due.longValue() <= 0) throw new StateTransitionException("dueDateEpochSec required");

        InvoiceRecord r = new InvoiceRecord();
        r.invoiceId = invoiceId;
        r.issuerBusinessId = tx.senderBusinessId();
        r.counterpartyBusinessId = counterparty;
        r.amountCents = amount.longValue();
        r.issuedAt = tx.timestampEpochSec;
        r.dueDate = due.longValue();
        r.status = InvoiceStatus.ISSUED;
        r.memo = memo;
        st.invoices.put(invoiceId, r);
    }

    private static void applyAccept(State st, Transaction tx) throws StateTransitionException {
        String invoiceId = (String)tx.payload.get("invoiceId");
        InvoiceRecord r = requireInvoice(st, invoiceId);
        if (!tx.senderBusinessId().equals(r.counterpartyBusinessId)) throw new StateTransitionException("only counterparty can accept");
        if (r.status != InvoiceStatus.ISSUED) throw new StateTransitionException("must be ISSUED to accept");
        r.status = InvoiceStatus.ACCEPTED;
        r.acceptedAt = tx.timestampEpochSec;
    }

    private static void applyConfirm(State st, Transaction tx) throws StateTransitionException {
        String invoiceId = (String)tx.payload.get("invoiceId");
        InvoiceRecord r = requireInvoice(st, invoiceId);
        if (!tx.senderBusinessId().equals(r.counterpartyBusinessId)) throw new StateTransitionException("only counterparty can confirm delivery");
        if (r.status != InvoiceStatus.ACCEPTED) throw new StateTransitionException("must be ACCEPTED to confirm delivery");
        r.status = InvoiceStatus.DELIVERED;
        r.deliveredAt = tx.timestampEpochSec;
    }

    private static void applyPay(State st, Transaction tx) throws StateTransitionException {
        String invoiceId = (String)tx.payload.get("invoiceId");
        InvoiceRecord r = requireInvoice(st, invoiceId);
        if (!tx.senderBusinessId().equals(r.counterpartyBusinessId)) throw new StateTransitionException("only counterparty can pay");
        if (r.status != InvoiceStatus.DELIVERED && r.status != InvoiceStatus.ACCEPTED) {
            throw new StateTransitionException("must be ACCEPTED or DELIVERED to pay");
        }
        if (r.paidAt != null) throw new StateTransitionException("already paid");
        r.status = InvoiceStatus.PAID;
        r.paidAt = tx.timestampEpochSec;
    }

    private static void applyCancel(State st, Transaction tx) throws StateTransitionException {
        String invoiceId = (String)tx.payload.get("invoiceId");
        InvoiceRecord r = requireInvoice(st, invoiceId);
        if (!tx.senderBusinessId().equals(r.issuerBusinessId)) throw new StateTransitionException("only issuer can cancel");
        if (r.status != InvoiceStatus.ISSUED) throw new StateTransitionException("can only cancel if ISSUED (not accepted)");
        r.status = InvoiceStatus.CANCELED;
    }

    private static void applyOpenDispute(State st, Transaction tx) throws StateTransitionException {
        String invoiceId = (String)tx.payload.get("invoiceId");
        String reason = (String)tx.payload.getOrDefault("reason", "unspecified");
        InvoiceRecord r = requireInvoice(st, invoiceId);
        String s = tx.senderBusinessId();
        if (!s.equals(r.issuerBusinessId) && !s.equals(r.counterpartyBusinessId)) throw new StateTransitionException("only parties can dispute");
        if (r.status == InvoiceStatus.PAID || r.status == InvoiceStatus.CANCELED) throw new StateTransitionException("cannot dispute paid/canceled invoice");
        r.status = InvoiceStatus.DISPUTED;
        r.disputeReason = reason;
    }

    private static void applyResolveDispute(State st, Transaction tx) throws StateTransitionException {
        // v1: simple "arbiter" = any miner can resolve, but must include outcome (issuer_wins/counterparty_wins/split)
        // v2: replace with arbitration committee / staking.
        String invoiceId = (String)tx.payload.get("invoiceId");
        String outcome = (String)tx.payload.get("outcome");
        InvoiceRecord r = requireInvoice(st, invoiceId);
        if (r.status != InvoiceStatus.DISPUTED) throw new StateTransitionException("invoice not disputed");
        if (outcome == null || outcome.isBlank()) throw new StateTransitionException("outcome required");
        r.status = InvoiceStatus.RESOLVED;
        r.disputeOutcome = outcome;
    }

    private static InvoiceRecord requireInvoice(State st, String invoiceId) throws StateTransitionException {
        if (invoiceId == null || invoiceId.isBlank()) throw new StateTransitionException("invoiceId required");
        InvoiceRecord r = st.invoices.get(invoiceId);
        if (r == null) throw new StateTransitionException("unknown invoiceId");
        return r;
    }
}
