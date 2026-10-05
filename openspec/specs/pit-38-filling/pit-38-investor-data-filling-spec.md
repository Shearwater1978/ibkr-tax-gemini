# PIT-38 Investor Data-Filling Specification

## Purpose

Automatically prepare data for completing the Polish PIT-38 tax return for an individual investor who earns income from:

- selling shares and other securities;
- selling short;
- selling financial instruments;
- selling shares received through non-cash contributions;
- dividends and other income from participation in legal entities;
- foreign investment accounts and foreign-source income;
- tax paid abroad.

The system MUST calculate tax-related values from imported broker statements and dividend records, while preserving the source data, currency conversions, tax calculations, and audit trail for every populated PIT-38 field.

The system MUST distinguish between:

1. income from the sale of securities and financial instruments, reported mainly in sections C–D;
2. dividend and other income subject to lump-sum taxation, reported mainly in sections E–G;
3. foreign income and tax paid abroad;
4. administrative and taxpayer-identification data.

The system MUST NOT submit the tax return automatically unless a separate explicit submission workflow has been implemented and confirmed by the taxpayer.

## Scope

The system MUST support the PIT-38 form version represented by the provided screenshots, including the following areas:

- form metadata and tax year;
- tax office;
- taxpayer identification and address;
- income and loss under Article 30b;
- calculation of tax on securities transactions;
- income and costs under Article 30b(1a);
- lump-sum tax on dividends and similar income;
- tax paid abroad;
- payable tax or overpayment;
- optional 1.5% transfer to a public benefit organisation;
- additional information;
- bank account for refund;
- large family card information;
- taxpayer signature or representation data.

The implementation SHOULD model the form version explicitly because PIT-38 fields and legal rules may change between tax years.

## Source Data

### Requirement: Broker transaction import

The system MUST import transaction records from one or more brokers.

Each transaction SHOULD contain:

- broker identifier;
- account identifier;
- transaction identifier;
- instrument identifier;
- ISIN, ticker, or other security identifier;
- instrument name;
- transaction type;
- buy or sell direction;
- quantity;
- execution price;
- gross transaction value;
- commission;
- other transaction costs;
- transaction currency;
- transaction date;
- settlement date, if available;
- withholding tax, if present;
- source document reference.

The system MUST preserve the original broker values without overwriting them during normalization.

#### Scenario: Import of a broker statement

- **WHEN** a broker statement is imported
- **THEN** the system creates normalized transaction records
- **AND** each normalized record remains linked to the source file and original broker record
- **AND** parsing errors are reported without silently discarding records

#### Scenario: Unsupported transaction type

- **WHEN** the imported statement contains a transaction type that cannot be mapped to PIT-38
- **THEN** the system marks the record as requiring review
- **AND** MUST NOT include it in tax calculations automatically
- **AND** reports the reason and source record

### Requirement: Dividend import

The system MUST import dividend records separately from securities-sale transactions.

Each dividend record SHOULD contain:

- payer name;
- payer country;
- security identifier;
- gross dividend amount;
- dividend currency;
- payment date;
- Polish tax withheld;
- foreign tax withheld;
- broker commission or fee;
- net amount received;
- source account;
- source document reference;
- tax classification status.

The system MUST distinguish gross dividend income from taxes withheld and fees.

#### Scenario: Dividend with foreign withholding tax

- **WHEN** a foreign broker reports a dividend with foreign tax withheld
- **THEN** the system stores the gross dividend and foreign tax as separate values
- **AND** converts both values using the configured exchange-rate method
- **AND** makes the amount available for the foreign-tax calculation
- **AND** does not treat the foreign tax as a reduction of gross dividend income

### Requirement: Taxpayer profile

The system MUST support a taxpayer profile containing:

- surname;
- first name;
- date of birth;
- country of residence;
- voivodeship;
- county;
- municipality;
- street;
- building number;
- apartment number;
- locality;
- postal code;
- tax office;
- PESEL or NIP, depending on the applicable form rules;
- Polish tax residency status;
- bank account for a possible refund;
- Karta Dużej Rodziny status;
- taxpayer or authorized representative details.

The system MUST validate that required identification fields are present before generating the completed form.

#### Scenario: Missing required identification data

- **WHEN** the taxpayer profile lacks a required field
- **THEN** form generation fails with a field-specific validation error
- **AND** the system identifies the PIT-38 field that requires attention

### Requirement: Tax year and form version

The system MUST require an explicit tax year.

