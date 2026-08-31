package io.github.vrcmteam.vrcm.presentation.settings.locale

import io.github.vrcmteam.vrcm.presentation.screens.home.compoments.formatFavoriteGroupClearMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocaleActionMessagesTest {
    @Test
    fun boopInviteAndRetryMessagesArePresentInEveryLocale() {
        val locales = listOf(
            LocaleStringsEn,
            LocaleStringsJa,
            LocaleStringsZhHans,
            LocaleStringsZhHant,
        )

        locales.forEach { locale ->
            val messages = listOf(
                locale.retry,
                locale.profileBoopSuccess,
                locale.profileBoopDisabled,
                locale.profileInviteSent,
                locale.profileInviteNotInInstance,
            )
            assertTrue(messages.all { it.isNotBlank() })
        }
    }

    @Test
    fun printEditorMessagesArePresentInEveryLocale() {
        val locales = listOf(
            LocaleStringsEn,
            LocaleStringsJa,
            LocaleStringsZhHans,
            LocaleStringsZhHant,
        )

        locales.forEach { locale ->
            val messages = listOf(
                locale.printEditorTitle,
                locale.printEditorBack,
                locale.printEditorUpload,
                locale.printEditorRotateLeft,
                locale.printEditorRotateRight,
                locale.printEditorFlipHorizontal,
                locale.printEditorFlipVertical,
                locale.printEditorZoom,
                locale.printEditorReset,
                locale.printEditorProcessing,
                locale.printEditorUploading,
                locale.printEditorUnsupportedFormat,
                locale.printEditorFileTooLarge,
                locale.printEditorImageTooLarge,
                locale.printEditorDesktopRegionDecodeUnavailable,
                locale.printEditorDecodeFailed,
                locale.printEditorRenderFailed,
                locale.printEditorUploadAuthenticationFailed,
                locale.printEditorUploadPermissionFailed,
                locale.printEditorUploadNetworkFailed,
                locale.printEditorUploadServerFailed,
                locale.printEditorUploadUnknownFailed,
                locale.printEditorUploaded,
                locale.printEditorSessionExpired,
            )
            assertTrue(messages.all { it.isNotBlank() })
            assertTrue(locale.printEditorReadFailed.contains("%s"))
        }
    }

    @Test
    fun favoriteGroupClearConfirmationKeepsNameAndCountPlaceholdersInEveryLocale() {
        val locales = listOf(
            LocaleStringsEn,
            LocaleStringsJa,
            LocaleStringsZhHans,
            LocaleStringsZhHant,
        )

        locales.forEach { locale ->
            assertEquals(1, locale.favoriteGroupClearTitle.windowed(2).count { it == "%s" })
            assertEquals(1, locale.favoriteGroupClearMessage.windowed(2).count { it == "%d" })
            assertTrue(
                listOf(
                    locale.favoriteGroupClearAction,
                    locale.favoriteGroupClearing,
                    locale.favoriteGroupClearSuccess,
                    locale.favoriteGroupClearFailed,
                    locale.favoriteGroupClearSyncFailed,
                ).all { it.isNotBlank() },
            )
            val message = formatFavoriteGroupClearMessage(locale.favoriteGroupClearMessage, 37)
            assertTrue("37" in message)
            assertTrue("%d" !in message)
        }
    }
}
