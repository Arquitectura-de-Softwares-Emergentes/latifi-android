# Distribution guide

This guide explains how a LatiFi Wallet release goes from a git tag to a tester's phone. It covers the signing key, the signed build, the upload to Firebase App Distribution and the install check on a clean phone.

The app has no Firebase SDK. Firebase only hosts the file and the tester list. The Firebase CLI uploads an APK that is already signed, so nothing Firebase related lands in the app.

## Overview

1. A release is a tag `vX.Y.Z` on a commit of `main`.
2. `scripts/release/build-release.sh` builds that tag, signs it with the release key kept outside the repository, and inspects the result.
3. `scripts/release/distribute.sh` uploads exactly that APK to the Firebase tester group.
4. A teammate installs it on a phone that has never had LatiFi and fills in the checklist at the end of this guide.

## Versioning

- The tag is the version name. Tag `v0.3.0` builds version name `0.3.0`.
- The version code is the number of commits on the tagged commit (`git rev-list --count HEAD`). It only grows, which Android needs for updates.
- Set `LATIFI_VERSION_CODE` to override the code. You rarely need it.
- A build from an untagged commit needs `--allow-untagged`. Its name ends in `-untagged`. Never send that build to testers.

## One time setup for Juan

### The release keystore

The release key is the identity of the app. Anyone who holds it can publish an update that every tester's phone accepts.

1. Create the folder and the key outside every repository:

   ```
   mkdir -p ~/.latifi/android && chmod 700 ~/.latifi/android
   keytool -genkeypair -v -keystore ~/.latifi/android/latifi-release.jks \
     -alias latifi -keyalg RSA -keysize 4096 -validity 10000
   ```

   keytool asks for the two passwords and your details. Type them at the prompt. Never put a password on the command line.
2. Store the keystore password and the key password in your password manager.
3. Back up `latifi-release.jks` in two separate places, for example an encrypted disk and your password manager's file storage.
4. Know the price of losing it: nobody can update an installed app any more. Every tester has to uninstall LatiFi before installing a build signed with a new key.
5. After the first build, copy the certificate SHA-256 from the build output and write it here. Later builds compare against it through `LATIFI_EXPECTED_CERT_SHA256`.

   Certificate SHA-256: `not recorded yet`

   You can also read it from the key at any time with `keytool -list -v -keystore ~/.latifi/android/latifi-release.jks -alias latifi`.

### Firebase App Distribution

1. Create a Firebase project in the Firebase console.
2. Register an Android app with the package name `com.latifi.wallet`. You do not need the `google-services.json` file, because the app has no Firebase SDK. Do not add it to the repository.
3. Open App Distribution and turn it on.
4. Create a tester group with the alias `latifi-demo`.
5. Open the group and create an invite link. Copy it for the landing page and for your testers.
6. Copy the app id of the Android app. It looks like `1:123456789012:android:0123456789abcdef`.
7. Choose how the CLI signs in. Either run `firebase login` once on your Mac, or create a service account with the role Firebase App Distribution Admin, download its JSON file, store it outside every repository and run `chmod 600` on it.

The Firebase CLI version is pinned in `scripts/release/firebase-tools.version`. The file holds the word `UNSET` until the package check in plan 07-08 replaces it with the version you verified. `distribute.sh` refuses to run while it says `UNSET`.

## Where each value goes

Set each value in the environment or in the git-ignored `local.properties`. The build script accepts either.

| Value | Where it goes | Secret |
| ----- | ------------- | ------ |
| Reown project id | `REOWN_PROJECT_ID` or `reown.projectId` | No. It ships inside the APK. |
| API base URL | `LATIFI_API_BASE_URL`, `api.baseUrl` or `--api-base-url`. It starts with `https://` and ends with `/api/v1/`. | No |
| Landing, terms and privacy URLs | `LATIFI_LANDING_URL`, `LATIFI_TERMS_URL`, `LATIFI_PRIVACY_URL`. The defaults are the live landing pages. | No |
| Keystore file | `LATIFI_KEYSTORE_PATH` or `signing.storeFile` | Yes. The file is the key. |
| Keystore password | `LATIFI_KEYSTORE_PASSWORD` or `signing.storePassword`. The script asks for it without echo when missing. | Yes |
| Key alias | `LATIFI_KEY_ALIAS` or `signing.keyAlias` (`latifi`) | No |
| Key password | `LATIFI_KEY_PASSWORD` or `signing.keyPassword`. The script asks for it without echo when missing. | Yes |
| Expected certificate | `LATIFI_EXPECTED_CERT_SHA256` | No |
| Firebase app id | `FIREBASE_APP_ID` or `--app-id` | Treat as private. |
| Tester group | `FIREBASE_GROUP` or `--group` (`latifi-demo`) | No |
| Service account file | `GOOGLE_APPLICATION_CREDENTIALS`, a path outside the repository with mode 600 | Yes. The file is the secret. |

## Build

1. Merge the release pull request into `main` and tag the merge commit `vX.Y.Z`. Plan 07-08 does this under Juan's authorization.
2. Check out the tag in a clean working tree and point `ANDROID_HOME` at the Android SDK.
3. Run:

   ```
   bash scripts/release/build-release.sh
   ```

The script runs the unit tests and lint, builds the signed release APK, inspects it and writes the result to `build/release/`:

- `latifi-wallet-VERSION-CODE.apk`, for example `latifi-wallet-0.3.0-87.apk`
- the same name with `.sha256`
- `permissions.txt`, the permission list of the APK
- `build-info.json`, with the version, git commit, certificate hash, APK hash and API host. It holds no secret.

