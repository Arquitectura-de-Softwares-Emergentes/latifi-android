#!/usr/bin/env bash
# Builds, signs and inspects the LatiFi Wallet release APK from a clean tree at a v-tag.
#
# Usage: bash scripts/release/build-release.sh [--api-base-url URL] [--allow-untagged]
#                                              [--skip-tests] [--out DIR]
#
# Inputs (flag, then local.properties, then environment, same order the Gradle build uses):
#   REOWN_PROJECT_ID, LATIFI_API_BASE_URL, LATIFI_KEYSTORE_PATH, LATIFI_KEYSTORE_PASSWORD,
#   LATIFI_KEY_ALIAS, LATIFI_KEY_PASSWORD, optional LATIFI_VERSION_CODE, LATIFI_EXPECTED_CERT_SHA256.
# A missing password or alias is read from the terminal without echo. Nothing secret is ever put on
# a command line, printed, or written to a file.
#
# Exit codes: 0 ok, 1 a check failed, 2 bad usage or invalid configuration, 3 a required tool is
# missing. Compatible with bash 3.2.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT="$(cd "$HERE/../.." && pwd -P)"

APP_ID="com.latifi.wallet"
LANDING_BASE="https://arquitectura-de-softwares-emergentes.github.io/latifi-landing/"

API_FLAG=""
ALLOW_UNTAGGED=0
SKIP_TESTS=0
OUT_DIR="build/release"

usage_error() { echo "build-release: $*" >&2; exit 2; }
check_failed() { echo "build-release: FAILED: $*" >&2; exit 1; }
tool_missing() { echo "build-release: required tool missing: $*" >&2; exit 3; }
warn() { echo "build-release: warning: $*" >&2; }

while [ $# -gt 0 ]; do
  case "$1" in
    --api-base-url) [ $# -ge 2 ] || usage_error "--api-base-url needs a value"; API_FLAG="$2"; shift 2 ;;
    --allow-untagged) ALLOW_UNTAGGED=1; shift ;;
    --skip-tests) SKIP_TESTS=1; shift ;;
    --out) [ $# -ge 2 ] || usage_error "--out needs a value"; OUT_DIR="$2"; shift 2 ;;
    -h | --help) sed -n '2,16p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) usage_error "unknown argument: $1" ;;
  esac
done

command -v git >/dev/null 2>&1 || tool_missing git
if command -v shasum >/dev/null 2>&1; then
  sha256_of() { shasum -a 256 "$1" | cut -d' ' -f1; }
elif command -v sha256sum >/dev/null 2>&1; then
  sha256_of() { sha256sum "$1" | cut -d' ' -f1; }
else
  tool_missing "shasum or sha256sum"
fi

cd "$ROOT" || usage_error "cannot enter $ROOT"
git rev-parse --git-dir >/dev/null 2>&1 || usage_error "$ROOT is not a git repository"
[ -x ./gradlew ] || usage_error "gradlew is missing or not executable in $ROOT"

case "$OUT_DIR" in /*) ;; *) OUT_DIR="$ROOT/$OUT_DIR" ;; esac

# --- 1. clean tree and tag -------------------------------------------------------------------
if [ -n "$(git status --porcelain)" ]; then
  check_failed "the working tree is dirty. Commit or remove the changes first"
fi

GIT_SHA="$(git rev-parse --short=12 HEAD)"
TAG="$(git tag --points-at HEAD | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | { sort -V 2>/dev/null || sort; } | tail -1)"
if [ -n "$TAG" ]; then
  VERSION_NAME="${TAG#v}"
elif [ "$ALLOW_UNTAGGED" -eq 1 ]; then
  BASE_TAG="$(git describe --tags --abbrev=0 --match 'v[0-9]*.[0-9]*.[0-9]*' 2>/dev/null || true)"
  BASE_TAG="${BASE_TAG#v}"
  case "$BASE_TAG" in [0-9]*.[0-9]*.[0-9]*) ;; *) BASE_TAG="0.0.0" ;; esac
  VERSION_NAME="${BASE_TAG}-untagged"
  warn "HEAD carries no vMAJOR.MINOR.PATCH tag. Building $VERSION_NAME, which must not be shipped to testers"
else
  check_failed "HEAD carries no tag like v1.2.3. Tag the release commit or pass --allow-untagged"
fi

# --- 2. configuration, same precedence as app/build.gradle.kts -------------------------------
prop() { # KEY: value from the git-ignored local.properties, empty when absent or blank
  [ -f "$ROOT/local.properties" ] || return 0
  grep -E "^[[:space:]]*$1[[:space:]]*=" "$ROOT/local.properties" | tail -1 |
    sed -E 's/^[^=]*=[[:space:]]*//; s/[[:space:]]+$//'
}

