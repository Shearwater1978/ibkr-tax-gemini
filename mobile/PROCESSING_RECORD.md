# Portfolio Companion: record of processing activities (Art. 30 GDPR)

> **Draft**, to be reviewed together with [PRIVACY.md](PRIVACY.md) before any public release. Publisher details are placeholders.
>
> The publisher operates no server and receives no personal data: all processing below runs on the user's phone under the user's control. This record documents the app's design so that its data flows can be reviewed; whether the publisher is a controller for any of it is a legal question to settle in that review.

**Publisher:** `[PUBLISHER NAME]`, `[CONTACT ADDRESS]`
**Version:** 2026-10-11 · Android app `com.ibkrtax.mobile`

## 1. Portfolio overview from broker reports

| Item | Entry |
|---|---|
| Purpose | Show the user's holdings, returns, dividends and activity from their own broker reports; informational only, no tax filing |
| Data subjects | The app's user (the account holder of the imported reports) |
| Data categories | Account number (stored as HMAC pseudonym and last-four mask only); trades, dividends, withholding tax, corporate actions, instruments held (financial values and quasi-identifiers). Field-by-field classification: `mobile/android/app/src/main/java/com/ibkrtax/mobile/security/DataClassification.kt` |
| Recipients | None; processed on the phone |
| Transfers outside the EEA | None |
| Retention | Until the user deletes imports or erases all data |
| Security measures | SQLCipher database keyed by the Android Keystore (hardware-backed on most phones); app lock; screenshots blocked; no logging (`NoLoggingTest`); no analytics or crash SDKs; OS backup and device transfer excluded |

## 2. Market prices and exchange rates

| Item | Entry |
|---|---|
| Purpose | Current prices for held instruments and an approximate USD total |
| Data categories | Ticker symbols of held instruments (could hint at holdings when combined with the IP address); the user's own API key for the chosen provider (Finnhub or Twelve Data) |
| Recipients | Only the provider the user chose: Finnhub or Twelve Data (with the user's own key), for US listings; while one of them is chosen, Yahoo Finance's public data for European listings (temporary; this source will be replaced). With None, no recipient. National Bank of Poland receives only a request for its public table |
| Transfers outside the EEA | Finnhub, Twelve Data and Yahoo Finance (USA): ticker symbols, the key of the chosen provider, IP address |
| Retention | Quotes and rates cached on the phone until refreshed or erased; the providers' own retention applies to their request logs |
| Security measures | No backend: requests go directly from the device to the chosen provider with the user's own key and quota; HTTPS; keys only in request headers (`X-Finnhub-Token`, `Authorization: apikey`), one encrypted key per provider; no automatic switching between providers; no quantities, amounts or account data sent; network code isolated from report handling (`OfflineProcessingTest`) |

## 3. Encrypted backup and restore

| Item | Entry |
|---|---|
| Purpose | Keep the user's imported reports safe and move them to a new phone |
| Data categories | Imported reports and a key manifest, both **encrypted on the phone** (AES-256-GCM); the data key, wrapped by the passphrase (Argon2id) and the recovery code |
| Recipients | The storage provider the user chooses (for example Google Drive); Google, through Android Block Store, for an end-to-end encrypted copy of the data key when the phone supports it |
| Transfers outside the EEA | Possible, depending on the provider; only ciphertext is transferred |
| Retention | Until the user deletes imports or erases all data; erasure destroys the keys, so copies in the provider's trash cannot be decrypted |
| Security measures | Client-side encryption before upload; read-back verification; passphrase and recovery code never stored; Block Store copy only with end-to-end encryption available |

## Data subject rights

- **Erasure:** "Erase all data" in Settings (local data, backup files and manifest, keys including Block Store, settings).
- **Restriction and objection:** backups are optional. Choosing None as the price provider stops all price requests.
- **Access and portability:** data stays with the user (phone and own backup folder). The app produces no tax output; tax reports come from the desktop tool (decision 2026-10-11).
