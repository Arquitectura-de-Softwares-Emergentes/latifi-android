# latifi-android

LatiFi Wallet is a native Android app (Kotlin, Jetpack Compose) for borrowers and lenders on LatiFi. It connects to the user's own wallet through Reown AppKit, so the app never holds a private key.

## Modes

The app runs in one of two modes, chosen at build time from the configuration below.

- **Demo.** The default. Data comes from fakes, and nothing leaves the device. You get this mode when the Reown project id is the placeholder or the API host is the placeholder.
- **Live.** The app talks to the real wallet and to the LatiFi API. It needs both a real Reown project id and a real API base URL.

With the placeholder project id the app runs in Demo mode with a fake wallet. It never starts the wallet SDK, so it cannot crash at start-up.

## Live mode

Live mode reads the real LatiFi API and a public Polygon Amoy node. The app picks it at build time, and only when both of these are set:

- `reown.projectId` or `REOWN_PROJECT_ID`: a real 32 character WalletConnect project id.
- `api.baseUrl` or `LATIFI_API_BASE_URL`: the real API address, ending with `/api/v1/`.

If either one is missing, the app stays in Demo mode and needs nothing else. Demo mode never builds the HTTP stack.

The chain reads (token balance, allowance, next installment and the transaction receipt) go to `amoy.rpcUrl` or `AMOY_RPC_URL`. The default is a public endpoint, which is enough for the app. If you use a provider that puts a key in the address, keep that URL in `local.properties` or in a CI variable. It never belongs in the repository, and the app never writes it to a log or an error message.

What the live data layer does today:

- Every API call sends `Accept-Language` as `en-US` or `es-419`.
- The session token goes out only while it has not expired. When the API answers 401, the app asks the wallet for one new signature and repeats the call once.
- The API error codes become the app's own messages. The server text is never shown.
- A copy of the API contract (`app/src/test/resources/contract/latifi-api.v0.yaml`) sits next to the tests. A test compares it with the Retrofit interface, so a renamed or removed operation fails the build. When the API contract changes, copy the new file over it and update the interface.

The live wallet connection uses Reown AppKit. The gateway only sends requests to the user's wallet, and the wallet signs. The session (address, token and expiry) is stored as one AES-256-GCM blob whose key lives in the Android Keystore, so it survives a process kill and holds no readable text on disk. At start-up the order is fixed: wallet SDK, answer consumers, delegate, session extension, then the transaction resumer, so a wallet answer that arrives while the app was dead is not lost.

A real run needs a person with a phone, a wallet app and a Reown project id. The steps are in [docs/DEVICE-CHECKLIST.md](docs/DEVICE-CHECKLIST.md). CI cannot do them, and the demo works without them.

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

The repository variables `REOWN_PROJECT_ID` and `LATIFI_API_BASE_URL` are optional. CI passes them to the build, and a blank value keeps the placeholder, so the build needs no secret.

## Documents

- [Device checklist](docs/DEVICE-CHECKLIST.md): the checks that need a physical phone, a real wallet and a project id, with a result column.
- [Accessibility exceptions](docs/EXCEPTIONS.md): the register, with the Reown AppKit entry.
- [Requirement traceability](docs/TRACEABILITY.md): the nine Phase 5 requirements and the tests that prove them. The evidence uses fakes and MockWebServer, not a live wallet or a live API.
- [Accessibility decisions](docs/A11Y-DECISIONS.md)

## Decisions

- [Reown AppKit decision record](docs/spikes/reown-appkit.md)
- [Keystore spike](docs/spikes/keystore-secp256k1.md), superseded on custody. The app holds no key.

See [CONTRIBUTING.md](CONTRIBUTING.md) for the branch model and the commit rules.
