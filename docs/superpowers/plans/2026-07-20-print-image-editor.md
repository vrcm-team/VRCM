# VRChat Print Image Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a cross-platform 16:9 Print editor that accepts JPEG/PNG/HEIC when the platform can decode them, renders the selected crop into the VRChat Print PNG canvas, and uploads it without losing editor state on failure.

**Architecture:** Common Kotlin owns crop geometry, editor state, Compose rendering, output-canvas composition, and upload orchestration. A small `PlatformImageCodec` implementation per target normalizes source orientation and converts between encoded bytes and `ImageBitmap`; `PrintUploadService` isolates authentication and `PrintsApi` from the editor UI.

**Tech Stack:** Kotlin Multiplatform 2.2.20, Compose Multiplatform 1.10.3, Material 3, Voyager, Koin, FileKit 0.11.0, Ktor, Android Bitmap/ExifInterface, iOS UIKit, Desktop Skia.

---

## File Structure

Create these focused common files under `composeApp/src/commonMain/kotlin/presentation/screens/gallery/editor/`:

- `PrintImageModels.kt`: source, dimensions, editor state, render plan, canvas constants, failures.
- `CropTransformCalculator.kt`: pure crop geometry and state transitions.
- `PlatformImageCodec.kt`: platform codec boundary.
- `PrintImageProcessor.kt`: validation, final canvas composition, PNG contract checks.
- `PrintImageEditorScreenModel.kt`: editor state machine and upload orchestration.
- `PrintImageEditorScreen.kt`: responsive Compose UI and gestures.

Create platform codecs:

- `composeApp/src/androidMain/kotlin/presentation/screens/gallery/editor/AndroidPlatformImageCodec.kt`
- `composeApp/src/iosMain/kotlin/presentation/screens/gallery/editor/IosPlatformImageCodec.kt`
- `composeApp/src/desktopMain/kotlin/presentation/screens/gallery/editor/DesktopPlatformImageCodec.kt`

Create `composeApp/src/commonMain/kotlin/service/PrintUploadService.kt` for authenticated upload. Remove the unused `ImageEditorScreen.kt` and `ImageEditorConfig.kt` fake implementation.

Tests live beside existing test conventions:

- `composeApp/src/commonTest/kotlin/presentation/screens/gallery/editor/CropTransformCalculatorTest.kt`
- `composeApp/src/commonTest/kotlin/presentation/screens/gallery/editor/PrintImageProcessorTest.kt`
- `composeApp/src/commonTest/kotlin/presentation/screens/gallery/editor/PrintImageEditorScreenModelTest.kt`
- `composeApp/src/desktopTest/kotlin/presentation/screens/gallery/editor/DesktopPlatformImageCodecTest.kt`

## Task 1: Crop Model and Geometry

**Files:**
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/gallery/editor/PrintImageModels.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/gallery/editor/CropTransformCalculator.kt`
- Test: `composeApp/src/commonTest/kotlin/presentation/screens/gallery/editor/CropTransformCalculatorTest.kt`

- [ ] **Step 1: Write failing geometry tests**

Cover landscape, portrait, odd quarter turns, combined zoom/pan, reset, resize stability, and all four edges. Use these public types consistently:

```kotlin
data class ImageSize(val width: Int, val height: Int)

data class CropTransform(
    val centerOffsetX: Float = 0f,
    val centerOffsetY: Float = 0f,
    val zoom: Float = 1f,
    val quarterTurns: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
)

data class RenderGeometry(
    val imageWidth: Float,
    val imageHeight: Float,
    val translationX: Float,
    val translationY: Float,
    val rotationDegrees: Float,
    val scaleXSign: Float,
    val scaleYSign: Float,
)
```

Representative assertions:

```kotlin
@Test
fun portraitImageAlwaysCoversSixteenByNineViewport() {
    val geometry = calculator.geometry(
        source = ImageSize(1080, 1920),
        viewport = ImageSize(1600, 900),
        transform = CropTransform(),
    )
    assertTrue(geometry.imageWidth >= 1600f)
    assertTrue(geometry.imageHeight >= 900f)
}

