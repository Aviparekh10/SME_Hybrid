package com.smechain.v2;

public interface StablecoinBridge {
    // In production, this would connect to external chains / custodians.
    String mint(String businessId, long amountMinor, String currency);
    String redeem(String businessId, long amountMinor, String currency);
}
