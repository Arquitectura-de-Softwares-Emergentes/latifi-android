#!/usr/bin/env bash
# Offline tests for build-release.sh and distribute.sh. Stubs stand in for gradlew, apksigner,
# aapt2 and firebase, so the suite needs no key, no Android SDK, no phone and no Firebase account.
# Run from anywhere. Compatible with bash 3.2. Exits 1 on the first failed case.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
WORK="$(mktemp -d)"
WORK="$(cd "$WORK" && pwd -P)"
trap 'rm -rf "$WORK"' EXIT

KS_PW="CANARY-STORE-PW-7f3a91"
KEY_PW="CANARY-KEY-PW-c04d52"
SA_SECRET="CANARY-SERVICE-ACCOUNT-9be1"
CERT="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
LANDING="https://arquitectura-de-softwares-emergentes.github.io/latifi-landing/"

mkdir -p "$WORK/bin" "$WORK/sdk/build-tools/33.0.0" "$WORK/sdk/build-tools/99.0.0" "$WORK/keys"
export STUB_GRADLE_LOG="$WORK/gradle-calls.log"
export STUB_FIREBASE_LOG="$WORK/firebase-calls.log"
ALL_OUT="$WORK/all-output.log"
: >"$STUB_GRADLE_LOG"
: >"$ALL_OUT"

# Stub gradlew: records argv and the names of the signing variables (never their values), then
# writes a fake APK as a text file that the stub apksigner and aapt2 read back.
cat >"$WORK/gradlew.stub" <<'STUB'
#!/usr/bin/env bash
echo "gradlew $*" >>"$STUB_GRADLE_LOG"
env | cut -d= -f1 | grep -E '^(LATIFI_|REOWN_)' | sort | tr '\n' ' ' >>"$STUB_GRADLE_LOG"
echo >>"$STUB_GRADLE_LOG"
[ -z "${LATIFI_KEYSTORE_PASSWORD:-}" ] || echo "store-password-reached-gradle" >>"$STUB_GRADLE_LOG"
[ -z "${LATIFI_KEY_PASSWORD:-}" ] || echo "key-password-reached-gradle" >>"$STUB_GRADLE_LOG"
echo "terms=${LATIFI_TERMS_URL:-} privacy=${LATIFI_PRIVACY_URL:-} landing=${LATIFI_LANDING_URL:-}" >>"$STUB_GRADLE_LOG"
[ -z "${STUB_GRADLE_FAIL:-}" ] || { echo "stub gradle: build failed" >&2; exit 1; }
case " $* " in *assembleRelease*) ;; *) exit 0 ;; esac
mkdir -p app/build/outputs/apk/release
{
  echo "package=${STUB_PACKAGE:-com.latifi.wallet}"
  echo "versionCode=${LATIFI_VERSION_CODE:-}"
  echo "versionName=${LATIFI_VERSION_NAME:-}"
  echo "signer=${STUB_SIGNER:-release}"
  echo "debuggable=${STUB_DEBUGGABLE:-no}"
  echo "cleartext=${STUB_CLEARTEXT:-no}"
  echo "extra=${STUB_MANIFEST_EXTRA:-}"
  for p in ${STUB_PERMS:-}; do echo "perm=$p"; done
} >app/build/outputs/apk/release/app-release.apk
STUB

cat >"$WORK/sdk/build-tools/99.0.0/apksigner" <<'STUB'
#!/usr/bin/env bash
file="${!#}"
[ -z "${STUB_APKSIGNER_FAIL:-}" ] || { echo "DOES NOT VERIFY" >&2; exit 1; }
grep -q '^signer=unsigned' "$file" && { echo "DOES NOT VERIFY" >&2; exit 1; }
cn="LatiFi Release"
grep -q '^signer=debug' "$file" && cn="Android Debug"
echo "Verifies"
cert="${STUB_CERT:-0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef}"
if [ -n "${STUB_OLD_FORMAT:-}" ]; then
  echo "Signer #1 certificate DN: CN=$cn"
  echo "Signer #1 certificate SHA-256 digest: $cert"
else
  echo "Number of signers: 1"
  echo "V2 Signer: certificate DN: CN=$cn"
  echo "V2 Signer: certificate SHA-256 digest: $cert"
  echo "V2 Signer: certificate SHA-1 digest: e207aeae3b68df7d3bc61ae9870cc22f25a2a624"
