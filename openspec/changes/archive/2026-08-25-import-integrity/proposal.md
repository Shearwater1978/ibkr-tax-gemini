# Proposal: Make Imports Safe and Complete

## Problem

`save_to_database` deletes the entire transaction table before every import. This makes a partial or malformed CSV set destructive, and the documented repeated-import behavior is not reliable. Corporate-action parsing also loses split ratios before data reaches FIFO.

## Goal

Make imports atomic, repeatable, and non-destructive while preserving enough corporate-action information for correct FIFO processing.

## Non-goals

- Redesigning the existing transaction schema beyond the minimum metadata needed for source identity and corporate-action ratios.
- Changing the FIFO matching policy.
- Adding remote synchronization or multi-user database access.