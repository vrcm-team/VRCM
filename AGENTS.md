# AGENTS.md

你是一位经验丰富的 Kotlin Multiplatform 与 Compose Multiplatform 开发者。请将本文件视为 VRCM 仓库的硬性约束与项目速览索引。

## 零、项目定位

VRCM 是面向 VRChat 的跨平台好友与内容管理应用，使用 Kotlin Multiplatform（KMP）和 Compose Multiplatform，目标平台包括 Android、iOS 与 Desktop（JVM）。

- Kotlin：2.2.20
- Compose Multiplatform：1.10.3
- Android：minSdk 24、targetSdk 35、compileSdk 36
- 包名：`io.github.vrcmteam.vrcm`
- 主要框架：Navigation 3、Lifecycle ViewModel、Material 3 Adaptive、Koin、Ktor、Multiplatform Settings、Coil

版本信息以 `gradle/libs.versions.toml` 为准，不要依赖文档中的旧版本号。

## 一、硬性约束

- 必须使用中文回复用户。
- 修改前先阅读相关实现、调用方与现有测试，遵循仓库已有模式，不要凭空引入新的架构风格。
- 工作区可能包含用户尚未提交的改动。禁止覆盖、回滚、格式化或提交与当前任务无关的内容。
- 未经用户明确要求，不要创建 Git 提交、推送远端、创建 PR 或改写提交历史。
- 禁止将临时 plan、spec、设计草案、任务拆解和分析记录加入 Git 跟踪。用户明确要求的正式文档除外。
- 禁止手工修改 `build/`、生成源码、IDE 状态目录、`.xcworkspace/` 等构建或工具生成物。
- 非必要不要修改 Android Manifest、签名配置、应用 ID 或平台权限。确需修改时必须说明兼容性与发布风险。
- 优先从问题源头修正，禁止使用魔法偏移、任意 `delay`、吞异常或重复状态补丁掩盖根因。

## 二、项目入口与模块地图

模块定义以 `settings.gradle.kts` 为准：

- `composeApp/`：KMP 主模块，包含共享业务、Compose UI 与 Android/Desktop 应用配置
- `iosApp/`：iOS 原生应用壳与 Xcode 工程
- `gradle/libs.versions.toml`：依赖版本、插件版本与应用版本
- `README.md`、`README_ZH.md`、`README_JP.md`：项目功能与使用说明

`composeApp/src/` 的主要源集：

- `commonMain`：跨平台主实现，优先在此编写可共享代码
- `commonTest`：跨平台单元测试
- `androidMain` / `androidUnitTest`：Android 专用实现与测试
- `iosMain` / `iosTest`：iOS 专用实现与测试
- `desktopMain` / `desktopTest`：Desktop 专用实现、集成测试与 Compose UI 测试

`commonMain/kotlin/` 的主要目录：

- `presentation/`：Compose UI、Navigation 3 路由、Lifecycle ViewModel、动画、主题、导航与设置
- `network/`：VRChat/GitHub API、Ktor 支持、WebSocket 与网络数据模型
- `service/`：认证、好友、收藏、版本、上传等跨 API 业务编排
- `storage/`：账户、缓存、设置与本地持久化
- `di/`：Koin 模块与依赖装配
- `core/`：通用算法、扩展与共享基础能力

仓库历史中存在 `presentation.compoments` 与 `presentation/compoments` 等既有命名。不要在无关任务中顺手重命名包或目录。

## 三、架构与状态管理

- 页面导航遵循现有 Navigation 3 `AppRoute`、`AppNavigator`、`NavDisplay` 和 Lifecycle `ViewModel` 模式。
- 依赖通过现有 Koin 模块注入，不要在 UI 中自行构造网络、存储或服务对象。
- 状态应保持单一数据源。优先使用 `StateFlow`、`SharedFlow` 或现有 Compose state 模式，不要创建彼此可能失同步的重复状态。
- 页面只负责展示和交互编排；可复用或跨页面的业务流程优先放入 ViewModel、Service、Storage 或 Network 层的既有边界。
- 网络请求使用现有 Ktor client、API 类型和认证重试封装，不要另建平行客户端。
- 平台差异优先使用现有源集和 `expect`/`actual` 模式，不要把平台判断散落在 `commonMain`。
- 协程必须绑定明确生命周期和 Dispatcher。测试或页面退出后不得遗留后台协程、全局状态或未恢复的 Main Dispatcher。

## 四、Compose UI 规范

- 颜色、字体、形状优先使用 `MaterialTheme` 与现有主题定义，禁止无理由硬编码颜色。
- 图标优先使用项目的 `AppIcons`；不要复制 SVG 路径或新建重复图标实现。
- 同级组件的外部间距由最近的共同父容器统一管理，子组件只负责自身内边距，避免同方向重复叠加 padding。
- 页面内容和文本容器应适应系统字体与不同窗口宽度。不要用固定高度掩盖文本溢出。
- 工具栏按钮、图片比例、卡片堆叠等需要稳定几何关系的控件可以使用明确尺寸，但必须说明该尺寸承担的布局契约，并验证窄窗口与大字体不会互相遮挡。
- 修改 `Modifier` 链时注意顺序语义，特别是 `padding`、`size`、`clip`、`background`、`alpha`、`clickable`、`graphicsLayer` 与绘制 Modifier。
- 共享元素与页面过渡必须沿用现有 shared key、suffix key 和作用域，不要为了局部视觉效果破坏导航返回动画。
- 新增交互必须处理 loading、empty、error、disabled 和重复点击等目标用户会自然遇到的状态。

