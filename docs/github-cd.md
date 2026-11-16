# GitHub 自动发布

`.github/workflows/release.yml` 沿用仓库历史版本的 tag 和附件命名，参考 VRCMC 的签名、DeepSeek 公告和草稿发布流程。它替代了会临时生成调试签名的旧 Android Release 工作流；普通 PR 的 Android/iOS CI 保持独立。

## 发布入口

版本 tag 使用 `1.2.0`，不加 `v`。tag 必须指向已经进入 `main` 的提交，且以下版本声明必须一致：

- `gradle/libs.versions.toml`：`app-version`、`app-code`。
- `AppConst.kt`：`APP_VERSION`。
- `installer/VRCM.iss`：`AppVersion`。
- Xcode 工程的所有 `MARKETING_VERSION`、`CURRENT_PROJECT_VERSION`。
- `Info.plist` 的两个版本字段继续引用 Xcode 设置。

tag 中还必须有非空的 `docs/releases/<版本>.md`，供公告末尾的详情链接使用。可先用既有 `scripts/codex-release.sh` 准备版本和三语详解，再审核并提交；CD 不自动生成或提交文档源码。

先在 `main` 提交并推送版本调整，再正常创建、推送 tag：

```bash
git tag 1.2.0
git push origin 1.2.0
```

推送 tag 会自动发布。也可在 Actions → Release 手动输入已有 tag：

- `publish=false`：只验证、打包和生成公告，下载工作流 Artifacts 检查结果，不创建或更新 Release。
- `platform=android`、`windows` 或 `apple`：只校验版本并构建指定平台，跳过其他平台和发布任务；产物保存在对应的 `release-packages-*` Artifact。默认 `all` 保持完整发布流程。
- `build_run`：可选的已完成 Release 运行 ID，用于只恢复发布，必须同时提供 `tag` 并选择 `platform=all`。仅接受源码 SHA 与 tag 一致、prepare 和四个平台构建均成功的运行；原 publish 可以失败。跳过所有构建，复用该运行尚未过期的安装包，并使用当前工作流中的发布脚本重新校验摘要和发布。
- `prerelease=true`：新建为预览版；更新已有版本时保留其预览/正式状态。
- `allow_existing=true`：明确允许替换已公开版本的同名附件和公告。默认禁止覆盖历史正式版本。
- `model`、`effort`：可选的 DeepSeek 模型和思考强度，默认为仓库 Variables `RELEASE_NOTES_MODEL`、`RELEASE_NOTES_EFFORT`，未设置时使用 `deepseek-flash`、`high`。

该流程面向包含现有安装器和 IPA 打包脚本的新版本 tag。引入这些文件之前的历史 tag 不能保证原样重建；不要为重建旧版本强制改写 tag。

需要修复已有 tag 的构建环境并单独重建某个平台时，将工作流运行分支选为含修复的 `main`，`tag` 填写原版本，选择对应 `platform` 并设置 `publish=false`。应用源码仍从原 tag 检出；重跑旧任务会继续使用旧工作流，不能应用 `main` 中的环境修复。独立构建不会修改原运行的结果或自动续接其发布任务。

如果四个平台已经成功、仅最后发布失败，先将发布修复推送到 `main`，再从 `main` 手动运行，指定原 `tag`、`build_run`、`platform=all` 和 `publish=true`。恢复流程下载当前 prepare 的工具和原运行的安装包，不必重新打包，也不改写 tag。若要修订公告，可在 `main` 提交 `.github/release-notes/<tag>.md`；恢复时该文件优先于 tag 中的公告，仍需通过相同格式校验。

需要先验证源码修复、再准备新版本时，选择运行分支 `main` 并留空 `tag`。此模式只接受当前 `origin/main` 提交，仍校验各平台版本一致，并执行相同的正式打包任务；无论 `publish` 取值如何都不会生成公告或发布 Release。构建全部通过后再调整版本、提交和创建新 tag。Android 和 Desktop 的测试报告在失败时也会上传为 Artifact。

## 正式 Android 签名

仓库 Actions Secrets 必须包含：

| Secret | 用途 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 正式 `vrcm-release.jks` 的 Base64 |
| `ANDROID_STORE_PASSWORD` | 正式签名库密码 |
| `ANDROID_KEY_ALIAS` | 正式私钥别名 |
| `ANDROID_KEY_PASSWORD` | 正式私钥密码 |
| `DEEPSEEK_API_KEY` | 生成双语 Release 公告 |

签名文件只写入 runner 临时目录，`local.properties` 只在 Android job 中生成，结束时清理。缺少任一签名 Secret 会直接失败，不会生成替代签名。

工作流同时检查签名库证书和实际 APK 的证书。预期 SHA-256 指纹与历史 `1.1.4` APK 一致：

