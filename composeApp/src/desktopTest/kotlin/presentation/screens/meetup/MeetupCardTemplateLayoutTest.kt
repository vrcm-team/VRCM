package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppContentSize
import io.github.vrcmteam.vrcm.service.meetup.DecorationSlot
import io.github.vrcmteam.vrcm.service.meetup.ResolvedDecoration
import io.github.vrcmteam.vrcm.storage.meetup.MEETUP_QR_MAX_CODES
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardTemplate
import io.github.vrcmteam.vrcm.storage.meetup.MeetupGroupSnapshot
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import io.github.vrcmteam.vrcm.storage.meetup.MeetupProfileSnapshot
import io.github.vrcmteam.vrcm.storage.meetup.MeetupQrLinkType
import io.github.vrcmteam.vrcm.storage.meetup.defaultMeetupCardConfig
import androidx.compose.ui.unit.DpSize
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 模板布局回归：这些槽位在窄屏、双二维码和大字体下反复调整过，
 * 靠人工目检成本高且容易漏，这里用真实测量出的边界做断言。
 */
@OptIn(ExperimentalTestApi::class)
class MeetupCardTemplateLayoutTest {

    private val cardWidth = 360.dp
    private val cardHeight = 780.dp

    @Test
    fun infoBarFitsTwoQrCodesWithoutStarvingFields() = runComposeUiTest {
        renderTemplate(MeetupCardTemplate.InfoBar, MeetupOrientation.Portrait)

        assertQrAndFieldsShareTheRow("资料栏")
        assertNoOverlap(
            onNodeWithTag(MeetupCardTestTags.Nameplate).getBoundsInRoot(),
            onNodeWithTag(MeetupCardTestTags.QrCodes).getBoundsInRoot(),
            "资料栏",
        )
    }

    @Test
    fun spotlightFitsTwoQrCodesWithoutStarvingFields() = runComposeUiTest {
        renderTemplate(MeetupCardTemplate.Spotlight, MeetupOrientation.Portrait)

        assertQrAndFieldsShareTheRow("聚光")
        assertNoOverlap(
            onNodeWithTag(MeetupCardTestTags.Nameplate).getBoundsInRoot(),
            onNodeWithTag(MeetupCardTestTags.QrCodes).getBoundsInRoot(),
            "聚光",
        )
    }

    @Test
    fun largeFontStillKeepsNameplateClearOfQrCodesAndInsideCard() = runComposeUiTest {
        // 系统字体放大是名字挤压二维码最容易翻车的场景。
        renderTemplate(MeetupCardTemplate.InfoBar, MeetupOrientation.Portrait, fontScale = 1.5f)

        val nameplate = onNodeWithTag(MeetupCardTestTags.Nameplate).getBoundsInRoot()
        assertNoOverlap(nameplate, onNodeWithTag(MeetupCardTestTags.QrCodes).getBoundsInRoot(), "大字体")
        assertTrue(
            nameplate.right.value <= cardWidth.value + 1f,
            "大字体下铭牌右边界 ${nameplate.right} 超出了卡片宽度 $cardWidth",
        )
        assertQrAndFieldsShareTheRow("大字体")
    }

    @Test
    fun landscapeSpotlightDoesNotStretchGroupBannerAcrossTheCard() = runComposeUiTest {
        setContent {
            MeetupTestHost(fontScale = 1f) {
                Box(modifier = Modifier.size(cardHeight, cardWidth)) {
                    val state = testState(MeetupCardTemplate.Spotlight)
                    MeetupCardTemplateContent(
                        state = state.copy(
                            config = state.config.copy(
                                showRepresentedGroup = true,
                                profile = state.config.profile.copy(
                                    representedGroup = MeetupGroupSnapshot(name = "VRCM Community"),
                                ),
                            ),
                        ),
                        orientation = MeetupOrientation.Landscape,
                        modifier = Modifier.size(cardHeight, cardWidth),
                    )
                }
            }
        }

        val banner = onNodeWithTag(MeetupCardTestTags.GroupBanner).getBoundsInRoot()
        val bannerWidth = banner.right.value - banner.left.value
        assertTrue(
            bannerWidth <= cardWidth.value + 1f,
            "聚光横屏的群组横幅宽 ${bannerWidth}dp，仍然横跨了整张卡片",
        )
    }

