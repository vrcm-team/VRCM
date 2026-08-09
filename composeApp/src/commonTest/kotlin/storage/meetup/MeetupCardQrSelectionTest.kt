package io.github.vrcmteam.vrcm.storage.meetup

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 二维码选择的解析规则：卡片上到底出现哪些码由这两个函数单独决定，
 * 而配置里的链接可能已经从 VRChat 资料删掉——这类不一致只能在这里挡住。
 */
class MeetupCardQrSelectionTest {

    private fun config(
        links: List<String>,
        qrLinkTypes: List<MeetupQrLinkType>,
        qrProfileLinks: List<String>,
    ) = defaultMeetupCardConfig("usr_qr-test").copy(
        qrLinkTypes = qrLinkTypes,
        qrProfileLinks = qrProfileLinks,
        profile = MeetupProfileSnapshot(displayName = "QR", links = links),
    )

    @Test
    fun profileLinksMustStillExistInTheSnapshot() {
        val config = config(
            links = listOf("https://x.com/someone"),
            qrLinkTypes = listOf(MeetupQrLinkType.VrchatWeb),
            // 第二条链接已从 VRChat 资料删除，卡片上不能再拿它生成二维码。
            qrProfileLinks = listOf("https://x.com/someone", "https://old.example/gone"),
        )

        assertEquals(listOf("https://x.com/someone"), config.resolvedQrProfileLinks())
    }

    @Test
    fun keepingOnlyProfileLinksDoesNotBringBackTheVrchatCode() {
        val config = config(
            links = listOf("https://x.com/someone"),
            qrLinkTypes = emptyList(),
            qrProfileLinks = listOf("https://x.com/someone"),
        )

        assertEquals(emptyList(), config.resolvedQrLinkTypes())
    }

    @Test
    fun emptySelectionFallsBackToTheVrchatCode() {
        val config = config(
            links = listOf("https://x.com/someone"),
            qrLinkTypes = emptyList(),
            // 选中的链接已失效，等于什么都没选：开着二维码就必须有一个能扫的码。
            qrProfileLinks = listOf("https://old.example/gone"),
        )

        assertEquals(listOf(MeetupQrLinkType.VrchatWeb), config.resolvedQrLinkTypes())
    }
}
