# Project Plan

## Product Goal
通用个人播客软件“我的播客”：一次选择ZIP导入完整系列，独立节目/分类/封面/全文，复用AntennaPod数据库与Media3。厨房课仅为用户第一个测试节目，不是软件品牌。

## Constraints
GPL-3.0，保留上游版权。无新增账号/服务器/遥测/重型框架；不打包用户媒体。正式版新包名com.homeo.mypodcasts，独立签名。用户接受重新导入，开发侧模拟器验证，手机真实触感留待正常体验。

## Packages
- WP-01 DONE: generic ZIP library/import/player integration; prior7parser+6Android checks.
- WP-02 DONE: original upstream built/launched first, fork/source/initial preview delivery.
- WP-03 DONE: generic app identity corrected to我的播客.
- WP-04 DONE: authentic UI baseline, scoped audit,10rules confirmed.
- WP-05 DONE: copy changes and interaction reviewed; user requested color and original rounded controls.
- WP-06 DONE:3directions/icons/motion compared; user selected A on2026-11-11.
- WP-07 DONE: native A implementation,3tabs,episode detail/directplay/transcript search, persistent import history/source state; resource0 startup and stale SAF metadata defects fixed in actual release regression.
- WP-08 DONE: dedicated signed/minified/shrunk nondebuggable1.1.0, actual Release emulator regression, public formal Release and matching GPL source.

## Current Directive
WP-08 DONE. Release https://github.com/Braieyre/AntennaPod/releases/tag/v1.1.0 published (draft=false,prerelease=false). Code/source tag points10fc1a2e6d7ebfe56b137f6bc94d7a773d0f706d. APK SHAf704062598c4aa5ea8f1d3feea9f83c21335e67c50e2965eb5ba892ec7b40e8c,16701967bytes. Local delivery ../交付/我的播客-1.1.0. GitHub's generated source tag archives verified HTTP200; duplicate manual source upload timed out, local source ZIP retained separately. No implementation remains authorized/unfinished. User may experience phone feel without a testing checklist.

## Evidence
Latest actual Release10instrumentation tests pass (4lifecycle+5import+1real Media3 playback); post-force-stop persistence/FLAG_DEBUGGABLE export passes. Real SAF source deletion, external deletion, corrupt retry, transcript search, category/title filters, light/dark,130%font and notification pause checked.16native contrast pairs min4.85. Raw logs/screenshots remain ../verification/v1.1.0; concise report docs/ui-polish/release-v1.1.0.md. Private sample cover/screenshot files excluded from public Git. Dedicated signing key outside repo with password in macOS Keychain; missing signing credentials fails. Physical haptics/Bluetooth/OEM battery policy not claimed.
