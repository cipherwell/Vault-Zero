# VaultZero — Play internal testing setup

Internal testing is the lowest-friction way to get a beta to testers: one install link, automatic
updates, no "unknown sources" warning. This is the operational checklist; nothing here is
automated from the repo.

## Prerequisites (one-time, Chris)

1. Play Console account: <https://play.google.com/console> — one-time **$25** fee, identity
   verification can take 1–3 days.
2. Decide on **Play App Signing**. Recommended: keep the existing Cipherwell upload key as the
   *upload key* and let Google hold the app-signing key. `~/.cipherwell-release/cipherwell-upload.jks`
   stays the key we sign releases with; the certificate in the store becomes Google's.
3. Package name must be exactly `com.cipherwell.vaultzero`.
4. App content forms: privacy policy URL (`https://cipherwell.github.io/Vault-Zero/privacy.html`),
   data safety (the honest answer: *no data collected, no data shared, no encryption in transit
   because no data leaves the device*, `INTERNET` permission absent), content rating (Everyone),
   target audience 13+/18+, ads: no.

## Build the upload artifact

Play needs an **AAB**, not an APK. Build it on CI so no local build paths are embedded:

```bash
gh workflow run release-apk.yml --repo cipherwell/Vault-Zero --ref main
# wait for the run, then download the "VaultZero-v<version>.aab" artifact
```

or, on a clean checkout:

```bash
source ~/.cipherwell-release/keystore.env
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:bundleRelease
# app/build/outputs/bundle/release/app-release.aab
```

## Publish the internal track

1. Create the app in Play Console → **Testing → Internal testing → Create new release**.
2. Upload the AAB, add release notes (`releases/v<version>.md` has the human summary).
3. Add testers: **Testers → email list** (up to 100 for internal testing; Gmail addresses).
4. Roll out, copy the **opt-in URL**, share that single link with testers.
5. Testers install via Play, and every later release updates automatically.

## Every future beta

1. Bump `versionCode` (must strictly increase), tag, CI builds the AAB.
2. Internal testing → Create new release → upload → roll out.
3. Play verifies the upload key signature; testers get it within minutes.

## Known Play constraints for this app

- `FLAG_SECURE` is fine.
- No `INTERNET` permission is fine, but the store listing still needs a privacy policy.
- Data safety form must state no collection; the app has no analytics and no crash SDK.
- Play may ask for a **foreground service / permissions declaration** — not applicable.
- First review of a brand-new account can take several days; start it before announcing the beta.
