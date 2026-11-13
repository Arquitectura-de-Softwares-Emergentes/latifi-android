#!/usr/bin/env bash
# Uploads the signed release APK to Firebase App Distribution through the Firebase CLI.
#
# Usage: bash scripts/release/distribute.sh APK [--app-id ID] [--group ALIAS]
#                                           [--notes-file FILE] [--dry-run]
#
# APK is the file written by build-release.sh. build-info.json must sit beside it.
# FIREBASE_APP_ID and FIREBASE_GROUP stand in for the flags. GOOGLE_APPLICATION_CREDENTIALS, when
# set, must name a service account file outside the repository with mode 600. Without it the script
# relies on an existing `firebase login` session. The app carries no Firebase SDK. Only this CLI
# talks to Firebase, and it uploads a file that is already signed.
#
# Exit codes: 0 ok, 1 a check failed, 2 bad usage or invalid configuration, 3 a required tool is
# missing. Compatible with bash 3.2.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT="$(cd "$HERE/../.." && pwd -P)"

APK=""
APP_ID="${FIREBASE_APP_ID:-}"
GROUP="${FIREBASE_GROUP:-}"
NOTES_FILE=""
DRY_RUN=0

usage_error() { echo "distribute: $*" >&2; exit 2; }
check_failed() { echo "distribute: FAILED: $*" >&2; exit 1; }
tool_missing() { echo "distribute: required tool missing: $*" >&2; exit 3; }

while [ $# -gt 0 ]; do
  case "$1" in
    --app-id) [ $# -ge 2 ] || usage_error "--app-id needs a value"; APP_ID="$2"; shift 2 ;;
    --group) [ $# -ge 2 ] || usage_error "--group needs a value"; GROUP="$2"; shift 2 ;;
    --notes-file) [ $# -ge 2 ] || usage_error "--notes-file needs a value"; NOTES_FILE="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h | --help) sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) usage_error "unknown option: $1" ;;
    *) [ -z "$APK" ] || usage_error "only one APK path is allowed"; APK="$1"; shift ;;
  esac
done

[ -n "$APK" ] || usage_error "the APK path is missing"
if command -v shasum >/dev/null 2>&1; then
  sha256_of() { shasum -a 256 "$1" | cut -d' ' -f1; }
elif command -v sha256sum >/dev/null 2>&1; then
  sha256_of() { sha256sum "$1" | cut -d' ' -f1; }
else
  tool_missing "shasum or sha256sum"
fi

# --- pinned CLI version ----------------------------------------------------------------------
PIN_FILE="$HERE/firebase-tools.version"
[ -f "$PIN_FILE" ] || usage_error "the pin file scripts/release/firebase-tools.version is missing"
PIN="$(tr -d '[:space:]' <"$PIN_FILE")"
case "$PIN" in
  '' | UNSET) usage_error "firebase-tools.version is UNSET. Verify the Firebase CLI package and pin its version first (plan 07-08)" ;;
esac
printf '%s' "$PIN" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' || usage_error "firebase-tools.version must hold a version like 13.35.1"

# --- the APK ---------------------------------------------------------------------------------
[ -f "$APK" ] || usage_error "the APK does not exist: $APK"
case "$APK" in *.apk) ;; *) usage_error "the file does not end in .apk" ;; esac
APK_NAME="$(basename "$APK")"
case "$(printf '%s' "$APK_NAME" | tr 'A-Z' 'a-z')" in
  *debug*) check_failed "refusing a file with debug in its name. Only the signed release APK is uploaded" ;;
esac
APK_DIR="$(cd "$(dirname "$APK")" && pwd -P)"
APK="$APK_DIR/$APK_NAME"
INFO="$APK_DIR/build-info.json"
[ -f "$INFO" ] || check_failed "build-info.json is missing beside the APK. Build it with build-release.sh"

json_field() { # KEY: one string or number value from build-info.json, which has one key per line
  sed -n "s/^[[:space:]]*\"$1\":[[:space:]]*\"\\{0,1\\}\\([^\",]*\\)\"\\{0,1\\},\\{0,1\\}[[:space:]]*\$/\\1/p" "$INFO" | head -1
}
INFO_CERT="$(json_field certSha256)"
INFO_SHA="$(json_field apkSha256)"
INFO_VERSION="$(json_field versionName)"
INFO_CODE="$(json_field versionCode)"
INFO_GIT="$(json_field gitSha)"
INFO_APK="$(json_field apkFile)"
[ -n "$INFO_CERT" ] && [ -n "$INFO_SHA" ] && [ -n "$INFO_VERSION" ] && [ -n "$INFO_CODE" ] ||
  check_failed "build-info.json is incomplete"
[ "$INFO_APK" = "$APK_NAME" ] || check_failed "build-info.json describes $INFO_APK, not $APK_NAME"
[ "$(sha256_of "$APK")" = "$INFO_SHA" ] || check_failed "the APK hash differs from build-info.json. The file changed after the build"

