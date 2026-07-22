package com.smechain.chain;

import com.smechain.p2p.P2PServer;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PoaScheduler {
    private final Node node;
    private final P2PServer p2pServer;
    private final PoaConfig config;
    private final PrivateKey myValidatorKey;
    private final PublicKey myValidatorPub;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private long lastObservedHeight = -1;
    private long lastObservedChangeEpochSec = 0;

    public PoaScheduler(Node node, P2PServer p2pServer, PoaConfig config, PrivateKey myValidatorKey, PublicKey myValidatorPub) {
        this.node = node;
        this.p2pServer = p2pServer;
        this.config = config;
        this.myValidatorKey = myValidatorKey;
        this.myValidatorPub = myValidatorPub;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, config.blockIntervalSeconds, config.blockIntervalSeconds, TimeUnit.SECONDS);
    }

    private void tick() {
        try {
            long nextHeight = node.getHeight() + 1;
            String expectedValidator = config.expectedValidatorAtHeight(nextHeight);
            String myPub = com.smechain.crypto.KeyUtil.pubKeyToB64(myValidatorPub);
            if (Objects.equals(expectedValidator, myPub)) {
                try {
                    Block block = node.produceBlock(config, myValidatorKey, myValidatorPub);
                    p2pServer.broadcastBlock(block);
                } catch (IllegalStateException ignored) {
                }
            } else {
                long height = node.getHeight();
                long now = java.time.Instant.now().getEpochSecond();
                if (height != lastObservedHeight) {
                    lastObservedHeight = height;
                    lastObservedChangeEpochSec = now;
                } else if (lastObservedChangeEpochSec > 0 && now - lastObservedChangeEpochSec >= (2 * config.blockIntervalSeconds)) {
                    System.err.println("WARNING: Expected validator did not produce a block within failover window. View-change/failover is TODO.");
                }
            }
        } catch (Exception e) {
            System.err.println("PoA scheduler error: " + e.getMessage());
        }
    }
}
