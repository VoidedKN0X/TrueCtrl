# TrueCtrl

**Monitor and manage your TrueNAS server from your phone.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/TrueCtrl?label=download)](https://github.com/VoidedKN0X/TrueCtrl/releases/latest) ![Android 15+](https://img.shields.io/badge/Android-15%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

TrueCtrl is an Android app for [TrueNAS](https://www.truenas.com/): a fast, touch-first alternative to the web UI for keeping an eye on storage health, protecting your data and handling day-to-day management.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/TrueCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Overview** of your server, with an attention list for alerts, unhealthy or full pools, failing disks, overdue scrubs, failed or stale backups, stopped services, crashed apps and pending updates, a system card, live CPU, memory and network graphs, and your pools
- **Alerts** that update live, with dismiss and restore
- **Storage**: pools with their health, space and layout, and scrubs you can start, pause, resume and stop with live progress
- **Datasets** as a tree: space, quota, compression, encryption and more. Create child datasets with the TrueNAS presets, edit quotas and comments, lock and unlock encrypted datasets, and delete with a preview of everything that goes with it
- **Disks** with temperatures, temperature and speed graphs, and SMART health (self-tests and attributes on TrueNAS 25.04)
- **Protection**: periodic snapshot, replication, cloud sync, rsync and scrub tasks with their schedule, last and next run, and the age of the last good backup. Run tasks now, stop a cloud sync, turn snapshot and scrub tasks on and off
- **Snapshots**: browse per dataset, take, delete one or many, roll back and clone
- **Sharing**: create, edit and delete SMB and NFS shares (pick the dataset, name, purpose, description, read-only, show in the share list, guest access, allowed networks and hosts, map root / map all, security), turn them on and off, see who's connected over SMB, and the services behind them (start, stop, restart, start on boot). TrueCtrl shows when the SMB or NFS service isn't running and offers to start it
- **System**: apps (start, stop, redeploy, upgrade, uninstall with or without their ix-volumes, open their web UI, live container logs with search and copy), **Update all apps** (pick the apps with an update, or take them all, and follow each upgrade job's progress, one app after another), the **app catalog** (search, categories, app pages with version, train, links and optional icon and screenshots, and installing with a form built from the app's own questions: text, numbers, switches, choices, ixVolume or host path storage, ports, lists and more, with anything unusual editable as JSON; the install runs as a job you follow in Jobs), jobs with live progress, TrueNAS updates, network interfaces with live traffic, the TrueNAS shell, and reboot and shutdown
- **Notifications**: background checks every 15 to 120 minutes, and TrueNAS alerts pushed within seconds through **[ntfy](https://f-droid.org/packages/io.heckel.ntfy/)** and your own ntfy server (TrueCtrl can set it up on TrueNAS for you), with the background checks as a fallback
- **Disk health warnings** (off until you turn them on): a separate background check, every 15 minutes to once a day, of pool status, the state and read/write/checksum errors of every disk in your pools, SMART self-test results (TrueNAS 25.04) and TrueNAS's disk alerts (SMART, disk temperature, pool status). Each new problem is notified once, and can also be posted to a topic on your own ntfy server for your other devices
- **Widgets and a Quick Settings tile** with pool health and usage, the number of active alerts, and whether anything needs attention
- **ProxCtrl link**: if TrueNAS runs as a Proxmox VM, open that VM in [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) straight from TrueCtrl, for example to start it again after a shutdown
- **Several servers**, signed in with an API key or a password (two-factor codes work too), with a list of what each sign-in is allowed to do
- **Works offline**: the last known state stays on screen, marked as out of date, when TrueNAS can't be reached
- **Safe by design**: read-only mode that blocks every change at the connection level, confirmations for anything disruptive (typing the dataset path or hostname for the most dangerous ones), certificate pinning, an optional [app lock](#app-lock) with its own PIN and fingerprint unlock that keeps your saved sign-ins encrypted while locked, **Hide in screenshots** and **Hide in recent apps** switches, screenshots always blocked wherever you type keys, passwords, passphrases or use the shell, API keys and passwords encrypted with the Android Keystore, and dataset passphrases that are never saved

## Requirements

- Android 15 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- TrueNAS 25.04 or newer (Community Edition). TrueCtrl only uses the JSON-RPC WebSocket API; the old REST API isn't used, so older versions aren't supported (see [supported versions](#supported-truenas-versions))
- HTTPS on your TrueNAS is recommended. A self-signed certificate is fine: TrueCtrl shows its fingerprint the first time and pins it. Plain HTTP works only for your own server, after a warning, and only with a password sign-in: TrueNAS revokes an API key that's used over an unencrypted connection, so TrueCtrl never sends one over HTTP
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).
- Optional: the [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) app, for instant push alerts

## Install

1. Open the [latest release](https://github.com/VoidedKN0X/TrueCtrl/releases/latest) on your phone, scroll down to **Assets** and tap the file that ends in `.apk` (for example `TrueCtrl-v0.14.5.apk`). The **Source code** (zip) and (tar.gz) files there are not the app.
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and login are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds TrueCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/TrueCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/TrueCtrl` in Obtainium by hand.

## Is it safe to install?

- **Open source:** all of TrueCtrl's code is in this repo, under the GPL-3.0 licence. You can read every line.
- **Built by GitHub, not on a personal computer:** every release on the [Releases page](https://github.com/VoidedKN0X/TrueCtrl/releases) is built by GitHub Actions from the code in this repo, using [the build workflow](.github/workflows/build.yml).
- **Always signed with the same key:** every release is signed with the same release key. Android refuses to install an update signed with a different key over the app you have, and the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key. Its SHA-256 certificate fingerprint is:

  ```
  C5:F8:E3:30:94:A8:C5:C0:CA:61:13:FB:65:7E:F2:6A:DD:09:5C:56:ED:24:BF:48:36:F7:98:B0:8F:8A:BF:90
  ```

  Advanced users can compare it with [AppVerifier](https://github.com/soupslurpr/AppVerifier).
- **Checksum:** each release's notes list the APK's SHA-256. Compare it with `sha256sum TrueCtrl-v*.apk`.
- **Built by this repo:** every release APK has a signed build provenance attestation. With the [GitHub CLI](https://cli.github.com), `gh attestation verify TrueCtrl-v0.14.8.apk -R VoidedKN0X/TrueCtrl` proves it was built by this repo's workflow from the tagged commit.

**Permissions** TrueCtrl asks for:

| Permission | What it's for |
| --- | --- |
| Internet (`INTERNET`) | Talking to your own TrueNAS servers (and, only when you tap **Show icon and screenshots**, a catalog app's images) |
| Network state (`ACCESS_NETWORK_STATE`) | Running background checks only when the phone is online |
| Notifications (`POST_NOTIFICATIONS`) | Showing alerts from background checks and push. Android asks you first, and you can say no |
| Biometrics (`USE_BIOMETRIC`) | Unlocking the optional app lock with your fingerprint |
| Hide other apps' floating windows (`HIDE_OVERLAY_WINDOWS`) | Hide other apps' floating windows over the PIN screen: while the lock, PIN, a password or key, or a confirmation shows, no chat head or overlay can sit on top of it. Granted at install, no prompt; overlays work as usual everywhere else |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Scheduling the background checks again after the phone restarts. Added by Android's scheduler for background work (WorkManager) |
| Keep awake (`WAKE_LOCK`) | Letting a background check finish while the screen is off. Added by WorkManager |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | An internal permission only TrueCtrl itself holds (added by AndroidX), so other apps can't send to its private receivers |

That's the full list. TrueCtrl doesn't ask for your location, contacts, files, camera or microphone, and doesn't run a foreground service. See [Privacy](#privacy) for what it connects to.

## Getting started

1. [Give TrueCtrl its own user and API key](#give-truectrl-its-own-user-and-api-key) on your TrueNAS.
2. Open TrueCtrl and enter your TrueNAS **Address**, like `192.168.1.9` or `https://nas.example.com`. Without `http://` or `https://`, TrueCtrl connects over HTTPS. Type `http://` in front only for a TrueNAS on your home network or VPN: TrueCtrl warns you first, and you'll have to sign in with a password.
3. Choose **API key** and enter the username and key, or choose **Password** to sign in with your TrueNAS login. Two-factor codes work too, but you'll need a new code each time TrueCtrl reconnects, so an API key is easier.
4. Tap **Connect**. If your TrueNAS uses the self-signed certificate it created on install, compare the fingerprint TrueCtrl shows with your server's, then tap **Trust**.
5. TrueCtrl shows what your sign-in is allowed to do. Tap **Continue**.

Your servers, read-only mode, **Settings** and **About** are on the **You** page: tap your profile picture at the top right of any tab. Every path below that starts with **Settings** is there.

## Give TrueCtrl its own user and API key

TrueCtrl works best with its own TrueNAS user and an API key that can only do what you need. API keys belong to a user and can never do more than that user.

**1. Create the user.** In the TrueNAS web UI, go to **Credentials → Users → Add**. Name it `truectrl`, give it no password login and no shell, and pick its access level:

- **Monitoring only:** add the user to the group `truenas_readonly_administrators`. That's the built-in **Read-only Administrator** privilege: TrueCtrl can see everything but change nothing.
- **Monitoring and day-to-day actions:** create your own privilege under **Credentials → Groups → Privileges → Add** with the roles below, attached to a group the `truectrl` user is in. Start from `READONLY_ADMIN` and add only the write roles for the actions you want.
- **Everything:** add the user to `builtin_administrators` (Full Admin). Only do this if you need it: rebooting and shutting down need it, and on TrueNAS 25.04 SMART self-tests, snapshot rollback and clone.

**2. Create the API key.** Go to **Credentials → Users**, open the `truectrl` user (or the API Keys page from your user menu), choose **Add API Key** for that user, and copy the key. TrueNAS shows it **once**. In TrueCtrl, enter the username `truectrl` and that key.

**What each role is for:**

| Role | Used for |
| --- | --- |
| `READONLY_ADMIN` | Everything TrueCtrl shows: the Overview, alerts, pools, datasets, disks, graphs, tasks, snapshots, shares, services, apps, jobs, updates and network |
| `POOL_WRITE` | Starting, pausing, resuming and stopping scrubs |
| `POOL_SCRUB_WRITE` | Turning scrub schedules on and off |
| `ALERT_LIST_WRITE` | Dismissing and restoring alerts |
| `ALERT_WRITE` | Setting up push notifications on TrueNAS for you (optional; you can add the alert service by hand) |
| `DATASET_WRITE` | Creating datasets, editing quotas and comments, locking and unlocking encrypted datasets |
| `DATASET_DELETE` | Deleting datasets (not included in `DATASET_WRITE`) |
| `SNAPSHOT_WRITE` | Taking snapshots. Rolling back also needs `POOL_WRITE`, cloning also needs `DATASET_WRITE` |
| `SNAPSHOT_DELETE` | Deleting snapshots (not included in `SNAPSHOT_WRITE`) |
| `SNAPSHOT_TASK_WRITE` | Turning periodic snapshot tasks on and off and running them |
| `REPLICATION_TASK_WRITE` | Running replication tasks |
| `CLOUD_SYNC_WRITE` | Running and stopping cloud sync tasks |
| `SHARING_SMB_WRITE`, `SHARING_NFS_WRITE` | Creating, editing, deleting and turning SMB and NFS shares on and off |
| `SERVICE_READ` + `SERVICE_WRITE` | Starting, stopping and restarting services and choosing whether they start on boot (`SERVICE_WRITE` doesn't include `SERVICE_READ`, so add both) |
| `APPS_WRITE` | Starting, stopping, redeploying, upgrading, installing and uninstalling apps |
| `SYSTEM_UPDATE_WRITE` | Installing TrueNAS updates |
| Web shell privilege | The shell (the **Web Shell Access** option on the privilege) |
| Full Admin only | Rebooting and shutting down. On TrueNAS 25.04 also SMART self-tests, snapshot rollback and clone |

Running rsync tasks needs whatever role your TrueNAS version asks for; TrueCtrl offers it when TrueNAS says your user may call it.

TrueCtrl checks what your key can do when it connects and greys out anything it isn't allowed to.

## Push notifications with ntfy

Push comes through the **ntfy** app only; other push apps can't be used. TrueCtrl's own background checks keep running as a fallback, with or without push.

TrueNAS has no ntfy alert type, but its **Slack** alert type posts a small JSON message to any address, which ntfy accepts.

1. Install [ntfy from F-Droid](https://f-droid.org/packages/io.heckel.ntfy/) on your phone and point it at your ntfy server. If ntfy isn't installed, TrueCtrl's notification settings show an **Install ntfy** button.
2. In TrueCtrl, open **Settings › Notifications › Push from TrueNAS**, turn on **Receive TrueNAS alerts instantly**, and wait for the push address from ntfy to appear.
3. Tap **Set up on** followed by your server's name. TrueCtrl adds a "TrueCtrl" alert service (type Slack, pointed at the push address, at your chosen alert level). This needs the `ALERT_WRITE` role; without it, the page shows the steps to add the service in the TrueNAS web UI yourself.
4. Tap **Send a test alert** to check the whole path.

## Disk health warnings

Turn them on in **Settings › Notifications › Disk health**. TrueCtrl then checks every saved server in the background (every 15 minutes to once a day, with a network connection), over its own short, read-only connection:

- **Pools** that aren't ONLINE or that TrueNAS marks unhealthy
- **Disks in a pool** that are FAULTED, REMOVED, UNAVAIL, OFFLINE or DEGRADED, or that have read, write or checksum errors (more errors later are a new warning)
- **SMART self-tests** whose latest result failed (TrueNAS 25.04; later versions report SMART through alerts)
- **TrueNAS's disk alerts**: SMART, disk temperature, pool and boot pool status

Each problem is notified once, including problems that are already there when you turn it on, and its notification goes away when it's fixed. While it's on, the general background check leaves pool status and SMART alerts to it, so you don't get them twice. It needs the same read access as the Overview (READONLY_ADMIN).

**Also post to ntfy** (off by default) posts each check's new warnings to a topic on your own ntfy server too, for your other devices: enter the server (like https://ntfy.example.com), the topic and, if the topic needs it, an access token, then tap **Save and send a test**. TrueCtrl uses ntfy's JSON publishing and contacts nothing else.
## Widgets and Quick Settings

- **TrueNAS pools** widget: the selected server, whether anything needs attention, and up to four pools with their health and usage.
- **TrueNAS alerts** widget: the number of active alerts, red when one is critical.
- **TrueNAS** Quick Settings tile: the selected server and whether anything needs attention. Tap it to open TrueCtrl.

They update whenever TrueCtrl reads the server and after every background check, so with background checks off they only change while the app is open.

## App lock

Turn it on under **Settings › Privacy and security › App lock** (tap your profile picture, then **Settings**). It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, such as the fingerprint sensor, checked by the phone's secure hardware. Face unlock that only uses the camera isn't accepted. The PIN is still needed after the phone restarts and at least every 72 hours, and adding a new fingerprint to the phone turns fingerprint unlock off until you turn it on again.
- **Your saved sign-ins are encrypted with the PIN while TrueCtrl is locked**, and the connection to TrueNAS is closed. The key only exists in memory while the app is unlocked and is wiped the moment it locks. The PIN itself is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware, so a copy of the app's files isn't enough to guess it on a computer.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Opening an app's web UI, ProxCtrl, ntfy, a link in your browser or Android's settings from TrueCtrl doesn't count as leaving, unless you stay away for more than 5 minutes.
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too.
- **Forgot your PIN?** It can't be recovered, because it encrypts your sign-ins. The lock screen offers to remove every server with its API key or password, and the cached data kept on this phone; nothing changes on your TrueNAS.
- **Background checks while locked:** with background checks, push or disk health on (Settings > Notifications), they keep running while TrueCtrl is locked, so your API keys and passwords (and the ntfy access token for disk warnings) are then only encrypted with the Android Keystore, as without the app lock. Settings says so under the app lock. Turn background checks, push and disk health off to keep them sealed with your PIN too. Background checks and notifications work the same either way.
- **Widgets and the Quick Settings tile** only say that TrueCtrl is locked while the app lock is locked (also once the *Lock again* time has run out while you were away), instead of showing your pools, alerts or server name. They show the last check again as soon as you unlock.

Two more switches in the same section work with or without the app lock, and are both off until you turn them on:

- **Hide in screenshots**: screenshots and screen recordings show a blank screen (and so does the recent-apps view).
- **Hide in recent apps**: the recent-apps view shows a blank card instead of TrueCtrl. Screenshots still work.

The lock and PIN screens, the shell and every screen where you type an API key, password, one-time password or passphrase are always kept out of screenshots and recordings, whatever these switches say. With the app lock on, the recent-apps view always shows a blank card.

## Supported TrueNAS versions

| Version | Status |
| --- | --- |
| 25.04 (Fangtooth) | Supported |
| 25.10 (Goldeye) | Supported |
| 26 and newer | Supported as they're released. The REST API is gone in 26, which doesn't affect TrueCtrl |
| 24.10 and older | Not supported |

Some method names changed between versions (snapshots, services, updates, SMART). TrueCtrl checks the version and the available methods when it connects, and hides anything your version doesn't offer. For example, TrueNAS 25.10 removed SMART test results and attributes from its API, so on 25.10 and newer TrueCtrl shows disk health from TrueNAS's SMART alerts and temperatures instead.

## Privacy

**Telemetry-free.** TrueCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to the servers you add (and, only when you ask for them, the images of a catalog app).

- **Servers:** your own TrueNAS servers, over HTTPS and a secure WebSocket by default; plain HTTP only for your own server after a warning (never with an API key). Push alerts come from TrueNAS through your own ntfy server and the ntfy app, the only push app TrueCtrl works with.
- **Your ntfy server, only if you turn on Disk health › Also post to ntfy** (off by default): TrueCtrl then posts new disk warnings (server, pool and disk names and what's wrong) to the server and topic you enter, and to nothing else. The optional access token is stored encrypted and only sent over HTTPS, redirects are never followed, and a self-signed certificate is pinned by its fingerprint after you confirm it. Turn the switch off to stop it.
- **Links:** the Support, Report a bug, Source code and Licence rows in About, an app's web UI and the links on a catalog app's page open in your browser only when you tap them; the app itself doesn't contact GitHub.
- **App catalog images:** a catalog app's icon and screenshots are hosted on the internet by the catalog, not on your TrueNAS. TrueCtrl only downloads them when you tap **Show icon and screenshots** on that app's page.
- **Notifications** on the lock screen hide server details when you hide sensitive notification content in Android's settings.
- **The shell** runs in a locked-down web view that only loads the terminal bundled with the app (xterm.js); it can't open web pages, and what you type goes only to your TrueNAS.
- **Install ntfy** (in notification settings, when ntfy is missing) opens ntfy's page on f-droid.org in your browser.
- **Google-free:** no Google Play services, Firebase or other Google code, and nothing is sent to Google. The web view's Safe Browsing lookups and usage metrics are switched off.
- **No backups:** Android doesn't back up TrueCtrl's data or move it to a new phone.

## How TrueCtrl is made

TrueCtrl is a one-person project by VoidedKN0X (VKNXWorks). Much of the code is written with the help of AI (Claude, by Anthropic). I decide what the app does, review the changes and use the app myself. Every change is listed in the [changelog](CHANGELOG.md), and you can read every line of code here.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/TrueCtrl.git
   cd TrueCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

**Dependency verification:** Gradle checks every library and plugin it downloads against the SHA-256 checksums in `gradle/verification-metadata.xml` and refuses anything that doesn't match. When you add or update a dependency or plugin, run the **Dependency verification metadata** workflow (Actions tab, *Run workflow*), copy the file from its log (between the BEGIN/END markers, gzipped and base64-encoded), check the diff and commit it in the same push.

## Support the project

TrueCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Starring the repo and [reporting bugs](https://github.com/VoidedKN0X/TrueCtrl/issues/new/choose) helps too: pick **Bug report** or **Feature request** and fill in the form.

Found a security problem? Please don't open a public issue; see [SECURITY.md](SECURITY.md).

In the app, the **About** section at the bottom of the **You** page (tap your profile picture) has **Support TrueCtrl** and **Report a bug** rows that open these pages.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CallCtrl](https://github.com/VoidedKN0X/CallCtrl) | Phone app: dialer, call history and in-call screen |
| [CameraCtrl](https://github.com/VoidedKN0X/CameraCtrl) | Private camera with RAW (DNG) on every lens |
| [ChatCtrl](https://github.com/VoidedKN0X/ChatCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [ClockCtrl](https://github.com/VoidedKN0X/ClockCtrl) | Alarms, timers, stopwatch and world clock |
| [ContactCtrl](https://github.com/VoidedKN0X/ContactCtrl) | Contacts: browse, edit, import and export |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [DateCtrl](https://github.com/VoidedKN0X/DateCtrl) | Calendar for the calendars on your phone and your accounts |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [GalleryCtrl](https://github.com/VoidedKN0X/GalleryCtrl) | Private, offline gallery with an encrypted vault |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [LijnCtrl](https://github.com/VoidedKN0X/LijnCtrl) | Live De Lijn bus and tram departures |
| [LumenCtrl](https://github.com/VoidedKN0X/LumenCtrl) | Private, offline photo and video editor |
| [MailCtrl](https://github.com/VoidedKN0X/MailCtrl) | Email for Gmail and Proton Mail |
| [MapsCtrl](https://github.com/VoidedKN0X/MapsCtrl) | Private, offline maps and navigation |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [RecorderCtrl](https://github.com/VoidedKN0X/RecorderCtrl) | Private, offline voice recorder |
| [RideCtrl](https://github.com/VoidedKN0X/RideCtrl) | Offline maintenance, fuel and mod log for your vehicles |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [ShutterCtrl](https://github.com/VoidedKN0X/ShutterCtrl) | Remote control and photo transfer for Canon cameras |
| [SoundCtrl](https://github.com/VoidedKN0X/SoundCtrl) | JBL headphones and speakers, without the account |
| [SumCtrl](https://github.com/VoidedKN0X/SumCtrl) | Offline calculator, unit converter and work tools |
| [SynapseCtrl](https://github.com/VoidedKN0X/SynapseCtrl) | Administer your Matrix Synapse server |
| [TalkCtrl](https://github.com/VoidedKN0X/TalkCtrl) | Cardo intercom settings, radio and updates |
| [TapoCtrl](https://github.com/VoidedKN0X/TapoCtrl) | Tapo plugs and bulbs on your home network |
| [TextCtrl](https://github.com/VoidedKN0X/TextCtrl) | Private SMS and MMS messaging |
| **TrueCtrl** (this app) | Monitor and manage your TrueNAS server |
| [WakeCtrl](https://github.com/VoidedKN0X/WakeCtrl) | Keep the screen awake, per app or while charging |
| [WrtCtrl](https://github.com/VoidedKN0X/WrtCtrl) | Manage your OpenWrt router |

## License

TrueCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

TrueCtrl is an independent app and isn't made by or affiliated with iXsystems or the TrueNAS project.

The [Space Grotesk](https://github.com/floriankarsten/space-grotesk) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) fonts are licensed under the SIL Open Font License 1.1; see `app/src/main/assets/licenses/`. The shell uses [xterm.js](https://xtermjs.org) (MIT licence, see `app/src/main/assets/xterm/LICENSE`).
