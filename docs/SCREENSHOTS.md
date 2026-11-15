# Screenshots

Current screenshots of release `1.1.0` (Android 14 emulator, dark/light themes). They are used in
the README and Play/F-Droid listings. Files live in `screenshots/`; legacy sets are under
`screenshots/archive/`.

## Vault

| Dark | Light |
| :---: | :---: |
| <img src="screenshots/oneui-vault-dark.png" width="300"> | <img src="screenshots/oneui-vault-light.png" width="300"> |

## 2FA dashboard / grouped list

| 2FA codes | Grouped + collapsed title |
| :---: | :---: |
| <img src="screenshots/oneui-totp-dashboard.png" width="300"> | <img src="screenshots/oneui-collapsed-title.png" width="300"> |

## Generator / Audit

| Generator | Audit |
| :---: | :---: |
| <img src="screenshots/oneui-generator-dark.png" width="300"> | <img src="screenshots/oneui-audit-dark.png" width="300"> |

## Settings

| Security controls | Recovery & backup |
| :---: | :---: |
| <img src="screenshots/oneui-settings-security.png" width="300"> | <img src="screenshots/oneui-settings-recovery.png" width="300"> |

## Detail / editor / dialogs

| Detail sheet | Unsaved-changes guard |
| :---: | :---: |
| <img src="screenshots/oneui-detail-sheet-dark.png" width="300"> | <img src="screenshots/oneui-editor-unsaved.png" width="300"> |
| **Change master password** | **Export protection** |
| <img src="screenshots/oneui-change-password.png" width="300"> | <img src="screenshots/oneui-export-protection.png" width="300"> |

## Other current captures

`screenshots/oneui-search-audit.png`, `oneui-editor-light.png`, `oneui-grouped-light.png`,
`oneui-lock-dark.png`, `oneui-settings-dark.png`, `oneui-settings-light.png`.

## Archive

`screenshots/archive/` contains historical captures (pre-One UI redesign, earlier feature demos)
kept for changelog context. They do not represent the current UI.

## Updating screenshots

Automation-friendly debug build:

```bash
./gradlew :app:assembleDebug -PallowScreenshots=true -PdevDisableAutoLock=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
# navigate, then:
adb exec-out screencap -p > docs/screenshots/oneui-<screen>.png
```

Never ship builds made with those flags. Production builds intentionally block capture.
