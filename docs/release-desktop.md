# macOS desktop release

The `Release macOS desktop` workflow (`.github/workflows/release-desktop.yml`, `workflow_dispatch`)
builds, signs, notarizes and staples a DMG, then attaches it to the `v<commit-count>` release,
shared with the Android and iOS releases of the same commit.

## Local build

```
./gradlew :desktop:run                 # run from source
./gradlew :desktop:packageDmg          # unsigned DMG under desktop/build/compose/binaries/main/dmg/
```

Packaging (jpackage) and the map's FFM rendering need a full JDK 25; the Gradle toolchain downloads
BellSoft Liberica 25 via the foojay resolver.

## Signing locally

```
./gradlew :desktop:packageDmg \
  -Pcompose.desktop.mac.sign=true \
  "-Pcompose.desktop.mac.signing.identity=Henrik Aronsen"
```

The identity is the common name of a `Developer ID Application` certificate in the login keychain.

## Required CI secrets

Notarization reuses the App Store Connect key already used by the iOS release; desktop signing needs
a Developer ID Application certificate, which the App Store distribution certificate cannot replace.

- `DEVELOPER_ID_CERTIFICATE_BASE64` / `DEVELOPER_ID_CERTIFICATE_PASSWORD`: base64 of a `.p12` holding
  the Developer ID Application cert + private key, and its export password.
- `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_CONTENT`: App Store Connect API key for `notarytool`
  (shared with the iOS workflow; `ASC_KEY_CONTENT` is base64 of the `.p8`).
- `TRACKING_HINT`, `GOOGLE_SERVICES_JSON`: as for the other builds (the Android `:app` module is
  configured as part of the build graph).

Entitlements for the hardened runtime are in `desktop/packaging/entitlements.plist` (JIT, unsigned
executable memory, and library-validation off, since the JVM, SQLite JDBC and MapLibre load unsigned
native code at runtime).