The system MUST associate the calculation with a concrete PIT-38 form version and configuration.

The system MUST NOT use the latest form version implicitly when generating a declaration for a historical tax year.

#### Scenario: Historical tax return

- **WHEN** the user selects a tax year different from the current year
- **THEN** the system loads the corresponding form version and tax configuration
- **AND** calculates the return using the rules configured for that tax year

## Currency Conversion

### Requirement: Deterministic exchange-rate conversion

The system MUST convert foreign-currency amounts into PLN before populating tax fields.

For each conversion, the system MUST store:

- original amount;
- original currency;
- converted PLN amount;
- exchange rate;
- exchange-rate date;
- exchange-rate source;
- conversion rule;
- rounding stage.

The conversion method MUST be configurable and MUST be applied consistently to income, costs, dividends, and foreign tax.

#### Scenario: Foreign securities transaction

- **WHEN** a securities transaction is denominated in a foreign currency
- **THEN** the system converts the revenue and eligible costs into PLN
- **AND** stores the conversion details
- **AND** uses the PLN values in subsequent tax calculations

#### Scenario: Missing exchange rate

- **WHEN** a required exchange rate is unavailable
- **THEN** the affected record is marked as unresolved
- **AND** the system MUST NOT invent or silently reuse an unrelated rate
- **AND** the form cannot be finalized until the issue is resolved or explicitly overridden

## Transaction Classification

### Requirement: Classification of securities income

The system MUST classify transactions into tax-relevant categories.

At minimum, it MUST distinguish:

- sale of shares;
- sale of bonds;
- sale of investment certificates;
- sale of derivatives or other financial instruments;
- short sale;
- purchase transactions;
- fees and commissions;
- dividends;
- corporate actions;
- transfers without a sale;
- redemptions or maturities;
- transactions requiring manual review.

Only taxable disposal transactions MUST contribute to revenue in the securities-sale calculation.

#### Scenario: Purchase transaction

- **WHEN** a purchase transaction is imported
- **THEN** it MUST NOT be treated as current-year revenue
- **AND** its eligible cost MUST be retained for matching against a later disposal where applicable

#### Scenario: Sale transaction

- **WHEN** a sale transaction is imported
- **THEN** its revenue is included in the applicable securities-sale calculation
- **AND** the corresponding eligible acquisition costs and transaction costs are considered according to the selected accounting method

#### Scenario: Stock transfer between accounts

- **WHEN** securities are transferred between the taxpayer’s own accounts without disposal
- **THEN** the transfer MUST NOT be treated as revenue
- **AND** the system preserves the transfer as a non-taxable event

### Requirement: Cost allocation

The system MUST calculate eligible costs separately from revenue.

Costs MAY include, depending on the transaction and applicable rules:

- acquisition price;
- broker commission;
- transaction fee;
- exchange fee;
- other directly related documented costs.

The system MUST preserve the distinction between:

1. costs incurred in the current tax year;
2. costs incurred in earlier years and carried forward;
3. costs that are not eligible;
4. costs requiring manual classification.

#### Scenario: Sale with commission

- **WHEN** a security is sold and the broker charges a commission
- **THEN** the system records the gross sale revenue separately
- **AND** records the commission as a separate cost
- **AND** includes the commission in the cost calculation only if classified as eligible

## Section C — Income or Loss Under Article 30b

### Requirement: Populate section C

The system MUST populate section C for income or loss from transactions covered by Article 30b.

The system MUST support the following logical values:

- revenue from transactions reported in the information section;
- other revenue;
- total revenue;
- costs of obtaining revenue;
- total income;
- total loss.

The internal model MUST map these values to the corresponding PIT-38 field numbers.

The system SHOULD expose the following field mappings for the form version shown in the screenshots:

| PIT-38 field | Meaning |
|---:|---|
| 20 | Revenue from information reported in PIT-8C |
| 21 | Costs related to field 20 |
| 22 | Other revenue |
| 23 | Costs related to field 22 |
| 24 | Total revenue |
| 25 | Total costs |
| 26 | Income |
| 27 | Loss |

#### Scenario: Positive result

- **WHEN** total revenue exceeds total eligible costs
- **THEN** field 26 is populated with the positive difference
- **AND** field 27 is set to zero or left empty according to the electronic-form requirements

#### Scenario: Negative result

- **WHEN** total eligible costs exceed total revenue
- **THEN** field 27 is populated with the loss
- **AND** field 26 is set to zero or left empty according to the electronic-form requirements

