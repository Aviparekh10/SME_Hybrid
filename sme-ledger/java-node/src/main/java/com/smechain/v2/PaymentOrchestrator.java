package com.smechain.v2;

import com.smechain.api.RestServer; 
import com.smechain.chain.Block;
import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PaymentOrchestrator {

    private final HttpClient httpClient;
    private final String bridgeApiKey;
    private final String bridgeUrl;

    public PaymentOrchestrator() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.bridgeApiKey = System.getenv("BRIDGE_PROD_API_KEY");
        this.bridgeUrl = "https://api.bridge.xyz/v1/transfers";
    }

    /**
     * Executes an atomic enterprise B2B payment loop.
     * Pulls fiat via FedNow/RTP, converts to USDC on Layer-2, and prepares ledger settlement.
     */
    public CompletableFuture<String> initiateEnterprisePayment(String invoiceId, String buyerId, String supplierId, double grossAmount) {
        // Essential for production: generate a deterministic idempotency key based on the invoice ID
        String idempotencyKey = UUID.nameUUIDFromBytes((invoiceId + "_fiat_pull").getBytes()).toString();

        double feeAmount = grossAmount * 0.007; // Your 0.7% platform take-rate
        double netSupplierAmount = grossAmount - feeAmount;

        Map<String, Object> requestPayload = Map.of(
            "amount", grossAmount,
            "source", Map.of(
                "payment_rail", "usd_rtp_ach",
                "customer_id", buyerId
            ),
            "destination", Map.of(
                "payment_rail", "usdc_polygon",
                "developer_treasury_fee", feeAmount,
                "supplier_payout_amount", netSupplierAmount,
                "customer_id", supplierId
            )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(bridgeUrl))
                .header("Authorization", "Bearer " + bridgeApiKey)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(requestPayload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 201) {
                        throw new RuntimeException("Fintech Rail Rejection: " + response.body());
                    }
                    return response.body(); // Returns the unique tracking object ID from the banking network
                });
    }
}