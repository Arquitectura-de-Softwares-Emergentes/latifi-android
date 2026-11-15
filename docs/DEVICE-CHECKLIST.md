# Device checklist

These checks need a physical Android phone, a real wallet app and a real Reown project id. CI cannot run them: the emulator jobs have no wallet and no TalkBack voice setup, and the planning machine has no device. Nothing here blocks a merge. Juan runs the list after Phase 5 and records every result in the last column. A failure that cannot be fixed at once goes into [EXCEPTIONS.md](EXCEPTIONS.md) with an owner and an expiry.

How to read the tables: each row is one check. "Expected" says what a pass looks like. Write `pass`, `fail` or `n/a`, the date, the phone model and the Android version in the "Result" column.

## 0. Setup

1. Create a project at dashboard.reown.com and copy its Project ID. It is not a secret, but it never goes into the repository.
2. In the project settings, add the app id `com.latifi.wallet` and the landing domain to the allowlist. Without this, wallets show an "unverified domain" warning (research Pitfall 11).
3. Set the project id in two places: the GitHub repository variable `REOWN_PROJECT_ID` (for the CI debug APK) and the line `reown.projectId=<id>` in `local.properties` (for a local build).
4. Set the API address the same way: the repository variable `LATIFI_API_BASE_URL` and the line `api.baseUrl=<url>` in `local.properties`. Live mode needs both the project id and the API address. With only the project id the app stays in Demo mode.
5. Build and install the debug APK, either the `latifi-wallet-debug-*` artifact of the latest `develop` run or `./gradlew assembleDebug` locally. If a debug APK from another machine is already installed, uninstall it first: debug signatures differ between machines and the install fails otherwise (research Pitfall 14).
6. Install a wallet app: MetaMask Mobile or Trust Wallet. Turn on test networks and add Polygon Amoy (chain id 80002). A wallet that does not know Amoy rejects the session or the request (research Pitfall 9, assumption A11).
7. Turn TalkBack on (Settings, Accessibility). Install the English and Spanish voices so both languages can be heard.
8. Fund the wallet: a little POL for fees from an Amoy faucet. Test coins (tUSD) come from the app itself.

Record the phone, the Android version, the wallet app and its version here: ______________________________

## 1. Wallet connection and sign-in (AUTH-01, AUTH-02)

| # | Step | Expected | Result |
| - | ---- | -------- | ------ |
| 1.1 | Open the app in Live mode and reach the Connect screen. Tap "Connect wallet". | The AppKit wallet picker opens as a bottom sheet. The app name and the allowlisted domain show as verified in the wallet. | |
| 1.2 | Look at the picker in light and dark theme. | Theming is readable and contrast is acceptable. Note any control that fails A11Y.md. | |
| 1.3 | Read the picker with TalkBack: wallet list, search, close. | Every control has a name and a sensible reading order. Note failures for EXCEPTIONS.md (the AppKit surface entry). | |
| 1.4 | Choose the installed wallet and approve the session in the wallet. | The app returns to the front, Connect shows "Waiting for your wallet", then the sign-in sheet "Confirm it is you" opens. | |
| 1.5 | In the sign-in sheet tap "Confirm in wallet" and sign in the wallet. | The wallet shows the sign-in text unchanged, with line breaks. After signing, the app moves to role selection (or the tabs when a role exists). The API accepts the signature (no `signin_failed`). | |
| 1.6 | Open the picker and close it without choosing a wallet. | Connect stops waiting after about two seconds and shows the primary button again. No stuck spinner. | |
| 1.7 | Open the picker with no wallet installed, and use "I do not have a wallet". | The help sheet explains the next step in plain words. | |
| 1.8 | Check the redirect: in the wallet, after approving, use its "return to app" action (assumption A6). | The app comes to the front through `latifi-wallet://request` and keeps its screen. No data from the link changes anything. | |
| 1.9 | Kill the app from the recents list after signing in, then open it again. | The session survives: the app opens signed in, no new signature is asked (the sealed session store). Connecting state restores from Reown. | |
| 1.10 | Profile, Disconnect, then confirm. | The wallet session ends, the sealed session is removed, Connect shows again. A new sign-in asks for a signature. | |
| 1.11 | With TalkBack, open the Disconnect dialog. | Focus lands on the safe action "Keep wallet connected" (open question from plans 05-04, 05-05, 05-06 and 05-12: `requestFocus()` is refused in touch mode, so confirm what TalkBack really does). | |
| 1.12 | Sign in with a wallet that is a smart contract wallet, if one is at hand. | Sign-in fails with a clear message and the app does not crash (the API verifies only 65 byte signatures and `coinbaseEnabled` is false, so this wallet type is out of scope). | |