#### Scenario: No PIT-8C document

- **WHEN** the taxpayer has broker income but no PIT-8C document
- **THEN** the system MUST allow the transaction data to be classified as “other revenue” if legally applicable
- **AND** MUST display a warning requiring taxpayer verification

## Section D — Tax Calculation Under Article 30b

### Requirement: Calculate section D

The system MUST calculate the tax obligation based on values from section C.

The calculation MUST include:

- income or loss;
- taxable base after applicable adjustments;
- tax rate;
- tax due;
- tax paid abroad where applicable;
- final tax after permitted foreign-tax credit.

The system MUST keep intermediate values and calculation explanations.

The system SHOULD expose the following field mappings for the form version shown in the screenshots:

| PIT-38 field | Meaning |
|---:|---|
| 28 | Loss from field 27 |
| 29 | Tax base after applicable deduction |
| 30 | Tax rate |
| 31 | Tax before foreign-tax credit |
| 32 | Tax paid abroad, converted to PLN |
| 33 | Tax due after foreign-tax credit |

#### Scenario: No taxable income

- **WHEN** the calculated income is zero or negative
- **THEN** the tax due for section D is zero
- **AND** the system does not produce a negative tax amount

#### Scenario: Tax rate configuration

- **WHEN** the tax calculation is performed
- **THEN** the system loads the rate associated with the selected tax year and form version
- **AND** stores the rate used in the calculation
- **AND** does not hard-code a rate without a tax-year configuration

## Section E — Income and Costs Under Article 30b(1a)

### Requirement: Support separate Article 30b(1a) calculations

The system MUST support income from paid disposal of virtual currencies or another category covered by the applicable PIT-38 form version under Article 30b(1a), if such data is provided.

The system MUST NOT place ordinary securities transactions into section E unless the selected form version and legal classification explicitly require it.

The system SHOULD expose the following field mappings for the form version shown in the screenshots:

| PIT-38 field | Meaning |
|---:|---|
| 34 | Revenue from paid disposal |
| 35 | Costs incurred in the tax year |
| 36 | Costs incurred in previous years and not deducted |
| 37 | Income under Article 30b(1a) |
| 38 | Costs carried forward to later years |

#### Scenario: No Article 30b(1a) income

- **WHEN** the taxpayer has no records applicable to section E
- **THEN** fields 34–38 remain empty or zero according to the electronic-form rules
- **AND** the system does not duplicate section C values in section E

## Section F — Lump-Sum Tax Under Article 30b(1a)

### Requirement: Calculate section F

The system MUST calculate the tax for section F only from values classified for section E.

The system SHOULD expose the following field mappings:

| PIT-38 field | Meaning |
|---:|---|
| 39 | Tax base |
| 40 | Tax rate |
| 41 | Tax before foreign-tax credit |
| 42 | Tax paid abroad |
| 43 | Tax due |

#### Scenario: Foreign tax in section F

- **WHEN** foreign tax is associated with income reported in section E
- **THEN** it is converted into PLN
- **AND** applied only within the applicable limitation
- **AND** the applied amount is shown separately from the unused amount

## Section G — Tax Payable or Overpayment

### Requirement: Calculate section G

The system MUST calculate the amount payable or overpayment from the relevant tax sections and payments already made.

The system SHOULD expose the following field mappings:

| PIT-38 field | Meaning |
|---:|---|
| 44 | Lump-sum tax paid by the payer |
| 45 | Lump-sum tax on dividends and similar income |
| 46 | Tax paid abroad under the applicable provision |
| 47 | Difference between lump-sum tax and foreign tax |
| 48 | Sum of advance payments transferred by payers |
| 49 | Tax payable |
| 50 | Overpayment |

The system MUST ensure that fields 49 and 50 are mutually exclusive.

#### Scenario: Tax payable

- **WHEN** total tax exceeds eligible payments and credits
- **THEN** field 49 contains the positive difference
- **AND** field 50 is zero or empty according to the form rules

#### Scenario: Overpayment

- **WHEN** eligible payments exceed total tax
- **THEN** field 50 contains the positive difference
- **AND** field 49 is zero or empty according to the form rules

#### Scenario: Negative final difference

- **WHEN** an intermediate calculation produces a negative amount for a field defined as non-negative
- **THEN** the system writes zero for that field
- **AND** retains the original intermediate value in the calculation audit log

## Dividend Tax Calculation

### Requirement: Classify dividends separately

