# CLAUDE.md

必须使用中文回复所有问题。

## 项目概述

VRCM 是使用 Kotlin Multiplatform 与 Compose Multiplatform 开发的 VRChat 好友及内容管理应用，支持 Android、iOS 与 Desktop（JVM）。

- Kotlin：2.2.20
- Compose Multiplatform：1.10.3
- 包名：`io.github.vrcmteam.vrcm`
- Android：minSdk 24、targetSdk 35、compileSdk 36
- 主要技术：Navigation 3、Lifecycle ViewModel、Material 3 Adaptive、Koin、Ktor、Multiplatform Settings、Coil、Kotlin Coroutines

具体版本以 `gradle/libs.versions.toml` 为准。

## 项目结构

| 路径 | 职责 |
|------|------|
| `composeApp/src/commonMain` | 跨平台业务、Compose UI、网络、服务与存储 |
| `composeApp/src/commonTest` | 跨平台单元测试 |
| `composeApp/src/androidMain` | Android 平台实现 |
| `composeApp/src/iosMain` | iOS 平台实现 |
| `composeApp/src/desktopMain` | Desktop 平台实现 |
| `composeApp/src/desktopTest` | Desktop 集成测试与 Compose UI 测试 |
| `iosApp/` | iOS 原生应用壳与 Xcode 工程 |
| `gradle/libs.versions.toml` | 依赖与应用版本 |

`commonMain/kotlin` 内的核心边界：

- `presentation/`：Navigation 3 路由、Lifecycle ViewModel、Compose UI、动画、导航、主题与设置
- `network/`：Ktor API、WebSocket、网络模型与协议支持
- `service/`：认证、好友、收藏、版本、上传等业务编排
- `storage/`：账户、缓存、设置与持久化
- `di/`：Koin 依赖装配
- `core/`：算法、扩展与共享基础能力

## 硬性约束

1. 修改前先阅读相关实现、调用方和现有测试，优先沿用现有架构与组件。
2. 工作区可能包含用户未提交的修改，禁止覆盖、回滚、格式化或提交无关内容。
3. 未经明确要求，不要提交、推送、创建 PR 或改写提交历史。
4. 禁止提交临时 plan、spec、设计草案和分析记录；用户明确要求的正式文档除外。
5. 禁止手工修改 `build/`、生成源码、IDE 状态目录和 `.xcworkspace/` 等生成物。
6. 非必要不要修改 Manifest、签名、应用 ID、权限或平台发布配置。
7. 依赖版本统一放在 `gradle/libs.versions.toml`，构建脚本通过 `libs.*` 引用。
8. 跨平台实现优先写在 `commonMain`；平台差异使用对应源集或现有 `expect`/`actual` 模式。
9. 不要在无关任务中重命名已有的 `compoments` 包、目录或做大范围格式化。
10. 从问题源头修正，禁止用魔法 offset、固定 delay、吞异常或重复状态补丁掩盖根因。

## 架构与状态

- 导航与页面生命周期遵循现有 Navigation 3 `AppRoute`、`AppNavigator`、`NavDisplay` 和 Lifecycle `ViewModel` 模式。
- 依赖通过 Koin 注入，不在 Composable 中直接构造网络、存储或服务对象。
- 状态保持单一数据源，优先使用现有 `StateFlow`、`SharedFlow` 或 Compose state 模式。
- 可复用业务流程放在既有 ViewModel、Service、Storage 或 Network 边界中，不在 UI 中复制。
- 网络调用使用现有 Ktor client、API 和认证重试封装。
- 协程必须绑定明确生命周期与 Dispatcher，不得在测试或页面退出后遗留任务或全局 Dispatcher 状态。

## Compose 与资源

- 使用 `MaterialTheme` 和现有主题定义，禁止无理由硬编码颜色、字体或形状。
- 图标优先使用 `AppIcons`，不要创建重复图标实现。
- 同级组件间距由共同父容器管理，子组件只负责自身内边距，避免同方向重复 padding。
- 文本与页面内容应适配系统字体和窗口宽度，不要用固定高度掩盖溢出。
- 稳定工具栏按钮、图片比例等控件可以有明确尺寸，但必须承担清晰布局契约且不造成遮挡。
- 修改 Modifier 时核对顺序，特别是 `padding`、`size`、`clip`、`background`、`alpha`、`clickable` 与 `graphicsLayer`。
- 用户可见文案必须进入现有 `LocaleStrings` 体系，同步核对英文、日文、简体中文和繁体中文结构。
- Compose 资源放在 `composeApp/src/commonMain/composeResources/`，沿用现有资源访问方式。

## 测试质量规范

是否添加测试必须根据实际回归风险判断，禁止仅因为代码发生了变化、容易测试或需要提高覆盖率就添加测试。

**禁止添加只是重复实现细节的低价值测试**：

- 直接复述算术、插值、换算或框架调用公式，包括分别断言公式的起点、中点和终点
- 测试颜色、透明度、尺寸、间距等纯视觉数值计算
- 测试常量、简单 getter、纯 DTO、简单数据类相等性、字段映射、对象构造或 `copy()`
- 把依赖全部 mock 后只断言方法调用次数，没有覆盖业务分支或真实结果
- 使用 `assertTrue(true)`、`assertNotNull(obj)`、`assertEquals(x, x)` 等永真断言
- 未明确要求 UI 测试时，测试 Compose Modifier 顺序、样式数值或框架默认行为
- 测试不构成独立行为契约的内部实现细节

**只有至少满足以下一项时，才应添加新测试**：

1. 能复现真实缺陷，并且在修复前已确认测试失败
2. 能保护非简单的业务分支、状态转换、不变量或错误处理
3. 能保护被多个组件依赖的公共行为契约
4. 能覆盖编译仍会通过、但实际行为可能发生回归的集成场景

测试重点是状态转换、缓存一致性、认证与错误路径、并发、序列化、导航契约、平台文件系统以及边界输入。

**新增测试前必须回答**：这条测试能够捕获哪一种现实且合理的错误实现？如果唯一理由只是“这段实现发生了变化”，则不要添加测试。

禁止仅为了方便测试而提取没有业务意义的辅助函数、放宽可见性、增加额外抽象或添加只为提高覆盖率而存在的测试。

对于只涉及视觉样式的 UI 修改，除非用户明确要求，否则不要添加 Compose UI 测试。运行现有测试和编译检查，并说明尚未自动验证的视觉风险。即使没有添加新测试，也必须运行相关现有测试。

## 常用命令

```bash
./gradlew :composeApp:compileKotlinDesktop        # 编译共享/Desktop 代码
./gradlew :composeApp:desktopTest                 # Desktop 测试套件
./gradlew :composeApp:allTests                    # KMP 聚合测试
./gradlew :composeApp:check                       # 模块完整检查
./gradlew :composeApp:assembleDebug               # Android Debug APK
./gradlew :composeApp:installDebug                # 安装 Android Debug
./gradlew :composeApp:createReleaseDistributable  # Desktop 发布包
./gradlew :composeApp:linkDebugFrameworkIosArm64  # iOS 调试 Framework
```

提交前根据改动风险运行相关命令，并执行 `git diff --check` 与 `git status`，确认没有空白错误或无关文件。

## Git 约定

- 提交格式沿用 `type(scope): 描述`。
- 只有用户明确要求时才提交，并且只暂存授权范围内的文件。
- 不自动推送、创建 PR、合并或删除分支。
- 交付时说明修改范围、实际验证和未验证风险。

更详细的仓库约束见 `AGENTS.md`。
