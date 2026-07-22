package com.smechain.chain;

import com.smechain.crypto.KeyUtil;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;

public class PoaBlockProducer {
    public static Block produceBlock(Block tip, List<Transaction> txs, long height,
                                     PoaConfig config, PrivateKey validatorKey, PublicKey validatorPub) {
        Block block = new Block();
        block.height = height;
        block.transactions = txs;

        BlockHeader header = new BlockHeader();
        header.prevHash = tip == null ? "0".repeat(64) : tip.blockHash;
        header.merkleRoot = Merkle.merkleRoot(txs.stream().map(tx -> tx.txId).toList());
        header.timestampEpochSec = Instant.now().getEpochSecond();
        header.validatorPublicKeyB64 = KeyUtil.pubKeyToB64(validatorPub);

        String expectedValidator = config.expectedValidatorAtHeight(height);
        if (!expectedValidator.equals(header.validatorPublicKeyB64)) {
            throw new IllegalStateException("validator is out of turn for height " + height);
        }

        header.signHeader(validatorKey);
        block.header = header;
        block.blockHash = header.headerHash();
        return block;
    }
}
