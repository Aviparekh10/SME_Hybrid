# Architecture

## Java node
- **chain/**: Block, header, PoW, merkle root, validation, fork choice (most cumulative work).
- **state/**: Invoice settlement state machine and deterministic state application.
- **p2p/**: JSON-over-TCP gossip (NEW_TX, NEW_BLOCK, GET_HEADERS, GET_BLOCKS).
- **api/**: REST endpoints using Java HttpServer.
- **storage/**: append-only block log + index; mempool persistence optional.
- **v2/**: optional modules (KYC, fiat on/off-ramp, stablecoin bridge, compliance rules) as runnable simulators/stubs.

## Python analytics
- Parses snapshot export.
- Builds per-business features.
- Produces CSV risk report + plots (optional).
