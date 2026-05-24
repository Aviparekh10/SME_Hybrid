package com.smechain.chain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smechain.crypto.CanonicalJson;
import com.smechain.state.State;
import com.smechain.state.StateMachine;
import com.smechain.state.StateTransitionException;
import com.smechain.storage.BlockStore;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Node {
    private final BlockStore store;
    private final Mempool mempool = new Mempool();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ObjectMapper mapper = CanonicalJson.MAPPER;

    // cached state at tip
    private State tipState = new State();
    private Block tip;

    // v1 difficulty fixed; v2 includes adjustment
    private volatile long difficulty = 4; // leading hex zeros

    private Node(BlockStore store) throws IOException {
        this.store = store;
        loadOrInit();
    }

    public static Node create(Path dataDir) throws IOException {
        BlockStore bs = new BlockStore(dataDir);
        return new Node(bs);
    }

    private void loadOrInit() throws IOException {
        lock.writeLock().lock();
        try {
            Block t = store.tip();
            if (t == null) {
                // create genesis
                Transaction coinbase = createSystemTx("genesis", 0);
                Block g = PowMiner.mineNext(null, List.of(coinbase), difficulty, 0);
                store.append(g);
                tip = g;
                tipState = new State();
                // apply genesis tx (no-op to invoice state)
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
        for (long i=0;i<=h;i++) {
            Block b = store.getByHeight(i);
            if (b == null) break;
            for (Transaction tx : b.transactions) {
                try { StateMachine.apply(st, tx); } catch (StateTransitionException ignored) {}
            }
        }
        tipState = st;
    }

    private Transaction createSystemTx(String memo, long fee) {
        // Not signed, so keep it out of normal path; for simplicity, we wrap as signed by an ephemeral key.
        var kp = com.smechain.crypto.KeyUtil.newEd25519();
        Transaction tx = Transaction.unsigned(
                TxType.ISSUE_INVOICE,
                Map.of("invoiceId", "SYSTEM-" + UUID.randomUUID(), "counterpartyBusinessId", "SYSTEM", "amountCents", 1, "dueDateEpochSec", Instant.now().getEpochSecond()+1, "memo", memo),
                Instant.now().getEpochSecond(),
                fee,
                kp.getPublic()
        );
        tx.sign(kp.getPrivate());
        return tx;
    }

    public Block getTip() {
        lock.readLock().lock();
        try { return tip; } finally { lock.readLock().unlock(); }
    }

    public long getHeight() throws IOException {
        return store.height();
    }

    public State getTipStateCopy() {
        lock.readLock().lock();
        try {
            State s = new State();
            s.invoices.putAll(tipState.invoices);
            return s;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void submitTx(Transaction tx) throws StateTransitionException {
        if (tx.signatureB64 == null || !tx.verifySignature()) throw new StateTransitionException("invalid signature");
        if (tx.feeMicrounits < 0) throw new StateTransitionException("fee must be >= 0");

        // pre-validate against current state (soft)
        State tmp = getTipStateCopy();
        StateMachine.apply(tmp, tx);

        mempool.add(tx);
    }

    public boolean hasTx(String txId) {
        return mempool.contains(txId);
    }

    public Block mineBlock(int maxTx) throws IOException, StateTransitionException {
        lock.writeLock().lock();
        try {
            List<Transaction> txs = mempool.topByFee(maxTx);
            // validate tx list deterministically
            State tmp = new State();
            tmp.invoices.putAll(tipState.invoices);
            for (Transaction tx : txs) StateMachine.apply(tmp, tx);

            long nextHeight = tip.height + 1;
            Block b = PowMiner.mineNext(tip, txs, difficulty, nextHeight);

            // Validate block against prev + commit
            BlockValidator.validateBlock(tip, b, nextHeight, tipState);

            store.append(b);
            tip = b;
            // commit state
            tipState = tmp;
            mempool.removeAll(txs.stream().map(t -> t.txId).toList());
            return b;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean tryAddBlock(Block b) {
        lock.writeLock().lock();
        try {
            // simple add-if-extends-tip; forks supported via future extension (v1.1)
            if (tip != null && b.height != tip.height + 1) return false;
            if (tip != null && !b.header.prevHash.equals(tip.blockHash)) return false;

            // validate by applying txs
            State tmp = new State();
            tmp.invoices.putAll(tipState.invoices);
            for (Transaction tx : b.transactions) {
                if (tx.signatureB64 == null || !tx.verifySignature()) return false;
                StateMachine.apply(tmp, tx);
            }
            // PoW + merkle + header hash
            try {
                BlockValidator.validateBlock(tip, b, tip.height + 1, tipState);
            } catch (Exception e) { return false; }

            store.append(b);
            tip = b;
            tipState = tmp;
            mempool.removeAll(b.transactions.stream().map(t -> t.txId).toList());
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String exportSnapshotJson() throws IOException {
        lock.readLock().lock();
        try {
            Map<String,Object> snapshot = new LinkedHashMap<>();
            snapshot.put("height", tip.height);
            snapshot.put("tipHash", tip.blockHash);
            snapshot.put("difficulty", difficulty);
            snapshot.put("invoices", tipState.invoices);
            snapshot.put("blocksFile", store.getLogFile().toString());
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
        } finally {
            lock.readLock().unlock();
        }
    }
}
