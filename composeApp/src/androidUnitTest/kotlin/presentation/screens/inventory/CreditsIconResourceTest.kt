package io.github.vrcmteam.vrcm.presentation.screens.inventory

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.LocalResourceReader
import org.jetbrains.compose.resources.ResourceReader
import org.jetbrains.compose.resources.painterResource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import vrcm.composeapp.generated.resources.Res
import vrcm.composeapp.generated.resources.vrchat_credits

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CreditsIconResourceTest {
    @OptIn(ExperimentalResourceApi::class)
    @Test
    fun creditsIconUsesAndroidCompatibleResourceFormat() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()

        controller.get().setContent {
            CompositionLocalProvider(LocalResourceReader provides AndroidVectorResourceReader) {
                MaterialTheme {
                    Icon(
                        painter = painterResource(Res.drawable.vrchat_credits),
                        contentDescription = null,
                    )
                }
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
    }
}

// The generated resource path selects the Android decoder; test bytes avoid Robolectric asset packaging.
@OptIn(ExperimentalResourceApi::class)
private object AndroidVectorResourceReader : ResourceReader {
    private val bytes = """
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="1dp"
            android:height="1dp"
            android:viewportWidth="1"
            android:viewportHeight="1">
            <path android:fillColor="#FFFFFFFF" android:pathData="M0,0L1,1" />
        </vector>
    """.trimIndent().encodeToByteArray()

    override suspend fun read(path: String): ByteArray = bytes

    override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray =
        bytes.copyOfRange(offset.toInt(), (offset + size).toInt())

    override fun getUri(path: String): String = path
}
