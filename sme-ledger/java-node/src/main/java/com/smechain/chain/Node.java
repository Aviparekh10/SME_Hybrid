package com.smechain.chain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smechain.crypto.CanonicalJson;
import com.smechain.crypto.KeyUtil;
import com.smechain.state.State;
import com.smechain.state.StateMachine;
import com.smechain.state.StateTransitionException;
import com.smechain.storage.BlockStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class Node {
    private final BlockStore store;
    private final Path dataDir;
    private final PoaConfig config;
    private final KeyPair genesisValidatorKeyPair;
    private final Mempool mempool = new Mempool();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ObjectMapper mapper = CanonicalJson.MAPPER;
    private final Map<String, Block> knownTips = new ConcurrentHashMap<>();
    private final ScheduledExecutorService evictScheduler = Executors.newSingleThreadScheduledExecutor();

    private State tipState = new State();
    private Block tip;

    private Node(BlockStore store, Path dataDir, PoaConfig config, KeyPair genesisValidatorKeyPair) throws IOException {
        this.store = store;
        this.dataDir = dataDir;
        this.config = config;
        this.genesisValidatorKeyPair = genesisValidatorKeyPair;
        loadOrInit();
        evictScheduler.scheduleAtFixedRate(() -> {
            List<String> evicted = mempool.evictOlderThan(86400);
            if (!evicted.isEmpty()) {
                System.out.println("Evicted " + evicted.size() + " stale txs from mempool");
            }
        }, 10, 10, TimeUnit.MINUTES);
    }

    public static Node create(Path dataDir, PoaConfig config, KeyPair genesisValidatorKeyPair) throws IOException {
        BlockStore bs = new BlockStore(dataDir);
        return new Node(bs, dataDir, config, genesisValidatorKeyPair);
    }

    private void loadOrInit() throws IOException {
        lock.writeLock().lock();
        try {
            knownTips.clear();
            Block t = store.tip();
            if (t == null) {
                saveGenesisKeyPair(genesisValidatorKeyPair);
                System.err.println("Genesis keypair saved to genesis-keypair.json — back this file up securely.");
                Block g = PoaBlockProducer.produceBlock(
                        null,
                        List.of(),
                        0L,
                        config,
                        genesisValidatorKeyPair.getPrivate(),
                        genesisValidatorKeyPair.getPublic()
                );
                store.append(g);
                tip = g;
                tipState = new State();
                knownTips.put(g.blockHash, g);
            } else {
                tip = t;
                rebuildState();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void rebuildState() throws IOException {
        State st = new State();
        long h = store.height();
        knownTips.clear();
        for (long i = 0; i <= h; i++) {
            Block b = store.getByHeight(i);
            if (b == null) break;
            knownTips.put(b.blockHash, b);
            for (Transaction tx : b.transactions) {
                try {
                    StateMachine.apply(st, tx);
                } catch (StateTransitionException e) {
                    String msg = String.format("CHAIN CORRUPTION at block %d txId %s: %s", i, tx.txId, e.getMessage());
                    System.err.println(msg);
                    throw new IOException("Chain corrupted: " + msg);
                }
            }
        }
        tipState = st;
    }

    public Block getTip() {
        lock.readLock().lock();
        try { return tip; } finally { lock.readLock().unlock(); }
    }

    public long getHeight() throws IOException {
        lock.readLock().lock();
        try { return tip == null ? -1 : tip.height; }
        finally { lock.readLock().unlock(); }
    }

    public State getTipStateCopy() {
        lock.readLock().lock();
        try { return tipState.copy(); }
        finally { lock.readLock().unlock(); }
    }

    public void submitTx(Transaction tx) throws StateTransitionException {
        if (tx.type == null) throw new StateTransitionException("type required");
        if (tx.senderPubKeyB64 == null || tx.senderPubKeyB64.isBlank()) throw new StateTransitionException("senderPubKeyB64 required");
        if (tx.signatureB64 == null || tx.signatureB64.isBlank()) throw new StateTransitionException("signature required");
        String computedTxId = tx.computeTxId();
        if (tx.txId != null && !tx.txId.equals(computedTxId)) throw new StateTransitionException("txId mismatch");
        tx.txId = computedTxId;
        if (tx.signatureB64 == null || !tx.verifySignature()) throw new StateTransitionException("invalid signature");
        if (tx.feeMicrounits < 0) throw new StateTransitionException("fee must be >= 0");

        State tmp = getTipStateCopy();
        StateMachine.apply(tmp, tx);
        mempool.add(tx);
    }

    public boolean hasTx(String txId) {
        return mempool.contains(txId);
    }

    public Block produceBlock(PoaConfig config, PrivateKey myValidatorKey, PublicKey myValidatorPub) throws IOException, StateTransitionException {
        return produceBlock(config, myValidatorKey, myValidatorPub, 200);
    }

    public Block produceBlock(PoaConfig config, PrivateKey myValidatorKey, PublicKey myValidatorPub, int maxTx) throws IOException, StateTransitionException {
        Block initialTipSnapshot = null;
        Set<String> originalCandidateTxIds = null;
        while (true) {
            Block tipSnapshot;
            State tipStateSnapshot;
            List<Transaction> txs;
            long nextHeight;

            lock.readLock().lock();
            try {
                tipSnapshot = tip;
                tipStateSnapshot = tipState.copy();
                txs = new ArrayList<>(mempool.topByFee(maxTx));
                nextHeight = tipSnapshot == null ? 0 : tipSnapshot.height + 1;
            } finally {
                lock.readLock().unlock();
            }

            if (initialTipSnapshot == null) {
                initialTipSnapshot = tipSnapshot;
            }
            if (txs.isEmpty()) {
                Block racedBlock = findCommittedRaceWinner(initialTipSnapshot, originalCandidateTxIds);
                if (racedBlock != null) {
                    throw new AlreadyMinedByRaceException(racedBlock);
                }
                throw new IllegalStateException("No transactions in mempool — block production skipped");
            }

            List<String> invalidTxIds = new ArrayList<>();
            List<Transaction> validTxs = filterValidTransactions(tipStateSnapshot, txs, invalidTxIds);
            if (!invalidTxIds.isEmpty()) {
                mempool.removeAll(invalidTxIds);
            }
            if (validTxs.isEmpty()) throw new IllegalStateException("No valid transactions in mempool — block production skipped");
            if (originalCandidateTxIds == null) {
                originalCandidateTxIds = validTxs.stream().map(tx -> tx.txId).collect(Collectors.toCollection(LinkedHashSet::new));
            }

            Block candidate = PoaBlockProducer.produceBlock(tipSnapshot, validTxs, nextHeight, config, myValidatorKey, myValidatorPub);

            lock.writeLock().lock();
            try {
                if (!Objects.equals(tip.blockHash, tipSnapshot.blockHash)) {
                    continue;
                }

                List<Transaction> stillPending = validTxs.stream()
                        .filter(tx -> mempool.contains(tx.txId))
                        .collect(Collectors.toList());
                if (stillPending.isEmpty()) {
                    Block racedBlock = findCommittedRaceWinner(initialTipSnapshot, originalCandidateTxIds);
                    if (racedBlock != null) {
                        throw new AlreadyMinedByRaceException(racedBlock);
                    }
                    throw new IllegalStateException("No transactions in mempool — block production skipped");
                }
                if (!sameTxList(validTxs, stillPending)) {
                    continue;
                }

                State validatedState = BlockValidator.validateBlock(tip, candidate, nextHeight, tipState, config);
                store.append(candidate);
                tip = candidate;
                tipState = validatedState;
                knownTips.put(candidate.blockHash, candidate);
                mempool.removeAll(stillPending.stream().map(t -> t.txId).toList());
                return candidate;
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    public boolean tryAddBlock(Block b) {
        lock.writeLock().lock();
        try {
            knownTips.put(b.blockHash, b);
            if (tip == null) {
                tipState = BlockValidator.validateBlock(null, b, 0L, new State(), config);
                store.append(b);
                tip = b;
                return true;
            }
            if (b.header.prevHash.equals(tip.blockHash)) {
                State validatedState = BlockValidator.validateBlock(tip, b, tip.height + 1, tipState, config);
                store.append(b);
                tip = b;
                tipState = validatedState;
                mempool.removeAll(b.transactions.stream().map(t -> t.txId).toList());
                return true;
            }

            Block parent = knownTips.get(b.header.prevHash);
            if (parent == null) return false;
            State parentState = replayStateToBlock(parent.blockHash);
            BlockValidator.validateBlock(parent, b, parent.height + 1, parentState, config);
            if (b.height > tip.height || (b.height == tip.height && betterValidatorSequence(b, tip))) {
                reorganize(b);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void reorganize(Block newTip) throws IOException, StateTransitionException {
        // v1 reorg: keeps fork blocks in memory and recomputes canonical state in memory.
        // It does not rewrite the append-only block log for old fork removal on disk yet.
        Set<String> currentChain = new HashSet<>();
        Block cursor = tip;
        while (cursor != null) {
            currentChain.add(cursor.blockHash);
            cursor = knownTips.get(cursor.header.prevHash);
        }

        List<Block> newFork = new ArrayList<>();
        Block candidate = newTip;
        while (candidate != null && !currentChain.contains(candidate.blockHash)) {
            newFork.add(candidate);
            candidate = knownTips.get(candidate.header.prevHash);
        }
        Block commonAncestor = candidate;
        if (commonAncestor == null) throw new StateTransitionException("no common ancestor for reorg");

        State rebuilt = replayStateToBlock(commonAncestor.blockHash);
        Collections.reverse(newFork);
        Block parent = commonAncestor;
        for (Block forkBlock : newFork) {
            rebuilt = BlockValidator.validateBlock(parent, forkBlock, parent.height + 1, rebuilt, config);
            parent = forkBlock;
        }
        tip = newTip;
        tipState = rebuilt;
    }

    private State replayStateToBlock(String blockHash) throws IOException, StateTransitionException {
        List<Block> chain = buildChainTo(blockHash);
        State st = new State();
        for (Block block : chain) {
            for (Transaction tx : block.transactions) {
                StateMachine.apply(st, tx);
            }
        }
        return st;
    }

    private List<Block> buildChainTo(String blockHash) throws StateTransitionException {
        List<Block> chain = new ArrayList<>();
        Block cursor = knownTips.get(blockHash);
        while (cursor != null) {
            chain.add(cursor);
            if ("0".repeat(64).equals(cursor.header.prevHash)) {
                break;
            }
            cursor = knownTips.get(cursor.header.prevHash);
        }
        if (chain.isEmpty()) throw new StateTransitionException("unknown block " + blockHash);
        Collections.reverse(chain);
        return chain;
    }

    private boolean betterValidatorSequence(Block candidateTip, Block currentTip) {
        return validatorSequenceScore(candidateTip) > validatorSequenceScore(currentTip);
    }

    private int validatorSequenceScore(Block chainTip) {
        int score = 0;
        Block cursor = chainTip;
        while (cursor != null) {
            if (cursor.header != null && Objects.equals(cursor.header.validatorPublicKeyB64, config.expectedValidatorAtHeight(cursor.height))) {
                score++;
            }
            if ("0".repeat(64).equals(cursor.header.prevHash)) break;
            cursor = knownTips.get(cursor.header.prevHash);
        }
        return score;
    }

    private List<Transaction> filterValidTransactions(State stateSnapshot, List<Transaction> txs, List<String> invalidTxIds) {
        State working = stateSnapshot.copy();
        List<Transaction> valid = new ArrayList<>();
        for (Transaction tx : txs) {
            try {
                if (!tx.verifySignature()) throw new StateTransitionException("invalid tx signature");
                if (!BlockValidator.isIdentityApproved(tx.senderBusinessId())) {
                    throw new StateTransitionException("sender not approved in identity whitelist");
                }
                StateMachine.apply(working, tx);
                valid.add(tx);
            } catch (Exception e) {
                invalidTxIds.add(tx.txId);
                System.err.println("Dropping invalid mempool tx " + tx.txId + ": " + e.getMessage());
            }
        }
        return valid;
    }

    private boolean sameTxList(List<Transaction> left, List<Transaction> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!Objects.equals(left.get(i).txId, right.get(i).txId)) return false;
        }
        return true;
    }

    private Block findCommittedRaceWinner(Block initialTipSnapshot, Set<String> originalCandidateTxIds) throws IOException {
        if (initialTipSnapshot == null || originalCandidateTxIds == null || originalCandidateTxIds.isEmpty()) {
            return null;
        }
        if (tip == null || Objects.equals(tip.blockHash, initialTipSnapshot.blockHash) || tip.height <= initialTipSnapshot.height) {
            return null;
        }
        long startHeight = initialTipSnapshot.height + 1;
        for (long h = startHeight; h <= tip.height; h++) {
            Block block = store.getByHeight(h);
            if (block != null && blockContainsAllTxIds(block, originalCandidateTxIds)) {
                return block;
            }
        }
        return null;
    }

    private boolean blockContainsAllTxIds(Block block, Set<String> txIds) {
        Set<String> blockTxIds = block.transactions.stream().map(tx -> tx.txId).collect(Collectors.toSet());
        return blockTxIds.containsAll(txIds);
    }

    public String exportSnapshotJson() throws IOException {
        lock.readLock().lock();
        try {
            Map<String,Object> snapshot = new LinkedHashMap<>();
            snapshot.put("height", tip.height);
            snapshot.put("tipHash", tip.blockHash);
            snapshot.put("consensus", "PROOF_OF_AUTHORITY");
            snapshot.put("validatorPubKeysB64", config.snapshotValidators());
            snapshot.put("invoices", tipState.invoices);
            snapshot.put("blocksFile", store.getLogFile().toString());
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void updateIdentityStatus(String businessId, boolean approved) {
        BlockValidator.updateIdentityStatus(businessId, approved);
    }

    public int getMempoolSize() {
        return mempool.size();
    }

    public Path getDataDir() {
        return dataDir;
    }

    private void saveGenesisKeyPair(KeyPair genesisKp) throws IOException {
        Path genesisKeyFile = dataDir.resolve("genesis-keypair.json");
        if (Files.exists(genesisKeyFile)) return;
        Map<String, String> kpMap = Map.of(
                "pubKeyB64", KeyUtil.pubKeyToB64(genesisKp.getPublic()),
                "privKeyB64", KeyUtil.privKeyToB64(genesisKp.getPrivate())
        );
        Files.writeString(genesisKeyFile, CanonicalJson.MAPPER.writeValueAsString(kpMap), StandardCharsets.UTF_8);
        genesisKeyFile.toFile().setReadable(false, false);
        genesisKeyFile.toFile().setReadable(true, true);
        genesisKeyFile.toFile().setWritable(false, false);
        genesisKeyFile.toFile().setWritable(true, true);
    }
}
