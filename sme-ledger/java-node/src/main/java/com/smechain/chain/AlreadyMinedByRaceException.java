package com.smechain.chain;

public class AlreadyMinedByRaceException extends IllegalStateException {
    private final Block committedBlock;

    public AlreadyMinedByRaceException(Block committedBlock) {
        super("transactions already mined by a concurrent block producer");
        this.committedBlock = committedBlock;
    }

    public Block getCommittedBlock() {
        return committedBlock;
    }
}
