package com.smechain.p2p;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PeerManager {
    private final Set<String> peers = ConcurrentHashMap.newKeySet();

    public PeerManager(List<String> bootstrap) {
        for (String p : bootstrap) if (p != null && !p.isBlank()) peers.add(p.trim());
    }

    public Set<String> allPeers() { return Collections.unmodifiableSet(peers); }

    public void addPeer(String hostPort) {
        if (hostPort == null || hostPort.isBlank()) return;
        peers.add(hostPort.trim());
    }
}
