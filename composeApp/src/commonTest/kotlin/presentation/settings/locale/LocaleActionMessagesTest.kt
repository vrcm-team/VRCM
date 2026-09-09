package io.github.vrcmteam.vrcm.presentation.settings.locale

import kotlin.test.Test
import kotlin.test.assertTrue

class LocaleActionMessagesTest {
    @Test
    fun profileActionMessagesArePresentInEveryLocale() {
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
                locale.profileChatboxModerationMute,
                locale.profileChatboxModerationUnmute,
                locale.profileChatboxModerationChecking,
                locale.profileChatboxModerationRetry,
                locale.profileChatboxModerationMuting,
                locale.profileChatboxModerationUnmuting,
                locale.profileChatboxModerationMuted,
                locale.profileChatboxModerationUnmuted,
                locale.profileChatboxModerationUpdateFailed,
                locale.profileBlock,
                locale.profileUnblock,
                locale.profileBlockStatusChecking,
                locale.profileBlockStatusRetry,
                locale.profileBlockStatusUnavailable,
                locale.profileBlockConfirmTitle,
                locale.profileBlockConfirmMessage,
                locale.profileUnblockConfirmTitle,
                locale.profileUnblockConfirmMessage,
                locale.profileBlockSuccess,
                locale.profileUnblockSuccess,
                locale.profileBlockFailed,
                locale.profileUnblockFailed,
                locale.profileBlockStatusLoadFailed,
            )
            assertTrue(messages.all { it.isNotBlank() })
            assertTrue(locale.profileBlockConfirmMessage.contains("%name%"))
            assertTrue(locale.profileUnblockConfirmMessage.contains("%name%"))
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
    fun playerInteractionMessagesArePresentInEveryLocale() {
        val locales = listOf(
            LocaleStringsEn,
            LocaleStringsJa,
            LocaleStringsZhHans,
            LocaleStringsZhHant,
        )

        locales.forEach { locale ->
            val messages = listOf(
                locale.profileInteractionChecking,
                locale.profileInteractionClose,
                locale.profileInteractionRestore,
                locale.profileInteractionClosing,
                locale.profileInteractionRestoring,
                locale.profileInteractionRetry,
                locale.profileInteractionUnavailable,
                locale.profileInteractionCloseConfirmTitle,
                locale.profileInteractionRestoreConfirmTitle,
                locale.profileInteractionClosedSuccess,
                locale.profileInteractionRestoredSuccess,
                locale.profileInteractionLoadFailed,
                locale.profileInteractionUpdateFailed,
            )
            assertTrue(messages.all { it.isNotBlank() })
            assertTrue(locale.profileInteractionCloseConfirmMessage.contains("%s"))
            assertTrue(locale.profileInteractionRestoreConfirmMessage.contains("%s"))
        }
    }

    @Test
    fun friendRemovalMessagesKeepTheirCountPlaceholders() {
        val locales = listOf(
            LocaleStringsEn,
            LocaleStringsJa,
            LocaleStringsZhHans,
            LocaleStringsZhHant,
        )

        locales.forEach { locale ->
            val messages = listOf(
                locale.friendDirectorySelect,
                locale.friendDirectorySelectAll,
                locale.friendDirectoryClearSelection,
                locale.friendDirectoryRemoveSelected,
                locale.friendDirectoryRemoveConfirmTitle,
            )
            assertTrue(messages.all { it.isNotBlank() })
            assertTrue(locale.friendDirectorySelectedCount.countPlaceholderCount() == 1)
            assertTrue(locale.friendDirectoryRemoveConfirmMessage.countPlaceholderCount() == 1)
            assertTrue(locale.friendDirectoryRemovingProgress.countPlaceholderCount() == 2)
            assertTrue(locale.friendDirectoryRemoveSuccess.countPlaceholderCount() == 1)
            assertTrue(locale.friendDirectoryRemovePartialFailure.countPlaceholderCount() == 2)
            assertTrue(locale.friendDirectoryRemoveFailed.countPlaceholderCount() == 1)
        }
    }
}

private fun String.countPlaceholderCount(): Int = windowed(size = 2).count { it == "%d" }
