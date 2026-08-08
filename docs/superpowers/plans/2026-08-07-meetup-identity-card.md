# Meetup Identity Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 VRCM 增加一个 Android/iOS 可沉浸展示、Desktop 可编辑预览、离线优先且支持 VRChat 官方 Profile Decoration 的线下聚会身份牌。

**Architecture:** 新增账号隔离的 Settings 配置、Okio 应用私有素材仓库和 `MeetupCardRepository`，先同步恢复本地快照，再用既有 Ktor/认证边界刷新本人资料、Profile Appearance、Inventory Template 与远端素材。两个 Navigation 3 页面共享 Lifecycle ViewModel 暴露的 `StateFlow<MeetupCardUiState>`；照片、三套模板、二维码和 animated WebP 装饰由 Compose 分层实时渲染，沉浸式与常亮通过窄 `expect`/`actual` 生命周期效果实现。

**Tech Stack:** Kotlin 2.2.20、Compose Multiplatform 1.10.3、Navigation 3、Lifecycle ViewModel、Koin、Ktor、Multiplatform Settings、Okio、Coil、Skia `Codec`、QRose 1.0.1、FileKit

---

## 实施状态（2026-08-08 更新）

- Task 1–14 已全部实现并通过 `desktopTest`（579 例中仅 `DesktopWindowTitleBarLocaleTest` 因执行机无显示环境（HeadlessException，与本功能无关）失败）。Task 15 的两项人工验收矩阵尚未执行。
- Task 1–6 由 commit 70d11f02 落地；本次会话完成了对该提交的书面审计、缺陷修复与 Task 7–14。

### 与原计划的主要偏差（均已按实际实现更新语义）

1. **Task 1 解码器平台分立**：`org.jetbrains.skia` 在 androidMain 不可用，原计划的 commonMain 单一 `SkiaAnimatedWebpDecoder` 不可行。实际：commonMain 只保留接口（push 式 `start/pause/resume` + `onError`），desktop/iOS 各有一份逐字节一致的 Skia 实现，Android 用 awebp 3.0.5（`AndroidAnimatedWebpDecoder`）。desktop/iOS 双文件建议后续合并到自建共享 source set。
2. **Task 7 候选照片不携带 Compose 预览对象**：`MeetupPhotoCandidate` 定义在 repository 层（bytes+尺寸+双向裁剪），预览位图由 `MeetupPreparedPhoto`/会话单独持有并在 complete/discard 时释放。
3. **Task 8 未改造 GalleryTabPager**：为零回归风险，单选改为独立 `GalleryPickerScreen` 路由复用 `GalleryScreenModel`（数据、VRC+/空态/刷新全复用，仅网格项轻量重复）；`consume` 语义为"pending 不移除"，配合编辑器返回后消费。
4. **Task 6/9 配置创建职责收紧**：`refresh()` 不再隐式建档（否则破坏 §5.1 首配分流），仅 `ensureDefault`/显式编辑创建；相关测试已同步。
5. **Task 12 iOS root controller**：category 生成的 final 扩展（`prefersHomeIndicatorAutoHidden` 等）无法 override，采用同名成员函数经 ObjC selector 分发的标准 workaround。
6. **依赖**：commonTest 补 `okio-fakefilesystem`（70d11f02 已在 commonTest 使用但只在 desktopTest 声明，Android 单测编译因此损坏）。

### 审计修复记录（本次会话）

- Repository：照片回退链穷尽时统一落主题背景（Success/Partial 与 Failed 路径一致）；背景 URL 未变且素材完好不重复下载、内容无变化不空转 revision；`finishFailed` 合并双请求错误；首帧过滤已删除文件的死路径。
- 远端边界：`DefaultMeetupCardRemoteDataSource.loadImage` 增加图片文件头嗅探（200+错误页不得顶替有效照片）；`HttpMeetupRemoteBytesLoader` 对素材下载放宽 per-request 超时（120s/30s socket），共享 client 的 15s 全局超时不适用于 20/50 MiB 素材。
- DecorationResolver：`restoreCached` 过滤悬挂文件引用并对非法 ID 给出显式 Unavailable；空响应 template id 一律拒绝；`ResolvedDecoration` 增加 `staticFallback` 供运行期动画失败回退 base。
- 存储：`MeetupCardAssetStore.exists()`；`deleteAccount` 对非法 ID 静默跳过（不再中断账号移除）；invalidation listener 异常隔离。
- 解码器：Android 拒绝 <10ms 帧（awebp 内部按原始时长调度会忙循环）、`setLoopLimit(0)` 强制无限循环、二次 start 回帧 0；Skia 实现改单帧保留合成（峰值 ≤2 帧位图）、修复恒真循环守卫、增加帧节奏补偿；删除吞错的单参 `start` 重载。
- 位图回收竞态（Android 真机崩溃 "trying to use a recycled bitmap"）：装饰动画被替换的帧改为进退休队列、等两个 Choreographer 帧后再关闭，不在解码线程立即回收；照片会话预览默认不再同步 recycle（complete/discard 时裁剪对话框可能仍在组合中），交给 GC。
- 深链迭代（2026-08-08）：新增 `AppDeepLinks`（common）+ Android intent-filter（`vrchat.com/home/user/*`、`vrcm://user/*`，MainActivity 改 singleTask + onNewIntent），扫身份牌二维码可用 VRCM 直接打开应用内用户详情页；跳转在登录完成、主页入栈后执行。二维码内容保持公开 VRChat 主页 URL 不变。iOS 受 Universal Links 域名限制暂不支持。
- 真机验收迭代（2026-08-08）：资料特效改为 Fit 居中保持素材纵横比（Crop 会把设计给资料卡版式的特效放大裁切，竖屏只剩局部）；新增横屏独立照片（`MeetupCardConfig.landscapePhoto`，可选，未设置时沿用竖屏照片；照片选择支持"两个方向/仅竖屏/仅横屏"应用范围，刷新链路对横屏照片做可读校验与来源重下）。
- 真机验收修复（2026-08-08）：退出展示页系统栏不恢复——edge-to-edge 下 `rootWindowInsets.isVisible(systemBars())` 读数不可靠导致条件恢复被跳过，改为 release 时无条件 `show()`；预览/展示构图偏差——裁剪按 9:16/16:9 参考视口归一化存储，渲染与裁剪编辑改为经 `MeetupCropMapper.derive` 换算到真实视口（保焦点保 cover，19.5:9 屏不再留边），编辑预览改为"窗口全尺寸渲染 + 等比缩放"的真缩略图，字体与模板比例与展示页一致。

### 已知余留问题（低优先级，未实现）

- 动画持续解码失败无"失败记忆"，每次刷新会重复下载（最多 3×20 MiB）；可在 Template 缓存记失败 URL。
- 素材 URL 无 scheme/host 白名单（信任边界是官方 API 响应）。
- 写入中断的 `.tmp` 孤儿文件无启动清扫；Windows 上 `atomicMove` 覆盖已存在目标依赖 JDK 行为。
- 配置 JSON 损坏的账号在他人 `clearAccount` 时装饰缓存可能被误删（可自愈）。
- 漏测清单：DecorationTemplateCacheDao 无专属测试、AccountCacheManager lease 边界、iOS 解码器零测试、Android 真实渲染链路（onRender→ImageBitmap）零覆盖。

## 执行边界与文件结构

执行本计划前先运行 `git status --short`，并重新读取任何将要修改且已有用户改动的文件。（原列出的"未提交修改"文件在 70d11f02 前均已提交，该约束已过时；仍保留通用规则：若 `git status` 出现重叠文件，按相同规则逐段合并。）

本计划不包含 `git add`、`git commit`、推送或 PR 步骤。每个任务结束只运行目标测试、`git diff --check` 和 `git status --short`；这是仓库约束对 writing-plans 默认提交节奏的覆盖。

新增代码按职责组织：

- `network/api/profile/`：Profile Appearance endpoint 与只读 DTO。
- `network/api/inventory/`：Inventory Template endpoint 与只读 DTO。
- `storage/meetup/`：配置 DAO、Template 元数据缓存、应用私有文件和原子写入。
- `service/meetup/`：账号隔离、离线恢复、刷新合并和素材降级。
- `presentation/screens/meetup/`：路由、单一 UI state、编辑器、展示页、三套模板和动画播放器。
- `presentation/screens/gallery/`：只增加 Gallery 单选模式与内存 session，不改变普通图库上传/删除行为。
- 各平台 `presentation/screens/meetup/` 与 `storage/meetup/`：私有目录及沉浸/常亮实现。

