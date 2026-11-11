# Project Plan

## Product Goal
通用个人播客软件“我的播客”：ZIP一次选择导入完整节目，支持多个节目及独立分类/封面/全文，保留AntennaPod原播放器与RSS功能。六分钟厨房课只是首个导入测试样本，不是产品品牌。

## Constraints
GPL-3.0，保留上游版权；不内置用户音频，不新增服务器、账号、遥测或重型依赖。已授权正式版使用新包名 com.homeo.mypodcasts 和独立签名，允许卸载重导。用户要求开发侧使用模拟器验证，用户只需直接体验APK。

## Packages
- WP-01 offline ZIP series library: DONE;7 parser tests and6 Android integration tests passed.
- WP-02 initial delivery and public source/release: DONE;original develop built/launched before edits.
- WP-03 generic identity correction: DONE;code commit4526ee659, app name我的播客, microphone icon/splash/about, version1.0.1-preview(code3130002), in-place upgrade verified.

## Current Directive
WP-07 IN_PROGRESS: User selected A on 2026-11-11. Implement approved warm-paper UI and reliable import history lifecycle throughout common pages. Preserve rounded controls, database/import rules and Media3. Acceptance: programme/queue/settings navigation, direct episode detail/play, transcript search, A light/dark tokens + soundfield icon, no automatic historical report/deletion popup, persistent task consumption and source terminal status, about accurately credits GPL upstream. Root reviews implementation and emulator evidence, then executes already-authorized independent signed v1.1.0 release com.homeo.mypodcasts.

## Evidence
Public prerelease v1.0.1-preview.1 targets4526ee6593dfab50d4a5758bcc138fe794a7f86f. Four attachments checked against serverSHA-256. Source supplied by GitHub tag archives, license retained. Emulator upgrade preserved allFeedIDs/media positions/played states; system app label and icon inspected. Original parser/player/importer/DB logic unchanged in branding update. Historical sample report remains labeled as such.

## WP-04
Status: IN_PROGRESS. Outcome: reproducible XML/Java UI chrome metrics, authentic baseline screenshots, audit + proposed ten rules for user review. Exclude imported content/transcript bodies from chrome metrics. Capture common screens where possible, mark missing evidence. Do not implement application behavior or visual design yet.

## WP-05
IN_PROGRESS. User-reviewable HTML interaction boards, Markdown change table, complete common-screen baseline where feasible. Stop for interaction review before code implementation.

## WP-06
IN_PROGRESS: visual-review.html,3directions x3screens + real baseline column + icon masks/sizes, design tokens and playable motion.

## WP-07
IN_PROGRESS: approved A implementation and regression.
## WP-08
PLANNED: dedicated fail-fast production signing, actual Release emulator regression, APK/checksums/GPL source and GitHub formal release.
