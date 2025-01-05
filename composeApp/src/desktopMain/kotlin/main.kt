package io.github.vrcmteam.vrcm

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.vrcmteam.vrcm.core.shared.AppConst.APP_NAME
import io.github.vrcmteam.vrcm.di.commonModules
import io.github.vrcmteam.vrcm.di.modules.platformModule
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import org.jetbrains.compose.resources.painterResource
import org.koin.core.context.startKoin
import vrcm.composeapp.generated.resources.Res
import vrcm.composeapp.generated.resources.logo

fun main() = run {
    startKoin {
        printLogger()
        modules(commonModules + platformModule)
    }
    application {
        val windowState = rememberWindowState(
            width = 400.dp,
            height = 800.dp,
            position = WindowPosition.Aligned(Alignment.Center)
        )
        Window(
            state = windowState,
            onCloseRequest = ::exitApplication,
            title = APP_NAME,
            icon = painterResource(Res.drawable.logo)
        ) {
//            AppDesktopPreview()
            App()
        }
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.size(300.dp, 450.dp),
            color = Color.LightGray
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    StateItem(UserStatus.JoinMe, statusDescription = "HelloWorld")
                    StateItem(UserStatus.Active, statusDescription = "HelloWorldHelloWorldHelloWorldHelloWorld")
                    StateItem(UserStatus.AskMe, statusDescription = "你吃了吗")
                    StateItem(UserStatus.Busy, statusDescription = "你吃了吗")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StateItem(
    userStatus: UserStatus,
    statusDescription: String = "",
) {
    Row(
        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { }.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(
            modifier = Modifier
                .size(24.dp)
        ) {
            drawCircle(GameColor.Status.fromValue(userStatus))
        }

        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip {
                    Text(text = statusDescription.ifBlank { userStatus.value })
                }
            },
            state = rememberTooltipState()
        ) {
            Text(
                text = statusDescription.ifBlank { userStatus.value },
                style = MaterialTheme.typography.titleLarge,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }
    }
}