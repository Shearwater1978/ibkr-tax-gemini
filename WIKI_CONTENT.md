# Project Documentation (v2.1.0)

Welcome to the **IBKR Tax Calculator** Wiki.
Current Release: **v2.1.0** (Stable).

---

## 🚀 What's New in v2.1.0?
* **Full Encryption:** SQLCipher integration.
* **Universal Parser:** Activity Statement support added.
* **Smart NBP:** Reduced API load by 95% via Batch Caching.

---

## 📥 Input Data
We support:
1. **Activity Statements** (CSV) - Recommended.
2. **Flex Queries** (CSV) - Supported.

Run imports via:
`python -m src.parser --files "data/*.csv"`

---

## 🧮 PIT-38 Mapping
| Field | Report Source |
|:---|:---|
| **Przychód (Revenue)** | PDF Page 5 -> Revenue |
| **Koszty (Costs)** | PDF Page 5 -> Costs |
| **Podatek (Tax Paid)** | PDF Page 4 -> Withholding Tax |

---