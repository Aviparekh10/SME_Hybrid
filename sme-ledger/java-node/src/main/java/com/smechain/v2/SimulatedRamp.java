package com.smechain.v2;

import java.util.UUID;

public class SimulatedRamp implements FiatOnOffRamp {
    @Override
    public RampQuote quote(String fromCurrency, String toCurrency, long amountMinor) {
        RampQuote q = new RampQuote();
        q.fromCurrency = fromCurrency;
        q.toCurrency = toCurrency;
        q.amountInMinor = amountMinor;
        q.feeMinor = Math.max(1, amountMinor / 500); // 0.2% simulated
        q.amountOutMinor = amountMinor - q.feeMinor;
        q.provider = "SIM_RAMP";
        return q;
    }

    @Override
    public RampReceipt cashIn(String businessId, String currency, long amountMinor) {
        RampReceipt r = new RampReceipt();
        r.receiptId = UUID.randomUUID().toString();
        r.businessId = businessId;
        r.currency = currency;
        r.amountMinor = amountMinor;
        r.status = "ok";
        r.detail = "simulated_cash_in";
        return r;
    }

    @Override
    public RampReceipt cashOut(String businessId, String currency, long amountMinor) {
        RampReceipt r = new RampReceipt();
        r.receiptId = UUID.randomUUID().toString();
        r.businessId = businessId;
        r.currency = currency;
        r.amountMinor = amountMinor;
        r.status = "ok";
        r.detail = "simulated_cash_out";
        return r;
    }
}
