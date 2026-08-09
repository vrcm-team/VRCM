package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import coil3.ImageLoader
import coil3.PlatformContext
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class UserStatusRowSharedTransitionTest {
    @Test
    fun clickedUserNameUsesTheNavigationSuffix() = assertClickedUserSharedKey(
        contentText = UserName,
        baseSharedKey = "${UserId}UserName",
    )

    @Test
    fun clickedUserStatusUsesTheNavigationSuffix() = assertClickedUserSharedKey(
        contentText = UserStatusDescription,
        baseSharedKey = "${UserId}UserStatusRow",
    )

    @Test
    fun explicitUserIdKeepsUserNameSharedKeyStable() = assertExplicitUserSharedKey(
        contentText = UserName,
        baseSharedKey = "${StableUserId}UserName",
    )

    @Test
    fun explicitUserIdKeepsUserStatusSharedKeyStable() = assertExplicitUserSharedKey(
        contentText = UserStatusDescription,
        baseSharedKey = "${StableUserId}UserStatusRow",
    )

    @Test
    fun navigationSharedBoundsBelongToStatusTextInsteadOfCompositeRow() = runComposeUiTest {
        val user = UserProfileVo(
            id = UserId,
            displayName = UserName,
            status = UserStatus.Active,
            statusDescription = UserStatusDescription,
        )

        setContent {
            MaterialTheme {
                SharedTransitionLayout {
                    AnimatedContent(targetState = Unit) {
                        CompositionLocalProvider(
                            LocalSharedTransitionScreenScope provides this@SharedTransitionLayout,
                            LocalAnimatedVisibilityScope provides this@AnimatedContent,
                        ) {
                            UserStatusRow(
                                modifier = Modifier.testTag(StatusRowTag),
                                user = user,
                            )
                        }
                    }
                }
            }
        }

        waitForIdle()
        val rowModifierNames = onNodeWithTag(StatusRowTag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .layoutInfo
            .getModifierInfo()
            .mapNotNull { it.modifier::class.qualifiedName }
        val textModifierNames = onNodeWithText(UserStatusDescription, useUnmergedTree = true)
            .fetchSemanticsNode()
            .layoutInfo
            .getModifierInfo()
            .mapNotNull { it.modifier::class.qualifiedName }

        assertTrue(
            SharedBoundsNodeElementName in textModifierNames,
            "status Text should own the navigation shared bounds; modifiers=$textModifierNames",
        )
        assertTrue(
            ScaleToBoundsModifierName in textModifierNames,
            "status Text should scale its final layout during shared transitions; " +
                "modifiers=$textModifierNames",
        )
        assertFalse(
            SharedBoundsNodeElementName in rowModifierNames,
            "the composite status Row must not participate in navigation shared bounds; " +
                "modifiers=$rowModifierNames",
        )
    }

    private fun assertClickedUserSharedKey(
        contentText: String,
        baseSharedKey: String,
    ) = runComposeUiTest {
        val user = UserProfileVo(
            id = UserId,
            displayName = UserName,
            status = UserStatus.Active,
            statusDescription = UserStatusDescription,
        )
        var clickedSuffixKey: String? = null

        setContent {
            KoinApplication(
                application = {
                    modules(
                        module {
                            single<PlatformContext> { PlatformContext.INSTANCE }
                            single<ImageLoader> {
                                ImageLoader.Builder(get<PlatformContext>()).build()
                            }
                        },
                    )
                },
            ) {
                MaterialTheme {
                    SharedTransitionLayout {
                        AnimatedContent(targetState = Unit) {
                            CompositionLocalProvider(
                                LocalSharedTransitionScreenScope provides this@SharedTransitionLayout,
                                LocalAnimatedVisibilityScope provides this@AnimatedContent,
                                LocalSharedSuffixKey provides SourceSuffixKey,
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    renderUserItems(listOf(user)) { _, suffixKey ->
                                        clickedSuffixKey = suffixKey
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        waitForIdle()
        onNodeWithText(user.displayName).performClick()
        val navigationSuffixKey = requireNotNull(clickedSuffixKey)
        val actualKeys = onNodeWithText(contentText, useUnmergedTree = true)
            .fetchSemanticsNode()
            .layoutInfo
            .sharedContentKeysInHierarchy()

        assertTrue(
            "$baseSharedKey:$navigationSuffixKey" in actualKeys,
            "the clicked user's navigation suffix must be used by $baseSharedKey; " +
                "actualKeys=$actualKeys",
        )
    }

    private fun assertExplicitUserSharedKey(
        contentText: String,
        baseSharedKey: String,
    ) = runComposeUiTest {
        val user = UserProfileVo(
            id = UserId,
            displayName = UserName,
            status = UserStatus.Active,
            statusDescription = UserStatusDescription,
        )

        setContent {
            MaterialTheme {
                SharedTransitionLayout {
                    AnimatedContent(targetState = Unit) {
                        CompositionLocalProvider(
                            LocalSharedTransitionScreenScope provides this@SharedTransitionLayout,
                            LocalAnimatedVisibilityScope provides this@AnimatedContent,
                        ) {
                            Column {
                                UserInfoRow(
                                    user = user,
                                    sharedUserId = StableUserId,
                                    sharedSuffixKey = SourceSuffixKey,
                                )
                                UserStatusRow(
                                    user = user,
                                    sharedUserId = StableUserId,
                                    sharedSuffixKey = SourceSuffixKey,
                                )
                            }
                        }
                    }
                }
            }
        }

        waitForIdle()
        val actualKeys = onNodeWithText(contentText, useUnmergedTree = true)
            .fetchSemanticsNode()
            .layoutInfo
            .sharedContentKeysInHierarchy()

        assertTrue(
            "$baseSharedKey:$SourceSuffixKey" in actualKeys,
            "the explicit user ID must remain part of $baseSharedKey; actualKeys=$actualKeys",
        )
    }

    private fun LayoutInfo.sharedContentKeysInHierarchy(): Set<String> =
        generateSequence(this) { it.parentInfo }
            .flatMap { layoutInfo -> layoutInfo.getModifierInfo().asSequence() }
            .mapNotNull { modifierInfo -> modifierInfo.modifier.stringSharedContentKey() }
            .toSet()

    // Compose exposes the registered state through getters, but keeps the modifier element internal.
    private fun Any.stringSharedContentKey(): String? {
        if (this::class.qualifiedName != SharedBoundsNodeElementName) return null
        val entry = javaClass.getMethod("getSharedElementState").invoke(this)
        val userState = entry.javaClass.getMethod("getUserState").invoke(entry)
        val key = userState.javaClass.getMethod("getKey").invoke(userState)
        if (key::class.qualifiedName != StringSharedElementKeyName) return null
        return key.javaClass.getMethod("getValue").invoke(key) as String
    }

    private companion object {
        const val StatusRowTag = "user-status-row"
        const val ScaleToBoundsModifierName = "androidx.compose.animation.SkipToLookaheadSizeElement"
        const val SharedBoundsNodeElementName = "androidx.compose.animation.SharedBoundsNodeElement"
        const val StringSharedElementKeyName =
            "io.github.vrcmteam.vrcm.presentation.compoments.StringSharedElementKey"
        const val UserId = "usr_shared_transition"
        const val StableUserId = "usr_stable_shared_transition"
        const val UserName = "Shared Transition User"
        const val UserStatusDescription = "Available"
        const val SourceSuffixKey = "source-list-pane"
    }
}