# resolve PROPKEY ENVNAME [FORCED]: sets RESOLVED. A forced value wins, but it may not disagree with
# a value in local.properties, because Gradle would silently prefer the file and the APK would not
# match. It is called directly, never inside $(...), so a usage error really stops the script.
RESOLVED=""
resolve() {
  local key="$1" env="$2" forced="${3:-}" fromfile fromenv
  fromfile="$(prop "$key")"
  eval "fromenv=\"\${$env:-}\""
  if [ -n "$forced" ]; then
    if [ -n "$fromfile" ] && [ "$fromfile" != "$forced" ]; then
      usage_error "$key in local.properties disagrees with the value for $env. Remove it from local.properties"
    fi
    RESOLVED="$forced"
  elif [ -n "$fromfile" ]; then
    RESOLVED="$fromfile"
  else
    RESOLVED="$fromenv"
  fi
}

# Version code: LATIFI_VERSION_CODE when set, else the commit count.
CODE_INPUT="${LATIFI_VERSION_CODE:-}"
[ -n "$CODE_INPUT" ] || CODE_INPUT="$(git rev-list --count HEAD)"
case "$CODE_INPUT" in '' | *[!0-9]*) usage_error "the version code must be a whole number" ;; esac
[ "$CODE_INPUT" -ge 1 ] || usage_error "the version code must be at least 1"
resolve version.code LATIFI_VERSION_CODE "$CODE_INPUT"; VERSION_CODE="$RESOLVED"
resolve version.name LATIFI_VERSION_NAME "$VERSION_NAME"; VERSION_NAME="$RESOLVED"

resolve api.baseUrl LATIFI_API_BASE_URL "$API_FLAG"; API_BASE_URL="$RESOLVED"
case "$API_BASE_URL" in
  https://*/api/v1/) ;;
  '') usage_error "the API base URL is missing. Pass --api-base-url or set LATIFI_API_BASE_URL" ;;
  *) usage_error "the API base URL must start with https:// and end with /api/v1/" ;;
esac
API_HOST="$(printf '%s' "$API_BASE_URL" | sed -E 's#^https://([^/:]+).*#\1#' | tr 'A-Z' 'a-z')"
case "$API_HOST" in
  '' | *.invalid) usage_error "the API base URL still points at a .invalid placeholder host" ;;
esac

resolve reown.projectId REOWN_PROJECT_ID; REOWN_PROJECT_ID="$RESOLVED"
if ! printf '%s' "$REOWN_PROJECT_ID" | grep -Eq '^[0-9a-f]{32}$' ||
  [ "$REOWN_PROJECT_ID" = "00000000000000000000000000000000" ]; then
  usage_error "REOWN_PROJECT_ID must be a real 32 character lowercase hex project id, not the placeholder"
fi

