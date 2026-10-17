把 KataGo 的 arm64 Android 可执行文件放到这里，并命名为：

  libkatago.so

它不是普通共享库，而是按 Android 原生库目录规则打包的 ELF 可执行文件。
这样系统允许从 nativeLibraryDir 执行，避开 Android 10+ 对 filesDir 的 W^X 限制。

同时请在 app/build.gradle.kts 中保留：
  packaging { jniLibs { useLegacyPackaging = true } }

否则部分设备上文件不会解压到 nativeLibraryDir。
