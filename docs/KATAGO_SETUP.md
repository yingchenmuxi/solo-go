# KataGo 资源接入

当前工程已经内置一套可运行的 arm64 CPU 资源；`minSdk` 为 29，与该 Android 引擎构建工具链一致。来源和 SHA256 见 `app/src/main/assets/katago/SOURCES.txt`。

## 推荐目录

```
app/src/main/jniLibs/arm64-v8a/libkatago.so
app/src/main/assets/katago/model.txt   # gzip 文本权重，避免 AAPT 自动解压/改扩展名
app/src/main/assets/katago/human-model.bin # gzip HumanSL 权重，约 99MB
app/src/main/assets/katago/gtp.cfg
app/src/main/assets/katago/checksums.properties
```

## 为什么不是 assets/katago/katago

Android 10+ 对应用可写目录执行 ELF 有 W^X 限制。`filesDir` 是可写目录，复制过去再 chmod 并不能保证 `execve` 成功。`nativeLibraryDir` 是系统管理的只读可执行目录，配合 `useLegacyPackaging = true` 才能稳定运行。

## checksums.properties

```
katago.md5=...
model.md5=...
gtp-cfg.md5=...
human-model.md5=...
```

本工程已经生成该文件；安装器会严格校验二进制、权重和 cfg。替换模型后必须同步更新 `model.md5`。

## gtp.cfg 建议

至少确认：

- `numSearchThreads` 不要高于设备实际能力；
- `maxVisits` 有合理的默认值；当前 APK 运行时会由 APP 覆盖；
- 不需要 GPU/OpenCL 的 CPU 版本；
- `logDir` 指向应用可写目录，或关闭文件日志，避免权限问题；
- `reportAnalysisWinratesAs` 与 UI 的黑棋视角约定一致。

当前随包的 `gtp.cfg` 使用 `numSearchThreads=4`、`maxTime=2.0`、`maxVisits=120`；
APP 端默认 5K profile、15 visits，单步最多约 2 秒，并保留 42℃ 高温暂停保护。

`KataGoEngine` 会通过 `-human-model` 加载官方 HumanSL 权重，并用 `kata-set-param`
设置 `humanSLProfile`（10K/5K/1K/1段/3段分别对应 `rank_10k` 等）及人类着法参数。
没有 HumanSL 权重时仍可退回普通 KataGo。普通模型的备用参数包括：
`chosenMoveTemperatureEarly`、`chosenMoveTemperature`、`chosenMoveTemperatureHalflife`、
`chosenMoveTemperatureOnlyBelowProb`、`rootNumSymmetriesToSample` 和 `useLcbForSelection`。

## 崩溃排查

1. 先看 Logcat 中 `KataGoInstaller`、`GtpClient` 的日志。
2. 手动执行 `adb shell run-as com.sologo.app ls -l files/katago` 检查权重是否复制完成。
3. 用 `adb shell run-as com.sologo.app cat files/katago/gtp.cfg` 确认配置路径。
4. 如果错误是 `EACCES`/`Permission denied`，检查 `libkatago.so` 是否真的在 APK 的 `lib/arm64-v8a/` 中。
5. 如果是 `Illegal instruction`，二进制 CPU 指令集与真机不匹配，需要换 Eigen/通用构建。
