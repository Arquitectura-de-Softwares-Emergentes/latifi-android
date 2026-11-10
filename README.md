# latifi-android

LatiFi Wallet is a native Android app (Kotlin, Jetpack Compose) for borrowers and lenders on LatiFi. It connects to the user's own wallet through Reown AppKit, so the app never holds a private key.

## Modes

The app runs in one of two modes, chosen at build time from the configuration below.

- **Demo.** The default. Data comes from fakes, and nothing leaves the device. You get this mode when the Reown project id is the placeholder or the API host is the placeholder.
- **Live.** The app talks to the real wallet and to the LatiFi API. It needs both a real Reown project id and a real API base URL.

With the placeholder project id the app opens and shows that the wallet connection is not set up. It does not start the wallet SDK.

## Build and test

You need JDK 21 (JDK 25 also works) and the Android SDK. On macOS with Homebrew command line tools:

```
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`. The instrumented tests need an emulator or a device, so they only compile locally. CI runs them on an API 34 emulator in the advisory `android-instrumented` workflow.

## Configuration

Each value comes from `local.properties` (git-ignored), then from an environment variable, then from a placeholder. A blank value counts as missing.

| `local.properties` key | Environment variable | Placeholder |
| ---------------------- | -------------------- | ----------- |
| `reown.projectId` | `REOWN_PROJECT_ID` | 32 zeros |
| `amoy.rpcUrl` | `AMOY_RPC_URL` | public Polygon Amoy RPC |
| `api.baseUrl` | `LATIFI_API_BASE_URL` | `https://latifi-api.invalid/api/v1/` |
| `landing.url` | `LATIFI_LANDING_URL` | `https://latifi.invalid` |
| `terms.url` | `LATIFI_TERMS_URL` | `https://latifi.invalid/terms` |
| `privacy.url` | `LATIFI_PRIVACY_URL` | `https://latifi.invalid/privacy` |
| `icon.url` | `LATIFI_ICON_URL` | `https://latifi.invalid/icon.png` |

The `.invalid` top level domain is reserved, so a placeholder host can never reach a real server.

A WalletConnect project id is not a secret, because it ships inside every APK. It still never goes into the repository. Set it in `local.properties` or in the `REOWN_PROJECT_ID` repository variable.

## Decisions

- [Reown AppKit decision record](docs/spikes/reown-appkit.md)
- [Keystore spike](docs/spikes/keystore-secp256k1.md), superseded on custody. The app holds no key.

See [CONTRIBUTING.md](CONTRIBUTING.md) for the branch model and the commit rules.
