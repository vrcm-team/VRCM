package io.github.vrcmteam.vrcm.presentation.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.DesktopAppPlatform
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.screens.auth.card.LoginCardInput
import io.github.vrcmteam.vrcm.presentation.screens.auth.card.VerifyCardInput
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthCardPage
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthUIState
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AuthKeyboardNavigationTest {
    @Test
    fun credentialsSupportTabAndEnterWithLoadingGuard() = runComposeUiTest {
        var state by mutableStateOf(AuthUIState(cardState = AuthCardPage.Login))
        var submissions = 0
        setContent {
            AuthTestContent {
                Column {
                    LoginCardInput(
                        uiState = state,
                        onUsernameChange = { state = state.copy(username = it) },
                        onPasswordChange = { state = state.copy(password = it) },
                        onClick = {
                            submissions++
                            state = state.copy(btnIsLoading = true)
                        },
                    )
                }
            }
        }
        val username = onAllNodes(hasSetTextAction())[0]
        val password = onAllNodes(hasSetTextAction())[1]
        username.performClick().performTextInput("test-user")
        username.performKeyInput { pressKey(Key.Tab) }
        password.assertIsFocused()
        password.performTextInput("test-password")
        password.performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Tab)
            keyUp(Key.ShiftLeft)
        }
        username.assertIsFocused()
        username.performImeAction()
        password.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(1, submissions) }

        password.performClick().performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(1, submissions, "加载期间不能重复提交") }

        runOnIdle { state = state.copy(btnIsLoading = false) }
        username.performClick().performKeyInput { pressKey(Key.NumPadEnter) }
        runOnIdle { assertEquals(2, submissions, "账号输入框也支持小键盘回车登录") }

        runOnIdle { state = state.copy(btnIsLoading = false) }
        password.performClick().performImeAction()
        runOnIdle { assertEquals(3, submissions, "软键盘完成操作应提交登录") }
    }

    @Test
    fun enterDoesNotSubmitIncompleteCredentials() = runComposeUiTest {
        var state by mutableStateOf(AuthUIState(cardState = AuthCardPage.Login))
        var submissions = 0
        setContent {
            AuthTestContent {
                Column {
                    LoginCardInput(
                        uiState = state,
                        onUsernameChange = { state = state.copy(username = it) },
                        onPasswordChange = { state = state.copy(password = it) },
                        onClick = { submissions++ },
                    )
                }
            }
        }
        onAllNodes(hasSetTextAction())[1].performClick().performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(0, submissions) }
    }

    @Test
    fun emailCodeKeepsFocusUntilEnterConfirms() = verifyCodeWithKeyboard(AuthCardPage.EmailCode, "123456")

    @Test
    fun authenticatorCodeCanBeConfirmedWithEnter() = verifyCodeWithKeyboard(AuthCardPage.TTFACode, "123456")

    @Test
    fun recoveryCodeCanBeConfirmedWithEnter() = verifyCodeWithKeyboard(AuthCardPage.TFACode, "abcd1234")

    private fun verifyCodeWithKeyboard(page: AuthCardPage, code: String) = runComposeUiTest {
        var state by mutableStateOf(AuthUIState(cardState = page))
        var submissions = 0
        setContent {
            AuthTestContent {
                Column {
                    VerifyCardInput(
                        uiState = state,
                        onVerifyCodeChange = { state = state.copy(verifyCode = it) },
                        onSwitchTwoFactorMethod = {},
                        onClick = {
                            submissions++
                            state = state.copy(btnIsLoading = true)
                        },
                    )
                }
            }
        }
        val input = onNode(hasSetTextAction())
        input.performClick().performTextInput(code.take(3))
        input.performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(0, submissions, "验证码不完整时不能提交") }
        input.assertIsFocused().performTextInput(code.drop(3))
        input.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        runOnIdle {
            assertEquals(code, state.verifyCode)
            assertEquals(1, submissions)
        }

        input.performClick().performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(1, submissions, "加载期间不能重复验证") }
    }
}

@Composable
private fun AuthTestContent(content: @Composable () -> Unit) {
    KoinApplication(application = { modules(module { single<AppPlatform> { DesktopAppPlatform() } }) }) {
        AppTheme(content = content)
    }
}