@Test
fun panIsClampedBeforeItCanRevealEmptyPixels() {
    val updated = calculator.transform(
        source = ImageSize(2400, 1080),
        viewport = ImageSize(1600, 900),
        current = CropTransform(),
        panX = 10_000f,
        panY = 10_000f,
        zoomChange = 1f,
    )
    val geometry = calculator.geometry(ImageSize(2400, 1080), ImageSize(1600, 900), updated)
    assertTrue(kotlin.math.abs(geometry.translationX) <= (geometry.imageWidth - 1600f) / 2f)
    assertTrue(kotlin.math.abs(geometry.translationY) <= (geometry.imageHeight - 900f) / 2f)
}
```

- [ ] **Step 2: Run the test and verify red**

Run:

```bash
./gradlew :composeApp:desktopTest --tests '*CropTransformCalculatorTest'
```

Expected: compilation failure because the editor model and calculator do not exist.

- [ ] **Step 3: Implement the pure calculator**

Implement `CropTransformCalculator` without Compose dependencies:

```kotlin
class CropTransformCalculator(
    private val maxZoom: Float = 3f,
) {
    fun geometry(source: ImageSize, viewport: ImageSize, transform: CropTransform): RenderGeometry

    fun transform(
        source: ImageSize,
        viewport: ImageSize,
        current: CropTransform,
        panX: Float,
        panY: Float,
        zoomChange: Float,
    ): CropTransform

    fun rotate(source: ImageSize, viewport: ImageSize, current: CropTransform, turns: Int): CropTransform
    fun flipHorizontal(current: CropTransform): CropTransform
    fun flipVertical(current: CropTransform): CropTransform
    fun reset(): CropTransform = CropTransform()
}
```

Normalize offsets as fractions of viewport width/height. Compute the cover scale with `max(viewport.width / rotatedWidth, viewport.height / rotatedHeight)`, multiply it by `zoom.coerceIn(1f, 3f)`, then clamp translation to half of the displayed overflow. Normalize `quarterTurns` with `((value % 4) + 4) % 4`.

- [ ] **Step 4: Run geometry tests**

Run the same targeted command. Expected: all `CropTransformCalculatorTest` tests pass.

- [ ] **Step 5: Commit the geometry unit**

```bash
git add composeApp/src/commonMain/kotlin/presentation/screens/gallery/editor composeApp/src/commonTest/kotlin/presentation/screens/gallery/editor/CropTransformCalculatorTest.kt
git commit -m "feat: add print crop geometry"
```

## Task 2: Common Image Processor and Output Contract

**Files:**
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/gallery/editor/PlatformImageCodec.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/gallery/editor/PrintImageProcessor.kt`
- Test: `composeApp/src/commonTest/kotlin/presentation/screens/gallery/editor/PrintImageProcessorTest.kt`

- [ ] **Step 1: Write failing processor tests with a fake codec**

Define the boundary:

```kotlin
data class SelectedImage(val fileName: String, val bytes: ByteArray)
data class DecodedImage(val bitmap: ImageBitmap, val originalSize: ImageSize)
data class PreparedImage(val preview: ImageBitmap, val originalSize: ImageSize)

interface PlatformImageCodec {
    suspend fun decode(bytes: ByteArray, maxDimension: Int): DecodedImage
    suspend fun encodePng(bitmap: ImageBitmap): ByteArray
}

interface PrintImageProcessor {
    suspend fun prepare(source: SelectedImage): Result<PreparedImage>
    suspend fun render(source: SelectedImage, transform: CropTransform): Result<ByteArray>
}
```

Use a fake codec that captures the output `ImageBitmap`. Assert:

- files over 50 MiB fail before decode;
- decoded images over 100 megapixels fail before render;
- render requests a final decode with max dimension 5760;
- output canvas passed to the encoder is 2048×1440;
- pixels outside `(64, 69, 1920, 1080)` are opaque white;
- invalid PNG signature or IHDR dimensions from the encoder becomes `EncodeFailed`.

- [ ] **Step 2: Run the processor test and verify red**

```bash
./gradlew :composeApp:desktopTest --tests '*PrintImageProcessorTest'
```

