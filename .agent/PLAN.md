# Project Plan

## Product Goal
Fork develop into 六分钟厨房课: a generic offline ZIP-series library integrated with AntennaPod playback, preserving RSS and GPL-3.0.

## Constraints
Baseline upstream APK build and launch MUST precede application edits. No new player, server, account, telemetry or heavy dependencies. Preserve user media outside repository. Respect AGENTS.md. New visible strings in ui:i18n English resource file (Chinese custom labels are intended). Application ID com.homeo.sixmin.kitchen, distinct icon/splash/about attribution. Do not claim real-phone validation from emulator evidence.

## Current Reality
Upstream 08107984b83fa0def9f6a1d2c244b1d9d1161322; branch feat/sixmin-zip-library. GitHub fork Braieyre/AntennaPod exists. Java 21 and SDK /opt/homebrew/share/android-commandlinetools. Original build pending; do not implement application changes until root confirms build and launch success. User folder repacked UTF-8 ZIP outside repo contains 26 audio/cover/transcript triplets, 5 categories.

## Work Packages
### WP-01: Offline series import and browsing
- Status: DONE
- Outcome: SAF single ZIP selection, durable background import/cancel/retry and report; generic filesystem scanner; private storage; idempotent DB integration; series/categories/episodes/search/transcript search; upstream playback with ordered next episode.
- Acceptance: actual supplied media counts and natural category/episode order; durable IDs and playback state across reimport/relaunch; partial malformed content reports; malformed ZIP safe handling; second series isolated; full transcript; no UI I/O blocking; no embedded media. Meaningful parser and Android integration checks.
- Exclusions: branding and final delivery handled by root; no changes to upstream database schema unless necessary and agreed.
- Dependencies/risks: baseline gate; upstream media fields and queue persistence; ZIP traversal/bombs/duplicate paths; staged file commit and cancellation; source ZIP deletion only on explicit user choice after success.

### WP-02: Branding, verification and delivery
- Status: IN_PROGRESS
- Outcome: new app identity/icon/splash/about, APK, README-定制版.md and truthful import report, GPL source on fork.
- Acceptance: build/install/launch; local Android evidence for requested behaviors; actual-device limitations explicit; source public with license retained.

## Historical Directive
Execute WP-02 only: publish matching GPL source and deliver tested APK, README and report. WP-01 accepted after 7 parser and 6 Android checks plus SAF/UI review.

## Key Decisions
Use existing Feed/FeedItem/FeedMedia, DBReader/DBWriter and upstream playback service. Prefer independent versioned import metadata over invasive upstream schema edits; prevent imported feeds being rescanned by SAF-local-feed updater. Never delete user source without choice.

## WP-03: Generic personal podcast identity
- Status: READY
- Outcome: App display name 我的播客; neutral microphone/headphone icon/splash/about; kitchen series remains only imported content and historical test fixture.
- Acceptance: exact assembleDebug passes, in-place upgrade retains library/progress; versionCode increment; generic product README.
- Constraints: keep applicationId and debug signing identity for existing installs/data; no player/importer/database behavior changes; preserve historical test report as labeled kitchen fixture evidence.

## Current Directive
Execute WP-03 only. Executor owns branding/resources/version/README changes and build. Root handles review, upgrade verification and delivery/release.
