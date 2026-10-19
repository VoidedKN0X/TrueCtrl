# Changelog

What changed in each TrueCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/TrueCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.2.2 (2026-10-19)

Optional anonymous usage stats, so it's clear which parts of TrueCtrl people use.

### New

- **Optional anonymous usage stats.** After you add your first server, TrueCtrl asks once whether you'd like to share anonymous usage stats. Nothing is sent unless you tap **Share stats**, and you can switch it on or off any time in **Settings > Usage stats**
- **What's shared:** which tabs and screens you open (for example "Storage" or "Settings", never which pool, dataset, share or app), how a server was set up (API key or password, two-factor or not, pinned or trusted certificate, read-only mode on or off), your theme setting, the app version, Android version, phone model, language, and phone or tablet
- **What's never shared:** your server address, hostname or IP, username, API key or password, the names of pools, datasets, shares, apps or anything else on your TrueNAS, or any ID that follows you between sessions. Each session gets a new random ID, and stats from before and after you switch them off and on again aren't linked
- **Where it goes:** to VKNXWorks' own self-hosted server (Aptabase), not to Google or any analytics company. Stats that can't be sent are dropped, never saved for later. Builds you make from source can't send stats at all, and the setting doesn't appear in them

### Security

- The usage stats use their own connection with your phone's normal certificate checks. A self-signed TrueNAS certificate you trusted is only ever accepted from that TrueNAS, never for the stats server, and the stats connection never carries your sign-in

### Changed

- The README has a new **Usage stats** section listing exactly what's collected

## v0.2.1 (2026-10-19)

Clearer errors when adding a server that TrueCtrl can't use.

### Fixed

- **TrueNAS 24.10 and older** were reported as "answered, but not like a TrueNAS server". Those versions send unknown addresses to the web UI instead of answering, and TrueCtrl now recognises that. It says the server runs a TrueNAS that only has the old REST API and needs updating to 25.04 or newer

### Changed

- **"Not like a TrueNAS server" errors now show what the server actually answered**, such as the HTTP status, where it redirected to, or the start of the page it sent. That makes a wrong address, a wrong port or a reverse proxy in the way easy to spot

## v0.2.0 (2026-10-19)

TrueCtrl now connects to your TrueNAS: add a server, sign in, and see exactly what your user is allowed to do. The tabs themselves are filled in from the next version on.

### New

- **Server setup** on first start: enter the address (like `192.168.1.9`, or `https://nas.example.com` behind a reverse proxy), an optional name, and how to sign in. TrueCtrl checks the address before sending anything secret, and gives a clear reason when something's wrong: unreachable, not a TrueNAS server, an older TrueNAS without the API TrueCtrl uses, a version older than 25.04, a rejected or expired API key, a wrong password or code, or an account that isn't allowed to sign in
- **Sign in with an API key** (recommended) for a TrueNAS user, or **with a username and password**, including the 6-digit two-factor code from your authenticator app
- **Self-signed certificates**: the first time, TrueCtrl shows the certificate's SHA-256 fingerprint (with how to check it on TrueNAS) and asks you to trust it. From then on it only accepts that exact certificate from that server, and warns you clearly if it ever changes. There is no "trust all certificates" option
- **What this sign-in can do**: after connecting, a list shows each kind of action (datasets, snapshots, scrubs, replication, cloud sync, shares, services, apps, alerts, updates, reboot and shutdown, shell) as allowed or not, with the TrueNAS role it needs. It's also on the account page
- **Read-only mode** per server, which hides every action that changes something. It's turned on automatically for a user that can only look
- **Several servers**: add, switch between, edit and remove servers from the account page (tap the profile picture, then Servers). Removing one deletes its saved API key or password from the phone
- **Live connection**: TrueCtrl keeps one secure connection to TrueNAS open while the app is on screen and closes it when you leave the app. If the connection drops (Wi-Fi changes, TrueNAS reboots, the reverse proxy is down), it reconnects on its own, waiting a little longer after each failed try
- **Connection problems** show as a banner on every tab with the reason and the one action that fixes it: Retry, Sign in again, or Check again for a certificate
- **Version detection**: TrueCtrl reads your TrueNAS version and what your user can call when it connects, so later features can use the right commands for 25.04, 25.10 and 26 and hide what your version doesn't have
- **Pool usage warning** setting (Settings › Warnings), 80 % by default, for the attention list that comes with the Overview

### Security

- API keys and passwords are encrypted with a key kept in the Android Keystore and never leave the phone; nothing is written to logs
- A password is only saved when it can sign in again on its own. With two-factor sign-in nothing is saved, and you're asked to sign in again when the session ends (an API key avoids this)
- TrueNAS is only reached over HTTPS and secure WebSockets, which TrueNAS requires for API keys; plain `http://` addresses are refused
- Redirects are never followed, so a sign-in can't be sent to another server

## v0.1.0 (2026-10-19)

The first build of TrueCtrl: the app's shell, ready for the features that follow. It doesn't connect to a TrueNAS server yet; server setup comes in the next version.

### New

- **Five tabs** in a floating glass bar at the bottom: Overview, Storage, Protection, Sharing and System. Each one says what it will show once a server is connected. On tablets, unfolded foldables and in landscape the tabs move to a side rail
- **Account page** opened by tapping the profile picture at the top right of any tab (there is no separate "You" tab), with links to Settings, GitHub Sponsors, the source code and the licence
- **Settings** for the theme (follow system, dark, light or pure black) and dynamic colour from your wallpaper
- **App icon** in the Ctrl style: a disk platter on a keycap, with a themed version for Android's themed icons

### Security

- Plain HTTP is switched off for the whole app; TrueNAS is only reached over HTTPS and secure WebSockets, which TrueNAS requires for API keys
- Nothing the app stores is included in Android backups or moved to a new phone