Expected: compilation failure because processor contracts do not exist.

- [ ] **Step 3: Implement models, failures, validator, and renderer**

Add fixed configuration and typed failures to `PrintImageModels.kt`:

```kotlin
data class PrintCanvasSpec(
    val canvasWidth: Int = 2048,
    val canvasHeight: Int = 1440,
    val contentWidth: Int = 1920,
    val contentHeight: Int = 1080,
    val contentOffsetX: Int = 64,
    val contentOffsetY: Int = 69,
)

sealed class PrintImageFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object FileTooLarge : PrintImageFailure("Selected image exceeds 50 MiB")
    data object ImageDimensionsTooLarge : PrintImageFailure("Selected image exceeds 100 megapixels")
    class UnsupportedFormat(cause: Throwable? = null) : PrintImageFailure("Unsupported image format", cause)
    class DecodeFailed(cause: Throwable) : PrintImageFailure("Image decode failed", cause)
    class RenderFailed(cause: Throwable) : PrintImageFailure("Image render failed", cause)
    class EncodeFailed(cause: Throwable? = null) : PrintImageFailure("PNG encode failed", cause)
}
```

Implement `DefaultPrintImageProcessor(codec, calculator, spec)` so `prepare` decodes at 2048, `render` decodes at 5760, creates an opaque `ImageBitmap(2048, 1440)`, fills it white, clips to the content rectangle, and draws the decoded image with geometry calculated against a 1920×1080 viewport. Encode through `PlatformImageCodec`, then parse the PNG signature and IHDR width/height before returning bytes.

The final draw must use the same `CropTransformCalculator.geometry` method as the preview; do not screenshot Compose content.

- [ ] **Step 4: Run processor and geometry tests**

```bash
./gradlew :composeApp:desktopTest --tests '*PrintImageProcessorTest' --tests '*CropTransformCalculatorTest'
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit the processing unit**

```bash
git add composeApp/src/commonMain/kotlin/presentation/screens/gallery/editor composeApp/src/commonTest/kotlin/presentation/screens/gallery/editor/PrintImageProcessorTest.kt
git commit -m "feat: render vrchat print png canvas"
```

## Task 3: Platform Image Codecs

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`
- Create: `composeApp/src/androidMain/kotlin/presentation/screens/gallery/editor/AndroidPlatformImageCodec.kt`
- Create: `composeApp/src/iosMain/kotlin/presentation/screens/gallery/editor/IosPlatformImageCodec.kt`
- Create: `composeApp/src/desktopMain/kotlin/presentation/screens/gallery/editor/DesktopPlatformImageCodec.kt`
- Modify: `composeApp/src/androidMain/kotlin/di/modules/PlatformModule.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/di/modules/PlatformModule.ios.kt`
- Modify: `composeApp/src/desktopMain/kotlin/di/modules/PlatformModule.desktop.kt`
- Test: `composeApp/src/desktopTest/kotlin/presentation/screens/gallery/editor/DesktopPlatformImageCodecTest.kt`

- [ ] **Step 1: Add a failing Desktop codec contract test**

Generate a small PNG in the test, decode it, encode it again, and assert dimensions/signature. Add an EXIF-oriented JPEG fixture under `composeApp/src/desktopTest/resources/print-editor/orientation-6.jpg`; assert the prepared result has swapped width/height. A malformed byte array must throw `UnsupportedFormat` or `DecodeFailed`, never return an empty bitmap.

- [ ] **Step 2: Run the Desktop test and verify red**

```bash
./gradlew :composeApp:desktopTest --tests '*DesktopPlatformImageCodecTest'
```

Expected: compilation failure because `DesktopPlatformImageCodec` does not exist.

- [ ] **Step 3: Add Android ExifInterface and Material icon dependencies**

Add version `androidx-exifinterface = "1.4.2"`, library alias `androidx-exifinterface`, and enable `compose.materialIconsExtended`. Add ExifInterface to `androidMain` only.

- [ ] **Step 4: Implement Android decode/encode**

