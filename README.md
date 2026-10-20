# SoloGo：KataGo 单机围棋学习 App

SoloGo 是一个 Android / Kotlin / Jetpack Compose 单机围棋学习工具。基础人机对局、SGF FF[4] 棋谱管理和本地复盘不依赖云端；高级胜率、黑白势力范围、双方目数、候选 PV 和整盘逐手分析通过本地 KataGo 完成。

当前工程已经从空脚手架拆成四个模块，并实现了第一条完整链路：

```
core   -> 棋盘/棋谱/分析领域模型，显示用提子缓存
engine -> KataGo 进程、GTP、资源安装、温度监控
sgf    -> SGF FF[4] 解析/写出、分析 JSON
app    -> Compose UI、MVVM、SGF 文件、隐私、内购
```

## 先读：原需求里必须修正的三处

### 1. Android 10+ 不能直接执行 filesDir 里的二进制

原方案“把 assets 里的 katago 复制到 filesDir，chmod 后 ProcessBuilder 执行”在 Android 10+ 的 W^X 策略下通常会失败。正式包应使用：

```
app/src/main/jniLibs/arm64-v8a/libkatago.so
```

它不是普通共享库，而是按 Android 原生库目录规则打包的 ELF 可执行文件。工程已打开 `useLegacyPackaging = true` 和 `extractNativeLibs`，让系统把文件解压到可执行的 `nativeLibraryDir`。

`KataGoInstaller` 仍保留 assets 回退路径，方便旧设备/调试；失败时会给出明确错误。不要把“assets 回退可用”当成正式发布的保证。

### 2. SGF 读写不要交给 KataGo

KataGo 的核心是搜索/分析，不应承担完整的 SGF 多分支读写。工程里的 `:sgf` 模块实现了 FF[4] 解析、转义、多分支树和主分支读取；AI 分析另存为同名 `*.analysis.json`，不污染标准 SGF。

### 3. 图表不必强行引入 MPAndroidChart

当前复盘曲线用 Compose Canvas 绘制，减少 JitPack 依赖和 View/Compose 互操作。若你坚持使用 MPAndroidChart，可以在 `:app` 增加 JitPack 仓库和 `com.github.PhilJay:MPAndroidChart:v3.1.0`，但需要额外处理 AndroidView 生命周期；当前实现不依赖它。

## 目录结构

```
app/
  src/main/java/com/sologo/app/
    AppContainer.kt / SoloGoApplication.kt / MainActivity.kt
    data/              SettingsRepository、SgfRepository
    monetization/      Google Play Billing
    ui/components/     GoBoard、图表、隐私/付费弹窗、Banner
    ui/game/           GameScreen、GameViewModel
    ui/review/         ReviewScreen、ReviewViewModel
    ui/library/        LibraryScreen、LibraryViewModel
    ui/settings/       SettingsScreen、SettingsViewModel
    ui/home/ and ui/about/
  src/main/assets/katago/      权重/cfg 放置说明、MD5 示例
  src/main/jniLibs/arm64-v8a/  libkatago.so 放置位置
core/
  .../model/            GoPoint、BoardPosition、GameRecord、分析模型
engine/
  .../engine/           GtpClient、KataGoEngine、Installer、Battery
sgf/
  .../sgf/              SgfParser、SgfWriter、SgfCodec、AnalysisStore
```

## 已内置的 KataGo 资源

工程已经放入了可运行的 arm64 CPU 资源：

1. `app/src/main/jniLibs/arm64-v8a/libkatago.so`
2. `app/src/main/assets/katago/model.txt`（文件内容是 gzip 文本权重；安装到 filesDir 后为 `model.txt.gz`）
3. `app/src/main/assets/katago/gtp.cfg`（`numSearchThreads=4`、`maxTime=1.5`）
4. `app/src/main/assets/katago/checksums.properties`
5. `app/src/main/assets/katago/human-model.bin`（gzip 的 HumanSL 权重，安装后为 `human-model.bin.gz`）

资源来源、SHA256 和修改说明记录在 [SOURCES.txt](app/src/main/assets/katago/SOURCES.txt)。普通权重是官方 `kata1` 小网络（约 5MB），HumanSL 权重是官方 `b18c384nbt-humanv0`（约 99MB）。

当前 debug APK 约 147MB，因为 HumanSL 权重随包携带；正式上架前需要确认商店包体限制，必要时改用 Play Asset Delivery 或首次下载。默认 5K profile，搜索受 `maxTime=1.5s` 和 4 线程限制。

首页“开始人机对局”上方可选择 **10K / 5K / 1K / 1段**。每档会自动选择 HumanSL 的 `rank_*` profile，并调整 visits、搜索时间和着法温度；设置页不再暴露 visits。HumanSL 是无搜索的人类策略模型，实际强度仍受手机算力、profile 和模型本身限制。

对局规则只提供 **中国规则** 和 **KataGo 默认规则**（当前 `gtp.cfg` 的默认规则也是中国规则）。贴目不开放给用户设置：分先 7.5 目、让先 0 目、让子棋 0.5 目，首页和对局状态栏会自动显示。让子方式仅提供 **分先（默认）/ 让先 / 2 / 3 / 4 / 5 / 9 子**；让子棋由人类执黑、AI 执白先行。

对局页暂时移除了“局势分析”。点击“数子”后会先用 `final_score` 得到 KataGo 结果，再用 ownership 在棋盘空点标出黑色和白色地盘；面板显示黑地、白地、含子与贴目的总占地、按标记计算的差距以及 KataGo 最终结果。死子会先从数子棋盘移除，再计算和标记。

