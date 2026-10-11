# Portfolio Companion: privacy notice (Android)

> **Draft.** Publisher and contact details are placeholders and must be filled in, and the text reviewed, before any public release (for example on Google Play). It describes what the app does; it is not legal advice.

**Publisher:** `[PUBLISHER NAME]`
**Contact for privacy questions:** `[CONTACT ADDRESS]`
**Version:** 2026-10-11

## In short

Portfolio Companion reads your broker reports **on your phone** and keeps the results **encrypted on your phone**. The publisher runs no server and receives none of your data. The app has no accounts, no advertising, no analytics and no crash reporting. Only three kinds of data leave the phone, all described below:

1. **ticker symbols**, to fetch market prices;
2. **encrypted backups**, to the folder you choose;
3. **an encrypted copy of the backup key**, in your Android backup, when your phone supports end-to-end encryption.

## What the app processes

| Data | Source | Where it stays |
|---|---|---|
| Trades, dividends, withholding tax, corporate actions, instruments held | Reports you import | Encrypted database on your phone |
| Account number | Report header | Stored only as a keyed pseudonym and the last four characters; never shown in full |
| Report file name | File you pick | Not stored; shown masked during import |
| Names, tax identification numbers | Not read | — |
| Backup passphrase | You | Never stored; only used to derive a key on the phone |
| Recovery code | Generated on the phone | Shown once; never stored |
| Finnhub API key (optional) | You | Encrypted on your phone; sent only to Finnhub |
| Settings (skin, broker, currency view, backup folder) | You | On your phone |

All processing (reading reports, calculating positions, returns and dividends) happens on the phone, also without a network connection.

## What leaves the phone

| Recipient | What is sent | Why | When |
|---|---|---|---|
| Finnhub (USA), only with your own key | Ticker symbols of US-listed holdings and your Finnhub key | Market prices | When prices refresh |
| Yahoo Finance (USA) | Ticker symbols (US listings without a Finnhub key; European listings always) | Market prices | When prices refresh |
| National Bank of Poland (NBP) | Nothing about you; the app downloads the public rate table | Approximate USD conversion | When rates refresh |
| Your storage provider (for example Google Drive), chosen by you | Backup files and a key manifest, **encrypted on the phone** before upload | Backup and moving to a new phone | After each import and on retry |
| Google, via Android Block Store | The backup data key, **end-to-end encrypted** with your phone's screen lock; only when your phone supports that | Restoring on a new phone without the passphrase | When backups are set up or restored |

Like any internet request, these providers see your IP address and the time of the request. The app sends no quantities, amounts, account data or report contents to Finnhub, Yahoo or NBP. Neither your storage provider nor Google can read your backups or your backup key. The providers listed may store data outside the European Economic Area (Finnhub, Yahoo and Google are US companies); for backups and the key copy they only ever receive encrypted data.

Tapping "Get key from Finnhub" opens Finnhub's website in a browser tab; what you enter there is between you and Finnhub.

## Protection

- The database is encrypted (SQLCipher) with a key protected by the Android Keystore, which is hardware-backed on most phones.
- Backups are encrypted on the phone (AES-256-GCM) with a data key that only your passphrase, your recovery code or the Block Store copy can unlock.
- The app locks with your screen lock or biometrics, blocks screenshots and hides its content in the app switcher.
- The app writes nothing to the system log and clears its cache folder whenever you leave it.
- Android's own cloud backup and phone-to-phone transfer exclude the app's data; only the app's encrypted backups move.

## How long data is kept

Until you delete it. There is no automatic deletion, because the data serves only you.

## Your choices and rights

- **Delete one import or all imports:** on the import screen.
- **Erase all data:** at the end of Settings. It deletes the database, every backup file and the manifest in the backup folder, the backup keys (also the Block Store copy), your price key and all settings. Copies that your storage provider keeps in its trash can no longer be decrypted.
- **Access and portability:** your data is on your phone and in your own backup folder. Exporting reports in a standard format is planned.
- **Market prices:** requested automatically when the portfolio opens; there is no switch to turn that off yet.
- **Questions or complaints:** contact `[CONTACT ADDRESS]`. You can also contact your data protection authority.

## Changes

Changes to this notice are published with the app's source code and dated above.
