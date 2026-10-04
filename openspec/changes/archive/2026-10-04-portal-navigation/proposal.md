## Why

The top bar now holds seven flat tabs and the year selector and Calculate button live on the Tax Calculation page, so users must leave their current view to change the year. The reference portal groups report views in a dropdown menu and keeps global controls in the header.

## What Changes

- Group the Dividends, Analytics, and Portfolio tabs under a "Reports" dropdown menu in the top bar.
- Move the year selector and the Calculate button into the top bar so any view can recalculate.
- Show calculation progress and errors where the user can see them: starting a calculation opens the Tax Calculation page with its loader and status.

## Capabilities

### New Capabilities

### Modified Capabilities
- `web-portal`: add the navigation menu and global calculation controls requirements.

## Impact

- `gui/ui/index.html` only. No API, dependency, or data changes.