resolve signing.storeFile LATIFI_KEYSTORE_PATH; KEYSTORE_PATH="$RESOLVED"
[ -n "$KEYSTORE_PATH" ] || usage_error "LATIFI_KEYSTORE_PATH is missing. Point it at the release keystore kept outside the repository"
[ -f "$KEYSTORE_PATH" ] || usage_error "the keystore file named by LATIFI_KEYSTORE_PATH does not exist"
KEYSTORE_REAL="$(cd "$(dirname "$KEYSTORE_PATH")" && pwd -P)/$(basename "$KEYSTORE_PATH")"
case "$KEYSTORE_REAL" in
  "$ROOT"/*) usage_error "the keystore lives inside the repository. Keep the release key outside it" ;;
esac

# read_secret PROPKEY ENVNAME PROMPT silent|visible: sets RESOLVED from the usual sources, else asks.
read_secret() {
  local value
  resolve "$1" "$2"
  value="$RESOLVED"
  if [ -z "$value" ]; then
    [ -t 0 ] || usage_error "$2 is missing and there is no terminal to ask for it"
    if [ "$4" = "silent" ]; then
      printf '%s' "$3" >&2
      IFS= read -rs value
      printf '\n' >&2
    else
      printf '%s' "$3" >&2
      IFS= read -r value
    fi
    [ -n "$value" ] || usage_error "$2 must not be empty"
  fi
  RESOLVED="$value"
}
read_secret signing.keyAlias LATIFI_KEY_ALIAS "Key alias: " visible; KEY_ALIAS="$RESOLVED"
read_secret signing.storePassword LATIFI_KEYSTORE_PASSWORD "Keystore password: " silent; KEYSTORE_PASSWORD="$RESOLVED"
read_secret signing.keyPassword LATIFI_KEY_PASSWORD "Key password: " silent; KEY_PASSWORD="$RESOLVED"

resolve landing.url LATIFI_LANDING_URL; LANDING_URL="$RESOLVED"
[ -n "$LANDING_URL" ] || LANDING_URL="$LANDING_BASE"
resolve terms.url LATIFI_TERMS_URL; TERMS_URL="$RESOLVED"
[ -n "$TERMS_URL" ] || TERMS_URL="${LANDING_BASE}terms.html"
resolve privacy.url LATIFI_PRIVACY_URL; PRIVACY_URL="$RESOLVED"
[ -n "$PRIVACY_URL" ] || PRIVACY_URL="${LANDING_BASE}privacy.html"

# The variables live only in this process and in the Gradle child, never on a command line.
export REOWN_PROJECT_ID LATIFI_API_BASE_URL="$API_BASE_URL"
export LATIFI_KEYSTORE_PATH="$KEYSTORE_REAL" LATIFI_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD"
export LATIFI_KEY_ALIAS="$KEY_ALIAS" LATIFI_KEY_PASSWORD="$KEY_PASSWORD"
export LATIFI_VERSION_CODE="$VERSION_CODE" LATIFI_VERSION_NAME="$VERSION_NAME"
export LATIFI_LANDING_URL="$LANDING_URL" LATIFI_TERMS_URL="$TERMS_URL" LATIFI_PRIVACY_URL="$PRIVACY_URL"

# --- 3. Android build tools ------------------------------------------------------------------
find_build_tools() { # prints the newest build-tools directory that holds both tools
  local sdk dir best="" v
  for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
    [ -n "$sdk" ] && [ -d "$sdk/build-tools" ] || continue
    for dir in $(ls "$sdk/build-tools" | sort -t. -k1,1n -k2,2n -k3,3n); do
      if [ -x "$sdk/build-tools/$dir/apksigner" ] && [ -x "$sdk/build-tools/$dir/aapt2" ]; then
        best="$sdk/build-tools/$dir"
      fi
    done
    [ -z "$best" ] || break
  done
  [ -n "$best" ] && printf '%s' "$best"
}
BT="$(find_build_tools || true)"
if [ -n "$BT" ]; then
  APKSIGNER="$BT/apksigner"
  AAPT2="$BT/aapt2"
elif command -v apksigner >/dev/null 2>&1 && command -v aapt2 >/dev/null 2>&1; then
  APKSIGNER="$(command -v apksigner)"
  AAPT2="$(command -v aapt2)"
else
  tool_missing "apksigner and aapt2 (set ANDROID_HOME to an SDK with build-tools)"
fi

# --- 4. build --------------------------------------------------------------------------------
mkdir -p "$OUT_DIR" || usage_error "cannot create $OUT_DIR"
echo "build-release: building $VERSION_NAME (code $VERSION_CODE) from $GIT_SHA against $API_HOST"
if [ "$SKIP_TESTS" -eq 1 ]; then
  warn "--skip-tests: unit tests and lint are NOT run for this build"
  GRADLE_TASKS="assembleRelease"
else
  GRADLE_TASKS="testDebugUnitTest lintDebug assembleRelease"
fi
# shellcheck disable=SC2086
./gradlew --no-daemon $GRADLE_TASKS 2>&1 | tee "$OUT_DIR/gradle.log"
if [ "${PIPESTATUS[0]}" -ne 0 ]; then
  check_failed "the Gradle build failed. See $OUT_DIR/gradle.log"
fi

BUILT_APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
[ -f "$BUILT_APK" ] || check_failed "Gradle finished but $BUILT_APK is missing. The release APK is unsigned or was not built"

# --- 5. inspection ---------------------------------------------------------------------------
SIGNER_OUT="$("$APKSIGNER" verify --verbose --print-certs "$BUILT_APK" 2>&1)" ||
  check_failed "apksigner could not verify the signature of the release APK"
CERT_SHA256="$(printf '%s\n' "$SIGNER_OUT" | sed -En 's/^(V[0-9.]+ )?Signer( #1)?:? certificate SHA-256 digest: *//p' | head -1 | tr 'A-F' 'a-f' | tr -d ':')"
printf '%s' "$CERT_SHA256" | grep -Eq '^[0-9a-f]{64}$' || check_failed "no signer certificate SHA-256 digest in the apksigner output"
if printf '%s\n' "$SIGNER_OUT" | grep -qi 'CN=Android Debug'; then
  check_failed "the APK is signed with the Android debug key"