Use `BitmapFactory.Options.inJustDecodeBounds` to validate MIME and dimensions, compute a power-of-two `inSampleSize`, decode, then apply all eight `ExifInterface.TAG_ORIENTATION` values with `android.graphics.Matrix`. Scale the oriented bitmap so its longest edge is at most `maxDimension`, expose it with `asImageBitmap()`, and encode with `asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)`.

Class signature:

```kotlin
class AndroidPlatformImageCodec : PlatformImageCodec {
    override suspend fun decode(bytes: ByteArray, maxDimension: Int): DecodedImage
    override suspend fun encodePng(bitmap: ImageBitmap): ByteArray
}
```

Treat a null decode or a MIME outside JPEG, PNG, HEIC, and HEIF as `UnsupportedFormat`.

- [ ] **Step 5: Implement iOS decode/encode**

Create `NSData` from the pinned source bytes and decode with `UIImage.imageWithData` so system-supported HEIC works. Use `UIGraphicsBeginImageContextWithOptions` and `UIImage.drawInRect` to normalize orientation and downsample into the requested pixel dimensions, convert the normalized UIKit image to PNG bytes, then call Compose `decodeToImageBitmap`. Encode the final Compose bitmap through `asSkiaBitmap()`, `Image.makeFromBitmap`, and `encodeToData(EncodedImageFormat.PNG)`.

Keep `NSData`/`ByteArray` conversion helpers private to the iOS file and copy bytes with `memcpy`.

- [ ] **Step 6: Implement Desktop decode/encode**

Use `org.jetbrains.skia.Codec` to read encoded format, raw size, and `encodedOrigin`. Render `Image.makeFromEncoded(bytes)` into a target raster surface using `encodedOrigin.toMatrix(width, height)`, with width/height swapped when required, then return `toComposeImageBitmap()`. Encode with `Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(PNG)`.

Accept only JPEG, PNG, and HEIF/HEIC when the installed Skia codec successfully creates a decoder.

- [ ] **Step 7: Register codecs in platform Koin modules**

Each platform module binds its implementation:

```kotlin
singleOf(::AndroidPlatformImageCodec) bind PlatformImageCodec::class
```

Use the corresponding class on iOS and Desktop. Register `CropTransformCalculator` and `DefaultPrintImageProcessor` in `presentationModule` later, not in platform files.

- [ ] **Step 8: Run codec test and compile all targets**

```bash
./gradlew :composeApp:desktopTest --tests '*DesktopPlatformImageCodecTest'
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

Expected: Desktop codec tests pass and both platform compilations succeed.

- [ ] **Step 9: Commit platform codecs**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts composeApp/src/androidMain composeApp/src/iosMain composeApp/src/desktopMain composeApp/src/desktopTest
git commit -m "feat: add platform print image codecs"
```

## Task 4: Upload Service and Editor State Machine

**Files:**
- Create: `composeApp/src/commonMain/kotlin/service/PrintUploadService.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/gallery/editor/PrintImageEditorScreenModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/screens/gallery/GalleryScreenModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/di/modules/PresentationModule.kt`
- Test: `composeApp/src/commonTest/kotlin/presentation/screens/gallery/editor/PrintImageEditorScreenModelTest.kt`

- [ ] **Step 1: Write failing state-machine tests with a fake uploader**

Define a narrow uploader contract so the model can use a fake:

```kotlin
interface PrintUploader {
    suspend fun upload(imageBytes: ByteArray, fileName: String): Result<PrintData>
}

class PrintUploadService(
    private val authService: AuthService,
    private val printsApi: PrintsApi,
) : PrintUploader
```

State and events:

```kotlin
enum class EditorPhase { Ready, Processing, Uploading }

data class PrintImageEditorState(
    val prepared: PreparedImage,
    val transform: CropTransform = CropTransform(),
    val phase: EditorPhase = EditorPhase.Ready,
    val error: EditorError? = null,
)

sealed interface EditorError {
    data class Processing(val failure: PrintImageFailure) : EditorError
    data class Upload(val detail: String) : EditorError
}

sealed interface EditorEvent { data object Uploaded : EditorEvent }
```

