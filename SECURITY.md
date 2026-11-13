# Security policy

Thank you for helping keep TrueCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/TrueCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of TrueCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

- Your API keys, passwords, one-time passwords, dataset passphrases or other data leaking to anyone other than your own TrueNAS server
- TrueCtrl accepting a certificate you didn't trust, or sending an API key over an unencrypted connection
- Ways around the app lock or read-only mode, or protected screens (the shell, sign-in and passphrase screens) showing up in screenshots when they shouldn't
- Another app on the phone being able to make TrueCtrl do something you didn't ask for, such as change something on your TrueNAS
- Anything that lets someone run code or read files they shouldn't

Problems in TrueNAS itself should go to that project.

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
C5:F8:E3:30:94:A8:C5:C0:CA:61:13:FB:65:7E:F2:6A:DD:09:5C:56:ED:24:BF:48:36:F7:98:B0:8F:8A:BF:90
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.

Each release's notes list the APK's SHA-256, and each release APK has a signed build provenance attestation: `gh attestation verify <apk> -R VoidedKN0X/TrueCtrl` proves it was built by this repo's workflow from the tagged commit. Every library and plugin the build downloads is checked against the checksums in `gradle/verification-metadata.xml`.
