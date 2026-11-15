# Decision record: Reown AppKit for the wallet connection

Status: accepted, 2026-11-09. Confirms CONTEXT decision D-01 for Phase 5.

## Decision

The app connects to the user's wallet with Reown AppKit 1.6.17, the dapp side SDK that includes the wallet picker. The user's wallet app holds the keys and signs every request. LatiFi never sees a private key.

Reown WalletKit 1.9.0 is the wallet side SDK, for apps that sign. It solves a different problem and is rejected.

## Verified versions

| Piece | Version |
| ----- | ------- |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.21 |
| KSP | 2.3.13 |
| Hilt | 2.60.1 |
| Compose BOM | 2026.09.00 |
| Reown BOM (`android-core`, `appkit`) | 1.6.17 |
| web3j `abi` and `utils` | 4.12.3-android |
| Retrofit | 3.0.0 |
| OkHttp BOM | 5.5.1 |

The versions live in `gradle/libs.versions.toml`. Do not bump them without running `./gradlew lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest`.

## Five build traps and their fixes

1. **JitPack groups.** AppKit and the Reown core need four groups that only JitPack hosts: `com.github.alexzhirkevich`, `com.walletconnect.Scarlet`, `com.github.multiformats` and `com.github.komputing.*`. `settings.gradle.kts` declares one JitPack repository inside `dependencyResolutionManagement` with a content filter for those groups. Never add JitPack without the filter and never add it to `pluginManagement`.
2. **web3j signing artifacts.** The `abi` and `crypto` AARs of the `-android` line declare the same namespace, so the manifest merge fails. The app declares only `abi` and `utils`. Reown brings its own transitive `crypto` jar. `Hash.sha3` lives in `utils`. The app has no use for the signing classes, and `NoKeyMaterialTest` keeps it that way.
3. **junit 4.12.** The kethereum chain pulls `junit:junit:4.12` into the runtime classpath, which breaks the androidTest classpath resolution. Both Reown dependencies exclude `junit:junit`. Run `assembleDebugAndroidTest` after every Reown bump.
4. **Material 2 at compile time.** AppKit hosts its picker in a Material 2 bottom sheet, so `androidx.compose.material` and `material-navigation` must be on the compile classpath. Every Material 2 import lives in `ui/wallet/AppKitHost.kt`. The rest of the app uses Material 3. The `UsingMaterialAndMaterial3Libraries` lint warning is suppressed in that one file.
5. **Current API shapes.** The public Reown documentation and many snippets are stale for 1.6.17. `CoreClient.initialize` requires `onError`. `AppKit.request` takes `com.reown.appkit.client.models.request.Request(method, params, chainId, expiry)`, not `Modal.Params.Request`. The bottom sheet comes from `androidx.compose.material.navigation`, not from Accompanist. Copy shapes from `ReownBootstrap.kt` and `AppKitHost.kt`, which compile against 1.6.17.

Two smaller rules that came from the same work: the Kotlin build script needs `import java.util.Properties` under AGP 9, and `AppCompatActivity` needs an AppCompat theme (`Theme.AppCompat.DayNight.NoActionBar`).

## Stale documentation warning

If a Reown snippet does not compile, trust the compiler and the javap output of the 1.6.17 artifacts over the web page.

## Evidence

- A scratch project built, linted and unit tested this stack on JDK 25 and JDK 21 on 2026-11-09.
- The encoded calldata was accepted by the live Polygon Amoy RPC (`eth_call` for `loanCount`, `balanceOf`, `allowance`, `nextInstallment`), and the error selectors match `Hash.sha3String`.
- This repository reproduces the same result: `lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest` passes locally on JDK 25 and in CI on JDK 21. The first compile used the API shapes of the research without a single change.

## Safe startup without a project id

`ReownBootstrap` starts the SDK only when the project id is a real 32 hex character id. With the placeholder id the app logs one line and skips AppKit, so the app opens and shows that the wallet is not configured. `AppLaunchSmokeTest` checks this on an API 34 emulator in the advisory `android-instrumented` workflow.

## What stays open for a real device

- Pending signature recovery after the process dies while the wallet is in the foreground. The recovery record and the late response behavior need a real wallet.
- The redirect behavior of `launchMode="singleTask"` with the `latifi-wallet://request` filter.
- A real project id, the allowlist for `com.latifi.wallet` and the landing domain in the Reown dashboard, and whether the user's wallet shows the Amoy test network.
- License: the Reown packages use the WalletConnect community license. Cite it in the report.

