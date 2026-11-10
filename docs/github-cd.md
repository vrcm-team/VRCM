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
- `prerelease=true`：新建为预览版；更新已有版本时保留其预览/正式状态。
- `allow_existing=true`：明确允许替换已公开版本的同名附件和公告。默认禁止覆盖历史正式版本。
- `model`、`effort`：可选的 DeepSeek 模型和思考强度，默认为仓库 Variables `RELEASE_NOTES_MODEL`、`RELEASE_NOTES_EFFORT`，未设置时使用 `deepseek-flash`、`high`。

该流程面向包含现有安装器和 IPA 打包脚本的新版本 tag。引入这些文件之前的历史 tag 不能保证原样重建；不要为重建旧版本强制改写 tag。

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

三组 runner 共同生成历史 Release 中使用的四类安装包：

| Runner | 验证与打包 | 附件 |
| --- | --- | --- |
| Ubuntu | GitHub 渠道 Android 单元测试、正式签名 APK、APK 证书核验 | `VRCM-v<版本>.apk` |
| Windows | Desktop 测试、release app-image、既有 Inno Setup 安装器 | `VRCM-v<版本>-setup.exe` |
| macOS | Compose release DMG、Xcode 无签名 archive、既有 ad-hoc IPA 脚本 | `VRCM-v<版本>.dmg`、`VRCM-v<版本>.ipa` |

Android 仅发布 `github` flavor，不上传 Google Play AAB。macOS 安装包遵循 runner 的 CPU 架构；iOS IPA 沿用项目的侧载打包方式，不是 App Store 分发或开发者账号签名。Windows EXE 和 DMG 沿用现有打包配置，不新增代码签名或公证凭据。

所有平台从同一个已校验的 tag commit 构建。四份安装包必须齐全且非空，才会生成公告和 `SHA256SUMS`。发布时先创建草稿、上传附件，再校验 GitHub 返回的大小和 SHA-256 摘要，全部通过才公开。失败的草稿可重跑恢复，不会暴露缺少附件的新 Release。

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

测试覆盖 tag 与工作区内容不一致、版本声明漂移、非 main 来源、缺少版本详解或安装包、上传摘要不一致、失败草稿恢复、覆盖已发布版本、手写公告优先、禁止额外公告区块，以及 AI 输出格式、翻译不完整和输出被截断时停止发布等边界。真实 Android、Windows、macOS 和 iOS 构建分别由对应 runner 验证。
