# Codex 一键发版

仓库根目录的 `scripts/codex-release.sh` 用于把版本同步、当前主机可用的发行包、Release 文案和 GitHub Release 串成一条可复核的流程。脚本不会自动提交版本修改，也不会推送代码或创建 tag。

## 前置条件

- Bash、`curl`、`jq`、`perl`、`git` 和可执行的 `./gradlew`。
- Android 产物需要 Android SDK/JDK 以及项目要求的签名配置；未配置签名时，Gradle 会按仓库现有规则构建。
- iOS IPA 只能在 macOS + Xcode 上生成，脚本使用无签名 archive，再调用 `iosApp/package-ipa.sh` 进行 ad-hoc 打包。
- Desktop 只生成当前操作系统支持的安装包：macOS 为 DMG，Linux 为 DEB，Windows 为 MSI。Windows 若找到 Inno Setup，还会额外生成 `VRCM-v<version>-setup.exe`。
- `--publish` 需要 GitHub Token，并拥有仓库 `contents: write` 权限。脚本优先使用 `GH_TOKEN` 或 `GITHUB_TOKEN`；都未设置时自动读取本机 GitHub CLI 或 Git Credential Helper（macOS Keychain 等）的登录凭据。仓库默认是 `vrcm-team/VRCM`，可用 `GITHUB_REPOSITORY` 覆盖。

## 使用

先用 dry-run 检查版本和文案，不会改写源码、构建或访问 GitHub：

```bash
./scripts/codex-release.sh 1.2.0 --dry-run
```

版本修改确认并推送到远端后，在 macOS、Linux 或 Windows 执行对应平台构建，然后发布当前主机产物：

```bash
./scripts/codex-release.sh 1.2.0 --skip-version-update --publish
```

发布 GitHub 预览版时加 `--preview`；GitHub 页面会显示 Pre-release，版本号和安装包命名保持不变：

```bash
./scripts/codex-release.sh 1.2.0 --skip-version-update --preview --publish
```

也可以显式限制平台，或固定 Android/iOS 的 version code：

```bash
./scripts/codex-release.sh 1.2.0 --platforms android,desktop --code 7
```

如果已经把版本修改提交并推送到远端，重新上传或补传产物时可用 `--skip-version-update`，避免脚本再次写版本字段：

```bash
./scripts/codex-release.sh 1.2.0 --skip-version-update --publish --allow-existing
```

`--platforms all` 会展开为 `android,desktop,ios`。在非 macOS 上选择 `ios` 时会给出跳过提示；要得到完整多端 Release，应在对应平台分别构建并上传，或在 CI 中为每个平台准备构建机。

## 版本与文案

版本变化时，脚本会同步以下现有字段：

- `gradle/libs.versions.toml` 的 `app-version` 和 `app-code`；
- `installer/VRCM.iss` 的 Inno Setup 版本；
- iOS Xcode 工程的 `MARKETING_VERSION`、`CURRENT_PROJECT_VERSION`；
- `iosApp/iosApp/Info.plist` 的 `CFBundleShortVersionString`、`CFBundleVersion`。

目标版本与当前版本相同且未指定 `--code` 时，version code 保持不变；目标版本变化时自动递增 1。Release 正文默认参考 GitHub 上 `1.1.1` Release 的实际格式生成：中文“新增 / 优化 / 修复”、分隔线、英文 “New Features / Optimizations / Bug Fixes”，最后附上 `详情 / Details` 文档链接。最近 tag 之后的功能与修复提交会整理到中文段落；需要人工补充更完整的中英文产品叙述时，使用 `--notes-file path/to/release.md` 覆盖自动文案。

## GitHub API 行为

新 Release 会先以 draft 创建，所有资产上传成功后再发布，避免出现没有安装包的公开 Release。默认发现相同 `<version>` tag 的已有 Release 会停止；确认要重发时加 `--allow-existing`，脚本会更新文案、删除同名旧资产并重新上传。脚本只操作 Release API，不会自动提交当前工作区的版本修改。

默认 `prerelease` 为 `false`。传入 `--preview` 或 `--prerelease` 会以 GitHub 预览版发布；更新已有 Release 时，如果没有显式指定该选项，脚本会保留已有 Release 的预览/正式状态，避免补传资产时意外改变发布类型。

发布前请确保本机已通过 GitHub CLI 登录，或已为 `https://github.com` 保存 Git 凭据：

```bash
gh auth login
gh auth status
```

如果存在多个凭据来源，脚本按 `GH_TOKEN`、`GITHUB_TOKEN`、`gh auth token`、Git Credential Helper 的顺序选择凭据。

GitHub API 创建的 tag 默认指向远端 `main`，并沿用仓库历史的无 `v` tag 格式（例如 `1.1.1`）。因此正式发布前应先人工检查并提交、推送版本修改；脚本不会替代这一步，也不会把未提交的本地源码伪装成已发布源码。

构建产物和生成文案位于 `composeApp/build/release/<version>/`，属于 Gradle 忽略的构建目录，不应提交到仓库。
