# Proposal: Establish Database Security

## Problem

The project documents SQLCipher AES-256 protection, but the implementation uses the standard Python `sqlite3` driver and merely executes `PRAGMA key`. The encryption guarantee is therefore unverified in normal installations. Password rotation also interpolates secrets into SQL text and has a broken old-password flow.

## Goal

Make the database security contract truthful and operational: use an explicitly supported SQLCipher backend, fail closed when encryption cannot be established, and provide safe key rotation with verification and recovery guidance.

## Non-goals

- Changing the transaction schema.
- Encrypting report files or source CSV files.
- Reworking the GUI beyond surfacing actionable startup errors.