fi
STUB

cat >"$WORK/sdk/build-tools/99.0.0/aapt2" <<'STUB'
#!/usr/bin/env bash
file="${!#}"
val() { sed -n "s/^$1=//p" "$file" | head -1; }
case "$1 $2" in
  "dump badging")
    echo "package: name='$(val package)' versionCode='$(val versionCode)' versionName='$(val versionName)' platformBuildVersionName='15'"
    echo "uses-permission: name='android.permission.INTERNET'"
    sed -n "s/^perm=\(.*\)/uses-permission: name='\1'/p" "$file"
    [ "$(val debuggable)" != yes ] || echo "application-debuggable"
    ;;
  "dump xmltree")
    echo "N: android=http://schemas.android.com/apk/res/android"
    echo "  E: manifest (line=2)"
    echo "    E: application (line=9)"
    [ "$(val cleartext)" != yes ] || echo "      A: http://schemas.android.com/apk/res/android:usesCleartextTraffic(0x010104ec)=true"
    [ "$(val cleartext)" != no ] || echo "      A: http://schemas.android.com/apk/res/android:usesCleartextTraffic(0x010104ec)=false"
    [ -z "$(val extra)" ] || echo "      A: http://schemas.android.com/apk/res/android:name(0x01010003)=\"$(val extra)\" (Raw: \"$(val extra)\")"
    ;;
  *) exit 8 ;;
esac
STUB

# aapt2 and apksigner in an older build-tools folder must be ignored in favour of the newest one.
printf '#!/usr/bin/env bash\necho "old build tools used" >&2\nexit 9\n' >"$WORK/sdk/build-tools/33.0.0/apksigner"
cp "$WORK/sdk/build-tools/33.0.0/apksigner" "$WORK/sdk/build-tools/33.0.0/aapt2"

