# Accessibility decisions

The app follows A11Y.md (WCAG 2.2 AA profile) on top of a project floor of WCAG 2.1 AA. Where the report and A11Y.md disagree, the stricter rule applies. The five decisions below are the places where the app deliberately departs from a literal reading of a rule, or where a rule needs a stated boundary. Each one has a date, an approver and the place where it is enforced.

## 1. The wallet timeout is extendable and cannot warn 20 seconds ahead

AUTH-04 asks for a 15 second wait for the wallet. A11Y.md asks that a time limit can be extended and that the user is warned at least 20 seconds before it ends. A 15 second limit is shorter than the warning window, so the warning cannot exist. The app handles it this way: the 15 second mark never discards anything. It opens a panel with "Keep waiting for wallet" (restarts the 15 second timer, with no limit on how many times), "Reconnect wallet" and a cancel action that names what it cancels, for example "Cancel payment". The same rule applies after a transaction hash arrives: a slow confirmation never turns into a failure.

- Date: 2026-11-09
- Approver: Juan
- Enforced by: unit tests of the transaction state machine with a virtual clock (plan for the Transaction Status sheet) and review of the copy table

## 2. Typography keeps 6 sizes and 4 weights, with raised line heights

The report 6.1.1 type scale has six sizes (32, 24, 18, 16, 14, 12 sp) and four weights (400, 500, 600, 700). The usual checker default is fewer sizes and two weights. Juan decided to keep the ratified brand tokens unchanged. Separately, the report line heights for Body M (20), Label (20) and Caption (16) are below 1.5 times the size, so the app raises them to 21, 21 and 18 sp to meet A11Y.md. Headings and Display keep the report values.

- Date: 2026-11-09
- Approver: Juan
- Enforced by: `TypographyTokensTest` (exact sizes, weights, 1.5 line height on Body L, Body M, Label and Caption, tabular figures on amounts)

## 3. Large text mode swaps the bottom bar for a drawer

When the system font scale is 1.3 or more, the bottom bar and the navigation rail are replaced by a modal navigation drawer opened from a Menu button in the top bar. A bar slot is about 90 dp wide and a ten character label at 200 percent is about 130 dp, so the labels would truncate. Drawer items are full width and 56 dp high, with an icon and a label.

- Date: 2026-11-09
- Approver: Juan
- Enforced by: emulator UI tests at font scales 1.3, 1.5 and 2.0 in the `android-instrumented` workflow (added with the navigation plan) and review

## 4. The Reown AppKit surface is third party UI

AppKit draws its own wallet picker and signature modal, and this app cannot change its internals. It is checked with TalkBack on a device. If it fails an A11Y.md rule, the failure goes into `EXCEPTIONS.md` with an owner, an approver, a tracking issue and an expiry date, and the app's own screens stay compliant.

- Date: 2026-11-09
- Approver: Juan
- Enforced by: the manual device checklist (written with the last Android plan) and review of `EXCEPTIONS.md` entries

## 5. One primary button per screen

Each screen has a single primary action. The invest feed therefore shows outlined secondary Fund buttons on every card, and the request detail screen carries the one primary "Fund request". When the feed is empty because of filters, "Clear filters" is the primary and there are no cards.

- Date: 2026-11-09
- Approver: Juan
- Enforced by: review of each screen plan against the UI-SPEC screen table, and the copy guards (`NoGenericLabelsTest`, `NoHardcodedUiTextTest`) for the labels and roles of the actions
