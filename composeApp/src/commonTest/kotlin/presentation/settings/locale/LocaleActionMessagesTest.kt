package io.github.vrcmteam.vrcm.presentation.settings.locale

import kotlin.test.Test
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
}
