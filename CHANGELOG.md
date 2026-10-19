# Changelog

What changed in each TrueCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/TrueCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.8.0 (2026-10-19)

System: apps, jobs, updates, network, power and the TrueNAS shell.

### New

- **System tab** replaces the placeholder. It shows the server (hostname, TrueNAS version, uptime, CPU, cores and memory) and links to everything below, each with a one-line status
- **Apps**: every installed app with its state, version, available upgrade and image updates. Crashed apps show in red
  - Tap an app to **open its web UI**, **start**, **stop**, **redeploy** (pull newer images and restart) or **upgrade** it. Stopping, redeploying and upgrading ask first; a spinner shows while TrueNAS works on it
  - **Live logs** for each container of an app, following new lines as they arrive (last 500 lines to start with; the text can be selected and copied). Stop following to scroll back
- **Jobs**: what TrueNAS is doing and has done recently, with live progress bars and the error when a job failed. Running jobs that allow it can be **stopped**
- **Updates**: the installed version, whether a newer one is available for your update profile, release notes, and download progress
  - **Install update** (with the System Update Write role): type the server's hostname to confirm, and choose whether TrueNAS reboots automatically once it's installed. TrueCtrl uses `update.run` on 25.10 and newer and `update.update` on 25.04
- **Network**: each interface with link state, type, speed, MTU, addresses (and whether they come from DHCP) and live download and upload rates
- **Shell**: the TrueNAS command line, full screen, with an extra-keys bar (Esc, Tab, Ctrl, Alt, arrows, Home/End, PgUp/PgDn, Del, paste), bigger and smaller text, and rotation. Only shown when your TrueNAS user has the web shell privilege and read-only mode is off
- **Power**: **reboot** and **shut down** (Full Admin only). Both ask you to type the hostname; the shutdown dialog says how to start the server again
- **Proxmox VM**: if your TrueNAS runs as a Proxmox VM, enter its VM ID under System › Power. TrueCtrl then shows **Open in ProxCtrl** (when ProxCtrl is installed), which opens that VM in ProxCtrl, for example to start it again after a shutdown

### Security

- The shell signs in with a one-minute token instead of your API key or password, goes through the same certificate pin as the rest of the app, and blocks screenshots and screen recording while it's open. Its page only loads files bundled with the app and can't reach the network itself
- Switching on read-only mode closes an open shell
- TrueCtrl asks Android only whether ProxCtrl is installed; it can't see your other apps

## v0.7.0 (2026-10-19)

Sharing: your SMB and NFS shares and the services behind them.

### New

- **Sharing tab** replaces the placeholder, with **Shares** and **Services**
- **SMB shares** with name, path, comment, special purpose (Time Machine, multiprotocol, time-locked and others), read-only and locked state. **NFS shares** with path, comment and the networks and hosts allowed
- **Turn a share on or off** with its switch (needs the SMB or NFS sharing write role). Turning a share off asks first; its settings and data stay as they are
- A note under the shares when the SMB or NFS service isn't running, so you know why clients can't connect
- **SMB connections** on TrueNAS 25.04: who is connected and from which machine (TrueNAS 25.10 no longer offers this to apps)
- **Services**: every TrueNAS service with whether it's running and whether it starts on boot. Services set to start on boot that aren't running are shown in yellow
  - The switch sets **start on boot**
  - Tap a service to **start**, **stop** or **restart** it. Stopping and restarting ask first and say what it does to connected clients. TrueCtrl uses the right method for your TrueNAS version and shows TrueNAS's error when a service doesn't come up

### Changed

- Tapping a stopped service on the Overview's attention list opens the Sharing tab, where you can start it

## v0.6.0 (2026-10-19)

Protection and datasets: see whether your data is protected, run backup tasks, manage snapshots and work with datasets from the app.

### New

- **Protection tab** replaces the placeholder, with a group for each kind of task:
  - **Periodic snapshots**: dataset, schedule in words ("Every day at 00:00"), how long snapshots are kept, the last run and the next run. A switch turns a task on or off, and tapping a task takes its snapshot now
  - **Replication**: source and target, the last run (with progress while it runs) and TrueNAS's error when it failed. Tap to run it now
  - **Cloud sync** and **Rsync**: path and remote, last run, next run. Tap to run now; tap a running cloud sync to stop it
  - **Scrub schedules** per pool, with their threshold and a switch to turn them on or off
  - Failed tasks show in red with an error icon; every run is confirmed before it starts
- **Last success age**: TrueNAS only keeps a task's latest run, so TrueCtrl remembers when each replication, cloud sync and rsync task last succeeded and shows it when the latest run failed or the success is getting old
- **Stale backup warning**: a backup task whose last success is older than your limit turns yellow on the Protection tab and shows on the Overview's attention list. Set the limit (1 to 30 days, 7 by default) under Settings › Warnings
- **Snapshots** (from the Protection tab or a dataset), newest first, with when each was taken and the space it uses:
  - **Take snapshot** with a name of your choice (a dated "manual-…" name is filled in), optionally including child datasets
  - **Delete** one, or touch and hold to select several and delete them in one go. Deleting five or more asks you to type the dataset's name. Held snapshots are marked and can't be selected
  - **Roll back**: always asks you to type the dataset's name, and warns how many newer snapshots will be destroyed with it
  - **Clone** into a new dataset in the same pool
  - **Copy name**