Use a fake `PrintUploader` and fake `PrintImageProcessor`. Tests must prove that two upload clicks trigger one renderer/uploader call, processing failure returns to `Ready`, upload failure keeps the cached PNG, an edit invalidates the cache, and only successful upload emits `Uploaded`. The real upload request remains covered by `PrintsApiTest` with Ktor MockEngine; no mocking framework is introduced for the concrete `AuthService`.

- [ ] **Step 2: Run targeted tests and verify red**

```bash
./gradlew :composeApp:desktopTest --tests '*PrintImageEditorScreenModelTest'
```

Expected: compilation failure because service and model do not exist.

- [ ] **Step 3: Implement `PrintUploadService`**

Call `authService.reTryAuthCatching { printsApi.uploadPrint(imageBytes, fileName) }` and return the `Result` unchanged. Do not emit Toasts, navigate, or refresh gallery state.

- [ ] **Step 4: Implement the editor model**

Use this constructor so production uses the default dispatcher and tests can pass `Dispatchers.Unconfined`:

```kotlin
class PrintImageEditorScreenModel(
    private val source: SelectedImage,
    prepared: PreparedImage,
    private val calculator: CropTransformCalculator,
    private val processor: PrintImageProcessor,
    private val uploader: PrintUploader,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ScreenModel
```

Implement `panAndZoom`, `rotateLeft`, `rotateRight`, `flipHorizontal`, `flipVertical`, `reset`, and `upload`. Every edit clears `cachedPng`; `upload` reuses it after network failure. Generate `print-<epochMillis>.png` only after render succeeds.

Expose immutable `StateFlow<PrintImageEditorState>` and `SharedFlow<EditorEvent>`. Log no image bytes and keep failures as typed UI state.

- [ ] **Step 5: Remove fire-and-forget Print upload from gallery model**

Delete `PrintActionMessages`, `GalleryScreenModel.uploadPrint`, and now-unused imports. Keep `refreshPrints` as the single list refresh entry point.

- [ ] **Step 6: Register common services and parameterized model**

In `presentationModule`:

```kotlin
singleOf(::CropTransformCalculator)
singleOf(::DefaultPrintImageProcessor) bind PrintImageProcessor::class
singleOf(::PrintUploadService) bind PrintUploader::class
factory { (source: SelectedImage, prepared: PreparedImage) ->
    PrintImageEditorScreenModel(source, prepared, get(), get(), get())
}
```

- [ ] **Step 7: Run state and upload tests**

Run the targeted command again. Expected: all selected tests pass.

- [ ] **Step 8: Commit orchestration**

```bash
git add composeApp/src/commonMain/kotlin/service/PrintUploadService.kt composeApp/src/commonMain/kotlin/presentation/screens/gallery composeApp/src/commonMain/kotlin/di/modules/PresentationModule.kt composeApp/src/commonTest
git commit -m "feat: orchestrate print processing and upload"
```

## Task 5: Editor UI, Navigation, and Localization

**Files:**
- Delete: `composeApp/src/commonMain/kotlin/presentation/screens/gallery/ImageEditorScreen.kt`
- Delete: `composeApp/src/commonMain/kotlin/presentation/screens/gallery/ImageEditorConfig.kt`
- Create: `composeApp/src/commonMain/kotlin/presentation/screens/gallery/editor/PrintImageEditorScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/screens/gallery/GalleryTabPager.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/settings/locale/LocaleStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/settings/locale/LocaleStringsEn.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/settings/locale/LocaleStringsJa.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/settings/locale/LocaleStringsZhHans.kt`
- Modify: `composeApp/src/commonMain/kotlin/presentation/settings/locale/LocaleStringsZhHant.kt`
- Test: `composeApp/src/commonTest/kotlin/presentation/settings/locale/LocaleActionMessagesTest.kt`

- [ ] **Step 1: Extend the locale completeness test and verify red**

Add editor keys for title, upload, rotate left/right, flip horizontal/vertical, zoom, reset, processing, unsupported format, too large, decode failure, render failure, upload failure, and uploaded. Assert all four locale objects provide nonblank values and retain `%s` where an error detail is inserted.

- [ ] **Step 2: Add localized values**

