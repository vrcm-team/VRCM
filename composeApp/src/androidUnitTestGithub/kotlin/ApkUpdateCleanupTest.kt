package io.github.vrcmteam.vrcm

import android.content.Context
import android.os.Environment
import io.github.vrcmteam.vrcm.core.shared.AppConst
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ApkUpdateCleanupTest {
    @Test
    fun keepsOnlyTheDownloadedUpdateThatIsNotInstalledYet() {
        val context = RuntimeEnvironment.getApplication()
        val updates = File(context.cacheDir, "updates").apply { mkdirs() }
        val pending = File(updates, "VRCM-999.0.0.apk").apply { writeText("pending") }
        val installed = File(updates, "VRCM-${AppConst.APP_VERSION}.apk").apply { writeText("installed") }
        // 1.1.2、1.1.3 用 DownloadManager 下载的安装包，以及它留下的下载记录
        val legacy = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "VRCM-1.1.3.apk")
            .apply { writeText("legacy") }
        context.getSharedPreferences("vrcm_update", Context.MODE_PRIVATE).edit().putLong("download_id", 7).commit()

        ApkUpdateInstaller(context).deleteObsoletePackages()

        // 下好还没装的新版本可能正被安装器读取，不能删
        assertTrue(pending.exists())
        assertFalse(installed.exists())
        assertFalse(legacy.exists())
    }
}
