# Repository map

```
vault-zero-app/
├── app/                                  Android application module
│   ├── build.gradle.kts                  app config, signing, R8, dependencies
│   ├── proguard-rules.pro                keeps crypto/data/model; obfuscates UI
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       permissions, launcher activity, no network
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt       single Activity, Compose host, FLAG_SECURE, SAF,
│       │   │   │                          biometric prompts, background lock, shortcuts
│       │   │   ├── core/                  pure helpers (search, audit filters, grouping,
│       │   │   │                          generator history, theme mode)
│       │   │   ├── crypto/                VaultCrypto, SecurityAuditEngine, BiometricKeyManager
│       │   │   ├── data/                  Room DB/DAO/entity, repository, prefs, backup report
│       │   │   ├── model/                 VaultItem, payloads, audit models
│       │   │   └── ui/                    ViewModel, screens, components, theme, backup IO
│       │   └── res/                       strings, themes, colors, adaptive icon, shortcuts.xml
│       ├── test/                          119 JVM/Robolectric tests
│       └── androidTest/                   12 device tests (biometrics, migration, offline, setup)
├── docs/                                  Documentation — start at docs/README.md
│   ├── README.md                          documentation index/reading paths
│   ├── GETTING-STARTED.md, FEATURES.md    user guide
│   ├── CRYPTO-SPEC.md, THREAT-MODEL.md, BACKUP-FORMAT.md, ARCHITECTURE.md
│   ├── BUILDING.md, TESTING.md, RELEASING.md, REPO-MAP.md
│   ├── DESIGN.md, SCREENSHOTS.md, FAQ.md, TROUBLESHOOTING.md
│   ├── PRIVACY.md, privacy.html, index.html
│   ├── PENTEST-2026-11-15.md
│   └── screenshots/                       current One UI captures + archive/
├── releases/                              release notes + checksums (no binaries in git)
├── store/                                 Play listing pack, data-safety answers, graphics
├── fastlane/metadata/android/en-US/       listing text, changelog, icon, screenshots
├── fdroid/                                F-Droid build recipe (+ fdroid/README.md)
├── .github/
│   ├── workflows/release-apk.yml          signed release workflow (gated by secrets/variable)
│   ├── ISSUE_TEMPLATE/                    bug/feature templates + config
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── dependabot.yml
├── gradle/                                version catalog, wrapper
├── README.md, SECURITY.md, CHANGELOG.md, CONTRIBUTING.md, CODE_OF_CONDUCT.md, SUPPORT.md
└── LICENSE (MIT)
```

## Where to change what

| Task | Files |
| --- | --- |
| Crypto/KDF/AAD/backup envelope | `crypto/VaultCrypto.kt`, `data/VaultRepository.kt`, docs/CRYPTO-SPEC, docs/BACKUP-FORMAT |
| Add a record type | `model/VaultModels.kt`, `ui/screens/ItemEditorScreen.kt`, `ui/components`, `VaultType`; never rename existing types (AAD) |
| UI/theme | `ui/screens`, `ui/components`, `ui/theme`, docs/DESIGN |
| Search/filter | `core/SearchQuery.kt`, `core/AuditFilter.kt`, `VaultViewModel.filteredItems` |
| Backups | `VaultRepository.export/import`, `ui/backup/BackupFileIo.kt`, docs/BACKUP-FORMAT |
| Biometrics | `crypto/BiometricKeyManager.kt`, `MainActivity` prompt routing, `VaultViewModel` |
| Database schema | `data/VaultItemEntity.kt`, `VaultDatabase.kt` (migration), docs/ARCHITECTURE |
| Release/signing | `app/build.gradle.kts`, `.github/workflows`, docs/RELEASING |
| Store assets | `store/`, `fastlane/`, docs/DESIGN |
