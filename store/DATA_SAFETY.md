# Play Data safety — exact answers

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Is all user data encrypted in transit? | N/A — app declares no network permission and contains no network code |
| Does your app provide a way for users to request data deletion? | Yes — uninstall deletes all local data; records can be deleted in-app. Developer has no server data |
| Data collected | None |
| Data shared | None |
| Security practices | Data encrypted at rest (AES-256-GCM); no network transmission; FLAG_SECURE |
| Ads / analytics / trackers | None |
| Account required | No |
