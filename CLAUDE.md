# TrueCtrl

Android app for monitoring and managing a TrueNAS server (25.04 and newer). Part of the VKNXWorks Ctrl series: same look, structure and libraries as ProxCtrl, NaviCtrl and the other Ctrl apps (Kotlin, Compose, Material 3, manual `AppContainer` DI, OkHttp + kotlinx.serialization, haze glass tab bar). No Firebase or Google Play Services: it must build for F-Droid.

## Working on this repo

- Don't commit. Leave changes uncommitted for review. Push straight to `main` only when asked, with no branches or pull requests.
- Every push needs a detailed `CHANGELOG.md` entry.
- Every user-visible string goes in `app/src/main/res/values/strings.xml`.
- TrueNAS is reached only through the versioned JSON-RPC 2.0 WebSocket API (`wss://<host>/api/current` or a pinned version). Never use the REST API (`/api/v2.0`): it is deprecated since 25.04 and removed in 26.
- Check method names and argument shapes against the server's own docs (`https://<host>/api/docs/`) for that version; never guess. Method names differ between versions (for example `zfs.snapshot.*` on 25.04 vs `pool.snapshot.*` from 25.10, `service.start/stop/restart` vs `service.control` from 25.10, the scrub event `zfs.pool.scan` vs `pool.scan` on 26), so go through the compatibility layer and `core.get_methods`. Hide a feature when its method is missing.
- Parse API responses leniently (missing or new fields must never crash).
- Never log or store API keys, passwords, OTPs or dataset passphrases in plain text. Dataset passphrases are never persisted.
- Test write actions on a throwaway test dataset and its snapshots, never on datasets used by Jellyfin, Immich, Paperless, the *arr stack or other services.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "TrueCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.
- CI signs with the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` and `KEY_PASSWORD` secrets and the `KEY_ALIAS` variable (`true`). Never commit a keystore, its base64 or its passwords.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.
