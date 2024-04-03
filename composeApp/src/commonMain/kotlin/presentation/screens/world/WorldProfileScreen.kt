package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import io.github.vrcmteam.vrcm.presentation.compoments.ProfileScaffold
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVO
import org.jetbrains.compose.ui.tooling.preview.Preview


/**
 *
 * kotlin类作用描述
 *
 * @author 次音(CiYin) QQ:2964221430
 * @github <a href="https://github.com/Ci-Yin">Ci-Yin</a>
 * @since 2024/3/23 19:44
 * @version: 1.0
 */
class WorldProfileScreen(private val worldProfileVO: WorldProfileVO) : Screen {
    @Composable
    override fun Content() {
        val currentNavigator = currentNavigator
        ProfileScaffold(
            profileImageUrl = worldProfileVO.worldImageUrl,
            iconUrl = worldProfileVO.worldImageUrl,
            onReturn = {currentNavigator.pop() },
            onMenu = {}
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    worldProfileVO.worldName,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                )
                Text(
                    worldProfileVO.worldDescription,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                )
            }

        }
    }
}

@Preview
@Composable
fun Root() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Text("测试")
    }

}