### Task 1: 锁定二维码依赖并建立 animated WebP 能力探针

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/animation/AnimatedWebpDecoder.kt`
- Create: `composeApp/src/desktopTest/kotlin/presentation/screens/meetup/animation/AnimatedWebpDecoderTest.kt`
- Create binary fixture: `composeApp/src/desktopTest/resources/meetup/animated.webp`

- [x] **Step 1: 添加与当前工具链兼容的 QRose 版本**

```toml
[versions]
qrose = "1.0.1"

[libraries]
qrose = { module = "io.github.alexzhirkevich:qrose", version.ref = "qrose" }
```

在 `commonMain.dependencies` 中加入：

```kotlin
implementation(libs.qrose)
implementation(libs.okio)
```

不要使用要求 Kotlin 2.3.0 的 QRose `1.1.0` 或 `1.1.1`。

- [x] **Step 2: 先验证依赖解析覆盖三个目标**

Run:

```bash
./gradlew :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosArm64
```

Expected: 三个任务均 `BUILD SUCCESSFUL`；依赖图不得升级 Kotlin stdlib 到 2.3.x，也不得降级 Compose 1.10.3。若解析结果违背任一条件，撤销 QRose 两行并把 QRose 1.0.1 的 MIT 许可纯 Kotlin encoder 源码按原 package 纳入 `commonMain`，不能升级项目工具链。

- [x] **Step 3: 放入真实多帧 WebP fixture**

```bash
mkdir -p composeApp/src/desktopTest/resources/meetup
curl -fL https://www.gstatic.com/webp/animated/1.webp -o composeApp/src/desktopTest/resources/meetup/animated.webp
file composeApp/src/desktopTest/resources/meetup/animated.webp
```

Expected: 报告 Web/P image，文件大于 1 KiB；测试运行时不联网。

- [x] **Step 4: 写失败测试**

```kotlin
class AnimatedWebpDecoderTest {
    @Test
    fun realAnimatedWebpExposesMultipleFramesAndPositiveDurations() {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/meetup/animated.webp"))
            .use { it.readBytes() }
        SkiaAnimatedWebpDecoder().decode(bytes).use { animation ->
            assertTrue(animation.frameCount > 1)
            assertTrue((0 until animation.frameCount).all { animation.durationMillis(it) > 0 })
            animation.frame(0).close()
            animation.frame(1).close()
        }
    }
}
```

- [x] **Step 5: 运行测试并确认失败**

Run: `./gradlew :composeApp:desktopTest --tests '*AnimatedWebpDecoderTest'`

Expected: FAIL，提示 `SkiaAnimatedWebpDecoder` 未定义。

- [x] **Step 6: 实现 Skia 所有权边界**

```kotlin
interface AnimatedWebpDecoder {
    fun decode(bytes: ByteArray): DecodedAnimation
}

interface DecodedAnimation : AutoCloseable {
    val frameCount: Int
    fun durationMillis(index: Int): Int
    fun frame(index: Int): OwnedAnimationFrame
}

interface OwnedAnimationFrame : AutoCloseable {
    val bitmap: ImageBitmap
}

class SkiaAnimatedWebpDecoder : AnimatedWebpDecoder {
    override fun decode(bytes: ByteArray): DecodedAnimation {
        val data = Data.makeFromBytes(bytes)
        val codec = try { Codec.makeFromData(data) } catch (error: Throwable) {
            data.close()
            throw error
        }
        require(codec.encodedImageFormat == EncodedImageFormat.WEBP && codec.frameCount > 1) {
            codec.close()
            data.close()
            "Expected an animated WebP"
        }
        return SkiaDecodedAnimation(data, codec)
    }
}
```

`SkiaDecodedAnimation.frame(index)` 分配独立 Skia Bitmap，使用 `getFrameInfo(index).requiredFrame` 作为 `priorFrame` 调用 `codec.readPixels(bitmap, index, priorFrame)`。frame、Codec、Data 的 `close()` 都要幂等；duration 用 `coerceAtLeast(16)` 防止坏资源造成忙循环。

- [x] **Step 7: 验证探针**

Run:

```bash
./gradlew :composeApp:desktopTest --tests '*AnimatedWebpDecoderTest'
git diff --check
git status --short
```

Expected: PASS；状态只增加本任务文件和执行前已有改动。

### Task 2: 定义可迁移、按账号隔离的配置

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/storage/DaoKeys.kt`
- Create: `composeApp/src/commonMain/kotlin/storage/meetup/MeetupCardConfig.kt`
- Create: `composeApp/src/commonMain/kotlin/storage/meetup/MeetupCardConfigDao.kt`
- Create: `composeApp/src/commonTest/kotlin/storage/meetup/MeetupCardConfigDaoTest.kt`

- [x] **Step 1: 写账号隔离、未知字段和损坏数据测试**

```kotlin
@Test
fun configsAreIsolatedAndUnknownFieldsRemainReadable() {
    val settings = MapSettings()
    val dao = MeetupCardConfigDao(settings)
    dao.save(defaultMeetupCardConfig("usr_a").copy(shortText = "A"))
    dao.save(defaultMeetupCardConfig("usr_b").copy(shortText = "B"))
    val key = dao.keyForTest("usr_a")
    settings.putString(key, settings.getString(key, "").dropLast(1) + ",\"future\":true}")

    assertEquals("A", dao.load("usr_a")?.shortText)
    assertEquals("B", dao.load("usr_b")?.shortText)
    dao.clear("usr_a")
    assertNull(dao.load("usr_a"))
    assertNotNull(dao.load("usr_b"))
}

@Test
fun corruptConfigDoesNotEraseAnotherAccount() {
    val settings = MapSettings()
    val dao = MeetupCardConfigDao(settings)
    dao.save(defaultMeetupCardConfig("usr_b"))
    settings.putString(dao.keyForTest("usr_a"), "not-json")
    assertNull(dao.load("usr_a"))
    assertNotNull(dao.load("usr_b"))
}
```

- [x] **Step 2: 运行测试并确认失败**

Run: `./gradlew :composeApp:desktopTest --tests '*MeetupCardConfigDaoTest'`

Expected: FAIL，配置类型与 DAO 尚不存在。

- [x] **Step 3: 定义序列化模型**

```kotlin
const val MEETUP_CARD_SCHEMA_VERSION = 1

@Serializable enum class MeetupCardTemplate { InfoBar, Spotlight, SideTag }
@Serializable enum class MeetupPhotoSource { ProfileBackground, LocalAlbum, VrchatGallery }
@Serializable enum class MeetupOrientation { Portrait, Landscape }

@Serializable
data class MeetupCrop(
    val centerOffsetX: Float = 0f,
    val centerOffsetY: Float = 0f,
    val zoom: Float = 1f,
)

@Serializable
data class MeetupAssetRef(
    val relativePath: String,
    val sha256: String,
)

@Serializable
data class MeetupPhoto(
    val source: MeetupPhotoSource = MeetupPhotoSource.ProfileBackground,
    val sourceId: String? = null,
    val sourceUrl: String? = null,
    val localAsset: MeetupAssetRef? = null,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class MeetupProfileSnapshot(
    val displayName: String,
    val avatarUrl: String = "",
    val pronouns: String = "",
    val languages: List<String> = emptyList(),
    val status: String = "",
    val statusDescription: String = "",
)

@Serializable
data class MeetupAppearanceSnapshot(
    val iconFrameTemplateId: String = "",
    val profileEffectTemplateId: String = "",
    val nameplateEffectTemplateId: String = "",
)

@Serializable
data class MeetupCardConfig(
    val schemaVersion: Int = MEETUP_CARD_SCHEMA_VERSION,
    val ownerUserId: String,
    val revision: Long = 0,
    val template: MeetupCardTemplate = MeetupCardTemplate.InfoBar,
    val accentArgb: Long = 0xFF3F8CFF,
    val scrimAlpha: Float = 0.36f,
    val showAvatar: Boolean = false,
    val showPronouns: Boolean = false,
    val showLanguages: Boolean = false,
    val showStatus: Boolean = false,
    val showStatusDescription: Boolean = false,
    val showShortText: Boolean = false,
    val showQrCode: Boolean = false,
    val showIconFrame: Boolean = true,
    val showProfileEffect: Boolean = true,
    val showNameplateEffect: Boolean = true,
    val shortText: String = "",
    val photo: MeetupPhoto = MeetupPhoto(),
    val profileBackgroundFallback: MeetupPhoto? = null,
    val portraitCrop: MeetupCrop = MeetupCrop(),
    val landscapeCrop: MeetupCrop = MeetupCrop(),
    val profile: MeetupProfileSnapshot,
    val appearance: MeetupAppearanceSnapshot = MeetupAppearanceSnapshot(),
)
```

