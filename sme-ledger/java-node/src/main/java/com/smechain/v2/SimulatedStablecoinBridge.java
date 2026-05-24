package com.smechain.v2;

import java.util.UUID;

public class SimulatedStablecoinBridge implements StablecoinBridge {
    @Override
    public String mint(String businessId, long amountMinor, String currency) {
        return "MINT-" + currency + "-" + UUID.randomUUID();
    }

    @Override
    public String redeem(String businessId, long amountMinor, String currency) {
        return "REDEEM-" + currency + "-" + UUID.randomUUID();
    }
}
