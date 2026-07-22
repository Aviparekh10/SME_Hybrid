# SME SettlementChain

SME SettlementChain is a hybrid blockchain system designed to model decentralized financial workflows for small and medium enterprises (SMEs), including invoice issuance, validation, financing, and settlement.

The system combines a permissionless Proof-of-Work blockchain with a structured financial state machine and analytics layer, enabling transparent and verifiable interactions between participants without reliance on centralized intermediaries.

---

## Overview

Traditional blockchain systems focus primarily on value transfer. SME SettlementChain extends this model by integrating business logic directly into the ledger, allowing the representation and enforcement of real-world financial processes.

The platform simulates a financial network involving suppliers, buyers, and lenders, where all interactions are recorded, validated, and enforced on-chain.

---

## Key Capabilities

- Permissionless Proof-of-Work consensus
- Peer-to-peer node communication and synchronization
- Transaction validation and block formation
- Invoice lifecycle state management
- REST API for external interaction
- Snapshot export for analytics and reporting
- Risk analysis pipeline over historical transaction data

---

## System Architecture

The system is composed of two primary components:

### Java Node (`java-node/`)

The Java node implements the core blockchain functionality:

- Block and transaction processing
- Proof-of-Work mining
- Mempool management
- Merkle tree construction
- P2P networking (JSON-over-TCP)
- REST API server
- Invoice state machine and validation
- Snapshot export interface

### Python Analytics (`python-analytics/`)

The analytics module processes exported blockchain data:

- Risk scoring based on transaction and invoice history
- Data transformation and reporting
- CSV-based output for downstream analysis

---

## Financial Workflow Model

The platform models a structured financial workflow:

1. A supplier issues an invoice  
2. A buyer validates the invoice  
3. A lender provides liquidity (simulated)  
4. The invoice is settled on-chain  

All transitions are governed by a deterministic state machine, ensuring correctness and preventing invalid operations.

---

## Compliance and Infrastructure

The system includes simulated components for:

- Identity verification (KYC)
- Fiat on/off ramp mechanisms
- Stablecoin bridge interactions

These components are implemented as functional simulations. Production deployment would require integration with regulated financial institutions and compliance frameworks.

---

## Technology Stack

- Java (blockchain node, networking, API)
- Python (analytics and reporting)
- REST APIs
- JSON-over-TCP communication
- Ed25519 cryptographic signatures

---

## Getting Started

### Prerequisites

- Java 17+
- Python 3.10+

---

### Run a Node

```bash
cd java-node
./gradlew run --args="--port 8080 --p2pPort 9000 --dataDir data/node1 --peers 127.0.0.1:9001,127.0.0.1:9002"
```

Access health endpoint:

```
http://localhost:8080/health
```

---

### Run Multiple Nodes

```bash
./gradlew run --args="--port 8080 --p2pPort 9000 --dataDir data/node1 --peers 127.0.0.1:9001,127.0.0.1:9002"
./gradlew run --args="--port 8081 --p2pPort 9001 --dataDir data/node2 --peers 127.0.0.1:9000,127.0.0.1:9002"
./gradlew run --args="--port 8082 --p2pPort 9002 --dataDir data/node3 --peers 127.0.0.1:9000,127.0.0.1:9001"
```

---

### Create a Wallet

```bash
curl -s -X POST http://localhost:8080/wallet/new | jq
```

---

### Issue an Invoice

```bash
curl -s -X POST http://localhost:8080/tx/issueInvoice \
  -H 'Content-Type: application/json' \
  -d '{"issuerPrivKeyB64":"...","counterpartyBusinessId":"...","amountCents":2500000,"dueDateEpochSec":1730000000,"memo":"Web design milestone 1","feeMicrounits":2000}' | jq
```

---

### Mine a Block

```bash
curl -s -X POST http://localhost:8080/mine?maxTx=200 | jq
```

---

### Export Snapshot

```bash
curl -s http://localhost:8080/export/snapshot > snapshot.json
```

---

### Run Analytics

```bash
cd python-analytics
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python src/risk_report.py --snapshot snapshot.json --out report.csv
```

---

## Protocol Summary

- Permissionless Proof-of-Work consensus
- Longest-chain selection rule
- Invoice-based transaction types
- Deterministic state machine validation
- JSON-over-TCP peer communication
- REST API for system interaction

---

## Future Work

- Alternative consensus mechanisms
- Integration with real payment systems
- Distributed cloud deployment
- Smart contract support for automated workflows

---

## Author

Avi Parekh  
Rutgers University — Computer Science and Data Science

---

## Disclaimer

This project is a research and engineering prototype intended for educational purposes. It simulates financial infrastructure and does not integrate with real banking systems.