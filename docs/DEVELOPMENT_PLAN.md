# SoloGo 开发分步计划

## 阶段 0：真机资源验证（先做，不要先做 UI）

- 当前工程已放入 `libkatago.so`、`model.txt`（gzip 文本权重）、`gtp.cfg`；在 arm64 真机验证加载。
- 启动 APP 后确认 `KataGoInstaller` 找到 `nativeLibraryDir/libkatago.so`。
- 依次验证 GTP：`name`、`boardsize 19`、`komi 7.5`、`clear_board`、`play B D4`、`genmove W`、`undo`。
- 如果 `ProcessBuilder` 报 `EACCES`，不要再往 filesDir 方向修；检查 `jniLibs` 和 `useLegacyPackaging`。

验收：屏幕能显示 KataGo 版本，连续 50 手 AI 对局无崩溃；切后台 5 秒后 `dumpsys`/进程列表里没有 KataGo。

## 阶段 1：引擎层稳定化

- 给 `GtpClient` 加命令级 watchdog、进程退出码记录和 stderr 环形日志。
- 在 `KataGoEngine` 中加入崩溃后的指数退避重启（当前是下一次命令时按需重启）。
- 为 `kata-analyze` 做不同 KataGo 版本的兼容测试：`interval` 单位、`stop` 行为、`ownership` 开关。
- 把 `maxVisits` 从“全局设置”提升为“对局/分析会话参数”，避免对局中切换设置时状态漂移。

验收：超时、进程被系统杀死、权重损坏三种情况下，UI 都能给出可恢复的错误，不出现无限 loading。

## 阶段 2：SGF 与棋谱库

- 增加多分支浏览 UI：当前解析器保留树，但复盘页面只走主分支。
- 支持 `AB/AW/PL/HA` 摆子和让子棋的完整回放。
- 增加导入编码检测（UTF-8/GBK）和超大 SGF 的大小限制。
- 对导入文件做 schema/属性白名单校验，防止异常 SGF 触发解析器资源耗尽。

验收：与 Sabaki、Lizzie 等外部软件互相导入导出 20 盘棋，变化、注释、结果字段不丢。

## 阶段 3：Compose 棋盘与对局体验

- 增加棋盘缩放/平移、落子确认、音效/震动开关、坐标显隐。
- 用 `BoardPosition` 只做显示；禁止在这里写胜负/劫争判定。
- 增加“AI 思考中”取消按钮，把 `stopSearch` 接到 UI。
- 处理屏幕旋转、进程重建、返回栈恢复；必要时把当前对局写入草稿文件。

验收：9/13/19 路在 5–7 英寸屏幕上触摸误差可接受；连续悔棋、pass、认输不产生引擎/棋盘状态错位。

## 阶段 4：高级复盘

- 目前整盘分析是“每个局面一次短分析 + 下一手后分析”的近似亏损；后续可改为前后各一次高 visits 分析。
- 给每手保存 PV、候选点、ownership、分析耗时和引擎版本。
- 增加分析取消、断点续跑、失败重试和缓存命中（按 SGF hash + visits）。
- 图表增加缩放、点击跳到对应手、恶手过滤。

验收：一盘 200 手棋的分析可取消/恢复，JSON 与 SGF 同名成对存在，外部软件只读 SGF 不受影响。

## 阶段 5：内购、隐私

- 当前版本已移除广告 SDK；如未来重新接入广告，再补充相应的同意与隐私流程。
- 配置 `INTERNET` 之外的权限要逐项证明必要性。
- 在 Play Console 创建 `premium_review_unlock`，验证购买、待处理、确认、退款、恢复购买。
- 拒绝隐私时隐藏商店入口；不要把“拒绝”实现成反复弹窗。
- 关于页、隐私政策页、商店 listing 三处文案保持一致；准备可公开访问的隐私政策 URL。

验收：新装、拒绝、同意、断网、商店不可用、退款后重进六种路径都能正确恢复状态。

## 阶段 6：发布前稳定性

- 低端 arm64 机、Android 10/12/14/16 真机测试。
- 电池温度 42℃ 阈值用假温度注入测试；确认暂停后不再发起 genmove/analyze。
- 后台/锁屏/来电/分屏/旋转切换，确认 KataGo 进程被结束且回前台可恢复。
- 用 `adb shell dumpsys meminfo` 观察 WebView/广告/Compose 图片是否有泄漏。
- 检查 APK 体积、权重压缩、Play Asset Delivery、MIT 声明和第三方 SDK 披露。
