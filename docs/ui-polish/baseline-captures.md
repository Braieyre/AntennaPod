# 真实基线截图清单

本轮版本：我的播客 `1.0.1-preview`（3130002），API 35 模拟器，1080 × 2424。应用包名 `com.homeo.sixmin.kitchen`。使用 `adb exec-out screencap -p` 原样保存 PNG；相关 UI 树由 `uiautomator dump` 保存。截图内设备时钟属于模拟器环境，不作为宿主实际捕获时间；宿主保存时间和 SHA-256 详见 `screenshot-manifest.json`。

## 本轮捕获

| 页面 | 图片 | 操作边界 |
| --- | --- | --- |
| 启动后的自动历史结果弹窗 | [current-startup.png](screenshots/current-startup.png) | 未点击删除；当前记录只有 OK |
| 节目首页 + 迷你播放器 | [current-home.png](screenshots/current-home.png) | 关闭结果弹窗后截图 |
| 节目简介、分类、剧集列表 | [current-programme.png](screenshots/current-programme.png) | 只打开现有节目，未重新导入 |
| 当前剧集三按钮选择弹窗 | [current-episode-modal.png](screenshots/current-episode-modal.png) | 只打开/取消，未从该弹窗开始新播放 |
| 原生完整播放器 | [current-player.png](screenshots/current-player.png) | 从既有迷你播放器展开，未改倍速/睡眠 |
| 原生待播列表 + 迷你播放器 | [current-queue.png](screenshots/current-queue.png) | 只切换导航，未移除/重排 |
| 原生常用设置首页 | [current-settings.png](screenshots/current-settings.png) | 只打开，未修改任何设置 |

## 历史证据（版本标签保留）

| 页面 | 图片 | 历史版本 | 原始来源 |
| --- | --- | --- | --- |
| 节目首页历史截图 | [historical-library.png](screenshots/historical-library.png) | 1.0.0-kitchen | `../verification/library-final.png` |
| 空库历史截图 | [historical-empty.png](screenshots/historical-empty.png) | 1.0.0-kitchen | `../verification/library-empty.png` |
| 文字稿全文搜索历史截图 | [historical-transcript.png](screenshots/historical-transcript.png) | 1.0.0-kitchen | `../verification/transcript-search.png` |
| 导入结果历史截图 | [historical-import-report.png](screenshots/historical-import-report.png) | 1.0.0-kitchen | `../verification/import-report.png` |
| 系统应用名称与图标历史截图 | [historical-app-info.png](screenshots/historical-app-info.png) | 1.0.1-preview | `../verification/generic-app-info.png` |

## 尚未覆盖 / 不应冒充已验证

- 本轮未重新导入来捕获正在解压的瞬时进度，避免为截图改动现有库；左侧以真实“完成结果”说明导入现状，计划进度状态只在右侧线框模拟。
- 当前 App 没有独立导入记录页面；不能提供该页面“真实截图”。采用历史结果弹窗作现状对照，计划独立页面明确标为线框。
- 文字稿搜索使用已存在的真实历史截图；本轮未新建文字稿正文或重构截图。
- 深色模式、大字体、窄屏、实际蓝牙及真实触感不在本轮截图覆盖范围。
- 启动自动出现结果弹窗已在本轮复现；该条记录未显示“删除原压缩包”，所以“删后仍有删除入口”仍以用户反馈和源码机制为证据，不声称本轮屏幕出现该按钮。

导航期间未删除 ZIP、节目或数据库，未重新导入、重排队列或修改设置。原型中的删除/导入按钮只改变网页演示状态。