cat >"$WORK/bin/firebase" <<'STUB'
#!/usr/bin/env bash
echo "firebase $*" >>"$STUB_FIREBASE_LOG"
if [ "${1:-}" = "--version" ]; then echo "${STUB_FIREBASE_VERSION:-13.35.1}"; exit 0; fi
[ -z "${STUB_FIREBASE_FAIL:-}" ] || { echo "Error: upload failed" >&2; exit 1; }
echo "i  uploading distribution..."
echo "View this release in the Firebase console: https://console.firebase.google.com/project/demo/appdistribution/app/android:com.latifi.wallet/releases/abc123"
echo "Share this release with testers who have access: https://appdistribution.firebase.google.com/testerapps/demo/releases/abc123"
echo "Download the release binary (link expires in 1 hour): https://firebasestorage.example.invalid/dl/abc?token=T"
STUB
chmod +x "$WORK/gradlew.stub" "$WORK/sdk/build-tools/"*/* "$WORK/bin/firebase"

printf 'not a real keystore\n' >"$WORK/keys/release.jks"
printf '{"type":"service_account","private_key":"%s"}\n' "$SA_SECRET" >"$WORK/keys/sa.json"
chmod 600 "$WORK/keys/sa.json"

# A clean environment: nothing from the caller leaks into a case.
unset LATIFI_VERSION_CODE LATIFI_VERSION_NAME LATIFI_EXPECTED_CERT_SHA256 LATIFI_LANDING_URL \
  LATIFI_TERMS_URL LATIFI_PRIVACY_URL GOOGLE_APPLICATION_CREDENTIALS FIREBASE_APP_ID FIREBASE_GROUP \
  ANDROID_SDK_ROOT STUB_PERMS STUB_MANIFEST_EXTRA STUB_SIGNER STUB_DEBUGGABLE STUB_CLEARTEXT \
  STUB_OLD_FORMAT STUB_PACKAGE STUB_GRADLE_FAIL STUB_APKSIGNER_FAIL STUB_CERT STUB_FIREBASE_FAIL STUB_FIREBASE_VERSION
export PATH="$WORK/bin:$PATH"
export ANDROID_HOME="$WORK/sdk"
export REOWN_PROJECT_ID="0123456789abcdef0123456789abcdef"
export LATIFI_API_BASE_URL="https://api.example.com/api/v1/"
export LATIFI_KEYSTORE_PATH="$WORK/keys/release.jks"
export LATIFI_KEYSTORE_PASSWORD="$KS_PW"
export LATIFI_KEY_PASSWORD="$KEY_PW"
export LATIFI_KEY_ALIAS="latifi"
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@example.invalid GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@example.invalid

N=0
fail() { echo "FAIL $1"; [ -z "${2:-}" ] || printf '%s\n' "$2" | sed 's/^/     | /' | head -20; exit 1; }
check() { # name, expected exit, actual exit
  N=$((N + 1))
  if [ "$2" = "$3" ]; then echo "PASS $1"; else fail "$1 (expected exit $2, got $3)" "$OUT"; fi
}
ok() { # name, command result (0 means pass)
  N=$((N + 1))
  if [ "$2" = "0" ]; then echo "PASS $1"; else fail "$1" "$OUT"; fi
}
has() { printf '%s' "$OUT" | grep -qF -- "$1"; echo $?; }

new_repo() { # NAME: a git repository with the scripts, a stub gradlew and the tag v1.2.3
  local r="$WORK/$1"
  mkdir -p "$r/scripts/release" "$r/app"
  cp "$HERE/build-release.sh" "$HERE/distribute.sh" "$HERE/firebase-tools.version" "$r/scripts/release/"
  cp "$WORK/gradlew.stub" "$r/gradlew"
  printf 'build/\n*.jks\nlocal.properties\n' >"$r/.gitignore"
  (
    cd "$r" || exit 1
    git init -q . && git checkout -q -b develop 2>/dev/null
    git add -A && git -c commit.gpgsign=false commit -q -m "chore: first"
    echo one >app/one.txt && git add -A && git -c commit.gpgsign=false commit -q -m "feat: one"
    git -c tag.gpgsign=false tag v1.2.3
  )
}

rb() { # REPO "VAR=value ..." [build-release arguments]
  local repo="$WORK/$1" envs="$2"
  shift 2
  # shellcheck disable=SC2086
  OUT="$(cd "$repo" && env $envs bash scripts/release/build-release.sh "$@" 2>&1 </dev/null)"
  RC=$?
  printf '%s\n' "$OUT" >>"$ALL_OUT"
}
rd() { # DIR/APK "VAR=value ..." [distribute arguments]
  local envs="$2" apk="$1"
  shift 2
  # shellcheck disable=SC2086
  OUT="$(cd "$WORK/r1" && env $envs bash scripts/release/distribute.sh "$apk" "$@" 2>&1 </dev/null)"
  RC=$?
  printf '%s\n' "$OUT" >>"$ALL_OUT"
}

new_repo r1
R1="$WORK/r1"

# ---------------------------------------------------------------- build-release.sh, refusals
echo dirty >"$R1/dirty.txt"
rb r1 "X=1"
check "build refuses a dirty tree" 1 "$RC"
ok "dirty refusal says so" "$(has dirty)"
ok "dirty refusal never started gradle" "$([ ! -s "$STUB_GRADLE_LOG" ]; echo $?)"
rm -f "$R1/dirty.txt"

new_repo r2
(cd "$WORK/r2" && echo two >app/two.txt && git add -A && git -c commit.gpgsign=false commit -q -m "feat: two")
rb r2 "X=1"
check "build refuses an untagged commit" 1 "$RC"
ok "untagged refusal names the tag form" "$(has 'v1.2.3')"
rb r2 "X=1" --allow-untagged --skip-tests
check "build allows an untagged commit with the flag" 0 "$RC"
ok "untagged version carries the suffix" "$(grep -q '"versionName": "1.2.3-untagged"' "$WORK/r2/build/release/build-info.json"; echo $?)"
ok "untagged build warns" "$(has 'must not be shipped')"
ok "skip-tests warns" "$(has '--skip-tests')"
ok "skip-tests drops the test tasks" "$(! grep -q 'testDebugUnitTest' "$STUB_GRADLE_LOG"; echo $?)"
: >"$STUB_GRADLE_LOG"

rb r1 "LATIFI_API_BASE_URL=http://api.example.com/api/v1/"
check "build refuses an http API URL" 2 "$RC"
rb r1 "LATIFI_API_BASE_URL=https://api.example.com/v1/"
check "build refuses an API URL with the wrong suffix" 2 "$RC"
rb r1 "X=1" --api-base-url https://latifi-api.invalid/api/v1/
check "build refuses a .invalid API host" 2 "$RC"
rb r1 "REOWN_PROJECT_ID=00000000000000000000000000000000"
check "build refuses an all-zero Reown id" 2 "$RC"
rb r1 "REOWN_PROJECT_ID=ABCDEF"
check "build refuses a malformed Reown id" 2 "$RC"
rb r1 "LATIFI_KEYSTORE_PATH=$WORK/keys/nope.jks"
check "build refuses a missing keystore" 2 "$RC"
rb r1 "LATIFI_KEYSTORE_PATH="
check "build refuses an unset keystore variable" 2 "$RC"
cp "$WORK/keys/release.jks" "$R1/in-repo.jks"
rb r1 "LATIFI_KEYSTORE_PATH=$R1/in-repo.jks"
check "build refuses a keystore inside the repository" 2 "$RC"
ok "in-repo refusal says so" "$(has 'inside the repository')"
rm -f "$R1/in-repo.jks"
rb r1 "LATIFI_KEY_PASSWORD="
check "build refuses a missing key password without a terminal" 2 "$RC"
rb r1 "ANDROID_HOME=$WORK/none PATH=/usr/bin:/bin"
check "build exits 3 when the build tools are absent" 3 "$RC"
rb r1 "LATIFI_VERSION_CODE=abc"
check "build refuses a non numeric version code" 2 "$RC"
printf 'version.name=9.9.9\n' >"$R1/local.properties"
rb r1 "X=1"
check "build refuses a local.properties version that contradicts the tag" 2 "$RC"
rm -f "$R1/local.properties"
: >"$STUB_GRADLE_LOG"

rb r1 "STUB_PERMS=android.permission.CAMERA"
check "build fails on a camera permission" 1 "$RC"
ok "camera failure names it" "$(has CAMERA)"
rb r1 "STUB_PERMS=android.permission.ACCESS_FINE_LOCATION"
check "build fails on a location permission" 1 "$RC"
rb r1 "STUB_PERMS=android.permission.READ_MEDIA_IMAGES"
check "build fails on a photo permission" 1 "$RC"
rb r1 "STUB_MANIFEST_EXTRA=com.google.firebase.messaging.FirebaseMessagingService"
check "build fails on a Firebase component" 1 "$RC"
rb r1 "STUB_MANIFEST_EXTRA=com.google.android.gms.measurement.AppMeasurementService"
check "build fails on a Play services component" 1 "$RC"
rb r1 "STUB_DEBUGGABLE=yes"
check "build fails on a debuggable APK" 1 "$RC"
rb r1 "STUB_CLEARTEXT=yes"
check "build fails on cleartext traffic" 1 "$RC"
rb r1 "STUB_SIGNER=unsigned"
check "build fails on an unsigned APK" 1 "$RC"
rb r1 "STUB_SIGNER=debug"
check "build fails on the debug key" 1 "$RC"
rb r1 "STUB_PACKAGE=com.example.other"
check "build fails on a wrong package id" 1 "$RC"
rb r1 "LATIFI_EXPECTED_CERT_SHA256=ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
check "build fails on an unexpected certificate" 1 "$RC"
rb r1 "STUB_GRADLE_FAIL=1"
check "build fails when gradle fails" 1 "$RC"

# ---------------------------------------------------------------- build-release.sh, success
: >"$STUB_GRADLE_LOG"
rb r1 "LATIFI_EXPECTED_CERT_SHA256=$CERT"
check "build succeeds from a clean tagged tree" 0 "$RC"
LAST="$(printf '%s\n' "$OUT" | tail -1)"
ok "last line is the ok line" "$([ "${LAST#build-release: ok }" != "$LAST" ]; echo $?)"
OUTDIR="$R1/build/release"
CODE="$(git -C "$R1" rev-list --count HEAD)"
APK="$OUTDIR/latifi-wallet-1.2.3-$CODE.apk"
ok "the APK is written" "$([ -f "$APK" ]; echo $?)"
ok "the sha256 file matches the APK" "$([ "$(cut -d' ' -f1 "$APK.sha256")" = "$( (shasum -a 256 "$APK" 2>/dev/null || sha256sum "$APK") | cut -d' ' -f1)" ]; echo $?)"
ok "permissions.txt lists internet only" "$([ "$(cat "$OUTDIR/permissions.txt")" = "android.permission.INTERNET" ]; echo $?)"
INFO="$OUTDIR/build-info.json"
ok "build-info has the version, tag, sha and certificate" "$(grep -q '"versionName": "1.2.3"' "$INFO" && grep -q "\"versionCode\": $CODE," "$INFO" && grep -q '"tag": "v1.2.3"' "$INFO" && grep -q "\"gitSha\": \"$(git -C "$R1" rev-parse --short=12 HEAD)\"" "$INFO" && grep -q "\"certSha256\": \"$CERT\"" "$INFO" && grep -q '"apiHost": "api.example.com"' "$INFO"; echo $?)"
ok "gradle ran tests, lint and the release build" "$(grep -q 'gradlew --no-daemon testDebugUnitTest lintDebug assembleRelease' "$STUB_GRADLE_LOG"; echo $?)"
ok "the signing variables reached gradle" "$(grep -q 'store-password-reached-gradle' "$STUB_GRADLE_LOG" && grep -q 'key-password-reached-gradle' "$STUB_GRADLE_LOG" && grep -q 'LATIFI_KEYSTORE_PATH' "$STUB_GRADLE_LOG" && grep -q 'LATIFI_VERSION_NAME' "$STUB_GRADLE_LOG"; echo $?)"
ok "the live landing pages are the legal defaults" "$(grep -q "terms=${LANDING}terms.html privacy=${LANDING}privacy.html landing=${LANDING}" "$STUB_GRADLE_LOG"; echo $?)"

rb r1 "STUB_OLD_FORMAT=1"
check "build reads the older apksigner output format too" 0 "$RC"

# a key password from local.properties works and stays out of every output
printf 'signing.keyPassword=%s\n' "$KEY_PW" >"$R1/local.properties"
rb r1 "LATIFI_KEY_PASSWORD="
check "build reads the key password from local.properties" 0 "$RC"
rm -f "$R1/local.properties"

# ---------------------------------------------------------------- canaries
CANARY_HITS="$(grep -rlF -e "$KS_PW" -e "$KEY_PW" -e "$SA_SECRET" "$OUTDIR" "$ALL_OUT" "$STUB_GRADLE_LOG" 2>/dev/null || true)"
OUT="$CANARY_HITS"
ok "no password appears in any output, file or gradle argv" "$([ -z "$CANARY_HITS" ]; echo $?)"

# ---------------------------------------------------------------- distribute.sh
APKP="$OUTDIR/latifi-wallet-1.2.3-$CODE.apk"
GOOD="FIREBASE_APP_ID=1:123456789012:android:0123456789abcdef FIREBASE_GROUP=latifi-demo"

rd "$APKP" "$GOOD"
check "distribute refuses the UNSET pin" 2 "$RC"
ok "UNSET refusal says so" "$(has UNSET)"
ok "UNSET refusal never called firebase" "$([ ! -s "$STUB_FIREBASE_LOG" ]; echo $?)"
printf '13.35.1\n' >"$R1/scripts/release/firebase-tools.version"

cp "$APKP" "$OUTDIR/latifi-wallet-debug.apk"
rd "$OUTDIR/latifi-wallet-debug.apk" "$GOOD"
check "distribute refuses a debug named file" 1 "$RC"
rm -f "$OUTDIR/latifi-wallet-debug.apk"

rd "$APKP" "FIREBASE_APP_ID=1:abc FIREBASE_GROUP=latifi-demo"
check "distribute refuses a bad app id" 2 "$RC"
rd "$APKP" "FIREBASE_APP_ID=1:123456789012:android:0123456789abcdef"
check "distribute refuses a missing group" 2 "$RC"
rd "$APKP" "$GOOD" --group "bad group!"
check "distribute refuses a bad group alias" 2 "$RC"
rd "$APKP" "$GOOD STUB_CERT=ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
check "distribute refuses a certificate mismatch" 1 "$RC"
rd "$APKP" "$GOOD STUB_APKSIGNER_FAIL=1"
check "distribute refuses an unsigned APK" 1 "$RC"
rd "$WORK/none/x.apk" "$GOOD"
check "distribute refuses a missing file" 2 "$RC"

mkdir -p "$WORK/tampered"
cp "$APKP" "$INFO" "$WORK/tampered/"
echo "extra=tampered" >>"$WORK/tampered/$(basename "$APKP")"
rd "$WORK/tampered/$(basename "$APKP")" "$GOOD"
check "distribute refuses an APK changed after the build" 1 "$RC"
mkdir -p "$WORK/noinfo"
cp "$APKP" "$WORK/noinfo/"
rd "$WORK/noinfo/$(basename "$APKP")" "$GOOD"
check "distribute refuses an APK without build-info.json" 1 "$RC"

: >"$STUB_FIREBASE_LOG"
rd "$APKP" "$GOOD" --dry-run
check "dry run succeeds" 0 "$RC"
EXPECTED_CMD="$(printf 'firebase appdistribution:distribute %q --app %q --groups %q --release-notes-file %q' "$APKP" "1:123456789012:android:0123456789abcdef" "latifi-demo" "$OUTDIR/release-notes.txt")"
ok "dry run prints the exact command" "$(has "$EXPECTED_CMD")"
ok "dry run never calls firebase" "$([ ! -s "$STUB_FIREBASE_LOG" ]; echo $?)"
ok "default release notes name the version" "$(grep -q '1.2.3' "$OUTDIR/release-notes.txt"; echo $?)"

rd "$APKP" "$GOOD STUB_FIREBASE_VERSION=13.0.0"
check "distribute refuses a firebase CLI that differs from the pin" 1 "$RC"
rd "$APKP" "$GOOD PATH=/usr/bin:/bin"
check "distribute exits 3 without the firebase CLI" 3 "$RC"
rd "$APKP" "$GOOD STUB_FIREBASE_FAIL=1"
check "distribute fails when the upload fails" 1 "$RC"

cp "$WORK/keys/sa.json" "$WORK/keys/sa-open.json" && chmod 644 "$WORK/keys/sa-open.json"
rd "$APKP" "$GOOD GOOGLE_APPLICATION_CREDENTIALS=$WORK/keys/sa-open.json"
check "distribute refuses a service account with mode 644" 2 "$RC"
cp "$WORK/keys/sa.json" "$R1/sa-in-repo.json" && chmod 600 "$R1/sa-in-repo.json"
rd "$APKP" "$GOOD GOOGLE_APPLICATION_CREDENTIALS=$R1/sa-in-repo.json"
check "distribute refuses a service account inside the repository" 2 "$RC"
rm -f "$R1/sa-in-repo.json"

: >"$STUB_FIREBASE_LOG"
rd "$APKP" "$GOOD GOOGLE_APPLICATION_CREDENTIALS=$WORK/keys/sa.json"
check "distribute uploads with a safe service account file" 0 "$RC"
LAST="$(printf '%s\n' "$OUT" | tail -1)"
ok "last line is the distribute ok line" "$([ "$LAST" = "distribute: ok" ]; echo $?)"
ok "the upload passes the expected arguments" "$(grep -qxF "firebase appdistribution:distribute $APKP --app 1:123456789012:android:0123456789abcdef --groups latifi-demo --release-notes-file $OUTDIR/release-notes.txt" "$STUB_FIREBASE_LOG"; echo $?)"
ok "the pinned version was checked first" "$([ "$(head -1 "$STUB_FIREBASE_LOG")" = "firebase --version" ]; echo $?)"
ok "release links are printed" "$(has 'https://appdistribution.firebase.google.com/testerapps/demo/releases/abc123')"
ok "a log file is written beside the APK" "$(ls "$OUTDIR"/distribute-*.log >/dev/null 2>&1; echo $?)"

# the flags work in place of the variables
rd "$APKP" "X=1" --app-id 1:123456789012:android:0123456789abcdef --group latifi-demo --notes-file "$OUTDIR/release-notes.txt" --dry-run
check "flags replace the environment variables" 0 "$RC"

CANARY_HITS="$(grep -rlF -e "$KS_PW" -e "$KEY_PW" -e "$SA_SECRET" "$OUTDIR" "$ALL_OUT" "$STUB_GRADLE_LOG" "$STUB_FIREBASE_LOG" 2>/dev/null || true)"
OUT="$CANARY_HITS"
ok "no secret appears anywhere after the distribute runs either" "$([ -z "$CANARY_HITS" ]; echo $?)"

echo "release-test: ok $N cases"