## 2. The 15 second timeout and recovery (AUTH-04)

| # | Step | Expected | Result |
| - | ---- | -------- | ------ |
| 2.1 | Start a payment or a test coins request. When the wallet opens, do not answer. | The status sheet shows Pending. After 15 seconds the title "Your wallet has not answered" appears with "Keep waiting for wallet", "Reconnect wallet" and a cancel action that names what it cancels. | |
| 2.2 | Tap "Keep waiting for wallet" three times in a row, each time without answering. | The 15 seconds restart each time. Nothing is discarded. | |
| 2.3 | Tap "Reconnect wallet". | The picker opens. After connecting, the same step is sent again with a new request. | |
| 2.4 | Start a request, then kill the app while the wallet shows the confirmation (state Pending). Approve in the wallet, then open LatiFi again. | The sheet reopens. If the wallet answered while the app was dead, the answer is found and the sheet goes on to Confirming and Confirmed (assumption A2: this decides whether the relay delivers a response published while the app was dead). If not, Pending shows for up to 5 minutes and then the record is reconciled with the chain. The app never sends the transaction twice. | |
| 2.5 | Start a request, approve it, then kill the app during Confirming (hash known). Open it again. | The sheet reopens in Confirming and polls until the receipt. | |
| 2.6 | Wait more than 15 seconds in Confirming (a slow block). | The title stays "Confirming". A notice says it is slow. It never says "Failed" because of delay. | |
| 2.7 | Reject the request in the wallet. | Failed with the declined reason and the right main action. | |
| 2.8 | Disconnect the wallet from inside the wallet app while a request is pending. | Failed with the session reason and "Reconnect wallet". | |
| 2.9 | Pay with a wallet that has no POL. | Failed with the fee reason, "Copy wallet address" announces "Address copied". | |
| 2.10 | Pay or fund with a wallet that has no tUSD. | "Get test coins" is offered. | |

## 3. TalkBack announcements and focus (UI-SPEC verification plan)

Run with TalkBack on, English first, then Spanish.