- [x] **Step 4: 实现 DAO**

`DaoKeys.MeetupCard.NAME` 为 `vrcm.meetup.card`，key prefix 为 `vrcm.meetup.card.config`。DAO 使用 `Json { encodeDefaults = true; ignoreUnknownKeys = true }`；key 是 `${prefix}.$ownerUserId`；提供 `load/save/all/clear`。损坏 JSON 返回 null，`save` 拒绝空 owner ID。

- [x] **Step 5: 跑测试**

Run: `./gradlew :composeApp:desktopTest --tests '*MeetupCardConfigDaoTest'`

Expected: 两项配置行为 PASS。

### Task 3: 实现应用私有素材仓库与原子切换

**Files:**
- Create: `composeApp/src/commonMain/kotlin/storage/meetup/MeetupCardAssetStore.kt`
- Create: `composeApp/src/commonMain/kotlin/storage/meetup/MeetupCardAssetRoot.kt`
- Create: `composeApp/src/androidMain/kotlin/storage/meetup/MeetupCardAssetRoot.android.kt`
- Create: `composeApp/src/iosMain/kotlin/storage/meetup/MeetupCardAssetRoot.ios.kt`
- Create: `composeApp/src/desktopMain/kotlin/storage/meetup/MeetupCardAssetRoot.desktop.kt`
- Create: `composeApp/src/desktopTest/kotlin/storage/meetup/MeetupCardAssetStoreTest.kt`

- [x] **Step 1: 写原子失败与账号清理测试**

```kotlin
@Test
fun failedWriteLeavesPreviousPhotoReadable() = runTest {
    val fs = FakeFileSystem()
    val store = MeetupCardAssetStore(fs, "/private/meetup".toPath())
    val first = store.writePhoto("usr_a", "first".encodeToByteArray(), "jpg")
    val failingFileSystem = object : ForwardingFileSystem(fs) {
        override fun sink(file: Path, mustCreate: Boolean): Sink {
            if (file.name.endsWith(".tmp")) throw IOException("disk full")
            return super.sink(file, mustCreate)
        }
    }
    val failingStore = MeetupCardAssetStore(failingFileSystem, "/private/meetup".toPath())
    assertFailsWith<IOException> {
        failingStore.writePhoto("usr_a", "second".encodeToByteArray(), "jpg")
    }
    assertContentEquals("first".encodeToByteArray(), store.read(first))
}

@Test
fun deletingAccountKeepsSharedDecorationCache() = runTest {
    val fs = FakeFileSystem()
    val store = MeetupCardAssetStore(fs, "/private/meetup".toPath())
    store.writePhoto("usr_a", byteArrayOf(1), "png")
    val decoration = store.writeDecoration("inv_template", DecorationAssetType.Base, byteArrayOf(2))
    store.deleteAccount("usr_a")
    assertContentEquals(byteArrayOf(2), store.read(decoration))
}
```

- [x] **Step 2: 运行测试并确认失败**

Run: `./gradlew :composeApp:desktopTest --tests '*MeetupCardAssetStoreTest'`

Expected: FAIL，素材仓库尚不存在。

- [x] **Step 3: 实现内容寻址 API**

```kotlin
enum class DecorationAssetType(val fileName: String) {
    MainAnimation("main-animation.webp"), Base("base.webp")
}

class MeetupCardAssetStore(
    private val fileSystem: FileSystem,
    private val root: Path,
) {
    suspend fun writePhoto(ownerId: String, bytes: ByteArray, extension: String): MeetupAssetRef
    suspend fun writeDecoration(templateId: String, type: DecorationAssetType, bytes: ByteArray): MeetupAssetRef
    suspend fun read(ref: MeetupAssetRef): ByteArray
    fun model(ref: MeetupAssetRef): String
    suspend fun deleteAccount(ownerId: String)
    suspend fun clearDecorationCache()
    suspend fun pruneDecorations(retainedTemplateIds: Set<String>)
}
```

owner/template ID 只接受 `[A-Za-z0-9_-]+`。SHA-256 使用 Okio；照片名为 `accounts/{owner}/photos/{sha}.{ext}`。先在同目录写随机 `.tmp`，关闭后重读校验 hash，再 `atomicMove`；异常/取消均在 `finally` 删除临时文件，不删除旧文件。`model` 只解析仓库产生的相对路径。

- [x] **Step 4: 实现平台私有目录**

```kotlin
internal expect fun meetupCardAssetRoot(appPlatform: AppPlatform): Path
```

Android actual 使用 `context.filesDir/meetup-card`；iOS 使用现有数据库相同的 `NSApplicationSupportDirectory/VRCM/meetup-card`；Desktop 使用 `desktopSettingsDirectory()/meetup-card`。不得用 temporary directory 保存相册图片。

- [x] **Step 5: 验证仓库与三端编译**

Run:

```bash
./gradlew :composeApp:desktopTest --tests '*MeetupCardAssetStoreTest'
./gradlew :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosArm64
git diff --check
```

Expected: 测试和三端编译 PASS。

### Task 4: 增加 Profile Appearance 与 Inventory Template API

**Files:**
- Create: `composeApp/src/commonMain/kotlin/network/api/profile/ProfileAppearanceApi.kt`
- Create: `composeApp/src/commonMain/kotlin/network/api/profile/data/ProfileAppearanceData.kt`
- Create: `composeApp/src/commonMain/kotlin/network/api/inventory/InventoryApi.kt`
- Create: `composeApp/src/commonMain/kotlin/network/api/inventory/data/InventoryTemplateData.kt`
- Create: `composeApp/src/commonTest/kotlin/network/api/profile/ProfileAppearanceApiTest.kt`
- Create: `composeApp/src/commonTest/kotlin/network/api/inventory/InventoryApiTest.kt`

- [x] **Step 1: 写 endpoint 与宽松 DTO 测试**

```kotlin
@Test
fun profileRequestUsesRequiredFlags() = runTest {
    val request = captureRequest { ProfileAppearanceApi(it).get("usr_123") }
    assertEquals("/api/1/profile/usr_123", request.url.encodedPath)
    assertEquals("true", request.url.parameters["asSelf"])
    assertEquals("true", request.url.parameters["withGroupsAndWorlds"])
}

@Test
fun missingAppearanceFieldIsNullButEmptyValueIsPreserved() = runTest {
    val result = decodeProfile("""{"id":"usr_123","iconFrame":""}""")
    assertEquals("", result.iconFrame)
    assertNull(result.profileEffect)
}

@Test
fun inventoryUsesTemplateEndpoint() = runTest {
    val request = captureRequest { InventoryApi(it).getTemplate("inv_123") }
    assertEquals("/api/1/inventory/template/inv_123", request.url.encodedPath)
}
```

- [x] **Step 2: 运行测试并确认失败**

Run: `./gradlew :composeApp:desktopTest --tests '*ProfileAppearanceApiTest' --tests '*InventoryApiTest'`

Expected: FAIL，新 API 未定义。

- [x] **Step 3: 实现最小 DTO**

```kotlin
@Serializable
data class ProfileAppearanceData(
    val id: String = "",
    val iconFrame: String? = null,
    val profileEffect: String? = null,
    val nameplateEffect: String? = null,
)

@Serializable
data class InventoryTemplateData(
    val id: String = "",
    val metadata: InventoryTemplateMetadata = InventoryTemplateMetadata(),
)

@Serializable
data class InventoryTemplateMetadata(
    val assets: List<InventoryTemplateAsset> = emptyList(),
    val gradientStart: String? = null,
    val gradientEnd: String? = null,
)

@Serializable
data class InventoryTemplateAsset(val type: String = "", val url: String = "")
```

- [x] **Step 4: 实现 API**

```kotlin
class ProfileAppearanceApi(private val client: HttpClient) {
    suspend fun get(userId: String): ProfileAppearanceData =
        client.get("/api/1/profile/$userId") {
            parameter("asSelf", true)
            parameter("withGroupsAndWorlds", true)
        }.checkSuccess()
}

class InventoryApi(private val client: HttpClient) {
    suspend fun getTemplate(templateId: String): InventoryTemplateData =
        client.get("/api/1/inventory/template/$templateId").checkSuccess()
}
```

请求前拒绝空 ID；响应账号匹配留给 repository，不能写进现有 `UserData`。

