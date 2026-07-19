#!/usr/bin/env bash
#
# Re-sign the ad-hoc VRCM IPA with a local Apple certificate and provisioning
# profile, then optionally install it on a connected iPhone.
#
# Build the input IPA first:
#   ./gradlew :iosApp:buildReleaseIpa
#
# Inspect available certificates, profiles, and devices:
#   ./iosApp/resign-and-install.sh --list
#
# Re-sign and install using automatically selected values:
#   ./iosApp/resign-and-install.sh
#
# Override automatic selection when needed:
#   IDENTITY="Apple Development: you@mail.com (ABCDE12345)" \
#   PROFILE="$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles/profile.mobileprovision" \
#   BUNDLE_ID="io.github.vrcmteam.vrcm" \
#   ./iosApp/resign-and-install.sh
#
# Re-sign without installing:
#   ./iosApp/resign-and-install.sh --no-install
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROFILE_DIRS=(
  "$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles"
  "$HOME/Library/MobileDevice/Provisioning Profiles"
)

# Priority: environment variables, optional config file, automatic detection.
_env_IPA="${IPA:-}"
_env_IDENTITY="${IDENTITY:-}"
_env_PROFILE="${PROFILE:-}"
_env_BUNDLE_ID="${BUNDLE_ID:-}"
_env_DEVICE="${DEVICE:-}"

CONFIG_FILE="${CONFIG_FILE:-$SCRIPT_DIR/resign.config}"
if [ -f "$CONFIG_FILE" ]; then
  printf '[resign] Loading config: %s\n' "$CONFIG_FILE"
  # shellcheck disable=SC1090
  source "$CONFIG_FILE"
fi

IPA="${_env_IPA:-${IPA:-$SCRIPT_DIR/build/archives/release/VRCM.ipa}}"
IDENTITY="${_env_IDENTITY:-${IDENTITY:-}}"
PROFILE="${_env_PROFILE:-${PROFILE:-}}"
BUNDLE_ID="${_env_BUNDLE_ID:-${BUNDLE_ID:-}}"
DEVICE="${_env_DEVICE:-${DEVICE:-}}"
DO_INSTALL=1

log() { printf '\033[0;36m[resign]\033[0m %s\n' "$*"; }
ok() { printf '\033[0;32m[ ok ]\033[0m %s\n' "$*"; }
warn() { printf '\033[0;33m[warn]\033[0m %s\n' "$*"; }
die() { printf '\033[0;31m[fail]\033[0m %s\n' "$*" >&2; exit 1; }

decode_profile() {
  security cms -D -i "$1" 2>/dev/null
}

plist_get() {
  /usr/libexec/PlistBuddy -c "Print :$2" "$1" 2>/dev/null || true
}

list_env() {
  echo "==================== Code signing identities ===================="
  security find-identity -p codesigning -v || true
  echo
  echo "==================== Provisioning profiles ===================="

  local found=0
  local dir p plist name appid team expiration
  shopt -s nullglob
  for dir in "${PROFILE_DIRS[@]}"; do
    [ -d "$dir" ] || continue
    for p in "$dir"/*.mobileprovision "$dir"/*.provisionprofile; do
      plist="$(mktemp)"
      decode_profile "$p" > "$plist" || true
      name="$(plist_get "$plist" 'Name')"
      appid="$(plist_get "$plist" 'Entitlements:application-identifier')"
      team="$(plist_get "$plist" 'Entitlements:com.apple.developer.team-identifier')"
      expiration="$(plist_get "$plist" 'ExpirationDate')"
      printf '  - %s\n    AppID=%s Team=%s Expires=%s\n    File=%s\n' \
        "${name:-Unknown}" "${appid:-?}" "${team:-?}" "${expiration:-?}" "$p"
      rm -f "$plist"
      found=1
    done
  done
  shopt -u nullglob
  [ "$found" -eq 0 ] && warn "No provisioning profiles found in the Xcode profile directories."

  echo
  echo "==================== Connected devices ===================="
  if xcrun devicectl list devices >/dev/null 2>&1; then
    xcrun devicectl list devices 2>/dev/null | grep -viE "simulator" || true
  else
    xcrun xctrace list devices 2>/dev/null \
      | sed -n '/== Devices ==/,/== Simulators ==/p' \
      | grep -v "Simulators" || true
  fi
}

auto_identity() {
  local line
  line="$(security find-identity -p codesigning -v 2>/dev/null \
    | grep -iE "Apple Development|iPhone Developer" \
    | head -1)" || true
  if [ -z "$line" ]; then
    line="$(security find-identity -p codesigning -v 2>/dev/null \
      | grep -E '^[[:space:]]*[0-9]+\)' \
      | head -1)" || true
  fi
  [ -z "$line" ] && return 1
  echo "$line" | sed -E 's/.*"([^"]+)".*/\1/'
}

