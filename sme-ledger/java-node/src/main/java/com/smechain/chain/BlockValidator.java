package com.smechain.chain;

import com.smechain.state.State;
import com.smechain.state.StateMachine;
import com.smechain.state.StateTransitionException;

import java.util.List;
import java.util.stream.Collectors;

public final class BlockValidator {
    private BlockValidator(){}

    public static void validateBlock(Block prev, Block b, long expectedHeight, State stateForPrev) throws StateTransitionException {
        if (b.height != expectedHeight) throw new StateTransitionException("bad height");
        if (prev == null) {
            if (!b.header.prevHash.equals("0".repeat(64))) throw new StateTransitionException("genesis prev hash invalid");
        } else {
            if (!b.header.prevHash.equals(prev.blockHash)) throw new StateTransitionException("prev hash mismatch");
        }

        String hash = b.header.headerHash();
        if (!hash.equals(b.blockHash)) throw new StateTransitionException("blockHash mismatch");
        if (!b.header.meetsDifficulty(b.blockHash)) throw new StateTransitionException("PoW not satisfied");

        List<String> txids = b.transactions.stream().map(t -> t.txId).collect(Collectors.toList());
        String merkle = Merkle.merkleRoot(txids);
        if (!merkle.equals(b.header.merkleRoot)) throw new StateTransitionException("bad merkle root");

        // Validate txs and apply to state
        State tmp = cloneState(stateForPrev);
        for (Transaction tx : b.transactions) {
            if (tx.signatureB64 == null || !tx.verifySignature()) throw new StateTransitionException("invalid tx signature");
            StateMachine.apply(tmp, tx);
        }
        // if needed: commit tmp elsewhere; validator just checks it doesn't throw
    }

    private static State cloneState(State st) {
        // Shallow clone is okay because InvoiceRecords are replaced by deterministic transitions in this v1.
        // For strictness you'd deep copy.
        State s = new State();
        s.invoices.putAll(st.invoices);
        return s;
    }
}
