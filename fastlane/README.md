# Release Deployment

## How to Release

Trigger from GitHub Actions tab → select workflow → "Run workflow" on `main`.

- **Release Android** — builds AAB, uploads to Play Store, tags version
- **Release iOS** — builds IPA, uploads to TestFlight, tags version

Both can be triggered from the same commit. The second one adds its artifact to the existing GitHub Release.

## Version Scheme

- Tag format: `v{commit_count}` (e.g., `v325`)
- Android: `versionCode` = commit count, `versionName` = `"{count}.{sha} {date}"`
- iOS: `CFBundleVersion` = commit count, `CFBundleShortVersionString` = `"1.0.{count}"`

## GitHub Secrets

### Shared

| Secret | Description | How to create |
|--------|-------------|---------------|
| `TRACKING_HINT` | HMAC secret for tracking requests | |
| `GOOGLE_SERVICES_JSON` | Firebase Android config (base64) | Download from Firebase Console, base64 encode |
| `GOOGLE_SERVICE_INFO_PLIST` | Firebase iOS config (base64) | Download from Firebase Console, base64 encode |
| `GOOGLE_SERVICE_INFO_DEBUG_PLIST` | Firebase iOS debug config (base64) | Download from Firebase Console, base64 encode |

### Android

| Secret | Description | How to create |
|--------|-------------|---------------|
| `SIGNING_KEYSTORE_BASE64` | Release keystore | `base64 -i your-keystore.jks \| pbcopy` |
| `SIGNING_STORE_PASSWORD` | Keystore password | Set when keystore was created |
| `SIGNING_KEY_ALIAS` | Key alias (e.g., `where`) | Set when keystore was created |
| `SIGNING_KEY_PASSWORD` | Key password | Set when keystore was created |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Play Console service account key (base64) | See [Play Store setup](#play-store-service-account) |

### iOS

| Secret | Description | How to create |
|--------|-------------|---------------|
| `DISTRIBUTION_CERTIFICATE_BASE64` | Apple Distribution .p12 (base64) | See [iOS signing setup](#ios-signing) |
| `DISTRIBUTION_CERTIFICATE_PASSWORD` | Password set when exporting .p12 | Set during Keychain export |
| `PROVISIONING_PROFILE_BASE64` | App Store provisioning profile (base64) | See [iOS signing setup](#ios-signing) |
| `ASC_KEY_ID` | App Store Connect API key ID | From App Store Connect → Users and Access → Integrations → Keys |
| `ASC_ISSUER_ID` | App Store Connect issuer ID | Shown at the top of the Keys page |
| `ASC_KEY_CONTENT` | App Store Connect .p8 key contents | Download when creating the key (one-time only) |

## Setup Guides

### Play Store Service Account

1. Go to Google Cloud Console → IAM & Admin → Service Accounts
2. Create a service account (e.g., `fastlane-deploy`)
3. Keys tab → Add Key → Create new key → JSON — downloads the key file
4. Enable the [Google Play Android Developer API](https://console.developers.google.com/apis/api/androidpublisher.googleapis.com/)
5. Go to Play Console → Users and permissions → Invite new users
6. Paste the service account email (e.g., `fastlane-deploy@project.iam.gserviceaccount.com`)
7. Grant release permissions for the app
8. Encode: `base64 -i service-account.json | pbcopy` → save as `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`

Note: The very first AAB must be uploaded manually via Play Console. Fastlane cannot create new app listings.

### iOS Signing

#### Distribution Certificate

1. Open Keychain Access → Certificate Assistant → Request a Certificate From a Certificate Authority
2. Save the `.certSigningRequest` file
3. Go to Apple Developer → Certificates → + → iOS Distribution (App Store and Ad Hoc)
4. Upload the CSR, download the certificate, double-click to install
5. In Keychain Access → My Certificates → find "iPhone Distribution" → right-click → Export as .p12
6. Set a password (this becomes `DISTRIBUTION_CERTIFICATE_PASSWORD`)
7. Encode: `base64 -i certificate.p12 | pbcopy` → save as `DISTRIBUTION_CERTIFICATE_BASE64`

#### Provisioning Profile

1. Go to Apple Developer → Certificates, Identifiers & Profiles → Profiles
2. Click + → App Store Connect (under Distribution)
3. Select app ID `no.synth.where` and your distribution certificate
4. Download the `.mobileprovision` file
5. Verify: `security cms -D -i profile.mobileprovision > /dev/null && echo "Valid"`
6. Encode: `base64 -i profile.mobileprovision | pbcopy` → save as `PROVISIONING_PROFILE_BASE64`

## Known Issues

### iOS test linking fails with Compose Multiplatform alpha

`iosSimulatorArm64Test` fails due to `UIViewLayoutRegion` (iOS 18+ symbol) not being weak-linked in Compose Multiplatform 1.11.0-alpha04. The release workflow works around this by running `testDebugUnitTest` (common tests on JVM) and `compileTestKotlinIosSimulatorArm64` (compile-only check). This should be resolved in a stable Compose release.

