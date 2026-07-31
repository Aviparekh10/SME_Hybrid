package com.smechain.v2;

import java.util.Locale;

public class SimulatedKycProvider implements KycProvider {
    @Override
    public KycResult verifyBusiness(String businessId, KycPayload payload) {
        KycResult r = new KycResult();
        if (payload == null || payload.legalName == null || payload.legalName.isBlank()) {
            r.approved = false;
            r.riskTier = "high";
            r.reason = "missing_legal_name";
            return r;
        }
        String c = payload.country == null ? "" : payload.country.toLowerCase(Locale.ROOT);
        r.approved = true;
        r.riskTier = (c.contains("us") || c.contains("ca") || c.contains("uk")) ? "low" : "med";
        r.reason = "simulated_ok";
        return r;
    }
}