## Result of the implementation

Plan 05-14 built the live wallet side on top of the shapes above. The adapter, the gateway, the sealed session and the start-up order are on `develop`.

### The adapter

`AppKitClient` is a small interface over the static AppKit API, so the gateway runs on the JVM with a fake. `RealAppKitClient` is the only class that imports Reown for this purpose (besides `ReownBootstrap` and the host composable). Its events are normalized to `SessionApproved(address)`, `SessionRejected`, `SessionDeleted`, `ConnectionError`, `ResponseResult(requestId, result)`, `ResponseError(requestId, code, message)` and `RequestExpired(requestId)`. The research listed six of these. `SessionRejected` was added so a person who declines in the wallet sees the declined message instead of a generic failure.

`AppKitWalletGateway` turns the events into `WalletState` and `WalletResponse`:

- It restores `Connected` from `AppKit.getAccount()` at construction, on an IO dispatcher, with a lowercase address.
- `personal_sign` sends `[0x + utf8 hex, address]`. `eth_sendTransaction` sends one object with `from`, `to`, `data` and `value` and no fee field. Both use `eip155:80002`. The params come from JSON builders, never string templates.
- A result of 132 characters is a signature and one of 66 a transaction hash. Codes 4001 and 5000 are `Rejected`. Codes 4900, 4901, 6000 and 8000 are `SessionLost`. Anything else keeps its code and message.
- `openWalletApp()` follows the session deep link and never calls `request` again. The link comes from the wallet over the relay, so links of the schemes `intent`, `file`, `content`, `javascript`, `android-app` and `data` are never opened.
- A connection error or a rejected proposal changes the state only while the person is connecting, so a relay hiccup cannot end a good session.

### API shapes that differed from the research

None. Every call compiled on the first try with the names the research recorded: `AppKit.ModalDelegate` with `onSessionApproved`, `onSessionRejected`, `onSessionDelete`, `onSessionRequestResponse`, `onRequestExpired` and `onError`; `Modal.Model.JsonRpcResponse.JsonRpcResult(id, result)` and `JsonRpcError(id, code, message)`; `SentRequestResult.WalletConnect.getRequestId()`; `Modal.Model.ApprovedSession.WalletConnectSession.accounts` with CAIP-10 ids; `Modal.Model.Session.getRedirect()`; `AppKit.extend` and `AppKit.disconnect`; `com.reown.appkit.ui.openAppKit` and `appKitGraph`. `AppKit.setDelegate` is declared `throws IllegalStateException`, so the client catches it and the bootstrap tries again when initialization reports success.

### The session

The address, the token and the expiry are one JSON blob sealed with AES-256-GCM. The key has the alias `latifi_session_wrap`, lives in the Android Keystore and has no exported bytes. The layout is the 12 byte IV, then the ciphertext and the tag. The DataStore file `latifi_session` holds only the Base64 text. A blob that fails to open is deleted and read as signed out. `androidx.security:security-crypto` is deprecated and was not added. `SessionEnvelopeTest` proves this on an emulator.

### Start-up order

`AppStartup` runs from `Application.onCreate` and only in Live mode: SDK start, the consumers of wallet answers (the gateway and the coordinator subscribe to their flows), the delegate, the session extension and then the transaction resumer. This answers research Pitfall 10.

### Host and redirect

`AppKitHost` keeps the only Material 2 imports and hands its controller to the one `NavHost`, which adds `appKitGraph`. `WalletPickerEffect` opens the picker when the gateway asks and reports when the screen that was showing comes back, so a person who leaves without choosing is not left waiting. `onNewIntent` reads nothing from the `latifi-wallet://request` link.

### Still open for a real device

All of it needs a phone, a wallet and a project id, and is listed in [DEVICE-CHECKLIST.md](../DEVICE-CHECKLIST.md): whether a response published while the app was dead is delivered after restart (assumption A2), the redirect with `singleTask` (A6), the AppKit modal theming and TalkBack reading (tracked in [EXCEPTIONS.md](../EXCEPTIONS.md) as EX-01), whether the wallet shows Amoy (A11), the Keystore security level, and one live Amoy cycle.
