#!/usr/bin/env bash
# Validates a secret value based on its expected type.
# Usage: verify <type> <name> [<password>]
# Types: nonempty, base64, json, p8, p12, mobileprovision

set -euo pipefail

type="$1"
name="$2"
value="${!name:-}"

fail() { echo "ERROR: $name: $1"; exit 1; }

[ -z "$value" ] && fail "is not set or empty"

case "$type" in
  nonempty)
    ;;
  base64)
    echo "$value" | base64 -d > /dev/null 2>&1 || fail "is not valid base64"
    ;;
  json)
    echo "$value" | base64 -d | jq . > /dev/null 2>&1 || fail "is not valid base64-encoded JSON"
    ;;
  p8)
    tmp=$(mktemp)
    echo "$value" | base64 -d > "$tmp"
    openssl ec -in "$tmp" -noout 2>/dev/null || { rm "$tmp"; fail "is not a valid base64-encoded EC private key"; }
    rm "$tmp"
    ;;
  p12)
    password="${3:-}"
    tmp=$(mktemp)
    echo "$value" | base64 -d > "$tmp"
    openssl pkcs12 -in "$tmp" -nokeys -passin "pass:$password" -legacy > /dev/null 2>&1 \
      || openssl pkcs12 -in "$tmp" -nokeys -passin "pass:$password" > /dev/null 2>&1 \
      || { rm "$tmp"; fail "is not a valid .p12 or password is wrong"; }
    rm "$tmp"
    ;;
  mobileprovision)
    tmp=$(mktemp)
    echo "$value" | base64 -d > "$tmp"
    /usr/bin/security cms -D -i "$tmp" > /dev/null 2>&1 || { rm "$tmp"; fail "is not a valid provisioning profile"; }
    rm "$tmp"
    ;;
  *)
    fail "unknown type: $type"
    ;;
esac

echo "  ✓ $name"
