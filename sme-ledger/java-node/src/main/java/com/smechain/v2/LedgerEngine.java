package com.smechain.v2;

import java.util.Map;

public class LedgerEngine {
    public String submitPayment(Map<String, Object> payload) {
        return "payment_submitted";
    }
    public void processValidatedPaymentEvent(String payload) {
        System.out.println("Validated payment event received: " + payload);
    }

}