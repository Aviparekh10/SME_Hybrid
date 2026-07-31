package com.smechain.v2;

public interface KycProvider {
    KycResult verifyBusiness(String businessId, KycPayload payload);
}