- [x] **Step 5: 运行 API 测试**

Run: `./gradlew :composeApp:desktopTest --tests '*ProfileAppearanceApiTest' --tests '*InventoryApiTest'`

Expected: URL、query 和缺失/空值语义 PASS。

### Task 5: 缓存 Template 元数据并解析装饰降级链

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/storage/DaoKeys.kt`
- Create: `composeApp/src/commonMain/kotlin/storage/meetup/DecorationTemplateCacheDao.kt`
- Create: `composeApp/src/commonMain/kotlin/storage/meetup/DecorationTemplateCache.kt`
- Create: `composeApp/src/commonMain/kotlin/service/meetup/DecorationResolver.kt`
- Create: `composeApp/src/commonTest/kotlin/service/meetup/DecorationResolverTest.kt`

- [x] **Step 1: 写去重和降级测试**

```kotlin
@Test
fun duplicateTemplateIdsAreFetchedOnce() = runTest {
    val remote = FakeDecorationRemote()
    resolver(remote).refresh(listOf("inv_same", "inv_same"))
    assertEquals(listOf("inv_same"), remote.templateRequests)
}

@Test
fun animationFailureFallsBackToBaseWithoutDroppingOtherSlots() = runTest {
    val result = resolver(fakeRemote(animationFailure = setOf("inv_frame")))
        .refresh(listOf("inv_frame", "inv_effect"))
    assertEquals(DecorationRenderMode.Static, result.getValue("inv_frame").mode)
    assertEquals(DecorationRenderMode.Animated, result.getValue("inv_effect").mode)
}

@Test
fun missingMainAndBaseDisablesOnlyThatDecoration() = runTest {
    val result = resolver(fakeRemote(emptyAssets = setOf("inv_bad")))
        .refresh(listOf("inv_bad", "inv_good"))
    assertEquals(DecorationRenderMode.Unavailable, result.getValue("inv_bad").mode)
    assertEquals(DecorationRenderMode.Animated, result.getValue("inv_good").mode)
}
```

- [x] **Step 2: 运行测试并确认失败**

Run: `./gradlew :composeApp:desktopTest --tests '*DecorationResolverTest'`

Expected: FAIL，resolver/cache 类型尚不存在。

- [x] **Step 3: 定义缓存与渲染结果**

```kotlin
@Serializable
data class DecorationTemplateCache(
    val templateId: String,
    val mainAnimationUrl: String = "",
    val baseUrl: String = "",
    val mainAnimationAsset: MeetupAssetRef? = null,
    val baseAsset: MeetupAssetRef? = null,
    val gradientStart: String = "",
    val gradientEnd: String = "",
)

enum class DecorationRenderMode { Animated, Static, Unavailable }

data class ResolvedDecoration(
    val templateId: String,
    val mode: DecorationRenderMode,
    val asset: MeetupAssetRef?,
    val gradientStart: String,
    val gradientEnd: String,
)
```

DAO key 为 `vrcm.meetup.decoration.template.$templateId`，JSON 忽略未知字段，提供 `load/save/clearAll`。

- [x] **Step 4: 实现解析器**

```kotlin
interface DecorationTemplateSource {
    suspend fun getTemplate(templateId: String): InventoryTemplateData
}

fun interface MeetupRemoteBytesLoader {
    suspend fun load(url: String, maxBytes: Long): ByteArray
}
```

`refresh` 去空并 `distinct()`；每个 template 独立捕获非取消异常；只识别 `mainAnimation` 和 `base`，忽略 `introAnimation`；动画下载后用 `AnimatedWebpDecoder.decode(bytes).close()` 验证。选择顺序严格为有效 mainAnimation、缓存/新下载 base、Unavailable。生产 loader 使用现有 HttpClient、检查 HTTP success，不创建新客户端；装饰传 20 MiB，照片传 `PrintImageLimits.MAX_FILE_BYTES`。

- [x] **Step 5: 运行测试**

Run: `./gradlew :composeApp:desktopTest --tests '*DecorationResolverTest'`

Expected: 去重、部分失败和降级链 PASS。

### Task 6: 实现离线优先 Repository 与并发提交规则

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/storage/AccountCacheManager.kt`
- Create: `composeApp/src/commonMain/kotlin/service/meetup/MeetupCardRepository.kt`
- Create: `composeApp/src/commonMain/kotlin/service/meetup/MeetupCardRemoteDataSource.kt`
- Create: `composeApp/src/commonTest/kotlin/service/meetup/MeetupCardRepositoryTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/storage/AccountCacheManagerTest.kt`

- [x] **Step 1: 写离线首帧、刷新合并和账号切换测试**

```kotlin
@Test
fun cachedCardIsEmittedBeforeBlockedNetworkCompletes() = runTest {
    val remote = BlockingMeetupRemote()
    val repository = repository(remote).also { it.saveForTest(cachedConfig("usr_a")) }
    val first = repository.observe("usr_a").value
    assertEquals("Cached Name", first.config.profile.displayName)
    assertFalse(first.refreshing)
}

@Test
fun refreshFailureKeepsLastValidCard() = runTest {
    val repository = repository(FailingMeetupRemote()).also {
        it.saveForTest(cachedConfig("usr_a"))
    }
    repository.refresh("usr_a").join()
    assertEquals("Cached Name", repository.observe("usr_a").value.config.profile.displayName)
    assertIs<MeetupRefreshResult.Failed>(repository.observe("usr_a").value.lastRefresh)
}

@Test
fun localEditDuringRefreshIsMergedInsteadOfOverwritten() = runTest {
    val remote = BlockingMeetupRemote()
    val repository = repository(remote).also { it.saveForTest(cachedConfig("usr_a")) }
    val refresh = repository.refresh("usr_a")
    repository.update("usr_a") { it.copy(shortText = "local", showShortText = true) }
    remote.complete(profileName = "Network Name")
    refresh.join()
    val config = repository.observe("usr_a").value.config
    assertEquals("local", config.shortText)
    assertEquals("Network Name", config.profile.displayName)
}

@Test
fun accountGenerationRejectsLateRefresh() = runTest {
    val remote = BlockingMeetupRemote()
    val repository = repository(remote).also { it.saveForTest(cachedConfig("usr_a")) }
    val refresh = repository.refresh("usr_a")
    accountCacheManager.clearAccount("usr_a")
    remote.complete(profileName = "stale")
    refresh.join()
    assertNull(configDao.load("usr_a"))
}
```

- [x] **Step 2: 运行测试并确认失败**

Run: `./gradlew :composeApp:desktopTest --tests '*MeetupCardRepositoryTest'`

Expected: FAIL，repository 尚不存在。

- [x] **Step 3: 扩展账号 generation 和移除清理契约**

在 `AccountCacheManager` 保持现有 token 行为，并增加：

```kotlin
internal fun isCurrent(token: AccountCacheWriteToken): Boolean = synchronized(lock) {
    token.globalGeneration == globalGeneration &&
        token.accountGeneration == (accountGenerations[token.userId] ?: 0L)
}
```

构造函数注入 `MeetupCardConfigDao` 与 `MeetupCardAssetStore`。`clearAccount(userId)` 在 generation 增长后清配置和账号照片，再依据 `configDao.all()` 的 appearance IDs 调用 `pruneDecorations`。现有 `clearAll()` 是普通缓存清理，仍不得删除身份牌配置或相册照片。

- [x] **Step 4: 定义 Repository 单一状态**

```kotlin
data class MeetupCardState(
    val config: MeetupCardConfig,
    val photoModel: String?,
    val decorations: Map<DecorationSlot, ResolvedDecoration> = emptyMap(),
    val refreshing: Boolean = false,
    val lastRefresh: MeetupRefreshResult = MeetupRefreshResult.NotStarted,
)

enum class DecorationSlot { IconFrame, ProfileEffect, NameplateEffect }

sealed interface MeetupRefreshResult {
    data object NotStarted : MeetupRefreshResult
    data object Success : MeetupRefreshResult
    data class Partial(val failedParts: Set<String>) : MeetupRefreshResult
    data class Failed(val reason: Throwable) : MeetupRefreshResult
}

interface MeetupCardRepository {
    fun hasConfig(ownerId: String): Boolean
    fun observe(ownerId: String): StateFlow<MeetupCardState>
    suspend fun ensureDefault(ownerId: String): MeetupCardConfig
    fun refresh(ownerId: String): Job
    suspend fun update(ownerId: String, transform: (MeetupCardConfig) -> MeetupCardConfig)
    suspend fun replacePhoto(ownerId: String, candidate: MeetupPhotoCandidate): Result<Unit>
}
```

