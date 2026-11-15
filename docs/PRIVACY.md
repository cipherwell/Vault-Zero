# VaultZero privacy policy

**Last updated: 15 November 2026**

VaultZero is an offline password manager and encrypted vault. It is designed so
that the developer cannot access, collect, or sell your data — the app has **no
network capability at all** and no accounts.

## What VaultZero stores on your device
- Encrypted vault records (AES-256-GCM), wrapped key material, KDF salts and
  public KDF parameters.
- Security settings you choose (appearance, timeouts, biometric opt-in).
- No analytics, advertising identifiers, crash telemetry, contacts, location,
  camera, microphone, or usage history.

## What leaves your device
Only what **you explicitly export**: an encrypted backup file written through
Android's system file picker to a location you choose. The file is encrypted and
contains no plaintext titles or secrets. The developer never receives it.

## Network
VaultZero declares no `INTERNET` or `ACCESS_NETWORK_STATE` permission and contains
no networking code. It cannot contact any server, including the developer's.

## Biometrics
When enabled, Android's biometric prompt unlocks a hardware-backed Keystore key
that wraps your vault key. VaultZero never receives fingerprint or face data;
matching happens inside the device's secure hardware. Biometric settings and
enrollment remain under Android's control.

## Data deletion
Uninstalling VaultZero deletes its local app data (vault, settings and Keystore
wrapping keys). Backups you exported remain wherever you saved them. There is no
server-side data to delete.

## Children
VaultZero is a general-purpose security tool and does not knowingly collect data
from anyone, including children.

## Changes
Any changes to this policy will be published in the project repository and the
app's release notes. Because the app has no network permission, policy updates
are delivered with app releases, not remotely.

## Contact
Cipherwell — `[ADD CONTACT EMAIL]`
Repository: https://github.com/cipherwell/Vault-Zero
