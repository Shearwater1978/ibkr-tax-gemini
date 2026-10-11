## Purpose

Keeps the app independent of any one broker or exchange-rate source in what users see, so it can support more brokers and the user's own currency later.

## ADDED Requirements

### Requirement: Use a neutral app name
The app SHALL be named "Portfolio Companion" wherever its name is shown, for example the launcher, the lock prompt and copied recovery codes. Its internal application identifier SHALL NOT change, so installed apps update in place.

#### Scenario: Launcher
- **WHEN** the user looks at the app on the home screen
- **THEN** it is labelled "Portfolio Companion"

### Requirement: Do not name the exchange-rate source
Screens SHALL NOT name the source of the exchange rates used for converted amounts. Converted amounts SHALL still be labelled approximate and SHALL show the date of the rates used, for example "Approximate, in USD · exchange rates of 2026-10-09".

#### Scenario: USD total
- **WHEN** the portfolio total includes converted amounts
- **THEN** the label says approximate and gives the rate date, and names no rate source

### Requirement: Name a broker only when it was chosen
Fixed texts SHALL NOT name a broker or a broker-specific report format unless the user has chosen that broker (see `mobile-broker-choice`). Without a chosen broker, texts SHALL speak of "broker reports".

#### Scenario: No broker chosen
- **WHEN** no broker is chosen and the user opens the empty portfolio
- **THEN** the text asks for broker reports and names no broker or format

#### Scenario: Interactive Brokers chosen
- **WHEN** the user chose Interactive Brokers
- **THEN** import texts may say "IBKR Activity Flex Query CSV"

### Requirement: Name new backup folders neutrally
A newly chosen backup location SHALL use a subfolder named "Portfolio Companion backups". A backup location chosen before this change SHALL keep its existing subfolder, so its backups stay in place.

#### Scenario: Existing backup folder
- **WHEN** the user set up "IBKR Tax Assistant backups" before this change
- **THEN** backups continue to go to that folder until the user chooses a new location