Add all keys to `LocaleStrings` and explicit English, Japanese, Simplified Chinese, and Traditional Chinese values. Do not hardcode user-visible editor strings in the screen or model.

- [ ] **Step 3: Implement the editor screen**

Build a `Scaffold` with `CenterAlignedTopAppBar`, a dark center workspace, fixed 16:9 crop area, `Canvas` preview, rule-of-thirds overlay, and responsive bottom controls. Use Material extended icons `RotateLeft`, `RotateRight`, `Flip`, and `RestartAlt`; rotate the `Flip` icon 90° for vertical flip. Wrap unfamiliar desktop actions in Material 3 tooltips.

Use `detectTransformGestures` for pan/pinch and `PointerEventType.Scroll` for mouse-wheel zoom. Draw preview using `CropTransformCalculator.geometry`, the same geometry used by final output. A slider controls zoom from 1 to 3 without changing layout dimensions.

Collect `EditorEvent.Uploaded`, emit the localized success Toast, invoke `onUploaded`, and pop the Voyager screen. Show localized recoverable errors in a Snackbar. Disable back, tools, and upload during `Processing` and `Uploading`, and show a progress indicator inside the upload action.

- [ ] **Step 4: Route the picker through preparation and navigation**

In `GalleryTabPager.Content`, obtain `LocalNavigator.currentOrThrow` and `PrintImageProcessor`. Configure:

```kotlin
FileKitType.File("jpg", "jpeg", "png", "heic", "heif")
```

After selection, read bytes, create `SelectedImage`, call `processor.prepare`, and push `PrintImageEditorScreen(source, prepared) { galleryScreenModel.refreshPrints() }` only on success. Map typed failures to localized Toasts on the gallery page. Remove the direct call to the old `galleryScreenModel.uploadPrint`.

- [ ] **Step 5: Delete fake editor files**

Delete both unused files and verify `rg 'ImageEditorConfig|ImageEditorScreen' composeApp/src` only finds the new Print-specific editor name where expected.

- [ ] **Step 6: Run locale tests and compile Desktop**

```bash
./gradlew :composeApp:desktopTest --tests '*LocaleActionMessagesTest'
./gradlew :composeApp:compileKotlinDesktop
```

Expected: tests pass and Desktop source compiles.

- [ ] **Step 7: Commit the user workflow**

```bash
git add composeApp/src/commonMain
git commit -m "feat: add print image editor workflow"
```

## Task 6: Full Verification and Contract Hardening

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/network/api/prints/PrintsApiTest.kt`
- Modify only if failures reveal a contract bug: files introduced in Tasks 1-5.

- [ ] **Step 1: Extend the API multipart test**

Capture the upload request body and headers with Ktor MockEngine. Verify the request contains `Content-Type: image/png`, a `.png` filename, and the timestamp field. Retain the existing test proving non-PNG bytes are rejected before any request.

- [ ] **Step 2: Run all Desktop/common tests**

```bash
./gradlew :composeApp:desktopTest
```

Expected: all tests pass with no skipped editor tests.

- [ ] **Step 3: Compile all supported targets**

```bash
./gradlew :composeApp:compileKotlinDesktop
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

Expected: all three commands exit successfully.

- [ ] **Step 4: Inspect the final diff**

```bash
git diff --check HEAD~4..HEAD
git status --short
```

Expected: no whitespace errors and a clean worktree. Confirm no original JPEG/HEIC bytes are sent to `PrintsApi`, no platform image class appears in `commonMain`, and no user-visible editor string is hardcoded.

- [ ] **Step 5: Perform manual acceptance where credentials/devices are available**

Verify touch drag/pinch, desktop drag/wheel/slider, all transforms, reset, disabled duplicate upload, network failure retry, and a real VRC+ upload. Inspect the resulting Print in VRChat for `(64, 69)` placement. If credentials are unavailable, record this single unverified external contract explicitly in the handoff.

- [ ] **Step 6: Commit verification fixes**

```bash
git add composeApp
git commit -m "test: cover print image editor workflow"
```

Skip this commit when Step 1 required no changes beyond a commit already made in Task 5; do not create an empty commit.
