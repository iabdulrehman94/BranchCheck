# Branch Check (MVP)

Android app for IT staff visiting branches: open a branch, and it immediately pings every device you have listed for it (router, switch, printer, server...) and shows what is online. Works on the internal network, no server or internet needed.

## What it does

- Branch list (100+ is fine): searchable, sorted naturally (Branch 2 before Branch 10), each row shows a green / red / grey status.
- Open a branch: all its devices are checked automatically, with latency in ms. "Check again" re-runs it.
- "Check all": checks every device in every branch in parallel (24 at a time). Tap again to stop.
- Two check types per device:
  - **Ping** (no port given): uses the phone's ping command.
  - **TCP port** (port given): tries to connect, e.g. 3389 for RDP, 9100 for a printer, 443 for a web page. Use this when a firewall blocks ping.
- Import all branches and devices from a CSV file (menu > Import CSV).
- Add / delete branches and devices by hand (long-press to delete).
- Share report: plain-text list of offline devices, ready to paste into a ticket or chat.

The phone must be connected to a network that can reach the branch addresses (branch Wi-Fi, or VPN to the internal network).

## CSV format

One row per device. See `sample_branches.csv`.

```
branch,device,host,port
Branch 001 - Riyadh North,Router,10.1.1.1,
Branch 001 - Riyadh North,Printer,10.1.1.50,9100
```

- `port` is optional. Empty = ping, a number = TCP check.
- Header row is optional. Comma, semicolon and tab separators all work.
- Importing again adds new branches/devices and skips duplicates, so you can re-import an updated file.
- Save the file from Excel as "CSV UTF-8", copy it to the phone (Drive, USB, email), then pick it in the app.

## Getting the APK

### Option A: GitHub Actions (no Android Studio needed)

1. Create a new private repository on GitHub and upload everything in this folder (keep the `.github` folder).
2. Open the repository's **Actions** tab. The "Build APK" workflow runs on its own (or press "Run workflow").
3. When it finishes (a few minutes), open the run and download the **BranchCheck-APK** artifact. Unzip it to get `app-debug.apk`.
4. Copy the APK to the phone and open it. Android will ask you to allow installs from that source.

### Option B: Android Studio

1. Install Android Studio and choose **Open** on this folder. Let it sync (it downloads the Android SDK pieces it needs).
2. **Build > Build APK(s)**. The file is `app/build/outputs/apk/debug/app-debug.apk`.

Or from a terminal with JDK 17 and the Android SDK installed: `./gradlew assembleDebug`.

The debug APK is signed with a debug key, which is fine for installing on your own team's phones. Updating works as long as every build comes from the same machine/key; if Android refuses an update, uninstall the old version first.

## Known limits of this MVP

- Branch list is stored on the phone only. To move it to another phone, re-import the CSV.
- Results are kept in memory only (closing the app clears them).
- IPv4/IPv6 addresses and host names both work, but host names need DNS reachable from the phone.
- Ping uses `/system/bin/ping`. If a phone vendor removes it, the app falls back to Android's built-in reachability test, which can be less reliable.

## Project layout

- `app/src/main/java/com/branchcheck/app/`
  - `Checker.kt` ping and TCP checks
  - `Store.kt` branch storage and latest results
  - `CsvImport.kt` CSV import
  - `Report.kt` text report
  - `MainActivity.kt` branch list, `BranchActivity.kt` device list
  - `Adapters.kt` list rows
- `app/src/main/res/` layouts, menus, strings
