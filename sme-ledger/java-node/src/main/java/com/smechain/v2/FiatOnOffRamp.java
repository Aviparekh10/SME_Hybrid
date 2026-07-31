package com.smechain.v2;

public interface FiatOnOffRamp {
    RampQuote quote(String fromCurrency, String toCurrency, long amountMinor);
    RampReceipt cashIn(String businessId, String currency, long amountMinor);
    RampReceipt cashOut(String businessId, String currency, long amountMinor);
}
