# 我的播客

基于 [AntennaPod](https://github.com/AntennaPod/AntennaPod) develop 的通用离线播客应用，起点提交 `08107984b83fa0def9f6a1d2c244b1d9d1161322`。原作者版权、LICENSE、贡献者信息均保留；本定制代码使用 GPL-3.0。源码：[Braieyre/AntennaPod / feat/sixmin-zip-library](https://github.com/Braieyre/AntennaPod/tree/feat/sixmin-zip-library)。

“我的播客”面向不同主题、不同集数的播客系列。每个用户导入的 ZIP 独立成为一个节目；应用不预装任何示例节目或音频。“六分钟厨房课”只是首个导入测试系列，不是产品名称，也不限制后续内容。

## 使用

1. 打开首页“我的播客”，点“导入压缩包”，在系统文件选择器中选一个 ZIP。
2. 等待后台复制、解压、校验和入库。可以取消，失败后可重试；导入记录逐条显示异常。有效剧集会导入，缺文件或损坏的剧集会说明原因。
3. 选择节目，按分类筛选或搜索标题。点标题进入剧集详情，点右侧播放键按学习顺序播放；详情中可打开全文文字稿；文字稿支持关键词高亮和前后匹配跳转。
4. 点击底部播放条进入 AntennaPod 原有播放器，可调倍速、设置睡眠倒计时；通知栏、锁屏和耳机控制沿用原实现。
5. 原 ZIP 默认保留。仅在“设置 → 导入记录”主动选择删除并确认后才尝试删除；删除成功或确认文件不存在后，该记录不再提供删除入口。打开软件不自动弹报告或删除提示。

播放一个系列时，当前播放队列会替换为从选中集开始的系列顺序。首页菜单可进入原有 RSS 首页，原有订阅、搜索、下载和同步入口保留。未新增账号、服务器、遥测或上传功能；本地导入与播放无需网络。原有 RSS 联网功能及其设置仍由用户自行使用。

## 包格式与顺序

ZIP 可以包含一个外层节目文件夹，也可以直接包含分类文件夹与根文档。每个根子文件夹是分类，名称按数字自然顺序排序；分类内按集号自然排序（如 1、2、10 或 A1、A2、A10）。格式通用，不固定集数、节目题材或标题。

```text
节目名称/
  系列总纲.md
  播放清单.md
  01-分类/
    01-标题.mp3
    01-标题-封面.webp
    01-标题-文字稿.txt
```

中文路径使用 UTF-8，文字稿按 UTF-8 读取，原始说话人标签与全文保留。MP3 时长由 Android 媒体元数据读取；播放清单只做参考校验。没有 manifest.json 可以正常导入；若存在，检查 JSON 合法性、可识别的媒体路径及可选 episodeCount，不要求私有清单格式。index.html、episodes.json 和 config.json 不是导入前提。

节目身份按 ZIP 内根文件夹名称的 Unicode NFC 形式确定；没有外层文件夹时使用 ZIP 文件名（去掉 .zip）。同一节目重复导入请保留根文件夹名称；不同节目请使用不同根名。剧集身份为“分类名/集号-标题”。同名项更新音频、封面、全文和实际时长，保留剧集 ID、播放位置与已听标记。改根名会被视为新节目，改分类或标题会被视为新剧集；此前有效但本次缺失/损坏的项会保留，报告会说明异常。

## 存储与兼容

文件保存在 App 私有 `files/zip-library/`：节目身份目录下按导入版本保存原分类/集文件及 `series.json`。先在临时目录解压并校验，再通过数据库事务激活新版本；取消/失败不替换此前生效版本。导入元数据格式版本为 1，沿用原 Feed/FeedItem/FeedMedia 表，没有新增数据库字段或更改上游数据库版本。

导入限制：压缩包及解压总量各不超过 2 GiB，最多 20000 项，单文件 512 MiB，单个文本文档 4 MiB。拒绝越界路径；重复路径、重复集号、空分类与坏文件进入报告。私有文件随卸载删除；ZIP 原件建议自行保留备份。

## 编译与 APK

需要 JDK 21、Android SDK Platform 36 / Build Tools 36.0.0 和网络下载构建依赖。播放器、Gradle 与依赖版本沿用起点 develop，没有新增重型库。

```sh
git clone --branch feat/sixmin-zip-library https://github.com/Braieyre/AntennaPod.git
```

在仓库根目录配置 JAVA_HOME、ANDROID_HOME 后运行：

```sh
./gradlew :app:assembleDebug
```

本机 Homebrew 环境实际命令：

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew :app:assembleDebug
```

调试产物位于 `app/build/outputs/apk/free/debug/app-free-debug.apk` 和 `app/build/outputs/apk/play/debug/app-play-debug.apk`，只用于开发检查。正式交付使用 **Free Release v1.1.0**，开启代码与资源压缩，不可调试，不包含用户音频、封面或文字稿。

应用名称“我的播客”，A 方案暖纸、陶土、青绿色调，图标为抽象声场。正式包名 **`com.homeo.mypodcasts`**，版本码 `3130100`。与历史体验版 `com.homeo.sixmin.kitchen` 可并存，新装后需重新选择 ZIP 导入。源码 Java namespace 保留上游名称，方便合并上游代码；“关于”保留上游归属、GPL 与源码入口。Free/Play 使用相同包名，不作为两个不同应用同时安装。

另行改包名时，同步修改 `app/build.gradle` 的 applicationId 与 provider_authority、`common.gradle` 的 testApplicationId，再检查最终 APK 清单。不要全仓库替换 Java namespace。

### 正式版签名与构建

本机专用 RSA-4096 发布密钥保存在仓库外 `~/keys/my-podcasts-release.p12`，别名 `mypodcasts`；口令保存在 macOS 钥匙串服务 `com.homeo.mypodcasts.release`，账户为当前用户。脚本读取钥匙串并通过环境变量传给 Gradle，口令不写入源码或日志。

```sh
./scripts/build-release.sh
```

产物：`app/build/outputs/apk/free/release/app-free-release.apk`。缺少正式密钥或口令会失败，不回退到 debug 签名或默认口令。

其他机器可自行创建独立发布密钥，并设置 `MYPODCASTS_STORE_FILE`、`MYPODCASTS_STORE_PASSWORD`、`MYPODCASTS_KEY_ALIAS`、`MYPODCASTS_KEY_PASSWORD` 四个环境变量，运行 `./gradlew :app:assembleFreeRelease`。其他密钥签出的 APK 不能覆盖本发行版。私钥和口令不能提交到 GitHub。

使用 Android 官方 [apksigner](https://developer.android.com/tools/apksigner) 检查最终 APK 签名：

```sh
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose --print-certs app/build/outputs/apk/free/release/app-free-release.apk
```

### 密钥备份

将 `~/keys/my-podcasts-release.p12` 另存到加密离线介质，并在密码管理器中单独保存钥匙串中的口令。通过“钥匙串访问”搜索 `com.homeo.mypodcasts.release` 获取口令；不要把密钥和明文口令放在同一公开位置。后续同包名更新必须使用此密钥；丢失密钥将无法继续覆盖升级。仓库、源码压缩包与 APK 附件均不包含密钥或口令。

对外分发时同时提供该 APK 对应标签的完整 GPL-3.0 源码与 LICENSE。正式版本标签为 `v1.1.0`；历史 prerelease 保留作回溯，不代表当前正式版。

## 验证

原版先编译并启动，再开始定制；基线提交与证据见交付测试报告。`导入测试报告.md` 保留了以“六分钟厨房课-26集.zip”为首个历史测试样本的模拟器实测结果和命令；其中的节目名与首版 APK 名称属于测试记录，不是当前产品名称。模拟器结果不代表某个手机品牌的后台策略或实体蓝牙设备已经验证。
