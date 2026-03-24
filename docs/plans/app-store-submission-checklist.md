# App Store Submission Checklist

## App Store Connect Setup

- [ ] Create app record in App Store Connect (bundle ID: `org.marshellis.commcare.ios`)
- [ ] Set primary category: **Health & Fitness** (or **Business**)
- [ ] Set secondary category: **Productivity**
- [ ] Set age rating: 4+ (no restricted content)
- [ ] Set pricing: **Free**
- [ ] Set availability: all territories (or specific target markets)

## App Metadata

- [ ] App name: **CommCare**
- [ ] Subtitle: **Mobile Data Collection & Case Management**
- [ ] Description: see `app/iosApp/metadata/en-US/description.txt`
- [ ] Keywords: see `app/iosApp/metadata/en-US/keywords.txt`
- [ ] Support URL: `https://www.dimagi.com/commcare/`
- [ ] Marketing URL: `https://www.dimagi.com/commcare/` (optional)
- [ ] Privacy policy URL: `https://www.dimagi.com/terms/`

## Screenshots (required)

Must provide for at least these device sizes:
- [ ] **6.7" iPhone** (iPhone 15 Pro Max / 16 Pro Max) — 1290 x 2796 or 1320 x 2868
- [ ] **6.5" iPhone** (iPhone 11 Pro Max / XS Max) — 1242 x 2688
- [ ] **12.9" iPad** (iPad Pro 6th gen) — 2048 x 2732

Minimum 1 screenshot per size, maximum 10. Recommended screenshots:
1. Login screen
2. App menu / home screen
3. Case list
4. Form entry
5. Sync status

## App Review Notes

Provide these to Apple's review team:
- [ ] Demo account credentials (username + password for a test domain)
- [ ] Test domain URL
- [ ] Brief description of app purpose: "CommCare is used by community health workers and frontline staff to collect data, manage cases, and submit forms to a central server (CommCare HQ). The reviewer can log in with the demo credentials to see sample forms and cases."
- [ ] Note about server requirement: "This app requires a CommCare HQ account to function. Demo credentials are provided above."

## Privacy Questionnaire

Apple requires answering the App Privacy section. CommCare's data practices:

### Data Collected
- [ ] **Contact Info** (name) — used for user profile, linked to identity
- [ ] **Health & Fitness** — if forms collect health data (user-configured, not by default)
- [ ] **Location** — precise location captured in forms with GPS questions
- [ ] **User Content** — photos, audio, video, documents attached to forms
- [ ] **Identifiers** — user ID, device ID for sync
- [ ] **Usage Data** — form submission counts, sync timestamps

### Data Use
- [ ] Data is **not** used for tracking
- [ ] Data is **not** sold to third parties
- [ ] Data is sent to the user's own CommCare HQ server (self-hosted or Dimagi-hosted)

## Encryption Export Compliance

CommCare uses AES-256 encryption for data at rest (PBKDF2 key derivation, AES-GCM on JVM, AES-CBC+HMAC on iOS). This requires:

- [ ] Set `ITSAppUsesNonExemptEncryption` to `YES` in Info.plist
- [ ] File an encryption export compliance self-classification report in App Store Connect
- [ ] Classification: the encryption is used for data protection (not communication), which qualifies for exemption under Category 5 Part 2 of the EAR
- [ ] Alternatively: if solely for authentication/data protection of user data stored on device, may qualify for the exemption (no ERN needed)

## Info.plist Verification

Before submission, verify:
- [x] `NSFaceIDUsageDescription` — present
- [x] `NSCameraUsageDescription` — present
- [x] `NSMicrophoneUsageDescription` — present
- [x] `NSPhotoLibraryUsageDescription` — present
- [x] `NSLocationWhenInUseUsageDescription` — present
- [ ] `ITSAppUsesNonExemptEncryption` — add (set to YES)
- [x] `UISupportedInterfaceOrientations` — portrait only (iPhone)
- [x] `UISupportedInterfaceOrientations~ipad` — all orientations
- [x] `UILaunchScreen` — configured with LaunchLogo + LaunchBackground

## Build & Archive

- [ ] Set version to 1.0.0 (CFBundleShortVersionString)
- [ ] Build number auto-incremented via git commit count
- [ ] Archive with Release configuration
- [ ] Upload via Xcode Organizer or `xcrun altool`
- [ ] Wait for processing (usually 5-30 minutes)
- [ ] Submit for review

## Post-Submission

- [ ] Monitor App Store Connect for review status
- [ ] Respond to any reviewer questions within 24 hours
- [ ] Expected review time: 24-48 hours (first submission may take longer)