- [x] **Step 5: 实现恢复、刷新与 revision 合并**

`DefaultMeetupCardRepository` 用单一 `Mutex` 保护 Settings 提交和 state 更新，网络 I/O 在 mutex 外执行。`ensureDefault` 从当前用户构建 InfoBar、仅 Display Name、QR 关闭的默认配置。

刷新必须按以下顺序实现：

1. 捕获 `AccountCacheWriteToken` 和 revision，立即保留内容并设 `refreshing=true`。
2. `coroutineScope` 并行请求 `UsersApi.fetchUser(ownerId)` 与 Profile Appearance。
3. Profile response ID 不匹配时整份 appearance 无效。
4. appearance 字段为 null 时保留旧 ID，空字符串时清除，非空时覆盖。
5. 对合并后非空 IDs 调用 `DecorationResolver`。
6. 提交前检查 generation；mutex 内重读最新配置，把网络 profile/appearance 合并到最新本地模板、开关、短句、裁剪和照片上。
7. 资料背景下载成功后总是更新 `profileBackgroundFallback`；只有当前照片仍是同一 ProfileBackground URL 时才同时切换 `photo`，Gallery/相册不能被刷新覆盖。
8. 构造 `photoModel` 时依次验证当前私有素材、按当前远端 source 重下、`profileBackgroundFallback` 私有素材、主题背景；任一步失败都保留后续回退机会并记录部分失败。

所有 `CancellationException` 原样抛出；其他失败变成 Partial/Failed，保留上一状态。

```kotlin
private fun mergeAppearance(
    old: MeetupAppearanceSnapshot,
    response: ProfileAppearanceData,
) = old.copy(
    iconFrameTemplateId = response.iconFrame?.trim() ?: old.iconFrameTemplateId,
    profileEffectTemplateId = response.profileEffect?.trim() ?: old.profileEffectTemplateId,
    nameplateEffectTemplateId = response.nameplateEffect?.trim() ?: old.nameplateEffectTemplateId,
)
```

这里 `trim()` 后的空字符串会清除槽位，只有 null 才保留旧值。

- [x] **Step 6: 跑 Repository 与账号清理测试**

Run:

```bash
./gradlew :composeApp:desktopTest --tests '*MeetupCardRepositoryTest' --tests '*AccountCacheManagerTest'
git diff --check
```

Expected: 缓存首帧、失败保留、本地编辑合并、过期结果丢弃和既有缓存测试 PASS。

### Task 7: 复用图片解码并建立双方向裁剪会话

**Files:**
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/editor/MeetupPhotoSessionStore.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/editor/MeetupCropMapper.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/editor/MeetupPhotoPreparer.kt`
- Create: `composeApp/src/commonTest/kotlin/presentation/screens/meetup/editor/MeetupCropMapperTest.kt`
- Create: `composeApp/src/commonTest/kotlin/presentation/screens/meetup/editor/MeetupPhotoSessionStoreTest.kt`

- [x] **Step 1: 写方向独立与安全默认测试**

```kotlin
@Test
fun derivingLandscapePreservesFocusAndCover() {
    val source = ImageSize(3000, 4000)
    val portrait = ImageSize(1080, 1920)
    val landscape = ImageSize(1920, 1080)
    val current = MeetupCrop(.12f, -.08f, 2.2f)
    val calculator = CropTransformCalculator()
    val derived = MeetupCropMapper(calculator).derive(source, portrait, landscape, current)
    assertTrue(derived.zoom >= calculator.zoomLimits(source, landscape, 0).cover)
    assertEquals(.12f, derived.centerOffsetX, .02f)
    assertEquals(-.08f, derived.centerOffsetY, .02f)
}

@Test
fun editingPortraitDoesNotMutateLandscape() {
    val session = MeetupPhotoSessionStore().create(preparedCandidate())
    val landscapeBefore = session.landscapeCrop.value
    session.updateCrop(MeetupOrientation.Portrait, MeetupCrop(.1f, .2f, 2f))
    assertEquals(landscapeBefore, session.landscapeCrop.value)
}
```

- [x] **Step 2: 运行测试并确认失败**

Run: `./gradlew :composeApp:desktopTest --tests '*MeetupCropMapperTest' --tests '*MeetupPhotoSessionStoreTest'`

Expected: FAIL，新类型未定义。

- [x] **Step 3: 实现候选图片和 prepare**

```kotlin
data class MeetupPhotoCandidate(
    val source: MeetupPhotoSource,
    val sourceId: String?,
    val sourceUrl: String?,
    val fileName: String,
    val bytes: ByteArray,
    val originalSize: ImageSize,
    val preview: ImageBitmap,
    val portraitCrop: MeetupCrop,
    val landscapeCrop: MeetupCrop,
)

class MeetupPhotoPreparer(private val codec: PlatformImageCodec) {
    suspend fun prepare(
        source: MeetupPhotoSource,
        sourceId: String?,
        sourceUrl: String?,
        fileName: String,
        bytes: ByteArray,
    ): Result<MeetupPhotoCandidate>
}
```

`prepare` 复用 `PrintImageLimits`、`DecodeRequest` 和 `PlatformImageCodec.decode`，不调用上传器或 `renderCrop`。两个方向初始 zoom 取 `CropTransformCalculator.zoomLimits(...).cover`；preview Bitmap 由 session 持有并在 discard/complete 释放。

- [x] **Step 4: 实现映射与 session**

`MeetupCropMapper.derive` 计算 `toCover * (from.zoom / fromCover)`，复制焦点后通过 `CropTransformCalculator.transform` 的零 pan/1x zoom 调用 clamp。Session store 用递增 ID 保存候选、两个方向 StateFlow 和当前方向；`complete(id)` 一次性返回候选并移除，`discard(id)` 释放 preview。

```kotlin
class MeetupCropMapper(private val calculator: CropTransformCalculator) {
    fun derive(
        source: ImageSize,
        fromViewport: ImageSize,
        toViewport: ImageSize,
        from: MeetupCrop,
    ): MeetupCrop
}

class MeetupPhotoSessionStore {
    fun create(candidate: MeetupPhotoCandidate): MeetupPhotoSession
    fun get(id: String): MeetupPhotoSession?
    fun complete(id: String): MeetupPhotoCandidate?
    fun discard(id: String)
}
```

- [x] **Step 5: 运行测试**

Run: `./gradlew :composeApp:desktopTest --tests '*MeetupCropMapperTest' --tests '*MeetupPhotoSessionStoreTest'`

Expected: 方向隔离、cover 和资源释放 PASS。

### Task 8: 为 VRChat Gallery 增加独立单选路由

**Files:**
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/gallery/GallerySelectionSessionStore.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/gallery/GalleryPickerScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/screens/gallery/GalleryScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/screens/gallery/GalleryTabPager.kt`
- Create: `composeApp/src/commonTest/kotlin/presentation/screens/gallery/GallerySelectionSessionStoreTest.kt`

- [x] **Step 1: 写一次性返回测试**

```kotlin
@Test
fun pickerResultIsConsumedOnceAndContainsNoBytes() {
    val store = GallerySelectionSessionStore()
    val id = store.create()
    store.complete(id, GallerySelection("file_1", "name", ".png", "https://image"))
    assertEquals("file_1", store.consume(id)?.fileId)
    assertNull(store.consume(id))
}
```

- [x] **Step 2: 运行测试并确认失败**

Run: `./gradlew :composeApp:desktopTest --tests '*GallerySelectionSessionStoreTest'`

Expected: FAIL，session/route 未定义。

- [x] **Step 3: 实现 session 和最小 route payload**

```kotlin
data class GallerySelection(
    val fileId: String,
    val fileName: String,
    val extension: String,
    val imageUrl: String,
)

class GallerySelectionSessionStore {
    fun create(): String
    fun complete(id: String, selection: GallerySelection): Boolean
    fun cancel(id: String)
    fun consume(id: String): GallerySelection?
}

@Serializable
data class GalleryPickerScreen(val sessionId: String) : AppRoute
```

路由只携带 session ID，不能携带 bytes、绝对路径或 URL。

- [x] **Step 4: 抽取共用 Gallery 内容**

```kotlin
internal sealed interface GalleryMode {
    data object Manage : GalleryMode
    data class Pick(val sessionId: String) : GalleryMode
}
```

Manage 保持五个 tab、上传、预览、长按选择和删除不变。Pick 只显示 `FileTagType.Gallery`，隐藏 FAB；单击完成 session 后 pop；重复点击只接受第一个结果；返回先 cancel 再 pop。VRC+、空内容和失败继续使用现有状态。

