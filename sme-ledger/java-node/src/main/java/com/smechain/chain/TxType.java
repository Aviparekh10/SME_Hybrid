package com.smechain.chain;

public enum TxType {
    ISSUE_INVOICE,
    ACCEPT_INVOICE,
    CONFIRM_DELIVERY,
    PAY_INVOICE,
    CANCEL_INVOICE,
    OPEN_DISPUTE,
    RESOLVE_DISPUTE,

    // V2 optional:
    MINT_STABLECOIN,
    REDEEM_STABLECOIN,
    KYC_ATTESTATION
}
