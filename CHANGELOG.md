# Changelog

What changed in each TrueCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/TrueCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.14.2 (2026-10-25)

Crash fixes when leaving a locked app, safer catalog images, and plain HTTP for your own TrueNAS with password sign-in. (These changes were prepared as 0.14.1 and 0.14.2; 0.14.1 was never released on its own.)

### Fixed

- **No crash when leaving a locked app**: with the app lock set to lock as soon as you leave, pressing Back on the first screen or swiping TrueCtrl away from recent apps could make it crash while it closed, because the lock screen was drawn on a window that was already going away. The lock screen is no longer drawn while the app is closing; TrueCtrl is still locked and opens on the lock screen next time.
- **No crash from a failed background task**: an unexpected error in work that runs outside a screen (saving a setting, handling a push from ntfy, keeping the connection up) now only stops that one task instead of closing the app.
- **Leaving a screen while it loads**: leaving an app catalog page, the install form or a share's page while it was still loading could show a "couldn't load" error for a moment, and leaving a share's page while its service was being started could close the screen you'd just gone to. Leaving now simply stops the loading.

### Changed

- Internal cleanup: removed unused texts left over from earlier versions (no visible change).
- **Plain HTTP for your own TrueNAS**: you can now type an address with `http://` in front (for example `http://192.168.1.9`) for a TrueNAS you only reach on your home network or over a VPN like Tailscale. Without `http://` or `https://`, TrueCtrl still always connects over HTTPS, and it never switches from HTTPS to HTTP on its own.
- **Warning before the first HTTP connection**: before anything is sent to an `http://` address, TrueCtrl asks **Use an unencrypted connection?** and explains that your password and everything TrueCtrl shows or changes travel unencrypted, which is fine at home or on a VPN but risky anywhere else. Tap **Use HTTP** to go on. The choice is remembered for that server; changing its address asks again.
- **Password sign-in only over HTTP**: TrueNAS automatically revokes an API key that's used over an unencrypted connection, so TrueCtrl never sends an API key to an `http://` address. With an `http://` address the setup screen says so and asks you to choose **Password** (two-factor codes work as usual) or use `https://`.
- **Not encrypted label**: servers on plain HTTP show **Not encrypted (HTTP)** in **Servers** and under the server on your profile page.

### Security

