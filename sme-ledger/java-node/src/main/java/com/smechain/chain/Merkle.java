package com.smechain.chain;

import com.smechain.crypto.HashUtil;

import java.util.ArrayList;
import java.util.List;

public final class Merkle {
    private Merkle(){}

    public static String merkleRoot(List<String> leavesHex) {
        if (leavesHex == null || leavesHex.isEmpty()) {
            return HashUtil.sha256Hex("");
        }
        List<String> level = new ArrayList<>(leavesHex);
        while (level.size() > 1) {
            List<String> next = new ArrayList<>();
            for (int i = 0; i < level.size(); i += 2) {
                String left = level.get(i);
                String right = (i + 1 < level.size()) ? level.get(i+1) : HashUtil.sha256Hex("");
                next.add(HashUtil.sha256Hex(left + right));
            }
            level = next;
        }
        return level.get(0);
    }
}
