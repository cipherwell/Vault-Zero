## Summary

<!-- What changed and why? One or two paragraphs. -->

## Threat / security impact

<!-- Required for security-relevant changes. Does this touch crypto, keys, AAD, backups,
     permissions, exported components, logging or privacy? What is the failure mode? -->

- [ ] No security impact (UI/docs only)
- [ ] Security-relevant — described above and docs updated

## Verification

- [ ] `./gradlew :app:testDebugUnitTest` passes (new tests added where applicable)
- [ ] `./gradlew :app:lintDebug` passes
- [ ] Manual checks: <!-- list flows/screens -->
- [ ] Device/emulator tested: <!-- device, Android version, build type -->
- [ ] Screenshots attached for UI changes (from a non-production build; release blocks capture)

## Docs / release impact

- [ ] Docs updated (`docs/`, `README.md`, `SECURITY.md` if claims changed)
- [ ] No version bump/tag in this PR (maintainers handle releases)
- [ ] No keystore, APK/AAB binary, or secrets included

## Checklist

- [ ] I have read `CONTRIBUTING.md` and the security invariants.
- [ ] This change does not add network access, telemetry or plaintext storage.
- [ ] I understand the Code of Conduct.