The system MUST classify dividends and similar income separately from capital gains from selling shares.

Dividends MUST NOT be added to section C revenue unless the selected tax rules explicitly require such treatment.

The system MUST calculate dividend-related values using:

- gross dividend;
- applicable Polish tax;
- foreign tax paid;
- permitted foreign-tax credit;
- tax already withheld in Poland;
- tax payable or overpayment.

#### Scenario: Polish dividend

- **WHEN** a Polish payer withholds tax from a dividend
- **THEN** the system stores gross income and withheld tax separately
- **AND** includes the amount in the applicable dividend-tax section
- **AND** does not classify the dividend as securities-sale revenue

#### Scenario: Foreign dividend

- **WHEN** a foreign payer distributes a dividend
- **THEN** the system records the gross dividend in PLN
- **AND** records foreign withholding tax in PLN
- **AND** calculates any additional Polish liability using the configured rules
- **AND** preserves the source-country and treaty information

#### Scenario: Dividend tax certificate unavailable

- **WHEN** the user has dividend income but no tax certificate or reliable withholding-tax data
- **THEN** the system marks the foreign-tax amount as uncertain
- **AND** prevents finalization unless the user explicitly confirms or corrects the amount

## Tax Paid Abroad

### Requirement: Track foreign tax

The system MUST track tax paid abroad independently from income.

For each foreign-tax amount, the system MUST retain:

- country;
- source income;
- original amount;
- currency;
- PLN equivalent;
- exchange rate;
- applicable limitation;
- amount used;
- amount not used;
- supporting document.

The system MUST NOT use foreign tax to reduce tax beyond the applicable legal limitation.

#### Scenario: Foreign tax exceeds permitted credit

- **WHEN** foreign tax exceeds the amount permitted to be credited
- **THEN** only the permitted amount is used in the current return
- **AND** the unused amount is displayed as requiring separate treatment
- **AND** the system does not silently discard the difference

## Section I — Income Reported Under Article 45(3c)

### Requirement: Support section I

The system MUST support the declaration of income or revenue reported under the applicable Article 45(3c) provision.

The system SHOULD map the value to field 63.

#### Scenario: No applicable income

- **WHEN** no income falls under the applicable Article 45(3c) category
- **THEN** field 63 remains empty or zero according to the form version

## Section J — 1.5% Public Benefit Organisation

### Requirement: Validate 1.5% allocation

The system MAY support allocation of 1.5% of tax to a public benefit organisation.

The system MUST require:

- KRS number;
- allocated amount;
- optional purpose-specific information, if supported.

The system MUST validate that:

- the KRS number has the correct format;
- the organisation is eligible according to the configured reference data;
- the allocated amount does not exceed the permitted percentage of the relevant tax;
- the amount is rounded according to the form rules.

The system SHOULD expose:

| PIT-38 field | Meaning |
|---:|---|
| 64 | KRS number |
| 65 | Amount transferred |

#### Scenario: Invalid KRS number

- **WHEN** the entered KRS number is invalid or not eligible
- **THEN** form generation fails validation for section J
- **AND** no allocation amount is generated

#### Scenario: Allocation exceeds limit

- **WHEN** the requested allocation exceeds the permitted limit
- **THEN** the system rejects the value
- **AND** displays the maximum allowed amount

## Section K — Additional Information

### Requirement: Support additional information

The system MUST support optional additional information.

It SHOULD support:

- selection of the 1.5% allocation consent;
- free-text notes;
- warnings requiring taxpayer review;
- references to imported broker documents;
- explanations for manual overrides.

The system SHOULD expose:

| PIT-38 field | Meaning |
|---:|---|
| 66 | Purpose-specific information |
| 67 | Consent checkbox |
| 68 | Additional information |

#### Scenario: Manual adjustment

- **WHEN** a user changes an automatically calculated value
- **THEN** the system requires a reason
- **AND** records the previous value, new value, user, timestamp, and reason
- **AND** optionally adds a note to field 68

## Section L — Attachments

### Requirement: Track attachments

The system MUST support declaration of the number of attached PIT/ZG forms where foreign income requires them.

The system SHOULD expose field 69.

The system MUST calculate the expected number of attachments from the configured foreign-income records.

#### Scenario: Foreign income requiring PIT/ZG

- **WHEN** the return contains foreign income requiring an attachment
- **THEN** the system increments the attachment count
- **AND** identifies which income records caused the requirement

#### Scenario: Attachment count mismatch

