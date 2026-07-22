package com.smechain.chain;

import com.smechain.state.State;
import com.smechain.state.StateMachine;
import com.smechain.state.StateTransitionException;

import java.util.List;
import java.util.stream.Collectors;

public final class BlockValidator {
    private BlockValidator(){}

    private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> identityWhitelist = new java.util.concurrent.ConcurrentHashMap<>();

    public static void updateIdentityStatus(String businessId, boolean isApproved) {
        identityWhitelist.put(businessId, isApproved);
    }

    public static boolean isIdentityApproved(String businessId) {
        return identityWhitelist.getOrDefault(businessId, false);
    }

    public static State validateBlock(Block prev, Block b, long expectedHeight, State stateForPrev, PoaConfig config) throws StateTransitionException {
        if (b.height != expectedHeight) throw new StateTransitionException("bad height");
        if (prev == null) {
            if (!b.header.prevHash.equals("0".repeat(64))) throw new StateTransitionException("genesis prev hash invalid");
        } else {
            if (!b.header.prevHash.equals(prev.blockHash)) throw new StateTransitionException("prev hash mismatch");
        }

        String hash = b.header.headerHash();
        if (!hash.equals(b.blockHash)) throw new StateTransitionException("blockHash mismatch");

        if (config == null) throw new StateTransitionException("missing PoA config");
        if (!b.header.verifyValidatorSignature(config.validatorPubKeysB64Set())) {
            throw new StateTransitionException("invalid validator signature");
        }
        String expectedValidator = config.expectedValidatorAtHeight(b.height);
        if (!expectedValidator.equals(b.header.validatorPublicKeyB64)) {
            throw new StateTransitionException("validator out of turn");
        }

        List<String> txids = b.transactions.stream().map(t -> t.txId).collect(Collectors.toList());
        String merkle = Merkle.merkleRoot(txids);
        if (!merkle.equals(b.header.merkleRoot)) throw new StateTransitionException("bad merkle root");

        State tmp = stateForPrev.copy();
        for (Transaction tx : b.transactions) {
            if (tx.signatureB64 == null || !tx.verifySignature()) throw new StateTransitionException("invalid tx signature");
            if (!identityWhitelist.getOrDefault(tx.senderBusinessId(), false)) {
                System.err.println("Compliance rejection: Sender " + tx.senderBusinessId() + " is not authorized in identity whitelist");
                throw new StateTransitionException("Compliance rejection: Sender " + tx.senderBusinessId() + " is not authorized in identity whitelist");
            }
            StateMachine.apply(tmp, tx);
        }
        return tmp;
    }
}