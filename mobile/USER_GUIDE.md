# Portfolio Companion: user guide (Android)

Portfolio Companion shows your portfolio, returns, dividends and activity from your broker's reports, on your phone. It is **informational only**: it does not calculate or file taxes (for PIT-38, use the desktop tool in this repository), and its amounts are not tax results.

## Getting started

1. **Choose a skin.** On first launch the app offers four looks (see [Skins](#skins)). They show the same data and numbers; you can switch at any time in Settings.
2. **Choose your broker.** Supported now: Interactive Brokers. Broker-specific wording appears only after you choose one. You can choose later, but adding reports asks for it first.
3. **Set up backups.** Before the first import, open Settings and
   1. set up **backup encryption** (a passphrase plus a recovery code — keep the code somewhere safe), then
   2. choose a **backup folder** (for example a cloud-synced folder offered by the system file picker).
4. **Add reports.** Pick one or more **IBKR Activity Flex Query CSV** files. Each file is checked and imported on its own; a rejected file does not affect the others, and a report imported twice adds nothing new.

## What the app shows

- **Portfolio value and today's change**, as an approximate total in US dollars.
- **Holdings** from FIFO open lots, with price, value, today's change and weight.
- **Position details:** invested amount (commissions excluded), price gain, gains from sales, dividends after withholding tax, and the total return. Sold positions keep their sales and dividends.
- **Top movers:** today's largest rises and falls.
- **Income:** dividends by year and month with gross amount, withholding tax and net; a comparison with earlier years; each company's full dividend history.
- **Activity:** every imported operation, with ticker search and type filters.

### Currencies

Amounts are shown in each position's own currency, or converted to US dollars when you choose "USD" (the choice is remembered). Converted amounts and totals are **approximate**: they use the latest exchange rates published by the National Bank of Poland (NBP), and the app shows the date of those rates. A currency without a rate is left out and the total is marked incomplete. Conversion into your own home currency is planned.

### Prices

- **US listings:** from Finnhub when you add your own free Finnhub key in Settings ("Market prices", with a "How to get a free key" guide); without a key, from Yahoo Finance.
- **European listings** (Xetra, Frankfurt, London, Amsterdam, Paris, Milan, Madrid, Zurich, the Nordic exchanges, Warsaw and others): always from Yahoo Finance, about 15 minutes delayed.
- Each price shows its freshness: live, delayed, latest close or stale. Instruments outside these markets show no price, and totals say so.
- Price requests contain only ticker symbols (and, for Finnhub, your key); the key is stored encrypted on the device and never put into backups.

## Skins

| Skin | Look | Where things are |
|---|---|---|
| **Classic** | Everything on one screen; five tabs | Portfolio, Dividends, Operations, Imports, Settings tabs |
| **Calm** | Light and simple | Home, Income and Activity tabs; "+" adds reports; the avatar opens Settings; "View" sorts, switches currency and shows sold positions |
| **Bento** | Dark tiles | Home, Income, Activity and Data tabs; Data holds reports, backup and price status, Settings and deletion |
| **Focus** | Plain sentences first | Today and Income; the gear on Today opens Settings; the "+" button opens reports, activity, currency, backup and prices, security, Settings and deletion |

Switch skins in Settings → **Skin** → **Change skin**.

## Privacy and security

- Reports are read and processed **on the device**; their contents are never sent anywhere. Data is stored in an encrypted database, and account identifiers are masked on screen.
- Backups are **encrypted on the device** before they are written to your backup folder. Keep your passphrase and recovery code: without them the backups cannot be decrypted. Restoring onto a new phone is not available yet.
- The app locks with your device's screen lock and blocks screenshots and previews in the app switcher. Without a device screen lock it cannot verify it is you; set one in system settings.
- Network requests: prices (ticker symbols only) and exchange rates (currency codes only).

## Deleting data

Open the list of imported reports (Imports tab, the import screen, Bento's "Reports imported", or Focus's "+" → "Delete imported data"):

- **Delete this import** removes records that only that report contains, plus its encrypted backup file. Overlapping reports stay complete.
- **Delete all imported data** asks for your backup passphrase and removes all reports and their backup files. Backup keys stay unchanged.