- **WHEN** the manually entered attachment count differs from the calculated count
- **THEN** the system displays a warning
- **AND** requires explicit confirmation before finalization

## Section M — Refund Bank Account

### Requirement: Validate refund account

The system MAY populate a bank account for receiving an overpayment refund.

The system MUST support:

- account holder;
- country of bank;
- account currency;
- full account number;
- IBAN;
- SWIFT/BIC for foreign accounts.

The system MUST validate the account format according to the selected country.

The system MUST mask account numbers in logs and user-facing diagnostics.

The system SHOULD expose:

| PIT-38 field | Meaning |
|---:|---|
| 70 | Account holder |
| 71 | Bank country |
| 72 | Account currency |
| 73 | Full account number, IBAN, and SWIFT |

#### Scenario: Polish IBAN

- **WHEN** a Polish bank account is entered
- **THEN** the system validates the IBAN checksum and Polish account structure
- **AND** stores the normalized account value
- **AND** does not expose the full account number in logs

#### Scenario: Foreign account

- **WHEN** a foreign account is entered
- **THEN** the system requires the country, currency, IBAN or local account number, and SWIFT/BIC where applicable
- **AND** validates the required fields for that country

## Section N — Karta Dużej Rodziny

### Requirement: Support KDR declaration

The system MAY support the Karta Dużej Rodziny declaration.

The system SHOULD expose field 74.

The system MUST NOT select this field automatically unless the taxpayer profile explicitly confirms eligibility.

#### Scenario: KDR not confirmed

- **WHEN** the taxpayer has no confirmed KDR status
- **THEN** the checkbox remains unselected
- **AND** the system displays no automatic assumption

## Section O — Signature

### Requirement: Prepare signature data

The system MUST support the taxpayer or authorized representative data required for signing.

The system SHOULD expose:

| PIT-38 field | Meaning |
|---:|---|
| 75 | Taxpayer signature or name |
| 76 | Name and surname of authorized person |

The system MUST distinguish between:

- the taxpayer signing personally;
- an authorized representative signing on behalf of the taxpayer.

#### Scenario: Missing signature data

- **WHEN** the form is exported without the required signature or signing metadata
- **THEN** the export is marked incomplete
- **AND** the system prevents the return from being represented as ready for submission

## Field Mapping

### Requirement: Stable internal field identifiers

The system MUST use stable internal field identifiers independent of UI labels.

Each field definition MUST contain:

- internal identifier;
- PIT-38 field number;
- section;
- label;
- data type;
- required or optional status;
- source;
- calculation rule;
- validation rules;
- tax-year applicability;
- output formatting rule.

Example:

```yaml
- id: pit38.section_c.revenue_pit8c
  form: PIT-38
  field_number: 20
  section: C
  label: Przychody wykazane w informacji PIT-8C
  type: money_pln
  source: broker_transactions
  calculated: true
  required: false
```

The implementation MUST NOT rely only on screen coordinates or OCR positions to populate the form.

#### Scenario: Form layout changes

- **WHEN** the Polish tax authority changes the visual layout of PIT-38
- **THEN** the internal field identifiers remain stable
- **AND** only the form-version mapping requires modification

## Validation

### Requirement: Validate before export

The system MUST execute validation before producing a completed PIT-38 document.

Validation MUST cover:

- required taxpayer information;
- tax year;
- form version;
- valid numeric values;
- non-negative fields;
- mutually exclusive income and loss fields;
- currency conversion completeness;
- dividend classification;
- foreign-tax limitations;
- KRS format and allocation limit;
- bank account format;
- attachment count;
- signature metadata.

The system MUST produce errors separately from warnings.

#### Scenario: Blocking validation error

- **WHEN** a required field is missing or a calculated value is inconsistent
- **THEN** export is blocked
- **AND** the error identifies the section, PIT-38 field number, and corrective action

#### Scenario: Non-blocking warning

- **WHEN** the data is technically complete but requires taxpayer verification
- **THEN** the system allows draft export
- **AND** marks the document as requiring review
- **AND** includes the warning in the audit report

## Rounding and Numeric Precision

### Requirement: Preserve calculation precision

The system MUST preserve sufficient precision during intermediate calculations.

The system MUST apply rounding only at the stages required by the configured PIT-38 rules.

The system MUST distinguish:

- currency amount precision;
- tax calculation precision;
- final form-field precision;
- display precision.

All monetary fields in the generated form MUST be represented in PLN with złoty and grosz fields where required by the form.

