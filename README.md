# TrueCtrl

**Monitor and manage your TrueNAS server from your phone.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/TrueCtrl?label=download)](https://github.com/VoidedKN0X/TrueCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

TrueCtrl is an Android app for [TrueNAS](https://www.truenas.com/): a fast, touch-first alternative to the web UI for keeping an eye on storage health, protecting your data and handling day-to-day management.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/TrueCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

> TrueCtrl is in early development. Features arrive release by release; see the [changelog](CHANGELOG.md) for what's in each version.

## Features

- **Overview** of your server, with an attention list for alerts, unhealthy or full pools, failing disks, overdue scrubs, failed backups and more, a system card, live CPU, memory and network graphs, and your pools
- **Alerts** that update live, with dismiss and restore
- **Storage**: pools with their health, space and layout, and scrubs you can start, pause, resume and stop with live progress
- **Disks** with temperatures, temperature and speed graphs, and SMART health (self-tests and attributes on TrueNAS 25.04)
- **Several servers**, signed in with an API key or a password (two-factor codes work too), with a list of what each sign-in is allowed to do
- **Works offline**: the last Overview, pools and disks stay on screen, marked as out of date, when TrueNAS can't be reached
- **Safe by design**: read-only mode that blocks every change at the connection level, certificate pinning, screenshots blocked wherever you type keys, passwords or codes, a blank preview in recent apps, and API keys and passwords encrypted with the Android Keystore

## Planned

Not in the app yet: datasets and zvols (including locking and unlocking encrypted ones) in the Storage tab; the **Protection** tab (snapshots, periodic snapshot tasks, replication, cloud sync, rsync and scrub schedules, with the age of the last good backup); the **Sharing** tab (SMB and NFS shares and the services behind them); the **System** tab (apps, jobs with live progress, updates, network and power); notifications without Google services (background checks, or TrueNAS alerts pushed instantly through UnifiedPush and your own ntfy); and home screen widgets and a Quick Settings tile. The tabs are already there and say what they'll show.

## Requirements

- Android 13 or newer
- TrueNAS 25.04 or newer (Community Edition). TrueCtrl only uses the JSON-RPC WebSocket API; the old REST API isn't used, so older versions aren't supported (see [supported versions](#supported-truenas-versions))
- HTTPS on your TrueNAS (TrueNAS only accepts API keys over a secure connection). A self-signed certificate is fine: TrueCtrl shows its fingerprint the first time and pins it
- **Google-free:** works without Google Play services, contains no Google libraries that contact Google, and draws emoji with your phone's own emoji font

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/TrueCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and login are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds TrueCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/TrueCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/TrueCtrl` in Obtainium by hand.

## Getting started

1. [Give TrueCtrl its own user and API key](#give-truectrl-its-own-user-and-api-key) on your TrueNAS.
2. Open TrueCtrl and enter your TrueNAS **Address**, like `192.168.1.9` or `https://nas.example.com`. TrueCtrl only connects over HTTPS.
3. Choose **API key** and enter the username and key, or choose **Password** to sign in with your TrueNAS login. Two-factor codes work too, but you'll need a new code each time TrueCtrl reconnects, so an API key is easier.
4. Tap **Connect**. If your TrueNAS uses the self-signed certificate it created on install, compare the fingerprint TrueCtrl shows with your server's, then tap **Trust**.
5. TrueCtrl shows what your sign-in is allowed to do. Tap **Continue**.

## Give TrueCtrl its own user and API key

TrueCtrl works best with its own TrueNAS user and an API key that can only do what you need. API keys belong to a user and can never do more than that user.

**1. Create the user.** In the TrueNAS web UI, go to **Credentials → Users → Add**. Name it `truectrl`, give it no password login and no shell, and pick its access level:

- **Monitoring only:** add the user to the group `truenas_readonly_administrators`. That's the built-in **Read-only Administrator** privilege: TrueCtrl can see everything but change nothing.
- **Monitoring and day-to-day actions:** create your own privilege under **Credentials → Groups → Privileges → Add** with the roles below, attached to a group the `truectrl` user is in. Start from `READONLY_ADMIN` and add only the write roles for the actions you want.
- **Everything:** add the user to `builtin_administrators` (Full Admin). Only do this if you need it: today that's only for SMART self-tests on TrueNAS 25.04.

**2. Create the API key.** Go to **Credentials → Users**, open the `truectrl` user (or the API Keys page from your user menu), choose **Add API Key** for that user, and copy the key. TrueNAS shows it **once**. In TrueCtrl, enter the username `truectrl` and that key.

**What each role is for:**

| Role | Used for |
| --- | --- |
| `READONLY_ADMIN` | Everything TrueCtrl shows: the Overview, alerts, pools, scrubs, disks and graphs |
| `POOL_WRITE` | Starting, pausing, resuming and stopping scrubs |
| `ALERT_LIST_WRITE` | Dismissing and restoring alerts |
| Full Admin only | Running SMART self-tests on TrueNAS 25.04 |

**Roles for planned features.** These actions aren't in the app yet, so you don't need these roles until they arrive:

| Role | Will be used for |
| --- | --- |
| `DATASET_WRITE` | Creating datasets, editing quotas and comments, locking and unlocking encrypted datasets |
| `DATASET_DELETE` | Deleting datasets (not included in `DATASET_WRITE`) |
| `SNAPSHOT_WRITE` | Taking snapshots. Rolling back also needs `POOL_WRITE`, cloning also needs `DATASET_WRITE` |
| `SNAPSHOT_DELETE` | Deleting snapshots (not included in `SNAPSHOT_WRITE`) |
| `POOL_WRITE` | Scrub schedules |
| `SNAPSHOT_TASK_WRITE` | Enabling periodic snapshot tasks and running rsync tasks |
| `REPLICATION_TASK_WRITE` | Running replication tasks |
| `CLOUD_SYNC_WRITE` | Running and stopping cloud sync tasks |
| `SHARING_SMB_WRITE`, `SHARING_NFS_WRITE` | Turning SMB and NFS shares on and off |
| `SERVICE_READ` + `SERVICE_WRITE` | Starting, stopping and restarting services (`SERVICE_WRITE` doesn't include `SERVICE_READ`, so add both) |
| `APPS_WRITE` | Starting, stopping, restarting and upgrading apps |
| `SYSTEM_UPDATE_WRITE` | Installing TrueNAS updates |
| Full Admin only | Rebooting and shutting down. On TrueNAS 25.04 also snapshot rollback and clone, and running rsync tasks |

TrueCtrl checks what your key can do when it connects and greys out anything it isn't allowed to.

## Supported TrueNAS versions

| Version | Status |
| --- | --- |
| 25.04 (Fangtooth) | Supported |
| 25.10 (Goldeye) | Supported |
| 26 and newer | Supported as they're released. The REST API is gone in 26, which doesn't affect TrueCtrl |
| 24.10 and older | Not supported |

Some method names changed between versions (snapshots, services, updates, SMART). TrueCtrl checks the version and the available methods when it connects, and hides anything your version doesn't offer. For example, TrueNAS 25.10 removed SMART test results and attributes from its API, so on 25.10 and newer TrueCtrl shows disk health from TrueNAS's SMART alerts and temperatures instead.

## Usage stats

TrueCtrl can share anonymous usage stats, **only if you say yes**. It asks once after you've added your first server, and you can change it any time in **Settings > Usage stats**.

- **Shared:** which tabs and screens you open (the kind of screen, never which pool, dataset, share or app), how a server was set up when you add or edit one (API key or password, two-factor or not, pinned or trusted certificate, read-only mode on or off), your theme setting, the app version, Android version, phone model, language, and phone or tablet.
- **Never shared:** your server address, hostname or IP, username, API key or password, the names of pools, datasets, shares, apps or anything else on your TrueNAS, or any ID that stays the same between sessions.
- **Where:** VKNXWorks' own self-hosted [Aptabase](https://aptabase.com) server. Nothing goes to Google or analytics companies. Like any website, the server sees your IP address; Aptabase uses it only to work out the country and doesn't store it. The stats use their own connection with your phone's normal certificate checks; certificates you trusted for your TrueNAS are never used for it.

Builds from source don't include the stats key, so they can't send anything and the setting is hidden. The code is in [`Telemetry.kt`](app/src/main/java/com/vknxworks/truectrl/data/telemetry/Telemetry.kt).

Apart from these opt-in stats, TrueCtrl only talks to your own TrueNAS servers, over HTTPS and a secure WebSocket.

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

## Support the project

TrueCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Starring the repo and [reporting bugs](https://github.com/VoidedKN0X/TrueCtrl/issues) helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| **TrueCtrl** (this app) | Monitor and manage your TrueNAS server |

## License

TrueCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The Manrope font is licensed under the SIL Open Font License; see `FONT-LICENSE-Manrope.txt`.
