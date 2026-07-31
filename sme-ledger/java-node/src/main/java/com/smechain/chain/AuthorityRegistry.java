package com.smechain.chain;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AuthorityRegistry {
    private final Set<String> approvedAuthorities = ConcurrentHashMap.newKeySet();

    public AuthorityRegistry(Set<String> initialAuthorities) {
        approvedAuthorities.addAll(initialAuthorities);
    }

    public boolean isApproved(String publicKeyB64) {
        return approvedAuthorities.contains(publicKeyB64);
    }

    public void addAuthority(String publicKeyB64) {
        approvedAuthorities.add(publicKeyB64);
    }

    public void removeAuthority(String publicKeyB64) {
        approvedAuthorities.remove(publicKeyB64);
    }
}