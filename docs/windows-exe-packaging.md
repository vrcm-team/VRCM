# Windows EXE 安装程序打包

本文说明如何为 VRCM 构建 Windows Desktop release app-image，并使用 Inno Setup 生成多语言 EXE 安装程序。

## 产物与组成

打包分为两步：

1. Compose Desktop 使用 `jpackage` 生成包含 VRCM、依赖 JAR 和裁剪后 Java Runtime 的 app-image。
2. Inno Setup 将完整 app-image 压缩为支持多语言和快捷方式选项的安装程序。

最终产物位于：

```text
composeApp/build/installer/VRCM-v<version>-setup.exe
```

安装程序默认安装到：

```text
%LOCALAPPDATA%\Programs\VRCM
```

这是当前用户级安装，不需要管理员权限。

## 前置条件

- Windows x64
- 项目要求的 JDK，`JAVA_HOME` 已正确配置
- 可以正常执行仓库中的 `gradlew.bat`
- Inno Setup 编译器存在于：

```text
\.gradle\tools\innosetup\ISCC.exe
```

打包配置涉及以下文件：

```text
gradle/libs.versions.toml
composeApp/build.gradle.kts
composeApp/src/desktopMain/resources/VRCM.ico
installer/VRCM.iss
installer/ChineseSimplified.isl
installer/ChineseTraditional.isl
installer/Japanese.isl
```

## 发布前更新版本

应用版本以 `gradle/libs.versions.toml` 为准：

```toml
[versions]
app-version = "1.1.1"
```

Inno Setup 预处理器不能直接读取 Version Catalog，因此还必须同步修改 `installer/VRCM.iss`：

```iss
#define AppVersion "1.1.1"
```

两个版本不一致时，应用 EXE 的版本、安装器文件名和 Windows 卸载信息会不一致。打包前必须核对。

`AppId`、Compose Desktop 的 `upgradeUuid` 和安装目录属于升级契约，不要在普通版本发布时修改：

```text
AEBFB803-0655-4C7E-8C79-F29E14618397
```

修改该 UUID 会让 Windows 将新版本识别为另一个应用，导致覆盖安装和卸载信息失效。

## 构建 release app-image

在 VRCM 仓库根目录执行：

```powershell
.\gradlew.bat :composeApp:createReleaseDistributable --rerun-tasks --console=plain --no-daemon
```

成功后应生成：

```text
composeApp/build/compose/binaries/main-release/app/VRCM/VRCM.exe
composeApp/build/compose/binaries/main-release/app/VRCM/app/
composeApp/build/compose/binaries/main-release/app/VRCM/runtime/
```

`installer/VRCM.iss` 的 `SourceDir` 必须指向这个完整的 `VRCM` 目录，不能只复制 `VRCM.exe`。应用运行依赖 `app/` 中的 JAR 和 `runtime/` 中的 Java Runtime。

### Desktop ProGuard 约束

当前 Desktop release 必须关闭 ProGuard：

```kotlin
buildTypes.release.proguard {
    isEnabled.set(false)
}
```

VRCM Desktop 使用了不能被当前 ProGuard 配置安全处理的运行时机制，包括：

- Room 通过约定名称加载 `VrcmDatabase_Impl`
- Bundled SQLite 的 JNI 方法与原生库
- Coil 的 `ServiceLoader` 实现
- Okio 的 JVM 协变返回桥接
- JNA 的 Windows 原生接口

启用 ProGuard 后，构建任务可能仍然成功，但安装后的程序会在启动或运行时出现 `Bad return type`、Koin 实例创建失败、Room 实现缺失、SQLite `NoSuchMethodError` 或 Coil provider 缺失。不要仅为减小安装包而重新启用。Inno Setup 仍会使用 LZMA2 压缩最终安装包。

## 生成 EXE 安装程序

app-image 构建成功后，在仓库根目录执行：

```powershell
& '.gradle\tools\innosetup\ISCC.exe' 'installer\VRCM.iss'
```

编译成功时，输出末尾会显示最终路径：

```text
composeApp/build/installer/VRCM-v<version>-setup.exe
```

安装器当前提供：

- 英文
- 简体中文
- 繁体中文
- 日文
- 可选桌面快捷方式
- 开始菜单快捷方式
- 安装完成后可选启动 VRCM
- VRCM 安装器、应用和卸载项图标

覆盖安装时，`[InstallDelete]` 会在复制前清理旧的 `{app}\app` 和 `{app}\runtime`。这是必要行为，因为未压缩 JAR 的文件名可能包含内容哈希，普通覆盖复制不会删除旧版本 JAR。

该清理仅针对安装目录内的程序文件。VRCM 的账户、设置和数据库位于用户数据目录，不应加入 `[InstallDelete]`。

## 验证

### 自动化检查

运行 Desktop 测试：

```powershell
.\gradlew.bat :composeApp:desktopTest --console=plain --no-daemon
```

检查工作区空白错误：

```powershell
git diff --check
```

计算安装器 SHA-256：

```powershell
Get-FileHash 'composeApp\build\installer\VRCM-v<version>-setup.exe' -Algorithm SHA256
```

### 安装后冒烟验证

安装或覆盖安装后，从以下位置启动：

```text
%LOCALAPPDATA%\Programs\VRCM\VRCM.exe
```

至少确认：

- VRCM 主窗口能够打开并持续响应
- 没有 JVM `Error` 对话框
- 使用已有账户时好友页面可以加载
- Room 数据库可以读取和写入
- 网络与缓存图片可以显示
- 桌面快捷方式选项符合安装时的选择
- 应用图标、任务栏图标和卸载项图标均为 VRCM Logo

只检查 Gradle 构建成功或进程仍在运行是不充分的。release 冒烟验证必须覆盖 Koin 初始化、Room/SQLite 和 Coil 图片加载，因为这些路径曾在压缩后的发行产物中发生运行时错误。

## 清理与重新打包

一般无需手工清理整个 `build/`。需要强制排除增量缓存时，使用 `--rerun-tasks` 重新生成 app-image，然后再次运行 `ISCC.exe`。

如果 Gradle 报告无法删除 `composeApp/build/classes`，通常是先前被中断的 Gradle/Kotlin 进程仍占用生成文件。先执行：

```powershell
.\gradlew.bat --stop
```

确认没有仍在使用本仓库构建目录的 Java 进程后，再重新执行构建命令。不要删除源码或用户数据来规避文件锁。

## 发布注意事项

- 当前安装器未进行代码签名，Windows SmartScreen 可能显示“未知发布者”。
- 不要提交 `composeApp/build/` 中的 app-image 或安装器产物。
- 修改安装语言时，应同步检查四种语言和 `[CustomMessages]`。
- 修改 Logo 时，需要重新生成包含 16、24、32、48、64、128、256 像素图层的 `VRCM.ico`，然后全量重新打包。
- Windows EXE 只能在 Windows 上完整验证；macOS DMG 应在 macOS 上单独执行并验证对应发行任务。
