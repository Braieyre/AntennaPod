# 我的播客

基于 [AntennaPod](https://github.com/AntennaPod/AntennaPod) develop 的通用离线播客应用，起点提交 `08107984b83fa0def9f6a1d2c244b1d9d1161322`。原作者版权、LICENSE、贡献者信息均保留；本定制代码使用 GPL-3.0。源码：[Braieyre/AntennaPod / feat/sixmin-zip-library](https://github.com/Braieyre/AntennaPod/tree/feat/sixmin-zip-library)。

“我的播客”面向不同主题、不同集数的播客系列。每个用户导入的 ZIP 独立成为一个节目；应用不预装任何示例节目或音频。“六分钟厨房课”只是首个导入测试系列，不是产品名称，也不限制后续内容。

## 使用

1. 打开首页“我的播客”，点“导入压缩包”，在系统文件选择器中选一个 ZIP。
2. 等待后台复制、解压、校验和入库。可以取消，失败后可重试；完成报告逐条显示异常。有效剧集会导入，缺文件或损坏的剧集会说明原因。
3. 选择节目，按分类筛选或搜索标题。点剧集可按学习顺序播放，也可打开全文文字稿；文字稿支持关键词高亮和前后匹配跳转。
4. 点击底部播放条进入 AntennaPod 原有播放器，可调倍速、设置睡眠倒计时；通知栏、锁屏和耳机控制沿用原实现。
5. 完成报告默认保留原 ZIP。只有点“删除原压缩包”并确认后才尝试删除；文件提供方不允许删除时会提示。

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

产物位于 `app/build/outputs/apk/free/debug/app-free-debug.apk` 和 `app/build/outputs/apk/play/debug/app-play-debug.apk`。交付优先使用 Free debug APK，可直接侧载；未打包用户的音频、封面或文字稿。debug 由本机 Android debug 密钥签名。Free/Play 使用相同应用 ID，属于两个构建口味，不可作为两个不同应用同时安装。

应用显示名称为“我的播客”，图标及启动画面使用通用麦克风与声波图形。应用 ID 沿用首版的 `com.homeo.sixmin.kitchen`，debug 不额外加后缀，可与原版 AntennaPod 共存。这个历史安装标识仅用于兼容已有安装与私有数据，不代表应用主题；使用相同签名的新版本可直接覆盖升级。源码 Java namespace 保留上游名称，方便后续合并上游代码。应用名称由 common.gradle 中 app_name 设置，“关于”页保留上游归属与 GPL-3.0 说明。

已有安装建议保持包名和签名不变。为全新独立分发改包名时，同步修改 app/build.gradle 的 applicationId 与两处 provider_authority、common.gradle 的 testApplicationId，并检查 APK 最终清单。不要全仓库替换 Java namespace。

### Release 签名

本次交付 debug；未生成或代管正式发布私钥。自行创建并妥善备份密钥（命令会交互式询问密码）：

```sh
keytool -genkeypair -v -keystore podcast-release.jks -alias podcast -keyalg RSA -keysize 3072 -validity 10000
```

在仓库外的 Gradle 用户属性文件中设置 `releaseStoreFile`（密钥绝对路径）、`releaseStorePassword`、`releaseKeyAlias`、`releaseKeyPassword`，然后运行 `./gradlew :app:assembleFreeRelease`。请勿提交密钥、口令或 local.properties。同一包名必须沿用同一签名才可覆盖升级；debug 换 release 签名不能直接覆盖安装，卸载又会删除私有库，切换前保留原 ZIP。对外分发 APK 时同时提供该 APK 对应版本的完整源码与 LICENSE。

## 验证

原版先编译并启动，再开始定制；基线提交与证据见交付测试报告。`导入测试报告.md` 保留了以“六分钟厨房课-26集.zip”为首个历史测试样本的模拟器实测结果和命令；其中的节目名与首版 APK 名称属于测试记录，不是当前产品名称。模拟器结果不代表某个手机品牌的后台策略或实体蓝牙设备已经验证。
