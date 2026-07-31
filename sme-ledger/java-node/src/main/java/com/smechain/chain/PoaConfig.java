package com.smechain.chain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class PoaConfig {
    public final List<String> validatorPubKeysB64;
    public final long blockIntervalSeconds;

    public PoaConfig(List<String> validators, long intervalSeconds) {
        if (validators == null || validators.isEmpty()) {
            throw new IllegalArgumentException("at least one validator is required");
        }
        this.validatorPubKeysB64 = new CopyOnWriteArrayList<>();
        validators.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).forEach(this.validatorPubKeysB64::add);
        if (this.validatorPubKeysB64.isEmpty()) {
            throw new IllegalArgumentException("at least one validator is required");
        }
        this.blockIntervalSeconds = intervalSeconds;
    }

    public String expectedValidatorAtHeight(long height) {
        return validatorPubKeysB64.get((int) (height % validatorPubKeysB64.size()));
    }

    public Set<String> validatorPubKeysB64Set() {
        return new HashSet<>(validatorPubKeysB64);
    }

    public synchronized boolean addValidator(String pubKeyB64) {
        if (pubKeyB64 == null || pubKeyB64.isBlank() || validatorPubKeysB64.contains(pubKeyB64)) {
            return false;
        }
        return validatorPubKeysB64.add(pubKeyB64.trim());
    }

    public synchronized boolean removeValidator(String pubKeyB64) {
        if (pubKeyB64 == null || pubKeyB64.isBlank() || validatorPubKeysB64.size() <= 1) {
            return false;
        }
        return validatorPubKeysB64.remove(pubKeyB64.trim());
    }

    public List<String> snapshotValidators() {
        return new ArrayList<>(validatorPubKeysB64);
    }
}