## 五、资源与多语言

- Compose 资源放在 `composeApp/src/commonMain/composeResources/`，通过 `org.jetbrains.compose.resources` 或项目现有封装访问。
- 所有面向用户的文案必须进入现有 `LocaleStrings` 体系，禁止直接在 Composable 中硬编码。
- 修改语言键时同步核对英文、日文、简体中文与繁体中文目录结构；必要时运行语言结构测试。
- 图片加载复用 Coil/项目图片组件，文件选择复用 FileKit，避免引入功能重复的依赖。

## 六、工程与依赖约定

- 依赖和版本统一维护在 `gradle/libs.versions.toml`，构建脚本使用 `libs.*` Version Catalog。
- KMP 代码默认放在 `commonMain`，仅在确有平台 API 差异时新增平台实现。
- 不要因为单个调用点就引入新抽象；只有在减少真实复杂度、重复或明确匹配既有模式时才提取公共能力。
- 新增公共类型和复杂逻辑应提供简洁 KDoc；不要为显而易见的局部变量或一行函数添加叙述性注释。
- 保持修改范围集中，不做与需求无关的目录整理、命名修正、格式化或依赖升级。

## 七、测试质量规范

是否添加测试必须根据实际回归风险判断，禁止仅因为代码发生了变化、容易测试或需要提高覆盖率就添加测试。

**禁止添加只是重复实现细节的低价值测试**，包括：

- 直接复述算术、插值、换算或框架调用公式的测试
- 颜色、透明度、尺寸、间距等纯视觉数值计算测试
- 常量、简单 getter、纯 DTO、简单数据类相等性或字段映射测试
- 只验证对象能构造、`copy()` 结果或语言/编译器已经保证的行为
- 把依赖全部 mock 后只断言某方法被调用过 N 次，没有覆盖业务分支或真实结果
- 使用 `assertTrue(true)`、`assertNotNull(obj)`、`assertEquals(x, x)` 等永真断言
- 未明确要求 UI 测试时，对 Compose Modifier 顺序、样式数值或框架默认行为的测试
- 不构成独立行为契约的内部实现细节测试

尤其禁止通过分别断言公式的起点、中点和终点来测试公式本身，因为这种测试只是把实现代码重新写成断言。

**只有至少满足以下一项时，才应添加新测试**：

1. 能复现真实缺陷，并且在修复前已确认测试失败
2. 能保护非简单的业务分支、状态转换、不变量或错误处理
3. 能保护被多个组件依赖的公共行为契约
4. 能覆盖编译仍会通过、但实际行为可能发生回归的集成场景

测试应优先覆盖状态转换、缓存一致性、认证与错误路径、并发、序列化、导航契约、平台文件系统以及边界输入。

**新增测试前必须自检**：

> 这条测试能够捕获哪一种现实且合理的错误实现？如果唯一理由只是“这段实现发生了变化”，则不要添加测试。

禁止仅为了方便测试而提取没有业务意义的辅助函数、放宽可见性、增加额外抽象或添加只为提高覆盖率而存在的测试。

对于只涉及视觉样式的 UI 修改，除非用户明确要求，否则不要添加 Compose UI 测试。应运行现有测试和编译检查，并如实说明尚未自动验证的视觉风险。

即使没有添加新测试，也必须运行与修改相关的现有测试。

## 八、常用验证命令

根据风险选择最小但充分的命令：

```bash
./gradlew :composeApp:compileKotlinDesktop        # 编译共享/Desktop 主代码
./gradlew :composeApp:desktopTest                 # Desktop 测试套件
./gradlew :composeApp:allTests                    # KMP 聚合测试
./gradlew :composeApp:check                       # 模块完整检查
./gradlew :composeApp:assembleDebug               # Android Debug APK
./gradlew :composeApp:installDebug                # 安装 Android Debug
./gradlew :composeApp:createReleaseDistributable  # Desktop 发布包
./gradlew :composeApp:linkDebugFrameworkIosArm64  # iOS 调试 Framework
```

- 修改共享业务逻辑：至少运行相关测试和 `desktopTest`。
- 修改构建、资源或平台代码：增加对应平台的编译或打包任务。
- 修改纯视觉 UI：至少编译目标平台；无法自动视觉验证时在结果中明确说明。
- 提交前运行 `git diff --check`，确认没有空白错误，并核对 `git status` 未包含无关文件。

## 九、问题解决原则

- 先复现并读取完整错误，再定位数据、状态或约束从哪里产生。
- UI 错位应检查父子约束、Insets 和 Modifier 测量顺序，不要堆叠补偿 offset。
- 时序问题应建立正确的 Flow、状态或协程依赖，不要使用固定 `delay`。
- 数据问题应在解析、映射或写入源头校验，不要在 UI 末端静默过滤。
- 异常应转换为明确结果或向上报告，禁止空 `catch` 或仅打印后继续伪装成功。
- 修复后必须重新运行能证明原问题与相关回归的验证命令。

## 十、Git 与交付

- 提交信息沿用仓库现有 Conventional Commits 风格：`type(scope): 描述`。
- 只有用户明确要求提交时才执行 `git add`/`git commit`，并只暂存用户授权范围内的文件。
- 不要自动推送、创建 PR、合并分支或删除分支。
- 交付时说明修改文件、实际执行的验证、未验证风险以及仍保留的无关工作区变更。