| # | Step | Expected | Result |
| - | ---- | -------- | ------ |
| 3.1 | Onboarding: tap Next. | TalkBack moves to the new page title and reads "Step 2 of 4". The illustration is silent (05-05). | |
| 3.2 | Open Terms from onboarding page 4 and from Connect. | A Custom Tab opens the Terms page of the configured `TERMS_URL`. | |
| 3.3 | Any screen: read the top bar with a back arrow. | The title is read first, then the back arrow (05-04). | |
| 3.4 | Switch the language in Profile. | After the screen recreates, TalkBack speaks "Idioma cambiado a Español" once, in Spanish (05-06). | |
| 3.5 | Open the language list. | "Español" is read with a Spanish voice while the app is in English, and "English" with an English voice (05-06). | |
| 3.6 | Submit the profile form with an invalid field. | The count of errors is spoken once, focus lands on the first invalid field (05-06). | |
| 3.7 | Save a valid profile. | "Details saved" is announced after the form closes (05-06, it is a platform Toast). | |
| 3.8 | Open the invest feed. Tap three filter chips quickly. | The counter ("12 requests") is read once after the last change, never while the skeleton shows, and "Loading requests" is not announced as a live change (05-07, 05-09). | |
| 3.9 | Read a status chip. | "Status, Pending, selected". Check it is not repeated in a confusing way (05-07). | |
| 3.10 | Read a request card with an estimate. | The sign "≈" is read as "approximately" in both languages (05-07). | |
| 3.11 | Read a merged card in the feed. | The sentence ends with "Double tap to open details" and the chevron is silent (05-09). | |
| 3.12 | Choose a sort option. | "Sort: Rate" is read from the button and focus does not move (05-09). | |
| 3.13 | Remove a filter chip, then use "Clear filters". | "Filter removed" is read and not cut by the counter update 400 ms later. After Clear filters TalkBack follows focus to the counter (05-09). | |
| 3.14 | Open the Filters button and the Levels disclosure on Reputation. | TalkBack announces expanded or collapsed from the Expand and Collapse actions (05-08, 05-09). | |
| 3.15 | Pull to refresh with TalkBack. | The visible Refresh button works. Pull gesture is not required (05-07). | |
| 3.16 | Open Home. | The greeting is announced once and does not compete with the screen title (05-08). | |
| 3.17 | Read the reputation history. | "+20 points" is read as "plus 20 points" (05-08). | |
| 3.18 | Read the next installment card. | The repeated word between the title and the "Next installment: date" line is not tiresome by ear (05-08). | |
| 3.19 | Open the transaction sheet. | TalkBack announces the status title once. The pane title is the action name ("payment" or "pago"). A swipe down or a tap on the scrim does not close it. System Back moves focus to "Cancel payment" and does not close it (05-11). | |
| 3.20 | After 15 seconds of Pending. | "Your wallet has not answered" is announced once, politely. Check whether "Reconnect wallet" takes TalkBack focus in touch mode. If TalkBack stays on the title, the primary action needs another mechanism (05-11). | |
| 3.21 | Reach Failed and then Confirmed. | Failed is announced assertively once. The receipt reads "Receipt, 0x12ab...9f3c", "Copy receipt" announces "Receipt copied", "View receipt online" opens the Amoy explorer in a Custom Tab, and the word "hash" is never spoken (05-11). | |
| 3.22 | Fund a request or pay an installment. | The status sheet shows the allowed amount line on the approve step and the success title and body above the receipt on the last step. TalkBack reads the title once (05-12). | |
| 3.23 | Move between sheets and screens. | Focus moves into a sheet when it opens and returns to the control that opened it when it closes. | |
| 3.24 | Read the Fund request button on the feed card and on the detail screen. | The label names the action and the request, and has one primary button per screen. | |
| 3.25 | Use a keyboard or a D-pad. | The focus ring is visible on buttons, chips and the drawer (05-04). | |

## 4. Wallet requests and money (AUTH-02, LEND-03, LEND-04)

| # | Step | Expected | Result |
| - | ---- | -------- | ------ |
| 4.1 | Pay an installment. Look at the approval in the wallet. | The wallet shows a spend cap of exactly the unpaid amount of the current installment, never a larger or unlimited one (05-12). | |
| 4.2 | Fund a request. Look at the approval in the wallet. | The cap is exactly the principal (05-12). | |
| 4.3 | After the approve is Confirmed, tap "Pay installment" or "Fund request". | The wallet asks a second time for the real action (05-12). | |
| 4.4 | Pay a late installment. Also pay after a partial payment made outside the app. | A late installment pays through the same path. An overpayment revert reloads the installment (05-12). | |
| 4.5 | Look at the network fee in the wallet. | The wallet estimates it. The app never sets a gas value. | |
| 4.6 | Publish a loan request. After Confirmed tap "View my loans". | The new request appears in Loans within 30 seconds on the live indexer (05-12). | |
| 4.7 | Tap "Open wallet" in the status sheet. | The wallet comes to the front. No second request is sent (research F8). | |
| 4.8 | Check the allowed domain in the wallet at each request. | The wallet shows the verified origin from the Reown allowlist. | |

## 5. Keystore and storage (D-03, ADR-0001 envelope pattern)

