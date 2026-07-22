import argparse, json
from dataclasses import dataclass
from typing import Dict, Any
import pandas as pd
import numpy as np

def load_snapshot(path: str) -> Dict[str, Any]:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)

def invoices_to_df(invoices: Dict[str, Any]) -> pd.DataFrame:
    rows = []
    for inv_id, rec in invoices.items():
        ever_disputed = rec.get("disputeReason") is not None
        rows.append({
            "invoiceId": inv_id,
            "issuer": rec.get("issuerBusinessId"),
            "counterparty": rec.get("counterpartyBusinessId"),
            "amountCents": rec.get("amountCents"),
            "issuedAt": rec.get("issuedAt"),
            "dueDate": rec.get("dueDate"),
            "status": rec.get("status"),
            "acceptedAt": rec.get("acceptedAt"),
            "deliveredAt": rec.get("deliveredAt"),
            "paidAt": rec.get("paidAt"),
            "memo": rec.get("memo", ""),
            "disputeOutcome": rec.get("disputeOutcome"),
            "everDisputed": ever_disputed,
        })
    df = pd.DataFrame(rows)
    if df.empty:
        return df
    for c in ["issuedAt","dueDate","acceptedAt","deliveredAt","paidAt"]:
        df[c] = pd.to_numeric(df[c], errors="coerce")
    df["amountCents"] = pd.to_numeric(df["amountCents"], errors="coerce")
    return df

def compute_features(df: pd.DataFrame) -> pd.DataFrame:
    # Build per-business features (as payer/counterparty)
    if df.empty:
        return pd.DataFrame()

    # Payment delay: paidAt - acceptedAt
    df["daysToPay"] = (df["paidAt"] - df["acceptedAt"]) / 86400.0
    df["isPaid"] = df["status"].eq("PAID")
    df["isLate"] = df["isPaid"] & (df["paidAt"] > df["dueDate"])
    df["lateDays"] = np.where(df["isLate"], (df["paidAt"] - df["dueDate"]) / 86400.0, 0.0)

    # Counterparty features (payer behavior)
    payer = df.copy()
    payer["businessId"] = payer["counterparty"]
    payer["partnerBusinessId"] = payer["issuer"]

    # issuer features (seller behavior): disputes frequency etc.
    issuer = df.copy()
    issuer["businessId"] = issuer["issuer"]
    issuer["partnerBusinessId"] = issuer["counterparty"]

    def agg(group: pd.DataFrame) -> pd.Series:
        total = len(group)
        paid = group["isPaid"].sum()
        total_amount = group["amountCents"].fillna(0).sum()
        paid_amount = group.loc[group["isPaid"], "amountCents"].fillna(0).sum()
        late_amount = group.loc[group["isLate"], "amountCents"].fillna(0).sum()
        avg_days = group.loc[group["isPaid"], "daysToPay"].mean()
        p90_days = group.loc[group["isPaid"], "daysToPay"].quantile(0.90) if paid > 0 else np.nan
        vol = group["amountCents"].std()
        dispute = int(group["everDisputed"].fillna(False).sum())
        partner_volume = group.groupby("partnerBusinessId")["amountCents"].sum()
        max_partner_volume = partner_volume.max() if not partner_volume.empty else 0
        concentration_ratio = (max_partner_volume / total_amount) if total_amount > 0 else 0.0
        return pd.Series({
            "nInvoices": total,
            "totalAmountCents": total_amount,
            "paidRate": (paid_amount / total_amount) if total_amount > 0 else 0.0,
            "lateRate": (late_amount / total_amount) if total_amount > 0 else 0.0,
            "avgDaysToPay": float(avg_days) if not np.isnan(avg_days) else np.nan,
            "p90DaysToPay": float(p90_days) if not np.isnan(p90_days) else np.nan,
            "amountStd": float(vol) if not np.isnan(vol) else 0.0,
            "disputeCount": int(dispute),
            "concentrationRatio": float(concentration_ratio),
        })

    payer_feat = payer.groupby("businessId").apply(agg).reset_index()
    issuer_feat = issuer.groupby("businessId").apply(agg).reset_index()

    payer_feat["role"] = "payer"
    issuer_feat["role"] = "issuer"

    feats = pd.concat([payer_feat, issuer_feat], ignore_index=True)

    # Risk score: 0 (best) -> 100 (worst)
    # Simple weighted score: lateRate and p90DaysToPay and disputeCount and paidRate
    feats["riskScore"] = (
        45 * feats["lateRate"].fillna(0) +
        20 * (1 - feats["paidRate"].fillna(0)) +
        0.15 * feats["p90DaysToPay"].fillna(feats["avgDaysToPay"].fillna(0)).clip(lower=0) +
        2.0 * feats["disputeCount"].fillna(0) +
        10 * feats["concentrationRatio"].fillna(0)
    )
    feats["riskScore"] = feats["riskScore"].clip(lower=0, upper=100)
    return feats.sort_values(["riskScore"], ascending=False)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--snapshot", required=True)
    ap.add_argument("--out", default="risk_report.csv")
    args = ap.parse_args()

    snap = load_snapshot(args.snapshot)
    invoices = snap.get("invoices", {})
    df = invoices_to_df(invoices)
    feats = compute_features(df)

    feats.to_csv(args.out, index=False)
    print(f"Wrote {args.out} with {len(feats)} rows.")
    if not df.empty:
        print("Invoices:", len(df), "Paid:", int(df['status'].eq('PAID').sum()))

if __name__ == "__main__":
    main()