如果数子前 App 切后台导致 KataGo 子进程被销毁，重新进入对局后会按当前棋谱重建引擎，再继续数子；数子过程不会在后台启动新进程。

设置页新增“对局结束后自动保存”。打开后，人类认输、KataGo 认输或数子完成时会自动把标准 SGF 保存到私有棋谱库；SGF 的 `DT` 记录对局日期，复盘列表同时显示精确保存时间、路数、手数和结果。复盘页在没有指定棋谱时会自动列出已存储对局，点击即可开始复盘，并支持开局/上一手/下一手/末手、进度条、AI 整盘分析、胜率/目数曲线、领地热力和候选 PV。

因为随包的 Android 引擎按 API 29 工具链构建，工程 `minSdk` 同步设为 29（Android 10）。如果必须支持 Android 7–9，需要换用对应 API 级别编译的 `libkatago.so`，不能直接复用当前二进制。

## 构建

环境要求：Android Studio（可使用其自带 JBR）、Android SDK Platform 37、JDK 17+、Gradle Wrapper 9.6.0。首次构建需要联网下载 Gradle 和 Maven 依赖。

### 从干净 clone 构建

仓库不提交 `local.properties`、Android SDK 或 Gradle 缓存。clone 后任选一种方式指定 SDK：

```powershell
git clone https://gitee.com/nandouliuxing/solo-go.git
cd solo-go

# 方式 A：临时环境变量（适合命令行/CI）
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\<你的用户名>\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :app:assembleDebug
```

方式 B：用 Android Studio 打开项目，等待 IDE 生成 `local.properties` 并完成 Sync，然后点 Run，或在项目根目录执行上面的 Gradle 命令。

SDK 至少需要 `platforms;android-37.0` 和 `build-tools;37.0.0`（AGP 也可能自动安装兼容的 Build Tools）。KataGo 的 arm64 二进制、权重、`gtp.cfg` 和 MD5 清单已经在仓库中，不需要在 clone 后手动复制。

本次已用 `git clone` 到临时目录、通过 `ANDROID_HOME` 指定 SDK，执行了一次干净的 `:app:assembleDebug`，93 个任务全部成功。

本轮已经在本机安装了项目私有的最小 Android SDK，并成功执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

产物为 `app/build/outputs/apk/debug/app-debug.apk`（约 147MB，SHA256 `B3A8E8F46CB5CBAE438EADF0A260579C41F3EEA61620261F75F807CF5AB266B6`）。APK 内已确认包含 `lib/arm64-v8a/libkatago.so`、普通权重、HumanSL 权重、`gtp.cfg` 和 MD5 清单。由于当前没有连接 arm64 真机/模拟器，尚未验证实际设备上的进程执行和触摸体验；这一步需要在 Android 10+ arm64 真机上安装 APK 后完成。

每次执行 `assembleDebug` 后还会自动生成一份带北京时间戳的副本，例如：

```
app/build/outputs/apk/debug/sologo_20261020_183712.apk
```

原始 `app-debug.apk` 仍保留，Android Studio 的 Run/Install 流程不受影响。`assembleRelease` 也会在 release 目录生成同样的 `sologo_日期_时间.apk`。

SGF/棋盘逻辑通过小型 smoke test；官方 Windows Eigen 版 KataGo 也已实测普通模型和 HumanSL 模型加载、`humanSLProfile=rank_5k`、`genmove` 与 GTP 参数设置，说明资源格式和运行时参数有效。

## 内购配置

- 内购商品 ID 是 `premium_review_unlock`，发布前在 Play Console 创建同名一次性商品。
- 测试真实扣款需要 Play Console 内测轨道 + 许可测试账号；debug 包无法凭空完成商店扣款。
- 当前版本不包含广告 SDK，也不会显示 Banner、插屏或激励视频。

## 合规实现点

- 隐私弹窗：首次启动强制二选一；拒绝后基础功能正常，商店购买入口不初始化。
- 进程：`ProcessLifecycleOwner.onStop` 调用 `shutdownNow()`；离开对局/复盘 ViewModel 也会结束进程。
- 温度：读取 `ACTION_BATTERY_CHANGED`，超过 42℃ 调用 `stop` 并阻止新的 AI 搜索；不需要额外权限。
- 权限：仅 `INTERNET`、`ACCESS_NETWORK_STATE`；SGF 导入导出走 SAF，不申请存储权限。
- MIT：关于页展示 KataGo 源码地址和 MIT 文本；发布前还要核对所选权重的许可证。

## 开发顺序（建议）

1. **资源与进程**：先确认 `libkatago.so`、权重、cfg 能在真机启动；用 `name`、`boardsize`、`genmove` 做冒烟。
2. **GTP 与对局**：验证 `play`、`undo`、`genmove`、异常重启、后台 kill。
3. **SGF**：用带变化、注释、AB/AW 的棋谱做导入导出回归；确认外部软件可打开。
4. **Compose 棋盘**：9/13/19 路触摸、提子显示、最后一手、候选点。
5. **分析与复盘**：`kata-analyze` 输出解析、领地热力、胜率/目数曲线、逐手亏损。
6. **商业化与合规**：Play Billing、退款/恢复购买、商店素材和隐私政策 URL。
7. **稳定性**：低端机温度/内存/进程卡死测试、后台切换、旋转、深色模式、弱网。

更细的分步计划见 [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md)。
