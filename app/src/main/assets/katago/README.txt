KataGo 资源放置说明
====================

正式包（推荐，Android 10+ 可执行）：
  app/src/main/jniLibs/arm64-v8a/libkatago.so

历史兼容方式（可能被 Android 10+ W^X 策略拒绝）：
  app/src/main/assets/katago/katago

权重与配置（本工程已经内置）：
  app/src/main/assets/katago/model.txt（gzip 内容，安装后改名为 model.txt.gz）
  app/src/main/assets/katago/human-model.bin（gzip 的 HumanSL 权重，安装后改名为 human-model.bin.gz）
  app/src/main/assets/katago/gtp.cfg

可选完整性校验：
  app/src/main/assets/katago/checksums.properties
  内容示例：
    katago.md5=<libkatago.so 或 katago 的 MD5>
    model.md5=<权重文件 MD5>
    gtp-cfg.md5=<gtp.cfg MD5>
    human-model.md5=<HumanSL 权重 MD5>

APP 首次启动会流式复制权重到 filesDir，避免一次性读入几十/上百 MB 造成 OOM。
请确认权重文件许可证允许随 APP 再分发。
