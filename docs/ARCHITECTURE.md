# 架构说明

## 分层

```
Compose Screen
    ↓ StateFlow / 事件回调
ViewModel
    ↓ suspend 方法
KataGoEngine (engine)
    ↓ GTP 文本协议
KataGo 子进程
```

SGF 文件和 JSON 分析由 `SgfRepository` + `AnalysisStore` 处理，不经过 UI 直接做字符串拼接。

## 关键状态

- `GameUiState`：棋盘显示、轮次、手数、候选、胜率、目数、温度、错误。
- `ReviewUiState`：棋谱、局面列表、当前手、复盘分析、进度、温度。
- `AppSettings`：隐私决定、买断、路数、贴目、AI 等级、自动分析、执子颜色。

## 线程

- 所有 GTP 读写、资源复制、SGF 文件读写都在 `Dispatchers.IO`。
- UI 只接收 StateFlow；温度广播回调只更新 StateFlow，不直接操作进程。
- `GtpClient` 用单独的 stdout 读取协程 + Channel，使命令可以超时，而不是卡在阻塞 readLine。

## 进程生命周期

- `SoloGoApplication` 注册 `ProcessLifecycleOwner`：`onStop` 立即 `shutdownNow()`。
- `GameViewModel`/`ReviewViewModel` 的 `onCleared` 再兜底一次。
- 回前台时 `GameViewModel.onForeground()` 按已落子记录重放，恢复 KataGo 棋盘。

## 为什么棋盘缓存不算“实现围棋规则”

`BoardPosition` 只把引擎已接受的落子映射成画面，并处理提子显示。它不负责判定劫争、禁着、终局或胜负；任何真正的合法性判断仍由 KataGo 的 `play`/`genmove` 返回结果决定。
