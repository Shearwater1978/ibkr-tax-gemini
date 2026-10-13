# Portfolio Companion: user guide

The app runs on Android and iPhone. Screens, numbers and skins are the same on both; the differences are listed in [On iPhone](#on-iphone).

Portfolio Companion shows your portfolio, returns, dividends and activity from your broker's reports, on your phone. It is **informational only**: it does not calculate or file taxes (for PIT-38, use the desktop tool in this repository), and its amounts are not tax results.

## Getting started

1. **Choose a skin.** On first launch the app offers four looks (see [Skins](#skins)). They show the same data and numbers; you can switch at any time in Settings.
2. **Choose your broker.** Supported now: Interactive Brokers. Broker-specific wording appears only after you choose one. You can choose later, but adding reports asks for it first.
3. **Set up backups.** Before the first import, open Settings and
   1. set up **backup encryption** (a passphrase plus a recovery code — keep the code somewhere safe), then
   2. choose a **backup folder** (for example a cloud-synced folder offered by the system file picker).
4. **Add reports.** Pick one or more **IBKR Activity Statements** saved as CSV in English (see [Getting your IBKR report](#getting-your-ibkr-report)). Each file is checked and imported on its own; a rejected file does not affect the others, and a report imported twice adds nothing new.

## Getting your IBKR report

The app reads Interactive Brokers **Activity Statements** saved as **CSV** in **English**. (A Flex Query export has a different layout and is not accepted.) The same steps are in the app: on the import screen, tap **How to get this report**.

1. Sign in to the Interactive Brokers portal: the website, on a computer or in your phone's browser.
2. Open **Performance & Reports** → **Statements**.
3. Under **Default Statements**, run the **Activity Statement**.
4. Choose the period:
   - **Annual** for each past year;
   - **Custom Date Range** for the current year so far (from 1 January to today).
5. Choose **Format: CSV** and **Language: English**, then run it and download the file.
6. Repeat for every year since you opened the account.

Then move the files to the phone, for example:

- download them directly in the phone's browser (they land in **Downloads**); or
- save them to **Google Drive** on the computer and pick them from Drive in the app.

On the import screen, select all files at once. Overlapping periods are fine: records that are already imported are skipped, and each file is checked on its own. To bring the portfolio up to date later, download a new statement for the current year and import it; only the new records are added.

The files contain your account number. Keep them private, and delete them from Downloads once they are imported: the app keeps its own encrypted copy, and stores the account number only masked.

If the import says the format is not supported, check that the statement is an **Activity Statement**, in **CSV**, in **English**. IBKR may label menus slightly differently over time; the statement name and these options stay the same.

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

## On iPhone

- **Moving files to the phone:** save the reports in **Files** or **iCloud Drive** (for example with AirDrop or from Safari) and pick them there.
- **Backup folder:** choose a folder in iCloud Drive or another provider in Files. A folder that is only on this iPhone works, but the app warns you that it will not survive losing the phone.
- **New iPhone:** there is no automatic copy of the backup key yet, so restoring always asks for your **passphrase** or **recovery code**.
- **Lock and privacy:** the app locks with Face ID, Touch ID or your passcode, and hides its contents in the app switcher. iOS does not let apps block screenshots.
- **Erase all data** resets the app in place instead of restarting it.

## Privacy and security

The full privacy notice is in [PRIVACY.md](PRIVACY.md); this is the short version.

- Reports are read and processed **on the device**; their contents are never sent anywhere. Data is stored in an encrypted database, and account identifiers are masked on screen.
- Backups are **encrypted on the device** before they are written to your backup folder. Keep your passphrase and recovery code: without them the backups cannot be decrypted.

## Moving to a new phone

1. Install the app on the new phone and open the import screen (Classic: Imports tab; Calm: "+"; Bento: Data → Add reports; Focus: "+" → Add reports).
2. Under **Restore from a backup**, tap **Choose backup folder** and pick the folder that holds your backups (or the folder that contains "Portfolio Companion backups").
3. On Android, if you set up the new phone from your old phone's Android backup and both phones have a screen lock, the app unlocks the backup by itself. Otherwise (and always on iPhone) enter your **passphrase** or **recovery code**.

All reports come back, new imports are backed up to the same folder again, and the old backups stay as they are. A wrong passphrase or code restores nothing.
- The app locks with your device's screen lock and blocks screenshots and previews in the app switcher. Without a device screen lock it cannot verify it is you; set one in system settings.
- Network requests: prices (ticker symbols only) and exchange rates (currency codes only).

## Deleting data

Open the list of imported reports (Imports tab, the import screen, Bento's "Reports imported", or Focus's "+" → "Delete imported data"):

- **Delete this import** removes records that only that report contains, plus its encrypted backup file. Overlapping reports stay complete.
- **Delete all imported data** asks for your backup passphrase and removes all reports and their backup files. Backup keys stay unchanged.
- **Erase all data** (at the end of Settings) removes everything: reports and records, every backup file and the key manifest in the backup folder, the backup keys (also the automatic copy), your price key and all settings. You confirm by typing ERASE, so it works even if you forgot your passphrase. Without the keys, a copy left in your storage provider's trash cannot be read. The app then restarts as if newly installed.
