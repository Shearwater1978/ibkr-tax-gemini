# Portfolio Companion: privacy notice

This notice describes the Android app; the iPhone app works the same way except for the points in [On iPhone](#on-iphone).

> **Draft.** Publisher and contact details are placeholders and must be filled in, and the text reviewed, before any public release (for example on Google Play). It describes what the app does; it is not legal advice.

**Publisher:** `[PUBLISHER NAME]`
**Contact for privacy questions:** `[CONTACT ADDRESS]`
**Version:** 2026-10-11

## In short

Portfolio Companion reads your broker reports **on your phone** and keeps the results **encrypted on your phone**. The publisher runs no server and receives none of your data. The app has no accounts, no advertising, no analytics and no crash reporting. Only three kinds of data leave the phone, all described below:

1. **ticker symbols**, to fetch market prices, and only if you chose a price provider (None sends nothing);
2. **encrypted backups**, to the folder you choose;
3. **an encrypted copy of the backup key**, in your Android backup, when your phone supports end-to-end encryption.

## What the app processes

| Data | Source | Where it stays |
|---|---|---|
| Trades, dividends, withholding tax, corporate actions, instruments held | Reports you import | Encrypted database on your phone |
| Operations you add by hand (date, ticker, currency, quantities, prices, amounts) | You | Encrypted database on your phone; backed up encrypted like reports |
| Account number | Report header | Stored only as a keyed pseudonym and the last four characters; never shown in full |
| Report file name | File you pick | Not stored; shown masked during import |
| Names, tax identification numbers | Not read | — |
| Backup passphrase | You | Never stored; only used to derive a key on the phone |
| Recovery code | Generated on the phone | Shown once; never stored |
| Price provider choice (None, Finnhub or Twelve Data) | You | On your phone (encrypted database) |
| Finnhub or Twelve Data API key (optional, your own) | You | Encrypted on your phone, one key per provider; each sent only to its own provider |
| Settings (skin, broker, currency view, backup folder) | You | On your phone |

All processing (reading reports, calculating positions, returns and dividends) happens on the phone, also without a network connection.

## What leaves the phone

| Recipient | What is sent | Why | When |
|---|---|---|---|
| Finnhub (USA), only if you chose it and added your own key | Ticker symbols of US-listed holdings and your Finnhub key (in a request header) | Market prices | When prices refresh |
| Twelve Data (USA), only if you chose it and added your own key | Ticker symbols of US-listed holdings and your Twelve Data key (in a request header) | Market prices | When prices refresh |
| Yahoo Finance (USA), temporarily | Ticker symbols of European listings, only while Finnhub or Twelve Data is chosen | Market prices | When prices refresh |
| National Bank of Poland (NBP) | Nothing about you; the app downloads the public rate table | Approximate USD conversion | When rates refresh |
| Your storage provider (for example Google Drive), chosen by you | Backup files (reports and operations added by hand) and a key manifest, **encrypted on the phone** before upload | Backup and moving to a new phone | After each import or manual change, and on retry |
| Google, via Android Block Store | The backup data key, **end-to-end encrypted** with your phone's screen lock; only when your phone supports that | Restoring on a new phone without the passphrase | When backups are set up or restored |

Price requests go **directly from your phone** to the provider you chose, with **your own key** and your own quota: there is no server of ours in between, no shared cache and no telemetry. With **None**, the app sends no price request at all and shows the last known prices, marked out of date. European listings use Yahoo Finance's public data temporarily; this source will be replaced. Like any internet request, these providers see your IP address and the time of the request. The app sends no quantities, amounts, account data or report contents to Finnhub, Twelve Data, Yahoo or NBP. Neither your storage provider nor Google can read your backups or your backup key. The providers listed may store data outside the European Economic Area (Finnhub, Yahoo and Google are US companies); for backups and the key copy they only ever receive encrypted data.

Tapping "Get key from Finnhub" or "Get key from Twelve Data", or the "Source: Twelve Data" label next to prices, opens that provider's website in a browser tab; what you enter there is between you and the provider.

## Protection

- The database is encrypted (SQLCipher) with a key protected by the Android Keystore, which is hardware-backed on most phones.
- Backups are encrypted on the phone (AES-256-GCM) with a data key that only your passphrase, your recovery code or the Block Store copy can unlock.
- The app locks with your screen lock or biometrics, blocks screenshots and hides its content in the app switcher.
- The app writes nothing to the system log and clears its cache folder whenever you leave it.
- Android's own cloud backup and phone-to-phone transfer exclude the app's data; only the app's encrypted backups move.

## On iPhone

- There is no copy of the backup key outside the phone: nothing goes to Apple or Google in place of Android Block Store, so restoring on a new iPhone needs your passphrase or recovery code.
- The database key and the pseudonym key are wrapped with a key in the iPhone's Secure Enclave that never leaves this device.
- The app's files use iOS's strongest file protection (readable only while the phone is unlocked) and are excluded from iCloud and computer backups; only the app's encrypted backups move.
- The app locks with Face ID, Touch ID or your passcode and hides its content in the app switcher. iOS does not let apps block screenshots.
- A copied recovery code stays on this iPhone (not shared with other devices) and is cleared from the clipboard after one minute.

## How long data is kept

Until you delete it. There is no automatic deletion, because the data serves only you.

## Your choices and rights

- **Delete one import or all imports:** on the import screen.
- **Erase all data:** at the end of Settings. It deletes the database, every backup file and the manifest in the backup folder, the backup keys (also the Block Store copy), your price keys and all settings. Copies that your storage provider keeps in its trash can no longer be decrypted.
- **Access and portability:** your data is on your phone and in your own backup folder. The app makes no tax reports; for PIT-38, use the desktop tool with the same broker reports.
- **Market prices:** requested when the portfolio opens, from the provider you chose. Choose **None** in Settings → Market prices to stop all price requests.
- **Questions or complaints:** contact `[CONTACT ADDRESS]`. You can also contact your data protection authority.

## Changes

Changes to this notice are published with the app's source code and dated above.
