package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class UserStatusRowSharedTransitionTest {
    @Test
    fun userStatusRowUsesScaleToBoundsModifier() = runComposeUiTest {
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
                                user = null,
                            )
                        }
                    }
                }
            }
        }

        waitForIdle()
        val modifierNames = onNodeWithTag(StatusRowTag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .layoutInfo
            .getModifierInfo()
            .mapNotNull { it.modifier::class.qualifiedName }

        assertTrue(
            ScaleToBoundsModifierName in modifierNames,
            "UserStatusRow should scale its final layout during shared transitions; " +
                "modifiers=$modifierNames",
        )
    }

    private companion object {
        const val StatusRowTag = "user-status-row"
        const val ScaleToBoundsModifierName = "androidx.compose.animation.SkipToLookaheadSizeElement"
    }
}