    /** 两个二维码必须完整留在卡片内，且不能把状态/语言那一列挤到不可用。 */
    private fun SemanticsNodeInteractionsProvider.assertQrAndFieldsShareTheRow(label: String) {
        val qr = onNodeWithTag(MeetupCardTestTags.QrCodes).getBoundsInRoot()
        val fields = onNodeWithTag(MeetupCardTestTags.Fields).getBoundsInRoot()
        assertTrue(
            qr.left.value >= -1f && qr.right.value <= cardWidth.value + 1f,
            "$label 模板中二维码 $qr 被挤出了卡片宽度 $cardWidth",
        )
        val fieldsWidth = fields.right.value - fields.left.value
        assertTrue(
            fieldsWidth >= 96f,
            "$label 模板中字段列只剩 ${fieldsWidth}dp，两个二维码把它挤没了",
        )
        assertNoOverlap(fields, qr, label)
    }

    @Test
    fun sideTagContentStaysInsideBandAndLeavesCentreFree() = runComposeUiTest {
        renderTemplate(MeetupCardTemplate.SideTag, MeetupOrientation.Portrait)

        val band = onNodeWithTag(MeetupCardTestTags.SideBand).getBoundsInRoot()
        val nameplate = onNodeWithTag(MeetupCardTestTags.Nameplate).getBoundsInRoot()
        val qr = onNodeWithTag(MeetupCardTestTags.QrCodes).getBoundsInRoot()

        assertTrue(
            band.right.value < cardWidth.value * 0.7f,
            "侧栏宽度 ${band.right} 占满了卡片，没有给中央主体留出空间",
        )
        listOf("铭牌" to nameplate, "二维码" to qr).forEach { (label, bounds) ->
            assertTrue(
                bounds.right.value <= band.right.value + 1f,
                "$label 右边界 ${bounds.right} 溢出了侧栏 ${band.right}",
            )
            assertTrue(
                bounds.bottom.value <= cardHeight.value + 1f,
                "$label 底部 ${bounds.bottom} 溢出了卡片高度 $cardHeight",
            )
        }
        assertEveryQrCodeIsWhole("竖屏侧签", DpRect(0.dp, 0.dp, band.right, cardHeight))
    }

    /** 横屏侧签只有一屏高，二维码必须能整块放下，而不是被信息挤出卡片。 */
    @Test
    fun landscapeSideTagKeepsQrCodesFullyVisible() = runComposeUiTest {
        setContent {
            MeetupTestHost(fontScale = 1f) {
                Box(modifier = Modifier.size(cardHeight, cardWidth)) {
                    MeetupCardTemplateContent(
                        state = testState(MeetupCardTemplate.SideTag),
                        orientation = MeetupOrientation.Landscape,
                        modifier = Modifier.size(cardHeight, cardWidth),
                    )
                }
            }
        }

        val band = onNodeWithTag(MeetupCardTestTags.SideBand).getBoundsInRoot()
        val qr = onNodeWithTag(MeetupCardTestTags.QrCodes).getBoundsInRoot()
        val nameplate = onNodeWithTag(MeetupCardTestTags.Nameplate).getBoundsInRoot()

        assertTrue(
            qr.bottom.value <= cardWidth.value + 1f && qr.top.value >= -1f,
            "横屏侧签二维码 $qr 超出了卡片高度 $cardWidth",
        )
        assertTrue(
            qr.right.value <= band.right.value + 1f,
            "横屏侧签二维码右边界 ${qr.right} 溢出了侧栏 ${band.right}",
        )
        assertEveryQrCodeIsWhole("横屏侧签", DpRect(0.dp, 0.dp, band.right, cardWidth))
        assertNoOverlap(nameplate, qr, "横屏侧签")
    }

    /** 与模板实现里的二维码尺寸保持一致；变了就该在这里显式改。 */
    private val MeetupQrSizeForTest = 68.dp