```text
6f7db55d319ae6487ef69c82c676e92bbf1e91c170afa368fbe9e2cb8d3ff12b
```

此指纹是公开证书信息。若未来正式迁移签名，必须审查兼容性并同步更新工作流中的指纹；不在仓库提交 JKS、签名密码或 API Key。

## 构建和产物

四组 runner 共同生成历史 Release 中使用的四类安装包：

| Runner | 验证与打包 | 附件 |
| --- | --- | --- |
| Ubuntu | GitHub 渠道 Android 单元测试、正式签名 APK、APK 证书核验 | `VRCM-v<版本>.apk` |
| Windows | Desktop 测试、release app-image、既有 Inno Setup 安装器 | `VRCM-v<版本>-setup.exe` |
| macOS ARM | Compose release DMG | `VRCM-v<版本>.dmg` |
| macOS Intel（14 GB 内存） | Xcode 无签名 archive、既有 ad-hoc IPA 脚本 | `VRCM-v<版本>.ipa` |

iOS Release 的 Kotlin/Native 链接在 Gradle 进程内执行，使用 8 GB 堆，因此与仅有 7 GB 内存的 ARM runner 分开。直接执行 Xcode archive，由其嵌入任务构建 Release Framework，避免独立预构建后再次链接。`platform=apple` 同时构建 DMG 和 IPA，分别上传 `release-packages-macos`、`release-packages-ios`。Intel 只是 iOS 构建宿主，`iphoneos` 产物仍面向 iPhone ARM64；DMG 继续使用 ARM runner。

Android 仅发布 `github` flavor，不上传 Google Play AAB。macOS 安装包遵循 runner 的 CPU 架构；iOS IPA 沿用项目的侧载打包方式，不是 App Store 分发或开发者账号签名。Windows EXE 和 DMG 沿用现有打包配置，不新增代码签名或公证凭据。

所有平台从同一个已校验的 tag commit 构建。四份安装包必须齐全且非空，才会生成公告和 `SHA256SUMS`。发布时先创建草稿，按 Release ID 上传、查询附件，并校验 GitHub 返回的大小和 SHA-256 摘要，全部通过才公开。tag 查询不返回草稿时，通过分页 Release 列表识别已有草稿，避免重复创建。更新草稿公告和公开草稿时显式保留版本标签，避免变为内部的 `untagged` 标识。恢复时复用大小和摘要均一致的已上传附件，仅替换不匹配的文件。失败的草稿可重跑恢复，不会暴露缺少附件的新 Release。

安装器升级标识、应用 ID、Manifest 和既有 Gradle 打包配置不在此流程中修改。本地 `scripts/codex-release.sh` 仍可按原方式使用。

## Release 公告

优先使用 tag 中的 `.github/release-notes/<tag>.md` 手写公告。没有手写公告时，使用前一个已发布、可追溯到本次源码的正式版本、真实提交差异和上一版公告，调用 DeepSeek 生成简体中文与英文的“新增 / 优化 / 修复”段落。

正文固定沿用历史 `1.1.3`、`1.1.4` 的版式，手写公告也必须通过同一格式校验：先是 `## 新增`、`## 优化`、`## 修复`，接着一行 `****************`，再是 `## New Features`、`## Optimizations`、`## Bug Fixes`，末尾为 `详情 / Details: <https://github.com/vrcm-team/VRCM/tree/main/docs/releases/<版本>.md>`。分类内使用单行 `- ` 列表，中英文条目逐项对应。

公告不增加下载、校验和或完整变更区块；安装包和 `SHA256SUMS` 只作为 Release 附件。详情文档缺失或为空会在构建前失败，避免发布失效链接。CD 不依赖本地 Codex 登录。

DeepSeek 未配置、请求失败、输出被截断或结构校验不通过时，工作流停止发布，提示重试或在 tag 中提供符合格式的手写公告；不会回退为格式不同的 GitHub 默认变更记录。生成模式记录在 Actions Summary，发布前会再次校验最终正文格式。

## 验证

发布编排的回归检查只使用 Node.js 内置测试模块，无需额外 npm 依赖：

```bash
node --test scripts/tests/release-ci.test.mjs
git diff --check
```

测试覆盖 tag 与工作区内容不一致、版本声明漂移、非 main 来源、缺少版本详解或安装包、上传摘要不一致、草稿 tag 查询返回 404、分页草稿恢复、错误构建运行复用、覆盖已发布版本、手写公告优先及恢复时覆盖公告、禁止额外公告区块，以及 AI 输出格式、翻译不完整和输出被截断时停止发布等边界。真实 Android、Windows、macOS 和 iOS 构建分别由对应 runner 验证。
