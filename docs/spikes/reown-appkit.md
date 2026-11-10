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