| # | Step | Expected | Result |
| - | ---- | -------- | ------ |
| 5.1 | Connect a USB debug build and run `adb logcat -s KeystoreEnvelope` after the first sign-in. | One line "Session key security level N". Record N: 0 software, 1 trusted environment, 2 strongbox. A phone with 0 is acceptable for the demo and goes on the record. | |
| 5.2 | Run `adb shell run-as com.latifi.wallet ls files/datastore` on a debug build. | A `latifi_session.preferences_pb` file exists. Opening it shows no address, no token and no readable expiry. | |
| 5.3 | Run `adb logcat` during a full sign-in and one payment. | No address, token, signature, hash or RPC URL appears in any line. | |

## 6. Look, language and numbers

| # | Step | Expected | Result |
| - | ---- | -------- | ------ |
| 6.1 | Look at Home, a loan card and the status sheet. | Sora and Inter render. Amounts use tabular figures, so digits line up in columns (assumption A9). | |
| 6.2 | Set the phone to es-419 or es-PE and open a loan with a local value. | The number format matches what the unit test asserts: `$ 41,234,560.00` and `1,000.00 tUSD`. The device ICU may differ from the JVM data. Write down what the phone shows (05-03, assumption A5). | |
| 6.3 | On a phone with Android 12 or older, change the app language in Profile. | The backport works: the activity recreates in the new language and the form state survives. | |
| 6.4 | On Android 13 or newer, change the app language in system Settings, App languages. | The app follows it. | |
| 6.5 | Set font size to the largest and display size to large, so the font scale is 2.0. Walk through Home, a card, a loan detail, the feed filters, the status sheet and a dialog. | No decision data is truncated. Chips wrap. The bottom bar becomes a drawer. The status sheet scrolls. | |
| 6.6 | On a narrow phone (320 dp wide) repeat 6.5. | The layout reflows with no horizontal scroll. | |
| 6.7 | Look at the bottom inset between the navigation bar and the tab bodies. | The bottom inset is not counted twice (05-04). | |
| 6.8 | Open the app on a tablet or an emulator at 600 dp and 840 dp widths. | The navigation switches between the bar and the rail at the right widths (05-04, `NavigationSuiteScaffold` is not covered by CI). | |
| 6.9 | Turn on "Remove animations". | Motion is reduced and nothing depends on animation to be understood. | |

## 7. Live API (plan 05-13)

| # | Step | Expected | Result |
| - | ---- | -------- | ------ |
| 7.1 | With the real API address, sign in and open every tab. | Each screen loads real data. Errors show the app's own copy, never server text. | |
| 7.2 | Wait 15 minutes with the app open, then open a screen that needs the API. | The sign-in sheet appears once, asks for one signature, and the original action continues (lazy re-authentication). | |
| 7.3 | Open several tabs at the moment the token expires. | Only one signature is asked. | |
| 7.4 | Trigger a 429 if the API allows it. | The countdown shows and the button stays off until it reaches zero. | |
| 7.5 | Turn on airplane mode and reload a list. | The offline error panel shows with Reload. | |

## 8. One live cycle on Polygon Amoy

This needs plan 03-08 to fund the demo wallets, two phones (or one phone and one emulator with a second wallet) and the live API.

| # | Step | Expected | Result |
| - | ---- | -------- | ------ |
| 8.1 | Phone A (borrower): publish a loan request. | Confirmed within 15 seconds under normal Amoy conditions. The request shows in the feed on phone B within about 30 seconds. | |
| 8.2 | Phone B (lender): open the request and fund it. | Approve, then fund, both Confirmed. The loan shows Active on both phones. | |
| 8.3 | Phone A: pay every installment. | After the last one the loan reads Paid on both phones. | |
| 8.4 | Phone A: open Reputation. | The score or the level changed after the repayment. | |
| 8.5 | From the `latifi-api` repository, run the `AmoyLiveTest`. | It passes against the same deployment. | |

## Summary of results

| Section | Pass | Fail | Notes and owner |
| ------- | ---- | ---- | --------------- |
| 1 Wallet connection and sign-in | | | |
| 2 Timeout and recovery | | | |
| 3 TalkBack | | | |
| 4 Wallet requests | | | |
| 5 Keystore and storage | | | |
| 6 Look, language, numbers | | | |
| 7 Live API | | | |
| 8 Live Amoy cycle | | | |
