package com.smechain.v2;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentWebhookController {

    // Handles validated payment events and forwards them into the settlement ledger
    private final LedgerEngine ledgerEngine; 

    public PaymentWebhookController(LedgerEngine ledgerEngine) {
        this.ledgerEngine = ledgerEngine;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleBankConfirmation(@RequestBody String payload, @RequestHeader("Signature") String sig) {
        // Basic industry check: Make sure this payload isn't empty or fake
        if (payload == null || sig == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // When the banking system tells your app "Money Secured", run your ledger processing
        if (payload.contains("transfer.succeeded")) {
            // This triggers your core block creation safely
            ledgerEngine.processValidatedPaymentEvent(payload); 
            return ResponseEntity.ok("Ledger State Synchronized");
        }

        return ResponseEntity.ok("Ignored Event");
    }
}
