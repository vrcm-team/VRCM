#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: package-ipa.sh <archive-dir> <output-ipa>" >&2
  exit 2
fi

ARCHIVE_DIR="$1"
OUTPUT_IPA="$2"
APPLICATIONS_DIR="$ARCHIVE_DIR/Products/Applications"

shopt -s nullglob
APPS=("$APPLICATIONS_DIR"/*.app)
shopt -u nullglob
if [ "${#APPS[@]}" -ne 1 ]; then
  echo "Expected exactly one .app in $APPLICATIONS_DIR, found ${#APPS[@]}" >&2
  exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
PAYLOAD_DIR="$WORK/Payload"
PACKAGED_APP="$PAYLOAD_DIR/$(basename "${APPS[0]}")"
mkdir -p "$PAYLOAD_DIR"

# ditto preserves executable modes, extended attributes, and symbolic links.
ditto "${APPS[0]}" "$PACKAGED_APP"

echo "[IPA] Applying ad-hoc signature to $(basename "$PACKAGED_APP")"
codesign --force --deep --sign - --timestamp=none "$PACKAGED_APP"

mkdir -p "$(dirname "$OUTPUT_IPA")"
rm -f "$OUTPUT_IPA"
(
  cd "$WORK"
  zip -qry -y "$OUTPUT_IPA" Payload
)
echo "[IPA] Created $OUTPUT_IPA"
