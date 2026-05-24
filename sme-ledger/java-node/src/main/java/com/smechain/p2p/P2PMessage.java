package com.smechain.p2p;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class P2PMessage {
    public String type; // NEW_TX, NEW_BLOCK, HELLO
    public Object body;
}