    /**
     * 选满 [MEETUP_QR_MAX_CODES] 个码时，每一个都必须整块画在 [bounds] 里。
     * 只量二维码容器的边界不够：码会换行换列，空间不足时还会被压成 0 高，
     * 这两种情况下容器边界都还"在卡片内"，但用户其实扫不到那个码。
     */
    private fun SemanticsNodeInteractionsProvider.assertEveryQrCodeIsWhole(
        label: String,
        bounds: DpRect,
    ) {
        val codes = onAllNodesWithTag(MeetupCardTestTags.QrCode)
            .fetchSemanticsNodes()
            .size
        assertTrue(
            codes == MEETUP_QR_MAX_CODES,
            "$label 只渲染了 $codes 个二维码，选满的 $MEETUP_QR_MAX_CODES 个没有全部出现",
        )
        repeat(codes) { index ->
            val code = onAllNodesWithTag(MeetupCardTestTags.QrCode)[index].getBoundsInRoot()
            val width = code.right.value - code.left.value
            val height = code.bottom.value - code.top.value
            assertTrue(
                width >= MeetupQrSizeForTest.value - 1f && height >= MeetupQrSizeForTest.value - 1f,
                "$label 第 ${index + 1} 个二维码被压成了 ${width}x${height}dp，扫不出来",
            )
            assertTrue(
                code.left.value >= bounds.left.value - 1f &&
                    code.right.value <= bounds.right.value + 1f &&
                    code.top.value >= bounds.top.value - 1f &&
                    code.bottom.value <= bounds.bottom.value + 1f,
                "$label 第 ${index + 1} 个二维码 $code 溢出了可用区域 $bounds",
            )
        }
    }

    private fun SemanticsNodeInteractionsProvider.assertNoOverlap(
        first: DpRect,
        second: DpRect,
        label: String,
    ) {
        val overlaps = first.left < second.right && second.left < first.right &&
            first.top < second.bottom && second.top < first.bottom
        assertTrue(!overlaps, "$label 模板中铭牌 $first 与二维码 $second 重叠了")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.renderTemplate(
        template: MeetupCardTemplate,
        orientation: MeetupOrientation,
        fontScale: Float = 1f,
    ) {
        setContent {
            MeetupTestHost(fontScale = fontScale) {
                Box(modifier = Modifier.size(cardWidth, cardHeight)) {
                    MeetupCardTemplateContent(
                        state = testState(template),
                        orientation = orientation,
                        modifier = Modifier.size(cardWidth, cardHeight),
                    )
                }
            }
        }
    }

    @Composable
    private fun MeetupTestHost(fontScale: Float, content: @Composable () -> Unit) {
        val imageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(
                    Interceptor { chain ->
                        ErrorResult(
                            image = null,
                            request = chain.request,
                            throwable = IllegalStateException("No network in layout tests"),
                        )
                    },
                )
            }
            .build()
        KoinApplication(
            application = {
                modules(
                    module {
                        single<PlatformContext> { PlatformContext.INSTANCE }
                        single<ImageLoader> { imageLoader }
                    },
                )
            },
        ) {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalAppContentSize provides DpSize(cardWidth, cardHeight),
                LocalDensity provides Density(base.density, fontScale),
            ) {
                MaterialTheme { content() }
            }
        }
    }

    /** 打开全部字段、放满二维码和装饰槽位——最挤的一种组合。 */
    private fun testState(template: MeetupCardTemplate) = MeetupCardUiState(
        ownerUserId = "usr_layout-test",
        displayName = "A Very Long VRChat Display Name",
        config = defaultMeetupCardConfig("usr_layout-test").copy(
            template = template,
            showAvatar = true,
            showPronouns = true,
            showLanguages = true,
            showStatus = true,
            showStatusDescription = true,
            showShortText = true,
            shortText = "很高兴在这次线下聚会见到你",
            showQrCode = true,
            // 两种内置码加两条资料链接码，正好用满 MEETUP_QR_MAX_CODES。
            qrLinkTypes = MeetupQrLinkType.entries,
            qrProfileLinks = listOf("https://x.com/someone", "https://github.com/someone"),
            profile = MeetupProfileSnapshot(
                displayName = "A Very Long VRChat Display Name",
                avatarUrl = "https://example.test/avatar.png",
                pronouns = "they/them",
                languages = listOf("eng", "jpn"),
                status = "active",
                statusDescription = "Looking for friends",
                links = listOf("https://x.com/someone", "https://github.com/someone"),
            ),
        ),
        photoModel = null,
        decorations = emptyMap<DecorationSlot, ResolvedDecoration>(),
        orientation = MeetupOrientation.Portrait,
    )
}