- **Datasets** in the Storage tab, next to Pools and Disks: every dataset and zvol as a tree per pool, with space used, free space, quota and lock state
- **Dataset page** with type, used and available space, quota, reservation, compression and ratio, record size, mount point, encryption and comments, plus its child datasets:
  - **Edit quota and comments** (quota in GiB; 0 or empty for none)
  - **Add dataset** inside it with the TrueNAS presets: Generic, SMB, NFS, Multiprotocol or Apps
  - **Lock** an encrypted dataset (type its path to confirm)
  - **Unlock** a passphrase-encrypted dataset
  - **Delete** a dataset after a preview of what goes with it: child datasets, the number of snapshots, the shares, tasks and apps using it, and processes with files open on it. You type the full path to confirm

### Security

- The unlock passphrase is typed in a dialog that's blocked from screenshots and screen recording, is never saved (not even across a screen rotation), and is wiped from memory once it's sent to TrueNAS
- Every action checks your TrueNAS roles and is hidden when your user doesn't have them or the server is in read-only mode. On TrueNAS 25.04, rolling back and cloning snapshots need a Full Admin user, as TrueNAS requires
- Snapshot, task and dataset methods are picked for your TrueNAS version (`zfs.snapshot` on 25.04, `pool.snapshot` from 25.10)

## v0.5.0 (2026-10-19)

Notifications: TrueCtrl can now tell you when something on your TrueNAS needs attention, without Google services.

### New

- **Notifications page** under Settings › Notifications
- **Background checks** every 15, 30, 60 or 120 minutes (only with a network connection). Each check opens its own short, read-only connection to every saved server and notifies about:
  - New TrueNAS alerts at the level you choose (only errors, warnings and worse, or everything)
  - Disk health: S.M.A.R.T. errors, failed self-tests and disks running too hot
  - A pool that isn't healthy, and a pool passing your usage warning level
  - Scrubs that found errors
  - Failed replication, periodic snapshot, cloud sync and rsync tasks, once per failed run, with TrueNAS's error
  - Apps that crashed
  - A new TrueNAS version, once per version
  - A server that can't be reached in two checks in a row, or whose sign-in stopped working
- Conditions like an unhealthy pool are notified when they start and the notification is removed when they're over. A TrueNAS alert that's cleared or dismissed takes its notification with it
- The first check of a server only remembers what's already there, so adding a server doesn't flood your phone with old alerts
- **Check now** runs a check straight away, and each server shows when it was last checked and whether that worked
- **Push from TrueNAS** through UnifiedPush, for example your own ntfy: every TrueNAS alert arrives within seconds
  - TrueCtrl registers with your push app and shows the push address (tap to copy; it's marked as sensitive so it doesn't show in clipboard previews)
  - **Set up on TrueNAS**: with the Alert Write role, TrueCtrl adds a "TrueCtrl" alert service on your TrueNAS for you (type Slack, pointed at the push address, at your chosen alert level), or updates it if it's already there. **Send a test alert** checks the whole path
  - Without that role, the page shows the steps to add it in the TrueNAS web UI yourself
  - While push is on, the background check doesn't notify about TrueNAS alerts a second time
- **Per-event switches** for each kind of notification
- **Notification channels** for critical problems, warnings, updates and info, and alerts pushed by TrueNAS, so you can pick the sound and importance of each in Android's settings
- **Battery** section that says whether battery optimisation can delay checks, with a button to TrueCtrl's app info and how to allow background activity on ColorOS

### Security

- Notifications on a locked phone only say "New alert from your TrueNAS"; pool, disk, app and server names show once you unlock
- Background checks never change anything on TrueNAS, whatever read-only mode is set to

### Changed

- **Google-free**: the built-in WebView (used by the web shell that's coming) doesn't send page addresses to Google Safe Browsing or usage data to Google

## v0.4.1 (2026-10-19)

TrueCtrl is now completely Google-free. Nothing changes in how you use it.

### Privacy

- **Emoji no longer make TrueCtrl ask Google for a font.** A standard Android library inside the app asked Google Play services for a downloadable emoji font every time TrueCtrl started, which could make Google Play services fetch that font from Google. This lookup is now switched off: emoji are drawn with your phone's own emoji font. On phones without Google Play services nothing changes; on other phones, emoji can look slightly different if the phone's built-in emoji font is older.
- **Checked: TrueCtrl is Google-free.** It contains no Google Play services, Firebase, ML Kit, Google Cast or Play Core, and it doesn't load fonts or anything else from Google's servers. It also never switches server addresses by itself: it always uses the address you set up, and only you can change it.

### Changed

- **README: new Google-free line** under Requirements: works without Google Play services, contains no Google libraries that contact Google, and draws emoji with your phone's own emoji font.
- **README brought up to date with 0.4.0.** Datasets and zvols, the Protection, Sharing and System tabs, notifications and the home screen widgets and Quick Settings tile aren't in the app yet, so they moved from the feature list to a new **Planned** section, and the ntfy push setup steps were taken out until notifications arrive. The feature list now describes what's there: the Overview, live alerts, pools and scrubs, disks with temperatures, graphs and SMART health, several servers, the offline view, and the blank recent-apps preview and encrypted API keys and passwords. The roles table now lists only the roles the app uses today (`READONLY_ADMIN`, `POOL_WRITE`, `ALERT_LIST_WRITE`, and Full Admin for SMART self-tests on 25.04), with the others in a separate table for planned features. There's a new **Getting started** section, the CtrlF-Droid badge and link in Install, and a line saying TrueCtrl only connects to your own TrueNAS besides the opt-in stats. No change to the app itself

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