find_apksigner() {
  local sdk dir best=""
  for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
    [ -n "$sdk" ] && [ -d "$sdk/build-tools" ] || continue
    for dir in $(ls "$sdk/build-tools" | sort -t. -k1,1n -k2,2n -k3,3n); do
      [ -x "$sdk/build-tools/$dir/apksigner" ] && best="$sdk/build-tools/$dir/apksigner"
    done
    [ -z "$best" ] || break
  done
  [ -n "$best" ] && printf '%s' "$best"
}
APKSIGNER="$(find_apksigner || true)"
[ -n "$APKSIGNER" ] || APKSIGNER="$(command -v apksigner || true)"
[ -n "$APKSIGNER" ] || tool_missing "apksigner (set ANDROID_HOME to an SDK with build-tools)"

SIGNER_OUT="$("$APKSIGNER" verify --print-certs "$APK" 2>&1)" ||
  check_failed "apksigner could not verify the APK. It is unsigned or damaged"
CERT="$(printf '%s\n' "$SIGNER_OUT" | sed -En 's/^(V[0-9.]+ )?Signer( #1)?:? certificate SHA-256 digest: *//p' | head -1 | tr 'A-F' 'a-f' | tr -d ':')"
[ -n "$CERT" ] || check_failed "no signer certificate in the apksigner output"
if printf '%s\n' "$SIGNER_OUT" | grep -qi 'CN=Android Debug'; then
  check_failed "the APK is signed with the Android debug key"
fi
[ "$CERT" = "$INFO_CERT" ] || check_failed "the signing certificate differs from the one in build-info.json"

# --- Firebase inputs -------------------------------------------------------------------------
[ -n "$APP_ID" ] || usage_error "the Firebase app id is missing. Pass --app-id or set FIREBASE_APP_ID"
printf '%s' "$APP_ID" | grep -Eq '^1:[0-9]+:android:[0-9a-f]+$' ||
  usage_error "the Firebase app id must look like 1:1234567890:android:0123456789abcdef"
[ -n "$GROUP" ] || usage_error "the tester group alias is missing. Pass --group or set FIREBASE_GROUP"
printf '%s' "$GROUP" | grep -Eq '^[A-Za-z0-9_,-]+$' || usage_error "the group alias holds characters Firebase does not allow"

if [ -n "$NOTES_FILE" ]; then
  [ -f "$NOTES_FILE" ] || usage_error "the release notes file does not exist"
else
  NOTES_FILE="$APK_DIR/release-notes.txt"
  {
    printf 'LatiFi Wallet %s (build %s)\n' "$INFO_VERSION" "$INFO_CODE"
    printf 'Commit %s. Testnet demo on Polygon Amoy, no real money.\n' "$INFO_GIT"
  } >"$NOTES_FILE"
fi

if [ -n "${GOOGLE_APPLICATION_CREDENTIALS:-}" ]; then
  CRED="$GOOGLE_APPLICATION_CREDENTIALS"
  [ -f "$CRED" ] || usage_error "GOOGLE_APPLICATION_CREDENTIALS names a file that does not exist"
  CRED_REAL="$(cd "$(dirname "$CRED")" && pwd -P)/$(basename "$CRED")"
  case "$CRED_REAL" in
    "$ROOT"/*) usage_error "the service account file lives inside the repository. Keep it outside" ;;
  esac
  CRED_MODE="$(stat -c '%a' "$CRED" 2>/dev/null || stat -f '%Lp' "$CRED" 2>/dev/null)"
  [ "$CRED_MODE" = "600" ] || usage_error "the service account file must have mode 600 (chmod 600 it)"
fi

# The dry run prints the exact command, shell quoted, and skips the call.
if [ "$DRY_RUN" -eq 1 ]; then
  echo "distribute: dry run, not calling firebase. The command would be:"
  printf 'firebase appdistribution:distribute %q --app %q --groups %q --release-notes-file %q\n' \
    "$APK" "$APP_ID" "$GROUP" "$NOTES_FILE"
  echo "distribute: dry run ok"
  exit 0
fi

command -v firebase >/dev/null 2>&1 || tool_missing "firebase (the pinned firebase-tools $PIN)"
FIREBASE_VERSION="$(firebase --version 2>/dev/null | tail -1 | tr -d '[:space:]')"
[ "$FIREBASE_VERSION" = "$PIN" ] ||
  check_failed "firebase --version is '$FIREBASE_VERSION' but the pin file says $PIN"

LOG="$APK_DIR/distribute-$(date -u +%Y%m%dT%H%M%SZ).log"
echo "distribute: uploading $APK_NAME ($INFO_VERSION, build $INFO_CODE) to group $GROUP"
firebase appdistribution:distribute "$APK" --app "$APP_ID" --groups "$GROUP" --release-notes-file "$NOTES_FILE" >"$LOG" 2>&1
STATUS=$?
if [ "$STATUS" -ne 0 ]; then
  echo "distribute: the Firebase CLI exited with status $STATUS. Log: $LOG" >&2
  tail -n 15 "$LOG" >&2
  exit 1
fi

echo "distribute: release links (the download link expires after one hour):"
grep -Eo 'https://[^[:space:])"]+' "$LOG" | sort -u
echo "distribute: ok"
