# Changelog

What changed in each TrueCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/TrueCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.4.0 (2026-10-19)

The Storage tab now shows your pools and disks, and you can start, pause and stop scrubs from your phone.

### New

- **Pools and Disks** in the Storage tab, switched with the buttons at the top. Datasets join this tab in a later version
- **Pool list** with each pool's health, a usage bar (yellow from your warning level, red when critical), used, total and free space, its layout (like "2 × MIRROR"), the number of disks, and a running scrub or resilver
- **Pool page** with:
  - Status and health, with TrueNAS's explanation when a pool isn't healthy
  - Space used, fragmentation and the compression ratio
  - A note when newer ZFS features are available for the pool (upgrading stays in the TrueNAS web UI, because it can't be undone)
  - The layout: data, metadata (special), dedup, log, cache and spare vdevs, each with its state and the disks inside it, with read, write and checksum errors per disk. Tap a disk to open it
- **Scrubs** on the pool page:
  - The last scrub: when it finished, how long it took and whether it found errors
  - When the next scheduled scrub runs, worked out from the pool's scrub schedule and its threshold (TrueNAS skips scheduled scrubs until the last one is old enough)
  - **Start**, **Pause**, **Resume** and **Stop** a scrub, each after a confirmation that explains what happens. Needs the Pool Write role and read-only mode off
  - Live progress while a scrub or resilver runs: percentage, a progress bar, when it started, roughly how long is left and any errors found
- **Disk list** with name, model, type (HDD or SSD, with rpm), size, which pool it's in, and its temperature (yellow from 50 °C, red from 60 °C)
- **Disk page** with:
  - Model, size and type, and the serial number, which is shortened until you tap Show
  - The pool it belongs to (tap to open it)
  - The temperature now, and the lowest, average and highest over the last 7 days
  - **Graphs** of temperature and read/write speed over the last hour, day, week or month. Touch and drag a graph to see the values at any moment
  - **S.M.A.R.T.**:
    - On TrueNAS 25.10 and newer, which no longer report S.M.A.R.T. details through their API, any S.M.A.R.T. alert TrueNAS raised for the disk is listed instead
    - On TrueNAS 25.04: self-test history with results, power-on hours, the disk's S.M.A.R.T. attributes, and running a short or long self-test (Full Admin only, as TrueNAS 25.04 requires)
- **Pull to refresh** on all storage screens; the lists refresh every 30 seconds while open
- **Works offline**: the last pools and disks are kept on the phone and shown, marked as stale, when TrueNAS can't be reached

### Security

- Starting, pausing and stopping scrubs and running S.M.A.R.T. tests are blocked in read-only mode, both in the app and before anything is sent to TrueNAS

## v0.3.0 (2026-10-19)

The Overview tab now shows your TrueNAS at a glance: what needs attention, live system graphs and your pools. Alerts get their own screen.

### New

- **Needs attention** list at the top of the Overview, most serious first, collapsed to five items with "Show all". It lists:
  - Active TrueNAS alerts by severity. Warnings and errors are shown one by one; extra info alerts are folded into one line. SMART failures, disk temperatures and quota warnings come in this way
  - Pools that aren't online or healthy
  - Pools above your usage warning (80 % by default, under Settings › Warnings); critical from 95 % or 10 points above your level
  - Scrubs that found errors, and scrubs that are overdue (no finished scrub within the schedule's threshold plus a week)
  - Failed replication, periodic snapshot, cloud sync and rsync tasks, with TrueNAS's error
  - Services set to start on boot that aren't running
  - Apps that crashed
  - A pending reboot, with the reasons TrueNAS gives
  - An available TrueNAS update
- Tapping an item opens where it can be dealt with: alerts open the Alerts screen, the rest open the matching tab
- **System card** with hostname, uptime, TrueNAS version, CPU model and thread count, RAM in use, ZFS ARC size and hit rate, and CPU temperature when TrueNAS reports it
- **Live graphs** for CPU, memory and network over the last 2 minutes, updated every 2 seconds while the Overview is on screen and stopped when you leave it
- **Pools** with a usage bar (yellow from your warning level, red when critical), used, total and free space, and a running scrub or resilver with its progress. Tap to go to Storage
- **Alerts screen** with every alert, active ones first. Tap one for the full text and its time. Dismiss it, or restore a dismissed one, when your user has the Alert List Write role and read-only mode is off
- **Alerts update live**: new, changed and cleared alerts appear without refreshing
- **Pull to refresh** on the Overview and Alerts, and the Overview refreshes every 30 seconds while it's open
- **Works offline**: the last Overview is kept on the phone, so it shows right away when you open the app and stays visible, marked as stale with the reason, when TrueNAS can't be reached

### Changed

- The Overview reads only what your user may see and what your TrueNAS version offers. On TrueNAS 25.04 the update check asks the update server, so it runs once per connection and then every 6 hours; on 25.10 and newer it uses TrueNAS's own background check

## v0.2.3 (2026-10-19)

A security and privacy check-up: read-only mode is now enforced for every request, sign-in screens are kept out of screenshots, and the connection to TrueNAS is locked to HTTPS even more strictly.

### Security

- **Read-only mode now blocks changes, not just hides them.** Until now read-only mode hid the buttons that change things. Now every request TrueCtrl sends to your TrueNAS also goes through one check first: while read-only mode is on, only requests that read something (lists, details, settings, status) are sent, and anything else is stopped on the phone with a "Read-only mode is on" message. It works the moment you flip the switch, without reconnecting, and if TrueCtrl isn't sure whether read-only mode is on, it assumes it is
- **No screenshots of sign-in screens.** The add-server and edit-server screens, where you type an API key, password or 6-digit two-factor code, can no longer be captured in screenshots or screen recordings, shown while casting your screen, or seen by other apps that record the screen. The two-factor code box is protected the same way
- **No previews in recent apps.** The recent-apps view now shows a blank card for TrueCtrl instead of a snapshot of your server, so pool, dataset and share names don't show when you switch apps
- **HTTPS only, with no way around it.** TrueNAS revokes an API key that's ever sent over plain HTTP, so TrueCtrl already only accepted https:// addresses. Now the connection itself also refuses anything that isn't HTTPS for the address check and a secure WebSocket (wss://) for signing in, even if a saved address were somehow changed. If that ever happens, TrueCtrl shows a clear message with a **Sign in again** button that opens the server so you can enter its address again, instead of trying to connect
- **Saved API key stays locked away while editing a server.** When you edit a server and leave the API key field empty to keep the current key, the saved key is no longer read out and held by the screen; it's only unlocked at the moment TrueCtrl signs in with it
- **Limits on what a server can send back.** The address check reads at most 64 KB of whatever answers (a reverse proxy page or a wrong address can't make TrueCtrl download a large file), and a connection that sends an absurdly large message (over 32 MB) is closed instead of being processed
- **Turning off usage stats drops anything waiting.** If you switch usage stats off, any stats not sent yet are thrown away right away and the session is forgotten. Nothing was ever sent after switching off before either, but now nothing is even kept

### Changed

- **All Ctrl apps in the README.** The README's Ctrl series table now lists all 14 apps. New in the table: [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) (Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app), [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) (scan and browse documents on Paperless-ngx) and [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) (Bambu Lab printers through Bambuddy, and Spoolman spools). Every app in the table is also in the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) for automatic updates.
- **README describes only what's in the app.** The README's "Safe by design" line no longer mentions typed confirmations and a fingerprint lock, which aren't in TrueCtrl yet. It now lists read-only mode, certificate pinning and the screenshot blocking above.

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