- [x] **Step 5: 跑 Gallery 回归测试**

Run:

```bash
./gradlew :composeApp:desktopTest --tests '*GallerySelectionSessionStoreTest' --tests '*GalleryScreenModelTest'
git diff --check
```

Expected: 新 session 与既有上传/删除测试 PASS。

### Task 9: 实现单一 `MeetupCardUiState` ViewModel

**Files:**
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/MeetupCardUiState.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/MeetupCardScreenModel.kt`
- Create: `composeApp/src/commonTest/kotlin/presentation/screens/meetup/MeetupCardScreenModelTest.kt`

- [x] **Step 1: 写最低状态、短句约束和重复操作测试**

```kotlin
@Test
fun cachedStateIsReadyWithoutWaitingForRefresh() = runTest {
    val model = model(repositoryWith(cachedState("usr_a")))
    advanceUntilIdle()
    assertEquals("Cached Name", model.state.value.displayName)
    assertFalse(model.state.value.blockingLoading)
}

@Test
fun shortTextRejectsMoreThanEightyCodePoints() = runTest {
    val model = model(repositoryWith(cachedState("usr_a")))
    model.setShortText("😀".repeat(81))
    assertIs<MeetupEditorError.ShortTextTooLong>(model.state.value.editorError)
    assertEquals("", repository.config.shortText)
}

@Test
fun duplicatePhotoConfirmationCommitsOnce() = runTest {
    val model = model(repositoryWith(cachedState("usr_a")))
    model.confirmPhoto("session-1")
    model.confirmPhoto("session-1")
    advanceUntilIdle()
    assertEquals(1, repository.photoReplacements)
}
```

- [x] **Step 2: 运行测试并确认失败**

Run: `./gradlew :composeApp:desktopTest --tests '*MeetupCardScreenModelTest'`

Expected: FAIL，ViewModel 未定义。

- [x] **Step 3: 定义唯一公开 state**

```kotlin
data class MeetupCardUiState(
    val ownerUserId: String,
    val displayName: String,
    val config: MeetupCardConfig,
    val photoModel: String?,
    val decorations: Map<DecorationSlot, ResolvedDecoration>,
    val orientation: MeetupOrientation,
    val blockingLoading: Boolean = false,
    val refreshing: Boolean = false,
    val savingPhoto: Boolean = false,
    val editorError: MeetupEditorError? = null,
    val refreshResult: MeetupRefreshResult = MeetupRefreshResult.NotStarted,
)

sealed interface MeetupEditorError {
    data object ShortTextTooLong : MeetupEditorError
    data class PhotoFailed(val reason: Throwable) : MeetupEditorError
    data class SaveFailed(val reason: Throwable) : MeetupEditorError
}
```

Display Name 从 snapshot 读取，空白时回退当前账号名称，再空白时使用 owner ID；任何 state 都不能为空。

- [x] **Step 4: 实现 ViewModel 操作**

构造参数为 owner ID、repository 和 photo session store。公开单一 `StateFlow`；操作包括模板、字段开关、主题色、遮罩、短句、方向、crop draft/commit、照片确认、刷新和清错。Unicode surrogate pair 计一个 code point，最多 80；离散值立即持久化，crop/slider 只在手势结束提交；consumed session ID 集合防重复。

```kotlin
class MeetupCardScreenModel(
    private val ownerUserId: String,
    private val repository: MeetupCardRepository,
    private val photoSessions: MeetupPhotoSessionStore,
) : ViewModel() {
    val state: StateFlow<MeetupCardUiState>
    fun setTemplate(value: MeetupCardTemplate)
    fun setShortText(value: String)
    fun setOrientation(value: MeetupOrientation)
    fun updateCropDraft(value: MeetupCrop)
    fun commitCrop()
    fun confirmPhoto(sessionId: String)
    fun refresh()
    fun clearError()
}
```

- [x] **Step 5: 运行 ViewModel 测试**

Run: `./gradlew :composeApp:desktopTest --tests '*MeetupCardScreenModelTest'`

Expected: 首帧、80 code point、失败保留旧照片和重复提交 PASS。

### Task 10: 实现照片、三模板、二维码与装饰图层

**Files:**
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/MeetupCardCanvas.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/MeetupCardPhoto.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/MeetupCardTemplateLayouts.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/MeetupCardQrCode.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/animation/AnimatedDecoration.kt`
- Create: `composeApp/src/commonTest/kotlin/presentation/screens/meetup/MeetupCardQrCodeTest.kt`

- [x] **Step 1: 写固定 QR payload 测试**

```kotlin
@Test
fun qrPayloadAlwaysUsesPublicVrchatProfile() {
    assertEquals(
        "https://vrchat.com/home/user/usr_abc-123",
        meetupCardProfileUrl("usr_abc-123"),
    )
    assertFailsWith<IllegalArgumentException> { meetupCardProfileUrl("https://other") }
}
```

- [x] **Step 2: 运行测试并确认失败**

Run: `./gradlew :composeApp:desktopTest --tests '*MeetupCardQrCodeTest'`

Expected: FAIL，payload 函数未定义。

- [x] **Step 3: 实现照片和 QR 图层**

```kotlin
internal fun meetupCardProfileUrl(userId: String): String {
    require(Regex("usr_[A-Za-z0-9-]+").matches(userId))
    return "https://vrchat.com/home/user/$userId"
}

@Composable
fun MeetupCardQrCode(userId: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color.White, shape = MaterialTheme.shapes.small) {
        Image(
            painter = rememberQrCodePainter(meetupCardProfileUrl(userId)),
            contentDescription = strings.meetupCardQrDescription,
            modifier = Modifier.padding(8.dp).fillMaxSize(),
        )
    }
}
```

模板给 QR 稳定 1:1 槽位，8.dp 白色 quiet zone 不被装饰覆盖。`MeetupCardPhoto` 根据原图尺寸、viewport 和 crop 调用 `CropTransformCalculator.geometry`，在裁剪 Box 内对 Coil 图片应用 size/translation/scale。照片失败显示主题 surface，不改变文字布局。

- [x] **Step 4: 实现三套模板和层级**

`MeetupCardCanvas` 层级固定为主题背景、照片、scrim、profileEffect、模板内容、iconFrame/nameplateEffect、控制层 slot。`InfoBarTemplate` 明确照片/资料分区；`SpotlightTemplate` 使用底部高对比遮罩；`SideTagTemplate` 使用方向对应侧栏。Display Name 最多两行，在 Material typography 离散档位选择而非随 viewport 连续缩放；字段用 FlowRow/Column 换行，不能覆盖 QR。

```kotlin
@Composable
private fun InfoBarTemplate(content: MeetupTemplateContent, orientation: MeetupOrientation)

@Composable
private fun SpotlightTemplate(content: MeetupTemplateContent, orientation: MeetupOrientation)

@Composable
private fun SideTagTemplate(content: MeetupTemplateContent, orientation: MeetupOrientation)
```

- [x] **Step 5: 实现前台感知动画播放器**

`AnimatedDecoration` 从 asset store 读 bytes 并解码；只在 Lifecycle RESUMED 时逐帧推进。每次换帧先让 Compose 接管新 bitmap，再释放旧 frame；dispose 释放当前 frame 和 animation。解码失败切同 slot static base，两者失败不渲染。装饰 Modifier 不添加任何指针处理。

```kotlin
@Composable
fun AnimatedDecoration(
    decoration: ResolvedDecoration,
    staticFallback: MeetupAssetRef?,
    modifier: Modifier = Modifier,
)
```

- [x] **Step 6: 运行测试和编译**

Run:

```bash
./gradlew :composeApp:desktopTest --tests '*MeetupCardQrCodeTest' --tests '*AnimatedWebpDecoderTest'
./gradlew :composeApp:compileKotlinDesktop
```

Expected: QR、动画测试 PASS，模板编译成功。

### Task 11: 实现照片选择、裁剪和四页编辑器