- **Safer catalog images**: icons and screenshots from the app catalog are now scaled down until both sides fit in 1280 pixels (before, a small file with huge dimensions could still use a lot of memory and close the app), images whose size can't be read are skipped, and a download that stalls gives up after 30 seconds.
- **Less shared with image hosts**: when you tap **Show icon and screenshots**, the catalog's image servers are no longer told that the request comes from TrueCtrl and which version (no TrueCtrl User-Agent). Requests to your TrueNAS are unchanged.
- **Fewer exported components**: two receivers that AndroidX libraries add for developer and app-store tooling (profile installer and WorkManager diagnostics) are removed, so only the launcher screen, the ntfy push receiver and the Quick Settings tile can be reached by other apps.
- **Saved API keys stay safe**: if a saved server that signs in with an API key ends up with an `http://` address, TrueCtrl refuses to connect and tells you why, without sending the key, so TrueNAS can't revoke it.
- **No redirects**: when an `http://` address answers with a redirect to HTTPS (TrueNAS's **Web Interface HTTP -> HTTPS Redirect** is on), TrueCtrl doesn't follow it and tells you to use `https://` instead. Redirects are still never followed.
- **HTTPS stays as strict as before**: certificate pinning for self-signed certificates, the certificate check before anything secret is sent, and https-only images from the app catalog are unchanged.

## v0.14.0 (2026-10-25)

### New

- **App catalog**: **System › App catalog** (and a **Browse the app catalog** row under your installed apps) lists every app in the trains your TrueNAS uses, most popular first. Search by name, description or tag, and filter by category. Installed apps are marked.
- **App pages**: tap an app for its description, app version and catalog version, train, categories, maintainers, the versions you can install, its home page and source links (open in your browser) and its read-me.
- **Icon and screenshots on request**: they're hosted on the internet by the catalog, not on your TrueNAS, so TrueCtrl only downloads them after you tap **Show icon and screenshots** on that app's page (https only, at most 6 MiB per image).
- **Install apps**: **Install** opens a form built from the app's own questions, the same ones the TrueNAS web UI shows, in their groups and filled in with TrueNAS's defaults for that version. It covers text, passwords (masked, with a show button), numbers with their limits, switches, choices, paths and host paths (with a dataset picker), ixVolume or host path storage, ports, nested settings and lists you can add to and remove from. Settings that only apply to another choice are hidden and not sent, like in the web UI. A field type TrueCtrl doesn't know is shown as text or JSON with its default, so new catalog apps never break the form.
- **Checks before installing**: the application name (lowercase letters, digits and hyphens, up to 40, not already used; TrueCtrl suggests a free one), required fields, number ranges, text length and allowed characters are checked as you type, and TrueCtrl warns when a port is already used by another app. Pick another version from **Version** if you need one.
- **Install as a job**: installing starts TrueNAS's install job and opens **Jobs**, where it shows with live progress (and can be stopped). Errors TrueNAS finds in the settings straight away are shown on the form instead.
- **Uninstall apps**: **Uninstall** in an installed app's sheet removes its containers and images. **Also delete its ix-volumes** (off by default) deletes the data TrueNAS created for the app too; with it on you type the app's name to confirm. Host paths are never touched.

### Changed

- **Apps with no apps**: the empty Apps screen offers **Browse the app catalog**.
- **Roles**: browsing the catalog needs `CATALOG_READ` (included in `READONLY_ADMIN` and `APPS_READ`); installing and uninstalling need `APPS_WRITE`. Read-only mode blocks installing and uninstalling, and screens hide what your sign-in can't do.

### Security

- **Install forms with passwords** (any field TrueNAS marks as private) are kept out of screenshots, screen recordings and the recent-apps view, whatever **Hide in screenshots** says, and the values you type are never saved on the phone.

## v0.13.0 (2026-10-25)

### New

- **Create SMB shares**: on the Sharing tab, **New SMB share** below the SMB shares. Pick a dataset (its mount point becomes the path, and its name the share name until you type your own) or type a path under /mnt/, then set the name, an optional description, the purpose (the purposes your TrueNAS offers, such as Default share, Multi-protocol, Time Machine or Private datasets), and whether the share is enabled, read-only, shown in the share list, uses access based share enumeration and allows guests. The name is checked the way TrueNAS checks it (up to 80 characters, no `\ / [ ] : | < > + = ; , * ? " %`, not global, printers or homes) before you can save.
- **Create NFS shares**: **New NFS share** below the NFS shares, with the path or dataset, description, enabled and read-only, the allowed networks (CIDR, like 192.168.1.0/24) and hosts or IP addresses (one per line, up to 42 of each; both empty allows every client), map root user and group or map all user and group (TrueCtrl stops you from setting both), and the security types (SYS, KRB5, KRB5I, KRB5P; none set uses TrueNAS's default).
- **Edit shares**: tap an SMB or NFS share to open all its settings and save changes. Settings TrueCtrl doesn't show (such as an SMB share's audit settings or its purpose options like a Time Machine quota) are kept as they are; changing an SMB share's purpose on TrueNAS 25.10 starts that purpose from its defaults.
- **Delete shares**: **Delete share** at the bottom of a share's page, after a confirmation. Only the share is removed: the dataset and its files stay. Connected SMB clients are disconnected, and NFS clients lose the export.
- **Service check and start**: a share's page shows whether the SMB or NFS service is running and starts on boot. When it isn't, there's a button to start it and set it to start on boot, and after creating or saving an enabled share TrueCtrl asks whether to do that, so a new share works straight away and after a restart.
- **Permission hint**: SMB shares explain that who may read and write is set by the dataset's permissions (ACL) in the TrueNAS web UI, not by the share.

### Changed

- **Shares open on tap**: tapping a share opens its page (read-only when your sign-in can't change shares or read-only mode is on); the switch on the row still turns it on and off.
- **TrueNAS 25.04 and 25.10**: TrueCtrl sends each version's own share fields (25.10 renamed read-only and access based share enumeration and moved guest access into the purpose options; guest access is only offered for the Legacy share purpose there). On 25.04 the purpose list includes **No presets**, and the page notes that other purposes set some options themselves.
- **Roles**: creating, editing and deleting shares need `SHARING_SMB_WRITE` or `SHARING_NFS_WRITE`; starting the service from a share's page needs `SERVICE_READ` and `SERVICE_WRITE`. Without them the actions are hidden. Read-only mode blocks them all.

### Removed

- An unused placeholder screen left over from early versions (no visible change).

## v0.12.0 (2026-10-25)

### New

- **Light mode that follows the device theme**: TrueCtrl now switches between dark and light by itself, following your phone's dark theme setting (and its schedule, if you use one). It changes straight away when the phone does, without restarting the app or losing what's on screen. There is no in-app theme choice and still no wallpaper colours: the phone's setting decides.
- **Signal Light**: an off-white background (#F5F5F1) with white cards edged by a thin warm-grey border, near-black text and grey secondary text. Hero cards, main buttons, the selected tab, switches that are on and the filled PIN cells keep the same violet fill (#B48CFF) with dark content as in dark mode.
- **Readable accent text in light mode**: violet text and icons on light backgrounds (the slash in TRUE/CTRL, healthy statuses like ONLINE, links and text buttons, check marks, the focused text field border, the fingerprint key on the lock screen, the first chart line) use a deeper violet (#6A3BDB), because the bright violet is too pale on white. In dark mode they stay #B48CFF.

### Changed

- **Status and navigation bars** match the background in both modes: dark icons on the light background, light icons in dark mode. They switch along with the theme.
- **Every screen works in both modes**: Overview (headline, CPU card, memory and storage cards, trend lines, attention list, pools), Storage, Protection, Sharing, System, every detail screen, You, Settings, Notifications, server setup and editing, dialogs, bottom sheets, banners and the app lock.
- **Light-mode details**: switches that are off are light grey with a grey knob; progress and usage bars run on a light grey track with a near-black fill (violet for the main figure); errors are a deeper red (#D93636) and warnings a darker amber so they read on white; the floating bottom bar is white at 92 % with a grey border; lock screen keys are white with a thin border and empty PIN cells have a grey outline.
- **Charts** draw their first line in the readable accent shade for the current mode and their second line in the text colour, so both lines stay visible on light and dark cards.
- **Widgets** (pools and alerts) now follow the phone's dark / light setting too: white with dark text in light mode, dark grey with light text in dark mode, with health colours to match. They switch by themselves when the phone does.
- **Notifications** show TrueCtrl's violet as their accent colour (the deeper shade in light mode).
- **The app's start-up background** matches the theme, so there's no dark flash when you open it in light mode.

### Not changed

- **The shell stays dark in both modes**, like a terminal: it's full screen with its own dark background and keyboard bar.

## v0.11.0 (2026-10-25)

TrueCtrl gets **Ctrl / Signal**, the new look shared by every VKNXWorks Ctrl app: near-black, high contrast, one violet accent. This is a visual update only: every screen, action, setting, notification and the app lock work exactly as before.

### Changed

- **Dark only, new colours**: a near-black background (#08080A) with dark grey cards (#141417) edged by a thin border, off-white text and a single violet accent (#B48CFF) for everything active or selected. Errors are a soft red, warnings stay amber. The status and navigation bars are the same colour as the app, with light icons, edge to edge.
- **New fonts, bundled in the app**: Space Grotesk for all text and headings, and JetBrains Mono for numbers, readouts, section labels and the wordmark. Both are under the SIL Open Font License 1.1 and their licences ship with the app (`assets/licenses/`). They replace Manrope, which has been removed. Nothing is downloaded at run time.
- **New top bar**: the main tabs show a **TRUE/CTRL** wordmark (the slash in violet) on the left and your profile picture on the right, then the tab's name as a large headline. The profile picture is now a rounded square with the server's initial and a small violet badge while TrueCtrl is connected. Tapping it still opens **You**, the only way into Settings.
- **Pushed screens** (pool, dataset, disk, snapshots, apps, jobs, updates, network, alerts, servers, settings, notifications) get a square back button and the title next to it, with no coloured bar behind them.
- **Overview redesigned** like the other Ctrl apps: the server's hostname as a big headline with its state (online or offline), TrueNAS version and uptime; a violet **CPU load** card with the current load as a big number and the last two minutes as bars; **Memory** and **Storage** cards with used / total and a bar (storage adds up all pools; tap it to open Storage); a card with the CPU, memory and network trend lines plus ARC and CPU temperature. Below that come **Needs attention**, then **Pools** as a plain list (used percentage, name, usage, health status in mono with a square dot, usage bar), and the alerts line.
- **New bottom bar**: a floating dark bar with rounded corners and a thin border. The selected tab is a violet block with its icon and name; the other tabs show their icon only (TalkBack still reads their names). On tablets and in landscape the side rail uses the same colours.
- **Switches**: a new rounded-square switch (violet when on, grey when off) everywhere: settings, notifications, read-only mode, snapshot and scrub tasks, shares and services. TalkBack still announces them as switches.
- **Cards and lists**: every grouped list sits in a dark card with 22dp corners and a thin border, rows are separated by hairlines, and section headings are small upper-case mono labels. Status dots are now small squares.
- **Buttons, fields, dialogs and sheets**: main buttons are violet with dark text; secondary buttons are dark with a border; text fields are dark with a border that turns violet while you type; dialogs and bottom sheets are dark with 26dp corners and a thin border; tab switches (Pools / Datasets / Disks, Shares / Services, API key / Password) use violet for the selected part. Progress bars are thinner and flat.
- **Icons**: outlined icons everywhere, at a consistent size.
- **App lock**: a violet rounded-square lock badge, "VKNXWORKS · CTRL" and **Locked** in large type, square PIN cells (violet when filled), and a keypad of large rounded keys in JetBrains Mono. When fingerprint unlock is on, the fingerprint key sits on the keypad (bottom left, outlined in violet) next to 0 and delete. The PIN, fingerprint, lockout and "Forgot PIN?" work exactly as before.
- **You page**: a close button and a "YOU" label at the top, a large violet avatar with the server's name, address, TrueNAS version and sign-in, then your servers, read-only mode, what your sign-in may do, Settings and About. **Support TrueCtrl** is now a violet button at the bottom (it still opens GitHub Sponsors in your browser), followed by "GOOGLE-FREE · TELEMETRY-FREE · ARM64" and the version.
- **Shell and app logs** use the same near-black background, and the terminal cursor is violet.
- **Charts**: the two chart series are now violet and off-white (always with a key, never told apart by colour alone).
- **Widgets** use the same dark colours as the app instead of following the wallpaper, and the Quick Settings tile is unchanged.

### Removed

- **Theme** and **Use wallpaper colours** are gone from **Settings > Appearance** (the whole Appearance group is gone): Ctrl / Signal is dark only. Your old choice is kept in storage but no longer changes anything; if you used Light or Follow system, TrueCtrl is now dark.
- The frosted-glass blur behind the old tab bar (and the haze library it needed) is gone; the new bar is a solid translucent dark surface.

## v0.10.10 (2026-10-22)

### Fixed

- **TrueCtrl now really asks to show notifications**: on Android 13 and later an app can only show notifications after you allow it, and TrueCtrl only asked when you turned **Check in the background** or **Push from TrueNAS** on. If that question was missed or answered with no, checks and push messages kept arriving but every alert (TrueNAS alerts, pools, disks and SMART, the server going offline, updates) was silently dropped. With background checks or push on, TrueCtrl now asks once by itself as soon as it's unlocked, and never again after that, so it doesn't nag on every start.
- **Allow notifications always does something**: once you'd said no twice, Android stops showing its question, and the **Allow notifications** button on the Notifications screen did nothing at all. It now opens TrueCtrl's notification settings in Android when the question can't be shown any more (and when notifications are turned off there), and the app lock doesn't lock you out when you come back.

### Changed

- **Notification switches show the truth**: while notifications are blocked, **Check in the background**, **Push from TrueNAS** and the event switches show as off, because nothing can reach you, and the red banner at the top explains why. The push address and the TrueNAS set-up and test buttons are hidden until notifications are allowed. Turning any of the switches on asks for the permission (or opens Android's settings when it can't ask any more). As soon as you allow notifications, the switches show your saved choices again; you don't have to set anything twice.
- **Settings shows when notifications are blocked**: the **Notifications** row in Settings reads "Notifications are blocked: tap to allow them" while background checks or push are on but TrueCtrl can't show notifications.

## v0.10.9 (2026-10-21)

### Fixed

- **Fingerprint unlock works again after you leave the app.** Once you'd left TrueCtrl with the back gesture or by swiping home, the lock screen no longer asked for your fingerprint when you came back, and tapping **Use fingerprint** did nothing either; only the PIN worked until the app was force-closed. The fingerprint prompt was asked for while the app was still coming back on screen, and the phone dropped it without answering, which left TrueCtrl waiting for an answer that never came. Now the prompt only opens once TrueCtrl is fully back on screen, a prompt that never answered can't block the next one, and the lock screen offers your fingerprint again every time you come back to it, not just the first time after it locked. The same applies to the one-time check with your phone's screen lock for people updating from the old app lock.

## v0.10.8 (2026-10-21)

### Security

- **Only web links from the server are opened**: the **Open** links of an app on the Apps screen and the **Release notes** link on the Updates screen open whatever address TrueNAS sends. TrueCtrl now only shows and opens them when they're ordinary `http://` or `https://` web addresses, so an address of another kind can't make the phone open some other app or file.
- **Your sign-ins are never saved without the app lock's seal**: if TrueCtrl had to save a server's API key or password while it was locked (with the app lock on and background checks off), it saved them encrypted with the Android Keystore only, because the PIN's key wasn't in memory. It now keeps what's stored instead and saves new sign-ins only while unlocked, like ProxCtrl.

## v0.10.7 (2026-10-21)

### Fixed

- **Clear message when a certificate can't be read**: when you add a server with a self-signed or changed certificate and the app can't fetch that certificate to show you, it now says either that it can't reach the server (check the address and network) or that it couldn't read the certificate the server presents. Before, it showed the phone's raw technical error text, or just the server's address.

## v0.10.6 (2026-10-21)

### Changed

- **The page behind your profile picture is now called You**, the same name as in every Ctrl app (before: "Account"). It still holds your servers, read-only mode, what your sign-in may do, **Settings** and **About**, and the profile picture at the top right of every tab is still the only way into Settings. Screen readers now announce the profile picture as "You" instead of "Account and settings".
- **README**: Getting started now says what's behind the profile picture, and the app lock and Support sections say to tap your profile picture.

## v0.10.5 (2026-10-21)

### Changed

- **Hide in screenshots** and **Hide in recent apps** are now two separate switches in **Settings > Privacy and security**, both off by default. **Hide in screenshots** makes screenshots and screen recordings show a blank screen (the recent-apps view too). **Hide in recent apps** only shows a blank card in the recent-apps view, so screenshots still work. They replace the single "Hide in screenshots and recent apps" switch from 0.10.4: if you had turned that on, both new switches are on after the update, so nothing changes for you.
- **The recent-apps preview is no longer always blank.** Before, TrueCtrl always showed a blank card in recent apps; now it only does with **Hide in recent apps** on, or while the app lock is on (a preview would otherwise show your server without the PIN). Turn on **Hide in recent apps** to keep the old behaviour.
- The lock and PIN screens, the shell and every screen where you type an API key, password, one-time password or passphrase are still always kept out of screenshots and recordings, whatever the switches say.

## v0.10.4 (2026-10-21)

Settings now follow the same layout as the other Ctrl apps, with a new option to keep TrueCtrl out of screenshots.

### New

- **Hide in screenshots and recent apps** (**Settings > Privacy and security**). When it's on, screenshots, screen recordings and casting show a blank screen instead of TrueCtrl. It's off by default and works with or without the app lock. The recent-apps preview is blank either way, as before.

### Changed

- **Settings are in the same order as in every Ctrl app:** Appearance first, then Notifications, then **Privacy and security** (renamed from Security: app lock, change app PIN, fingerprint unlock, lock again and the new screenshot setting), then Warnings.
- **"Dynamic colour" is now called "Use wallpaper colours"**, the same name as in the other Ctrl apps. It works as before.
- **The lock screen after updating from the old app lock** looks like the PIN screen and the other Ctrl apps: the same lock badge, and a full-width **Unlock** button.

### Security

- **The lock screen and every PIN screen are always kept out of screenshots and screen recordings**, so a recording can't show the PIN being typed.
- **Screens where you type secrets stay protected when you move between them.** Leaving one screen that blocks screenshots (sign-in, a dataset passphrase, the shell) while another is still open no longer lifts the protection early.

## v0.10.3 (2026-10-21)

### Security

- **Trips out of the app no longer skip the lock if you stay away.** Opening a link, ProxCtrl, ntfy or Android settings still don't count as leaving the app, but if you don't come back within 5 minutes (or your "Lock again" time, if that's longer), TrueCtrl locks anyway. Before, such a trip never locked, however long you were gone.

## v0.10.2 (2026-10-21)

The app lock now works the same way in every Ctrl app, using the strongest of the options compared.

### Changed

- **The app lock has its own PIN**, separate from your phone's screen lock. It needs at least 6 digits, and very obvious ones (like 111111 or 123456) are refused. Someone who knows or has seen your phone's PIN can't open TrueCtrl with it
- **Updating with the app lock already on**: the first time you open TrueCtrl, unlock once more with your fingerprint, face or screen lock as before, then choose your app PIN. This step can't be skipped. If your phone no longer has a screen lock, TrueCtrl goes straight to choosing the PIN
- **Optional fingerprint unlock** (Settings › Security › Unlock with fingerprint). It only accepts strong biometrics such as the fingerprint sensor, not face unlock that only uses the camera, which a photo can sometimes fool. The PIN is still needed after the phone restarts and at least every 72 hours, the same as Android does for your screen lock
- **Choose when the lock asks again** (Settings › Security › Lock again): as soon as you leave the app (the default, and how TrueCtrl always worked), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts
- **Opening a link, ProxCtrl, ntfy or Android's settings from TrueCtrl no longer counts as leaving the app**, so you don't have to unlock again when you come back from them. Going to the home screen or another app yourself still does
- **Change app PIN** is in Settings › Security and asks for your current PIN first. Turning the app lock off also asks for the PIN
- **The screen you were on stays as it was** behind the lock screen, so a half-filled form is still there after unlocking. Screen readers can't read it while locked

### Security

- **Your saved API keys and passwords are encrypted with your PIN while TrueCtrl is locked.** The key that decrypts them only exists in memory while the app is unlocked and is wiped the moment it locks, and the connection to TrueNAS is closed until you unlock. A locked app, or a copy of its files, can't use your sign-in
- **Exception, so notifications keep working**: background checks (and checks started by a push from TrueNAS) have to sign in while the app is locked. While either is turned on under Settings › Notifications, your API keys and passwords stay encrypted with the Android Keystore only, as before; Settings › Security says so. Turn both off and they are sealed with your PIN again the next time you unlock
- **The PIN can't be guessed off the phone.** It's never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a secret held in the phone's secure hardware (StrongBox where available), so copying TrueCtrl's files to a computer isn't enough to try PINs there
- **Wrong PINs lock the screen**: after 5 tries you wait 30 seconds, doubling with every further 5 wrong tries up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't reset the wait
- **Fingerprint unlock can't be faked**: the fingerprint has to unlock a key in the phone's secure hardware, so a tampered "success" on a rooted phone doesn't open the app. Adding a new fingerprint turns fingerprint unlock off until you turn it on again with your PIN
- **Forgot your PIN?** The lock screen offers to remove every server with its sign-in, and the cached data, check results and notifications on the phone. Because the PIN encrypts your sign-ins, there's no way around it. Nothing changes on your TrueNAS

## v0.10.1 (2026-10-20)

### Changed

- **64-bit ARM phones only.** TrueCtrl now ships only the 64-bit ARM (arm64-v8a) build that nearly every phone from the last several years uses, like every Ctrl app, so the download is smaller. Old 32-bit phones and x86 devices (emulators, some Chromebooks) are no longer supported.
- **README**: Requirements say 64-bit ARM phones only

## v0.10.0 (2026-10-20)

### Changed

- **Push notifications through ntfy only.** TrueNAS alerts are now always pushed through the [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) app; other push apps can no longer be picked, and the "Choose a push app" list is gone. If you already used ntfy, nothing changes for you: TrueCtrl keeps the same push address, so the TrueCtrl alert service on TrueNAS keeps working. If you used another push app, TrueCtrl cancels its registration there when you update and switches to ntfy if it's installed. Your push address then changes, so tap **Set up on TrueNAS** again under Settings › Notifications (or paste the new address into the alert service yourself). Until ntfy is installed, push is off. The background checks keep running either way, as before
- **"Install ntfy" button**: when push is on but ntfy isn't on your phone, the notification settings say so and offer an Install ntfy button that opens ntfy on F-Droid. The screen checks again when you come back to it, and TrueCtrl then gets its push address from ntfy straight away
- **"ntfy temporarily unavailable"** is shown above the push address when ntfy reports that your ntfy server can't be reached for now. The address stays the same and alerts arrive again once the server is back
- TrueCtrl now handles push messages itself instead of through a separate push library, which makes the app smaller. TrueNAS alerts look exactly as before

### Security

- TrueCtrl only takes push messages from the ntfy app: on Android 14 and newer, a push that Android reports as sent by any other app is ignored, on top of the secret token every push must carry

### Privacy

- **Google-free and de-Googled**: nothing Google-related is left in TrueCtrl. The push library and the parts it brought along are gone, including its built-in hooks for Google's push service (never used by TrueCtrl) and the crypto library it needed, and the last mentions of Google are removed from the app's texts and setup notes, such as "Neither uses Google services" on the Notifications screen. TrueCtrl still has no Google Play services, Firebase or other Google services, never contacts Google, and works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps)
- **README**: a "Google-free and de-Googled" statement, and the push setup steps now describe ntfy as the only push app, with the background checks as a fallback

## v0.9.2 (2026-10-20)

### Privacy

- **No more usage stats: TrueCtrl is now telemetry-free.** The optional anonymous usage stats are gone completely, along with the Usage stats setting in Settings and the one-time question after adding your first server. The app no longer contacts VKNXWorks' stats server at all, and the settings it used are deleted from your phone when you update. TrueCtrl now only talks to your own TrueNAS servers

### Changed

- The **Settings** row on your account page now reads "Appearance, notifications, warnings and app lock"
- **README**: a Screenshots section (screenshots are coming soon), a new Privacy section and a "Telemetry-free" badge, and a link preview image (`.github/assets/social-preview.png`) with TrueCtrl's icon, name and summary, for when the repo is shared on Reddit, Discord or elsewhere

## v0.9.1 (2026-10-19)

### Changed

- **One About section in every Ctrl app**: Support, Report a bug, Source code and Licence (GPL-3.0), with the version underneath, at the bottom of the Account page (tap the profile picture on any tab)
- New **Report a bug** row in About opens TrueCtrl's GitHub issues, where you can report a problem or suggest an idea
- The "Source code and issues" row is now just **Source code**, since bugs have their own row

## v0.9.0 (2026-10-19)

Widgets, a Quick Settings tile and an app lock. With this version every planned feature is in the app.

### New

- **TrueNAS pools widget**: the selected server, whether anything needs attention ("All good", "2 things need attention, 1 critical"), and up to four pools with their health and a usage bar that turns yellow and red at your pool warning level. It follows your wallpaper colours in light and dark mode
- **TrueNAS alerts widget**: the number of active TrueNAS alerts, yellow when there are any and red when one is critical
- **Quick Settings tile**: the selected server and whether anything needs attention; it's highlighted when something does. Tap it to open TrueCtrl
- Widgets and the tile update every time TrueCtrl reads the server and after every background check. When a server can't be reached they show when it was last seen
- **App lock** under Settings › Security: TrueCtrl asks for your fingerprint, face or the phone's screen lock when it opens and every time it comes back from the background. Notifications, widgets and the tile keep working while it's locked

### Changed

- The README now describes every feature, the TrueNAS roles each action needs, setting up push notifications with ntfy, and the widgets

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