auto_profile() {
  local wanted_bundle="$1"
  local wanted_team="${2:-}"
  local dir p plist appid team profile_bundle
  local best_both=""
  local best_team=""
  local best_bundle=""
  local best_any=""

  shopt -s nullglob
  for dir in "${PROFILE_DIRS[@]}"; do
    [ -d "$dir" ] || continue
    for p in "$dir"/*.mobileprovision "$dir"/*.provisionprofile; do
      plist="$(mktemp)"
      decode_profile "$p" > "$plist" || {
        rm -f "$plist"
        continue
      }
      appid="$(plist_get "$plist" 'Entitlements:application-identifier')"
      team="$(plist_get "$plist" 'Entitlements:com.apple.developer.team-identifier')"
      rm -f "$plist"
      [ -z "$appid" ] && continue

      profile_bundle="${appid#*.}"
      [ -z "$best_any" ] && best_any="$p"
      if [ "$profile_bundle" = "*" ] || [ "$profile_bundle" = "$wanted_bundle" ]; then
        [ -z "$best_bundle" ] && best_bundle="$p"
        if [ -n "$wanted_team" ] && [ "$team" = "$wanted_team" ]; then
          [ -z "$best_both" ] && best_both="$p"
        fi
      fi
      if [ -n "$wanted_team" ] && [ "$team" = "$wanted_team" ]; then
        [ -z "$best_team" ] && best_team="$p"
      fi
    done
  done
  shopt -u nullglob

  local selected="${best_both:-${best_team:-${best_bundle:-$best_any}}}"
  [ -n "$selected" ] || return 1
  echo "$selected"
}

identity_team() {
  echo "$1" | sed -nE 's/.*\(([A-Z0-9]{10})\).*/\1/p'
}

auto_device() {
  local json udid
  json="$(xcrun devicectl list devices --json-output /dev/stdout 2>/dev/null || true)"
  if [ -n "$json" ]; then
    udid="$(printf '%s' "$json" | /usr/bin/python3 -c '
import json
import sys

try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)

for device in data.get("result", {}).get("devices", []):
    connection = device.get("connectionProperties", {})
    connected = connection.get("tunnelState") in ("connected", "available")
    paired = connection.get("pairingState") == "paired"
    if connected or paired:
        udid = device.get("hardwareProperties", {}).get("udid")
        if udid:
            print(udid)
            break
' 2>/dev/null || true)"
  fi
  echo "${udid:-}"
}

for arg in "$@"; do
  case "$arg" in
    --list)
      list_env
      exit 0
      ;;
    --no-install)
      DO_INSTALL=0
      ;;
    -h|--help)
      sed -n '2,22p' "${BASH_SOURCE[0]}"
      exit 0
      ;;
    *)
      die "Unknown argument: $arg (use --list, --no-install, or --help)"
      ;;
  esac
done

command -v codesign >/dev/null || die "codesign is unavailable. Install the Xcode command-line tools."
command -v security >/dev/null || die "security is unavailable."
[ -x /usr/libexec/PlistBuddy ] || die "PlistBuddy is unavailable."
[ -f "$IPA" ] || die "IPA not found: $IPA (run ./gradlew :iosApp:buildReleaseIpa first)"

if [ -z "$IDENTITY" ]; then
  IDENTITY="$(auto_identity || true)"
fi
[ -n "$IDENTITY" ] || die "No signing identity found. Set IDENTITY or run --list."
ok "Signing identity: $IDENTITY"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
log "Extracting IPA to $WORK"
unzip -q "$IPA" -d "$WORK"
APP="$(find "$WORK/Payload" -maxdepth 1 -type d -name '*.app' | head -1)"
[ -n "$APP" ] || die "Payload does not contain an .app bundle."
ORIGINAL_BUNDLE="$(plist_get "$APP/Info.plist" 'CFBundleIdentifier')"
[ -n "$ORIGINAL_BUNDLE" ] || die "The app Info.plist has no CFBundleIdentifier."
log "Original bundle identifier: $ORIGINAL_BUNDLE"