#### Scenario: Fractional grosz

- **WHEN** an intermediate calculation produces a fraction of a grosz
- **THEN** the system retains the unrounded intermediate result
- **AND** applies the configured rounding rule at the final required stage
- **AND** records the rounding operation

## Auditability

### Requirement: Calculation audit trail

The system MUST create an audit trail for every populated or calculated field.

The audit record MUST contain:

- PIT-38 field number;
- resulting value;
- source records;
- formula or rule;
- exchange rates used;
- rounding operations;
- manual overrides;
- timestamp;
- application version;
- form version.

#### Scenario: Explain field value

- **WHEN** the user requests an explanation for a populated field
- **THEN** the system shows how the value was derived
- **AND** lists the source broker records and intermediate calculations

## Privacy and Security

### Requirement: Protect financial data

The system MUST protect broker statements, tax records, bank account numbers, and taxpayer identifiers.

It MUST:

- avoid logging raw account numbers;
- avoid logging complete tax identifiers;
- avoid logging authentication tokens;
- encrypt sensitive stored data where applicable;
- restrict access to taxpayer records;
- record access and modifications;
- securely delete temporary imported statements where configured.

#### Scenario: Diagnostic logging

- **WHEN** an import or calculation error is logged
- **THEN** sensitive values are masked
- **AND** the log contains enough metadata to diagnose the issue without exposing financial data

## Export

### Requirement: Generate a completed PIT-38 draft

The system MUST generate a completed PIT-38 draft in a supported output format.

The export MUST:

- use the selected tax year and form version;
- populate only fields supported by the selected form;
- preserve empty fields where no value applies;
- include validation status;
- include warnings and unresolved records;
- include an audit report separately or as metadata.

The generated form MUST be clearly marked as:

- draft;
- ready for taxpayer review;
- or ready for submission, only if all blocking validations pass and the workflow explicitly supports that state.

#### Scenario: Draft generation with warnings

- **WHEN** no blocking errors exist but warnings remain
- **THEN** the system generates the draft
- **AND** marks it as requiring taxpayer review
- **AND** includes the warnings in the accompanying report

#### Scenario: Export with blocking errors

- **WHEN** one or more blocking errors exist
- **THEN** the system does not generate a final completed return
- **AND** provides a structured error report

## Reconciliation

### Requirement: Reconcile imported data with broker documents

The system MUST provide a reconciliation view between:

- imported transactions;
- imported dividends;
- broker annual statements;
- PIT-8C values;
- calculated PIT-38 fields.

The system SHOULD identify:

- missing transactions;
- duplicated transactions;
- unmatched dividends;
- differences in revenue;
- differences in costs;
- differences in taxes withheld;
- currency-conversion discrepancies.

#### Scenario: Broker total differs from calculated total

- **WHEN** the broker’s annual revenue total differs from the system’s calculated revenue
- **THEN** the system reports the difference
- **AND** identifies the transactions causing the difference where possible
- **AND** prevents silent finalization

## Example End-to-End Scenario

### Scenario: Investor with Polish and foreign shares

- **GIVEN** an investor has:
  - Polish and foreign share sales;
  - broker commissions;
  - Polish dividends;
  - foreign dividends;
  - foreign withholding tax;
  - a valid taxpayer profile;
  - a selected tax year and PIT-38 version
- **WHEN** the investor imports broker statements and requests PIT-38 generation
- **THEN** the system:
  - normalizes all transactions;
  - separates sales from purchases and dividends;
  - converts foreign amounts into PLN;
  - calculates revenue and eligible costs;
  - calculates income or loss under Article 30b;
  - calculates dividend-related tax separately;
  - applies foreign-tax credits within configured limits;
  - calculates tax payable or overpayment;
  - validates the taxpayer and bank-account data;
  - calculates the required PIT/ZG attachment count;
  - produces a draft PIT-38;
  - produces an audit report explaining every populated field;
  - marks unresolved or manually overridden data for taxpayer review.

## Non-Goals

The first version MUST NOT:

- provide legal or tax advice as a substitute for a tax adviser;
- automatically infer uncertain tax classifications without marking them;
- submit the tax return to the Polish tax authority without explicit confirmation;
- treat all broker-reported “income” values as legally equivalent;
- merge dividends into securities-sale revenue;
- discard foreign-tax data after conversion;
- overwrite original imported broker data;
- silently round or alter monetary values;
- assume that a form screenshot represents the current form version for every tax year.
