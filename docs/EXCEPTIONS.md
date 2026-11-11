# Accessibility exceptions register

This file follows the governance template of A11Y.md. An exception is a known departure from an accessibility rule that the team cannot fix right now. Every entry has an owner, an approver, a tracking issue and an expiry date. An entry without an expiry is not allowed. When the expiry passes, the entry is either fixed or renewed on purpose by the approver.

The rule that goes with every entry: our own screens stay compliant. An exception covers only the surface named in it.

## Template

| Field | Meaning |
| ----- | ------- |
| ID | `EX-NN` |
| Surface | The screen, component or third party UI that fails |
| Rule | The A11Y.md rule, or the WCAG criterion, that fails |
| Description | What fails and for whom |
| Owner | The person who fixes it or removes the dependency |
| Approver | The person who accepted the departure |
| Tracking issue | A link, or "not applicable, backlog pending" until the Jira backlog exists |
| Status | open, pending the device check, fixed, expired |
| Expiry | A date or a phase boundary |
| Mitigation | What the app does in the meantime |

## EX-01: Reown AppKit wallet picker and signature modal

| Field | Value |
| ----- | ----- |
| ID | EX-01 |
| Surface | The wallet picker that Reown AppKit 1.6.17 draws. It is third party UI hosted as a Material 2 bottom sheet destination in `ui/wallet/AppKitHost.kt` |
| Rule | A11Y.md rules for names, roles, focus order, contrast and touch targets, applied to a surface the app cannot edit |
| Description | The app cannot change AppKit internals. Its theming, contrast, TalkBack reading order and the long text of wallet names and errors are unknown until a phone runs it with a real Reown project id (UI-SPEC "A11Y decisions to record", item 4, and the Accessibility Contract for the Reown AppKit surface). No failure is recorded yet |
| Owner | Claude |
| Approver | Juan |
| Tracking issue | Not applicable, backlog pending |
| Status | pending the device check (`docs/DEVICE-CHECKLIST.md`, rows 1.2 and 1.3) |
| Expiry | End of Phase 7 |
| Mitigation | AppKit shows only while the person picks a wallet. Every other screen is our own Material 3 UI. The "I do not have a wallet" help sheet is our own accessible screen. A failure found on the device is written below as a sub entry with the exact control, then reported to Reown |

Rules that stay in force while this entry is open:

1. Material 2 imports exist only in `AppKitHost.kt`. A grep gate in the plan checks it.
2. No screen of ours depends on AppKit for information. The connect state and its errors are our own copy.
3. The 15 second timeout panel, the sign-in sheet and the transaction sheet are our own and follow the A11Y decisions.

### Device findings

Fill this table after the device run. Leave it empty when nothing failed.

| Date | Control | Rule | What failed | Issue |
| ---- | ------- | ---- | ----------- | ----- |
| | | | | |