**Files:**
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/editor/MeetupCardEditorScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/editor/MeetupCardEditorTools.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/editor/MeetupCardCropDialog.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/editor/MeetupPhotoSelectionCoordinator.kt`

- [x] **Step 1: 实现三种照片来源入口**

```kotlin
enum class MeetupPhotoAction { ProfileBackground, LocalAlbum, VrchatGallery }
```

Profile Background 从 config snapshot 的 URL 通过既有 Ktor client 下载；Local Album 使用 `rememberFilePickerLauncher`、`galleryImagePickerType(listOf("jpg", "jpeg", "png", "webp", "heic", "heif"))` 与 `readBoundedBytes`；VRChat Gallery 创建 selection session 并 push `GalleryPickerScreen(sessionId)`，返回后下载选中 URL。三者都交给 `MeetupPhotoPreparer`，失败显示本地化错误且不替换当前照片。

```kotlin
class MeetupPhotoSelectionCoordinator(
    private val preparer: MeetupPhotoPreparer,
    private val gallerySessions: GallerySelectionSessionStore,
    private val bytesLoader: MeetupRemoteBytesLoader,
) {
    suspend fun profileBackground(config: MeetupCardConfig): Result<String>
    suspend fun localAlbum(fileName: String, bytes: ByteArray): Result<String>
    fun beginGallerySelection(): String
    suspend fun finishGallerySelection(sessionId: String): Result<String>
}
```

- [x] **Step 2: 实现裁剪对话框**

`MeetupCardCropDialog` 复用 preview、`detectTransformGestures` 和 `CropTransformCalculator`。顶部用分段控件切换 Portrait/Landscape，各自读写独立 draft。只在手势结束、`onValueChangeFinished` 和确认时持久化；取消 discard session。确认必须先 `repository.replacePhoto` 成功再关闭；写失败保留 dialog 和旧配置。

```kotlin
@Composable
fun MeetupCardCropDialog(
    sessionId: String,
    state: MeetupCardUiState,
    onOrientationChange: (MeetupOrientation) -> Unit,
    onCropChange: (MeetupOrientation, MeetupCrop) -> Unit,
    onCropChangeFinished: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
)
```

- [x] **Step 3: 实现四个工具页**

底部四个 tab 为 Photo、Layout、Content、Style。控件固定为：三种照片来源 icon+text action；三模板单选预览；头像/人称代词/语言/状态/状态描述/短句/QR Switch；80 code point TextField；主题色 swatch；scrim Slider；三个官方装饰独立 Switch。`savingPhoto` 或操作进行中时禁用相关控件，不能重复提交。

```kotlin
private enum class MeetupEditorTab { Photo, Layout, Content, Style }

@Composable
private fun MeetupEditorTools(
    selectedTab: MeetupEditorTab,
    state: MeetupCardUiState,
    actions: MeetupEditorActions,
)
```

- [x] **Step 4: 实现响应式编辑布局**

Compact 为上方固定比例预览 + 下方工具；Medium/Expanded 为左预览 + 右工具。预览复用 `MeetupCardCanvas(interactive=false)`，不使用卡片套卡片。方向切换只改变预览，不旋转设备。页面返回前 `flushDrafts()`，不添加保存按钮。

```kotlin
@Composable
fun MeetupCardEditorScreen(
    state: MeetupCardUiState,
    onBack: () -> Unit,
    actions: MeetupEditorActions,
)
```

- [x] **Step 5: 编译编辑器**

Run: `./gradlew :composeApp:compileKotlinDesktop`

Expected: 编辑器、FileKit 和 Gallery picker 接线编译成功。

### Task 12: 实现展示页控制层与三端沉浸/常亮生命周期

**Files:**
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/display/MeetupCardDisplayScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/display/MeetupPresentationEffect.kt`
- Create: `composeApp/src/androidMain/kotlin/presentation/screens/meetup/display/MeetupPresentationEffect.android.kt`
- Create: `composeApp/src/iosMain/kotlin/presentation/screens/meetup/display/MeetupPresentationEffect.ios.kt`
- Create: `composeApp/src/desktopMain/kotlin/presentation/screens/meetup/display/MeetupPresentationEffect.desktop.kt`
- Modify: `composeApp/src/iosMain/kotlin/MainViewController.kt`
- Create: `composeApp/src/commonTest/kotlin/presentation/screens/meetup/display/MeetupControlsStateTest.kt`

- [x] **Step 1: 写控制层状态机测试**

```kotlin
@Test
fun interactionShowsControlsAndTimeoutHidesThem() = runTest {
    val state = MeetupControlsState(backgroundScope, timeout = 3.seconds)
    state.onInteraction()
    assertTrue(state.visible.value)
    advanceTimeBy(2_999)
    assertTrue(state.visible.value)
    advanceTimeBy(1)
    assertFalse(state.visible.value)
}

@Test
fun anotherInteractionRestartsTimeout() = runTest {
    val state = MeetupControlsState(backgroundScope, timeout = 3.seconds)
    state.onInteraction()
    advanceTimeBy(2_000)
    state.onInteraction()
    advanceTimeBy(2_000)
    assertTrue(state.visible.value)
}
```

- [x] **Step 2: 实现可取消状态机和控制层**

`MeetupControlsState` 只保留一个 hide Job；交互取消旧 job、显示并启动产品定义的 3 秒 timeout；`close` 取消 job。展示页轻点、触摸或鼠标活动均重置 timeout。控制层初始隐藏，仅有 Back、Edit 和当前方向图标。Back/Edit 使用 `actionInFlight` 防重复，系统返回始终 pop。

```kotlin
class MeetupControlsState(
    private val scope: CoroutineScope,
    private val timeout: Duration = 3.seconds,
) : AutoCloseable {
    val visible: StateFlow<Boolean>
    fun onInteraction()
    override fun close()
}
```

- [x] **Step 3: 定义平台效果**

```kotlin
@Composable
internal expect fun MeetupPresentationEffect(enabled: Boolean)
```

Android actual 用 `LocalActivity.current.window`：进入时记录 keep-awake flag、system bars 可见状态和 behavior；RESUMED 添加 `FLAG_KEEP_SCREEN_ON` 并隐藏 bars；ON_STOP/dispose 幂等恢复。不得改亮度或 Manifest 权限。

iOS actual 保存/恢复 `UIApplication.sharedApplication.idleTimerDisabled`。`MainViewController` 改为包裹 `ComposeUIViewController` 的 root controller，通过 `IosMeetupPresentationState` 切换 `prefersStatusBarHidden` 与 `prefersHomeIndicatorAutoHidden` 并调用 `setNeedsStatusBarAppearanceUpdate()`；后台/dispose 恢复。不要修改 Info.plist 全局策略。

Desktop actual 为幂等 no-op，不切 OS fullscreen。

- [x] **Step 4: 实现展示页**

展示页订阅 `MeetupCardUiState`，首帧立即绘制本地 Canvas。照片/装饰刷新只换对应层，不显示阻塞 spinner。手机根节点不加 `systemBarsPadding()`；Desktop 填满应用内容区。后台时动画暂停且平台效果恢复，回前台重新 acquire。

```kotlin
@Composable
fun MeetupCardDisplayScreen(
    state: MeetupCardUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
)
```

- [x] **Step 5: 跑状态测试与三端构建**

Run:

```bash
./gradlew :composeApp:desktopTest --tests '*MeetupControlsStateTest'
./gradlew :composeApp:compileKotlinDesktop :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosArm64
```

Expected: timeout 测试 PASS，APK 与 iOS framework 构建成功。

### Task 13: 接入 Navigation 3 与首页长按入口

**Files:**
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/meetup/MeetupCardRoutes.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/screens/home/HomeScreenModel.kt`
- Modify carefully: `composeApp/src/commonMain/kotlin/presentation/screens/home/HomeScreen.kt`
- Create: `composeApp/src/desktopTest/kotlin/presentation/screens/home/HomeMeetupCardGestureTest.kt`

- [x] **Step 1: 写单击/长按导航契约测试**

```kotlin
@Test
fun clickAndLongClickDispatchDifferentActions() = runComposeUiTest {
    var clicks = 0
    var longClicks = 0
    setContent {
        HomeUserAvatar(
            iconUrl = "",
            onClick = { clicks++ },
            onLongClick = { longClicks++ },
        )
    }
    onNodeWithTag("home-user-avatar").performClick()
    onNodeWithTag("home-user-avatar").performTouchInput { longClick() }
    assertEquals(1, clicks)
    assertEquals(1, longClicks)
}
```

- [x] **Step 2: 运行测试并确认失败**

Run: `./gradlew :composeApp:desktopTest --tests '*HomeMeetupCardGestureTest'`

Expected: FAIL，`HomeUserAvatar` 尚未抽取。

- [x] **Step 3: 定义最小路由 payload**

```kotlin
@Serializable
data class MeetupCardDisplayRoute(val ownerUserId: String) : AppRoute