IDENTITY_TEAM="$(identity_team "$IDENTITY")"
if [ -z "$PROFILE" ]; then
  PROFILE="$(auto_profile "${BUNDLE_ID:-$ORIGINAL_BUNDLE}" "$IDENTITY_TEAM" || true)"
fi
[ -n "$PROFILE" ] && [ -f "$PROFILE" ] \
  || die "No provisioning profile found. Set PROFILE or run --list."
ok "Provisioning profile: $PROFILE"

PROFILE_PLIST="$WORK/profile.plist"
decode_profile "$PROFILE" > "$PROFILE_PLIST"
ENTITLEMENTS="$WORK/entitlements.plist"
/usr/libexec/PlistBuddy -x -c 'Print :Entitlements' "$PROFILE_PLIST" > "$ENTITLEMENTS" \
  || die "The provisioning profile has no Entitlements dictionary."
TEAM_ID="$(plist_get "$PROFILE_PLIST" 'Entitlements:com.apple.developer.team-identifier')"
PROFILE_APP_ID="$(plist_get "$PROFILE_PLIST" 'Entitlements:application-identifier')"
PROFILE_BUNDLE="${PROFILE_APP_ID#*.}"
ok "Team=$TEAM_ID ProfileAppID=$PROFILE_APP_ID"

if [ -n "$IDENTITY_TEAM" ] && [ -n "$TEAM_ID" ] && [ "$IDENTITY_TEAM" != "$TEAM_ID" ]; then
  die "Signing identity team $IDENTITY_TEAM does not match profile team $TEAM_ID."
fi

if [ -z "$BUNDLE_ID" ]; then
  if [ "$PROFILE_BUNDLE" = "*" ]; then
    BUNDLE_ID="$ORIGINAL_BUNDLE"
  else
    BUNDLE_ID="$PROFILE_BUNDLE"
  fi
fi
log "Signed bundle identifier: $BUNDLE_ID"

rm -rf "$APP/_CodeSignature"
cp "$PROFILE" "$APP/embedded.mobileprovision"
/usr/libexec/PlistBuddy -c "Set :CFBundleIdentifier $BUNDLE_ID" "$APP/Info.plist"

sign_one() {
  codesign --force --timestamp=none --sign "$IDENTITY" "$@"
}

log "Signing nested libraries and frameworks"
while IFS= read -r -d '' item; do
  sign_one "$item"
done < <(find "$APP" \( -name '*.dylib' -o -name '*.framework' \) -print0)

if [ -d "$APP/PlugIns" ]; then
  while IFS= read -r -d '' extension; do
    log "Signing extension: $(basename "$extension")"
    sign_one --entitlements "$ENTITLEMENTS" "$extension"
  done < <(find "$APP/PlugIns" -maxdepth 1 -name '*.appex' -print0)
fi

log "Signing main app"
sign_one --entitlements "$ENTITLEMENTS" "$APP"
codesign --verify --deep --strict --verbose=2 "$APP" \
  && ok "Signature verification passed" \
  || die "Signature verification failed."
codesign -dv "$APP" 2>&1 \
  | grep -iE "Authority|TeamIdentifier|Identifier" \
  | sed 's/^/    /' || true

OUT_IPA="$SCRIPT_DIR/build/archives/release/VRCM-signed.ipa"
rm -f "$OUT_IPA"
(
  cd "$WORK"
  zip -qry "$OUT_IPA" Payload
)
ok "Signed IPA: $OUT_IPA"

if [ "$DO_INSTALL" -eq 0 ]; then
  log "--no-install selected; skipping device installation."
  exit 0
fi

log "Installing on a connected iPhone"
if command -v ios-deploy >/dev/null 2>&1; then
  if ios-deploy --bundle "$APP" --no-wifi; then
    ok "Installation completed with ios-deploy."
    exit 0
  fi
  warn "ios-deploy failed; retrying with devicectl."
fi

if xcrun devicectl list devices >/dev/null 2>&1; then
  if [ -z "$DEVICE" ]; then
    DEVICE="$(auto_device)"
  fi
  [ -n "$DEVICE" ] || die "No connected iPhone found. Set DEVICE or run --list."
  log "Target device: $DEVICE"
  if xcrun devicectl device install app --device "$DEVICE" "$APP"; then
    ok "Installation completed with devicectl. You can now open VRCM."
    exit 0
  fi
  die "devicectl installation failed."
fi

die "Neither ios-deploy nor devicectl is available. Install with Xcode Devices instead."
