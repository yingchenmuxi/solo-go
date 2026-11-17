# 独弈围棋：KataGo 单机围棋学习 App

独弈围棋是一个 Android / Kotlin / Jetpack Compose 单机围棋学习工具。人机对局、规则说明、SGF FF[4] 棋谱、本地复盘和 AI 分析均可离线运行，围棋规则与搜索由内置 KataGo 负责。

- 当前版本：`1.0`
- 主要分支：`develop`、`master`
- GitHub：[yingchenmuxi/solo-go](https://github.com/yingchenmuxi/solo-go)
- Gitee 镜像：[nandouliuxing/solo-go](https://gitee.com/nandouliuxing/solo-go)

## 当前功能

### 禅意木纹 UI

- 使用暖纸色、墨色、朱砂和木纹棋盘组成的固定品牌主题。
- 首页、对局、规则、复盘、设置、隐私和关于页面风格统一。
- 支持系统深色模式，并提供对应的暖黑配色。
- 底部导航顺序为 **对弈 → 规则 → 复盘 → 设置**。
- App 图标采用木纹底色和单颗棋子设计，源文件位于 `design/logo-redesign/duyi-lone-stone.svg`。

### 人机对弈

- 支持 9、13、19 路棋盘。
- AI 水平支持 **10K、5K、1K、1段**，由 HumanSL profile 和搜索参数共同控制。
- 支持中国规则和 KataGo 默认规则；贴目按规则和让子方式自动计算。
- 让子支持分先、让先、2、3、4、5、9 子。
- 支持悔棋、停一手、认输、保存 SGF、自动保存和重新开局。
- 悔棋会退回到人类上一手落子之前，并同时撤掉其后的 AI 应手。
- 人类和 AI 实际落子时播放木石落子音效；停一手和悔棋不播放。
- AI 思考状态显示实时秒数计时器。
- 黑白连续停一手后自动弹出确认窗口，可选择数子或结束对局。

### 形势判断与数子

- 对局和复盘页均可使用同一套形势判断。
- 形势判断读取 KataGo `whiteOwnership`，统一转换为黑方视角后绘制势力色块。
- 黑白领地按整点计数，统计阈值与棋盘势力色块一致，避免出现小数“领地目数”。
- 置信度不足的交叉点显示为“未定点”。
- 贴目后的领先差距可能包含 `0.5`，这是贴目或让子规则造成的正常结果。
- “数子”不再调用容易超时或报错的 `final_score`、`final_status_list`，直接复用形势判断结果。
- 数子结果只通过形势分析区域显示，不再维护第二套重复数子面板。
- 数子后仍可关闭并重新打开形势分析；手动数子不会强制结束对局。

### 复盘

- App 私有目录中的 SGF 会自动出现在复盘列表。
- 支持选择已保存对局，并显示对局时间、保存时间、路数、手数和结果。
- 支持开局、上一手、下一手、末手、进度条和落子声音。
- 支持“形势判断”，显示黑白势力、整数占地、未定点及贴目后差距。
- 支持“AI 分析”，使用 KataGo `kata-raw-nn` 的 policy 概率给出下一手候选位置。
- 棋盘标记前三个候选点，并在列表中显示前五个推荐坐标。
- 只分析当前选中的局面，不执行耗时很长的整盘逐手分析。
- 切换手数时会清除上一局面的形势判断和 AI 候选结果。

### 规则页

规则页位于“对弈”之后，面向零基础用户，包含：

- 棋盘交叉点与落子。
- 气、连块和提子。
- 禁入点与劫争。
- 停一手、认输和终局。
- 中国规则数子与贴目。
- 使用 Compose Canvas 绘制的落子、气和提子示意图。

### 隐私、温度与进程管理

- 首次启动要求用户明确选择是否同意隐私政策。
- 拒绝隐私不影响基础对弈和本地复盘；广告 SDK 已完全移除。
- App 进入后台或离开对局/复盘页面时销毁 KataGo 子进程，避免后台耗电。
- 读取电池温度，超过 42℃ 时暂停 AI 搜索并阻止新的搜索任务。
- 仅申请网络相关权限；SGF 导入导出使用系统文件选择器，不申请存储权限。

### 设置与内购

- 设置页支持“对局结束后自动保存”，并显示当前 App 版本。
- 设置页已移除高级复盘状态、买断按钮和恢复购买内容。
- 商店内购代码仍保留，商品 ID 为 `premium_review_unlock`。
- Debug 构建通过 `FeatureFlags.unlockAllForTesting` 开放高级功能，便于测试。
- Release 构建可通过 Google Play 官方一次性内购解锁高级分析。

## 模块结构

```text
app/
  src/main/java/com/sologo/app/
    MainActivity.kt
    AppContainer.kt / SoloGoApplication.kt
    audio/             SoundPool 落子音效
    data/              DataStore 设置、SGF 文件仓库
    monetization/      Google Play Billing
    ui/components/     GoBoard、禅意组件、图表、隐私/付费弹窗
    ui/home/           对弈首页与开局设置
    ui/game/           对局页面、状态机、数子与形势判断
    ui/review/         棋谱列表、复盘详情、形势判断与 AI 候选
    ui/rules/          围棋基本规则图文页
    ui/settings/       自动保存、版本和合规入口
    ui/about/          隐私政策与开源许可
  src/main/assets/katago/
    model.txt          普通 KataGo 权重
    human-model.bin    HumanSL 权重
    gtp.cfg            KataGo 配置
    checksums.properties
  src/main/jniLibs/arm64-v8a/
    libkatago.so       arm64 CPU 可执行文件

core/
  .../model/           棋盘、棋谱、AI、形势和数子领域模型

engine/
  .../engine/          GTP、KataGo 进程、资源安装、温度监控

sgf/
  .../sgf/             SGF FF[4] 解析/写出、分析 JSON
```

## KataGo 引擎与模型

### 二进制

```text
app/src/main/jniLibs/arm64-v8a/libkatago.so
```

该文件不是普通 JNI 共享库，而是按 Android 原生库目录规则打包的 ELF 可执行文件。工程启用 `useLegacyPackaging = true`，使系统将其解压到可执行的 `nativeLibraryDir`。

Android 10+ 不允许直接执行 `filesDir` 中的二进制，因此正式包使用 `jniLibs` 路径。`KataGoInstaller` 仍保留 assets 回退路径，仅用于旧设备或调试。

### 模型文件

| 文件 | 源目录 | 大小 |
|---|---|---:|
| `model.txt` | `app/src/main/assets/katago/` | 4,973,600 字节 |
| `human-model.bin` | `app/src/main/assets/katago/` | 99,066,230 字节 |

这两个文件实际内容是 gzip 权重。首次启动后复制到应用私有目录并重命名：

```text
/data/user/0/com.sologo.app/files/katago/model.txt.gz
/data/user/0/com.sologo.app/files/katago/human-model.bin.gz
```

模型来源、SHA256、MD5 和用途记录在 [SOURCES.txt](app/src/main/assets/katago/SOURCES.txt)。资源来源和许可证：

- KataGo 引擎：[lightvector/KataGo](https://github.com/lightvector/KataGo)，MIT License。
- 普通权重：官方 `kata1-b6c96-s30394624-d4635675` 小网络。
- HumanSL 权重：官方 `b18c384nbt-humanv0`。
- 权重适用 KataGo Neural Network License，许可证文本位于 `app/src/main/assets/licenses/`。

### 分析实现

- 形势判断使用 `kata-raw-nn 0` 的 `whiteOwnership`。
- AI 下一手候选使用同一命令返回的 `policy` 数组。
- policy 和 ownership 均支持跨行读取，并显式兼容 `NAN` 占位值。
- KataGo 输出不完整时自动重新执行一次 raw 推理。
- AI 候选按 policy 概率排序，只展示当前位置的下一手候选，不生成完整整盘分析。
- 对局着法、提子、劫争、终局和分析搜索均由 KataGo 处理，App 不实现围棋规则引擎。

## 构建要求

- Android Studio 及其自带 JBR，或 JDK 17+。
- Android SDK Platform 37。
- Android Build Tools 37.0.0 或兼容版本。
- Gradle Wrapper 9.6.0。
- `minSdk = 29`，目标 ABI 为 `arm64-v8a`。

首次构建需要联网下载 Gradle 和 Maven 依赖。

### 从 GitHub clone

```powershell
git clone https://github.com/yingchenmuxi/solo-go.git
cd solo-go

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\<用户名>\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

.\gradlew.bat :app:assembleDebug
```

也可以直接用 Android Studio 打开项目，等待 Sync 生成 `local.properties` 后运行。

### 构建产物

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/debug/sologo_yyyyMMdd_HHmmss.apk
```

每次 `assembleDebug` 后会自动复制一份带北京时间戳的 APK。当前 debug APK 约 147MB，主要来自约 99MB 的 HumanSL 权重。

### 仓库体积说明

模型和二进制文件使用普通 Git 对象提交，没有使用 Git LFS。`human-model.bin` 约 99MB，低于 GitHub 100MB 单文件硬限制，但超过其 50MB 推荐值。仓库 clone 会下载完整模型；如需优化体积，可迁移至 Git LFS 或 Play Asset Delivery。

## 测试

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

当前单元测试覆盖：

- 形势判断的整点统计、贴目计算和未定点。
- 数子统计和棋盘标记。
- 黑白连续停一手判断。
- SGF/棋盘基础逻辑。

模拟器已验证：

- 9、13、19 路 raw policy 长度分别为 81、169、361。
- HumanSL 对局、悔棋、停一手、数子和形势判断。
- 复盘页形势判断与 AI 下一手候选可分别执行并同时显示。
- 黑白连续停一手弹窗和数子入口。

## 合规说明

- 本 App 定位为单机围棋教育/工具，不提供联网多人对战。
- 不包含广告 SDK，对局期间不会被广告打断。
- 基础对弈免费；高级分析通过应用商店官方内购解锁，不接受外部收款。
- 关于页展示 KataGo 源码地址、MIT License 和权重许可说明。
- 发布前仍需在应用商店后台配置商品、隐私政策公网地址和最终包体分发方式。