@Serializable
data class MeetupCardEditorRoute(val ownerUserId: String) : AppRoute
```

两者按 owner ID 取得参数化 ScreenModel；路由不含 bytes、路径、配置 JSON 或用户对象。Display 的 Edit push Editor，Editor 返回 pop。

- [x] **Step 4: 抽取头像手势并保留 shared key**

将头像 Box 的 `simpleClickable` 换成 `combinedClickable(onClick, onLongClick)`，之后原样保留当前两个 `sharedBoundsBy` key/suffix 与 `.size(54.dp)`；不改 UserStateIcon、cached placeholder 或单击的 `UserProfileScreen(UserProfileVo(user), sharedSuffixKey)`。

`HomeScreenModel` 注入 repository：

```kotlin
fun meetupCardStartRoute(): AppRoute = if (meetupCardRepository.hasConfig(userId)) {
    MeetupCardDisplayRoute(userId)
} else {
    MeetupCardEditorRoute(userId)
}
```

长按只在 currentUser 非空时 push；用 route/action 闩锁防同一次长按重复入栈；当前 route 已是同 owner Display/Editor 时忽略。

- [x] **Step 5: 跑手势和导航回归测试**

Run:

```bash
./gradlew :composeApp:desktopTest --tests '*HomeMeetupCardGestureTest' --tests '*ProfileScaffoldAvatarClickTest' --tests '*AppNavigatorTest'
git diff --check
```

Expected: 单击只进资料，长按只进身份牌，现有 shared transition 测试 PASS。

### Task 14: 完成 Koin、多语言、缓存和账号移除接线

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/di/modules/NetworkModule.kt`
- Modify: `composeApp/src/commonMain/kotlin/di/modules/StorageModule.kt`
- Modify: `composeApp/src/commonMain/kotlin/di/modules/ServiceModule.kt`
- Modify: `composeApp/src/commonMain/kotlin/di/modules/PresentationModule.kt`
- Modify: `composeApp/src/androidMain/kotlin/di/modules/PlatformModule.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/di/modules/PlatformModule.ios.kt`
- Modify: `composeApp/src/desktopMain/kotlin/di/modules/PlatformModule.desktop.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/screens/home/sheet/SettingsBottomSheet.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/settings/locale/LocaleStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/settings/locale/LocaleStringsJa.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/settings/locale/LocaleStringsZhHans.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/settings/locale/LocaleStringsZhHant.kt`
- Modify: `composeApp/src/commonTest/kotlin/di/modules/PresentationModuleTest.kt`

- [x] **Step 1: 注册依赖并保持现有单例边界**

Network 注册 ProfileAppearanceApi/InventoryApi。Storage 注册 MeetupCardConfigDao、Template cache DAO 和 `MeetupCardAssetStore(FileSystem.SYSTEM, meetupCardAssetRoot(get()))`。Service 注册 production remote source、DecorationResolver、Default repository。Presentation 注册 photo preparer、两个 session store 和参数化 ViewModel：

```kotlin
viewModel { parameters ->
    MeetupCardScreenModel(
        ownerUserId = parameters.get(),
        repository = get(),
        photoSessions = get(),
    )
}
```

不得创建第二个 HttpClient、ImageLoader、PlatformImageCodec 或 Settings factory。

- [x] **Step 2: 让普通清缓存只清远端装饰副本**

`SettingsBottomSheet` 现有清缓存协程额外调用 Template cache `clearAll()` 和 asset store `clearDecorationCache()`；不得清 MeetupCardConfigDao 或 `accounts/*/photos`。文件错误通过现有 toast 报告。

```kotlin
decorationTemplateCacheDao.clearAll()
meetupCardAssetStore.clearDecorationCache()
```

- [x] **Step 3: 补齐四种语言**

在英文 base 与日/简中/繁中覆盖以下键，Composable 不得硬编码文案：

| Key | English | 简体中文 | 日本語 | 繁體中文 |
| --- | --- | --- | --- | --- |
| `meetupCardTitle` | Meetup card | 聚会身份牌 | オフ会ネームカード | 聚會身份牌 |
| `meetupCardEdit` | Edit | 编辑 | 編集 | 編輯 |
| `meetupCardPhoto` | Photo | 照片 | 写真 | 照片 |
| `meetupCardLayout` | Layout | 布局 | レイアウト | 版面 |
| `meetupCardContent` | Content | 内容 | 表示内容 | 內容 |
| `meetupCardStyle` | Style | 样式 | スタイル | 樣式 |
| `meetupCardProfileBackground` | Profile background | 资料背景图 | プロフィール背景 | 資料背景圖 |
| `meetupCardAlbum` | Photo library | 手机相册 | フォトライブラリ | 手機相簿 |
| `meetupCardGallery` | VRChat Gallery | VRChat Gallery | VRChat Gallery | VRChat Gallery |
| `meetupCardInfoBar` | Info bar | 资料栏 | 情報バー | 資料欄 |
| `meetupCardSpotlight` | Spotlight | 聚光 | スポットライト | 聚光 |
| `meetupCardSideTag` | Side tag | 侧签 | サイドタグ | 側標 |
| `meetupCardShortTextTooLong` | Keep the message within 80 characters. | 短句不能超过 80 个字符。 | メッセージは80文字以内にしてください。 | 短句不能超過 80 個字元。 |
| `meetupCardQrDescription` | VRChat profile QR code | VRChat 个人主页二维码 | VRChatプロフィールQRコード | VRChat 個人主頁 QR Code |
| `meetupCardRefreshPartial` | Some decorations could not be refreshed. | 部分资料装饰刷新失败。 | 一部の装飾を更新できませんでした。 | 部分資料裝飾重新整理失敗。 |
| `meetupCardPhotoFailed` | The selected photo could not be used. | 无法使用所选照片。 | 選択した写真を使用できません。 | 無法使用所選照片。 |

头像、人称代词、语言、状态、状态描述、二维码、头像框、资料特效、铭牌特效、竖屏、横屏、主题色和遮罩也各自建立键，四种语言结构一致。

- [x] **Step 4: 更新 DI 测试 fake**

`PresentationModuleTest` 注入 fake repository、PlatformImageCodec 和 session stores，断言参数化 ScreenModel 可解析且两次 route scope 得到不同实例；不要测试简单构造或字段相等。

- [x] **Step 5: 跑模块测试**

Run:

```bash
./gradlew :composeApp:desktopTest --tests '*PresentationModuleTest' --tests '*AccountCacheManagerTest'
./gradlew :composeApp:compileKotlinDesktop
git diff --check
```

Expected: DI、账号清理和语言编译通过；普通清缓存保留配置与相册照片。

### Task 15: 全平台验证与人工验收

**Files:**
- Modify only when a verification failure identifies a root cause in files already listed above.

- [x] **Step 1: 运行共享/Desktop 测试**

Run: `./gradlew :composeApp:desktopTest`

Expected: `BUILD SUCCESSFUL`，无遗留协程、未释放 Skia frame 或 Main dispatcher 污染。

- [x] **Step 2: 构建 Android 与 iOS**

Run:

```bash
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:linkDebugFrameworkIosArm64
```

Expected: Android APK 与 iOS arm64 framework 生成；不修改 Manifest 权限、签名、applicationId 或 Info.plist 全局系统栏策略。

- [ ] **Step 3: 执行移动端人工矩阵**

在 Android/iOS 真机逐项确认：

1. 首页头像单击仍进本人资料，长按首次进编辑器；配置后直接展示。
2. 飞行模式下立即显示上次照片和完整 Display Name，无空白等待页。
3. 三种照片来源可用；竖横裁剪独立且焦点合理。
4. 三模板在窄屏、横屏和大字体下名字、QR、字段互不覆盖。
5. 动画成功、动画失败回退 base、两者失败关闭单个装饰时主体均可用。
6. 轻点显示控制，3 秒隐藏；返回/编辑重复点击不重复导航。
7. 展示时常亮且亮度不变；返回、后台、异常销毁后系统栏与常亮恢复。
8. 切换/移除账号后不串用配置或相册图片。

- [ ] **Step 4: 执行 Desktop 人工矩阵**

确认 Desktop 能编辑、预览、选择本地/Gallery 图片；展示填满应用内容区但不强制 OS 全屏；动画播放与离页释放正常。

- [x] **Step 5: 最终工作区核对**

Run:

```bash
git diff --check
git status --short
```

Expected: 无空白错误；只有本功能文件与执行前已有用户改动。不得出现 `build/`、IDE 状态、`.xcworkspace/`、临时下载或执行日志。交付说明实际命令、人工平台、未覆盖真机风险和保留的无关改动。