The last line is `build-release: ok PATH`. Useful flags: `--api-base-url URL`, `--out DIR`, `--skip-tests` (prints a warning) and `--allow-untagged`.

## Upload

1. Try it first without uploading:

   ```
   bash scripts/release/distribute.sh build/release/latifi-wallet-VERSION-CODE.apk --dry-run
   ```

   It prints the exact Firebase command and stops.
2. Run it for real:

   ```
   bash scripts/release/distribute.sh build/release/latifi-wallet-VERSION-CODE.apk
   ```

   It prints the release links and ends with `distribute: ok`. The download link expires after one hour, so share the invite link with testers instead.

The release notes come from `build-info.json`. Pass `--notes-file FILE` to write your own.

## What the build refuses and why

| Refusal | Why |
| ------- | --- |
| A placeholder Reown project id or a `.invalid` API host | The app would start in Demo mode with a fake wallet. |
| An API URL that is not `https://` or does not end in `/api/v1/` | The app would call the wrong place or send data without encryption. |
| A missing key, or a key inside the repository | An unsigned APK cannot be installed, and a key inside the repository can leak. |
| A dirty working tree or a commit without a `vX.Y.Z` tag | A build must be reproducible from a tag. |
| A wrong package id, version name or version code | The file would not match the tag. |
| A debuggable APK or the Android debug key | Testers would receive a development build. |
| Cleartext traffic allowed | The app talks to the API over HTTPS only. |
| A location, contacts, camera, microphone, photo or storage permission | The privacy page says the app asks for none of these (open item b of plan 06-05). |
| A Firebase or Google Play services component in the manifest | The privacy page says the team added no analytics or tracking tools (open item a of plan 06-05). |

`distribute.sh` adds its own refusals: an unpinned Firebase CLI, a file with `debug` in its name, an unsigned APK, an APK that differs from `build-info.json`, a certificate that differs from the build, a bad app id, and a service account file that is inside the repository or readable by other users.

If a check fails because of a library the app depends on, do not loosen the check. Change the dependency or change the privacy page, because the page makes the promise.

## Clean device install

Use a physical phone that has never had LatiFi and has no debug build. Fill in the Result column with Pass, Fail and a short note.

| # | Step | Expected | Result |
| - | ---- | -------- | ------ |
| 1 | Check the phone: no LatiFi Wallet in the app list and no debug build installed. | The phone is clean. | |
| 2 | Open the tester invite link in the phone browser while signed in to a Google account. | The App Distribution page for LatiFi Wallet opens. | |
| 3 | Accept the invite and install from App Distribution. Accept the Play Protect prompt for an app from outside the store. | The install finishes without an error. | |
| 4 | Look at the app icon and name in the launcher. | The name reads LatiFi Wallet and the icon is the LatiFi icon. | |
| 5 | Open App info and read the version. | The version equals the tag, and the build number equals the version code in `build-info.json`. | |
| 6 | Launch the app for the first time and open Profile. | Onboarding shows first, and the Profile mode reads Live, not Demo. | |
| 7 | Use the app for five minutes, send it to the background for a minute and open it again. | No crash, and the screen state is restored. | |
| 8 | Change the phone language between English and Spanish and reopen the app. | The app language follows the phone. | |
| 9 | Open the wallet picker from the Connect screen. | The picker opens and shows the verified domain of the landing site. | |
| 10 | Sign in with a wallet against the public API. | The sign in succeeds and Home loads. | |
| 11 | Open App info and read the permissions. | No location, contacts, camera, microphone, photo or storage permission, and the list matches `permissions.txt`. | |
| 12 | Uninstall the app and install it again from the same invite link. | The reinstall works. | |

Record these under the table:

- Phone model:
- Android version:
- Install duration in minutes (from tapping the invite link to the first launch):
- Tester name:
- Date:

## Rollback and revocation

1. A bad build: open App Distribution, open the release and delete it. Testers can no longer download it.
2. A leaked or unwanted invite link: open the tester group and revoke or reset the invite link, then share the new one.
3. A fix: merge a hotfix, tag the next patch version (for example `v0.3.1`) and run the build and upload steps again. The version code grows with every commit, so the fix installs over the bad build.
4. A suspected key leak: stop sharing builds, create a new key, and tell every tester to uninstall before installing the build signed with it.

## Open finding: Firebase Messaging in the release APK

A test release build of the current `develop` fails the Firebase component check on purpose. The merged manifest holds Firebase Messaging and Firebase Installations components, plus the permissions `POST_NOTIFICATIONS`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE` and `com.google.android.c2dm.permission.RECEIVE`. They come from `com.reown:android-core` 1.6.17, which depends on `firebase-messaging` through the Firebase BOM. The app never uses push notifications.

This matters because the privacy page of the landing site says the team added no analytics or tracking tools of its own. Before the first upload, Juan decides one of two things:

1. Exclude `firebase-messaging` from the Reown dependencies and re-run the build and the sign in check.
2. Keep the library and disclose it on the privacy page.

Do not loosen the check in `build-release.sh` to get around this.

## Known limits

- The debug APKs that CI uploads from `develop` are for development only. They carry a different signature, so they do not update a release install. Uninstall first.
- The invite link joins anyone who has it to the tester group. The app is a testnet demo with no real value, and you can revoke the link at any time.
- The download link in the upload output expires after one hour.
