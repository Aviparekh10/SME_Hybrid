# SME SettlementChain (Permissionless PoW + Invoice Settlement + Risk Analytics)

This repository contains:
- **java-node/**: a permissionless Proof-of-Work blockchain node with P2P gossip, invoice settlement transactions, state validation, REST API, and snapshot export.
- **python-analytics/**: risk scoring + analytics over the on-chain invoice/payment history.

> Notes about "v2": This repo includes **code stubs + a working simulator** for fiat on/off-ramp, KYC, and stablecoin bridge interfaces.
> Real production integrations require licensing, banking partnerships, and compliance—out of scope for an offline code-only repo.

## Quickstart

### 1) Run a node
Prereqs: Java 17+

```bash
cd java-node
./gradlew run --args="--port 8080 --p2pPort 9000 --dataDir data/node1 --peers 127.0.0.1:9001,127.0.0.1:9002"
```

Open: http://localhost:8080/health

### 2) Start multiple nodes (locally)
In 3 terminals:

```bash
cd java-node
./gradlew run --args="--port 8080 --p2pPort 9000 --dataDir data/node1 --peers 127.0.0.1:9001,127.0.0.1:9002"
./gradlew run --args="--port 8081 --p2pPort 9001 --dataDir data/node2 --peers 127.0.0.1:9000,127.0.0.1:9002"
./gradlew run --args="--port 8082 --p2pPort 9002 --dataDir data/node3 --peers 127.0.0.1:9000,127.0.0.1:9001"
```

### 3) Create a wallet
```bash
curl -s -X POST http://localhost:8080/wallet/new | jq
```

### 4) Issue an invoice
```bash
curl -s -X POST http://localhost:8080/tx/issueInvoice \
  -H 'Content-Type: application/json' \
  -d '{"issuerPrivKeyB64":"...","counterpartyBusinessId":"...","amountCents":2500000,"dueDateEpochSec":1730000000,"memo":"Web design milestone 1","feeMicrounits":2000}' | jq
```

### 5) Mine a block
```bash
curl -s -X POST http://localhost:8080/mine?maxTx=200 | jq
```

### 6) Export chain snapshot
```bash
curl -s http://localhost:8080/export/snapshot > snapshot.json
```

### 7) Run Python analytics
Prereqs: Python 3.10+

```bash
cd python-analytics
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python src/risk_report.py --snapshot snapshot.json --out report.csv
```

---

## Protocol summary
- Permissionless PoW, longest/most-work chain.
- Tx types implement invoice settlement state machine.
- Ed25519 signatures (Java built-in).
- JSON-over-TCP P2P (newline-delimited messages).
- REST API for wallets, tx creation, mining, inspection, export.

See `docs/architecture.md`.
