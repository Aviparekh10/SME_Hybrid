# SME SettlementChain  
### Hybrid Blockchain for Invoice Settlement, Risk Analytics, and Decentralized Financial Workflows

---

## 🚀 Overview

SME SettlementChain is a hybrid blockchain system designed to model real-world financial workflows for small and medium enterprises (SMEs).

Unlike traditional blockchains that only handle currency transfers, this platform integrates a decentralized ledger with a full business logic layer, enabling structured financial interactions such as invoice issuance, validation, financing, and settlement.

The system simulates a complete financial network where buyers, suppliers, and lenders interact transparently without relying on centralized intermediaries.

---

## 🧠 What Makes This Different

This is not just a cryptocurrency.

This system combines:

- Blockchain (PoW, Merkle trees, validation)
- Distributed Systems (P2P networking, node sync)
- Backend Engineering (REST API, transaction flow)
- Fintech Logic (invoice lifecycle, settlement, liquidity)
- Compliance Simulation (KYC, regulated participation)

---

## ⚙️ System Components

### 🔗 Java Node (`java-node/`)
- Proof-of-Work blockchain implementation  
- P2P gossip-based networking  
- Transaction validation and mempool  
- Invoice settlement state machine  
- REST API for interaction  
- Snapshot export functionality  

### 📊 Python Analytics (`python-analytics/`)
- Risk scoring on invoice/payment history  
- Financial analytics pipeline  
- CSV-based reporting system  

---

## 🧾 Financial Workflow Model

The system models real-world SME interactions:

1. A supplier issues an invoice  
2. A buyer validates the invoice  
3. A lender provides liquidity (simulated)  
4. The invoice is settled on-chain  

All actions are:
- Verifiable  
- Immutable  
- Governed by state transitions  

---

## 🛡️ Compliance & Infrastructure (v2)

Includes simulated modules for:
- KYC (Know Your Customer)  
- Fiat on/off ramp  
- Stablecoin bridge  

> These are implemented as working simulations + stubs.  
> Real-world deployment would require banking integrations and regulatory approval.

---

## 🧩 Tech Stack

- Java (Blockchain node, networking, API)
- Python (Analytics & reporting)
- REST APIs
- JSON-over-TCP (P2P communication)
- Ed25519 cryptography

---

## ⚡ Quickstart

### 1) Run a node
```bash
cd java-node
./gradlew run --args="--port 8080 --p2pPort 9000 --dataDir data/node1 --peers 127.0.0.1:9001,127.0.0.1:9002"
```

Open:
http://localhost:8080/health

---

### 2) Start multiple nodes

```bash
./gradlew run --args="--port 8080 --p2pPort 9000 --dataDir data/node1 --peers 127.0.0.1:9001,127.0.0.1:9002"
./gradlew run --args="--port 8081 --p2pPort 9001 --dataDir data/node2 --peers 127.0.0.1:9000,127.0.0.1:9002"
./gradlew run --args="--port 8082 --p2pPort 9002 --dataDir data/node3 --peers 127.0.0.1:9000,127.0.0.1:9001"
```

---

### 3) Create a wallet
```bash
curl -s -X POST http://localhost:8080/wallet/new | jq
```

---

### 4) Issue an invoice
```bash
curl -s -X POST http://localhost:8080/tx/issueInvoice \
  -H 'Content-Type: application/json' \
  -d '{"issuerPrivKeyB64":"...","counterpartyBusinessId":"...","amountCents":2500000,"dueDateEpochSec":1730000000,"memo":"Web design milestone 1","feeMicrounits":2000}' | jq
```

---

### 5) Mine a block
```bash
curl -s -X POST http://localhost:8080/mine?maxTx=200 | jq
```

---

### 6) Export snapshot
```bash
curl -s http://localhost:8080/export/snapshot > snapshot.json
```

---

### 7) Run analytics
```bash
cd python-analytics
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python src/risk_report.py --snapshot snapshot.json --out report.csv
```

---

## 🔬 Protocol Summary

- Permissionless Proof-of-Work consensus  
- Longest-chain (most work) rule  
- Invoice-based transaction types  
- State machine validation  
- JSON-over-TCP P2P messaging  
- REST API for external interaction  

See:
docs/architecture.md

---

## 📈 Future Improvements

- Replace PoW with more efficient consensus  
- Real banking/payment integration  
- Cloud-based distributed deployment  
- Smart contract automation  

---

## 👨‍💻 Author

Avi Parekh  
Computer Science & Data Science @ Rutgers University  

---

## 📌 Note

This project is a full-stack blockchain simulation designed to explore decentralized financial systems and real-world fintech architecture.