fi
if [ -n "${LATIFI_EXPECTED_CERT_SHA256:-}" ]; then
  EXPECTED_CERT="$(printf '%s' "$LATIFI_EXPECTED_CERT_SHA256" | tr 'A-F' 'a-f' | tr -d ':')"
  [ "$CERT_SHA256" = "$EXPECTED_CERT" ] ||
    check_failed "the signing certificate does not match LATIFI_EXPECTED_CERT_SHA256. Wrong keystore?"
fi

BADGING="$("$AAPT2" dump badging "$BUILT_APK" 2>&1)" || check_failed "aapt2 could not read the APK badging"
PKG_LINE="$(printf '%s\n' "$BADGING" | grep -m1 '^package:')"
printf '%s' "$PKG_LINE" | grep -q "name='$APP_ID'" || check_failed "the package id is not $APP_ID"
printf '%s' "$PKG_LINE" | grep -q "versionCode='$VERSION_CODE'" || check_failed "the APK version code is not $VERSION_CODE"
printf '%s' "$PKG_LINE" | grep -q "versionName='$VERSION_NAME'" || check_failed "the APK version name is not $VERSION_NAME"
if printf '%s\n' "$BADGING" | grep -q '^application-debuggable'; then
  check_failed "the APK is debuggable"
fi

PERMS="$(printf '%s\n' "$BADGING" | sed -n "s/^uses-permission[a-z0-9-]*: name='\\([^']*\\)'.*/\\1/p" | sort -u)"
FORBIDDEN="$(printf '%s\n' "$PERMS" | grep -E 'LOCATION|CONTACTS|GET_ACCOUNTS|CAMERA|RECORD_AUDIO|READ_MEDIA|EXTERNAL_STORAGE|READ_PHONE|READ_SMS|BODY_SENSORS' || true)"
if [ -n "$FORBIDDEN" ]; then
  check_failed "forbidden permission in the release APK, the privacy page says there is none: $(printf '%s' "$FORBIDDEN" | tr '\n' ' ')"
fi

MANIFEST="$("$AAPT2" dump xmltree --file AndroidManifest.xml "$BUILT_APK" 2>&1)" || check_failed "aapt2 could not read the manifest"
COMPONENTS="$(printf '%s\n' "$MANIFEST" | grep -Ei 'firebase|com\.google\.android\.gms' | sed 's/^[[:space:]]*//' | sort -u | head -5)"
if [ -n "$COMPONENTS" ]; then
  check_failed "the manifest holds a Firebase or Google Play services component, the privacy page says there is none: $(printf '%s' "$COMPONENTS" | tr '\n' ' ')"
fi
if printf '%s\n' "$MANIFEST" | grep -E 'usesCleartextTraffic' | grep -Eq '=(true|\(type 0x12\)0xffffffff|\(type 0x12\)0x1)'; then
  check_failed "the manifest allows cleartext traffic"
fi

# --- 6. outputs ------------------------------------------------------------------------------
FINAL_APK="$OUT_DIR/latifi-wallet-$VERSION_NAME-$VERSION_CODE.apk"
cp "$BUILT_APK" "$FINAL_APK" || check_failed "cannot copy the APK to $OUT_DIR"
APK_SHA256="$(sha256_of "$FINAL_APK")"
printf '%s  %s\n' "$APK_SHA256" "$(basename "$FINAL_APK")" >"$FINAL_APK.sha256"
printf '%s\n' "$PERMS" >"$OUT_DIR/permissions.txt"
BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
{
  printf '{\n'
  printf '  "versionName": "%s",\n' "$VERSION_NAME"
  printf '  "versionCode": %s,\n' "$VERSION_CODE"
  printf '  "gitSha": "%s",\n' "$GIT_SHA"
  printf '  "tag": "%s",\n' "$TAG"
  printf '  "apkFile": "%s",\n' "$(basename "$FINAL_APK")"
  printf '  "certSha256": "%s",\n' "$CERT_SHA256"
  printf '  "apkSha256": "%s",\n' "$APK_SHA256"
  printf '  "apiHost": "%s",\n' "$API_HOST"
  printf '  "builtAtUtc": "%s"\n' "$BUILD_TIME"
  printf '}\n'
} >"$OUT_DIR/build-info.json"

echo "build-release: certificate SHA-256 $CERT_SHA256"
echo "build-release: APK SHA-256 $APK_SHA256"
echo "build-release: permissions: $(printf '%s' "$PERMS" | tr '\n' ' ')"
echo "build-release: ok $FINAL_APK"
