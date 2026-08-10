package io.github.vrcmteam.vrcm.storage

import android.content.Context
import io.github.vrcmteam.vrcm.AndroidAppPlatform
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.koin.core.logger.EmptyLogger
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class VrcmDatabaseFactoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(VRCM_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(VRCM_DATABASE_NAME)
    }

    @Test
    fun androidSystemSqliteSupportsTheRoomDatabaseAtMinSdk() = runBlocking {
        val database = buildVrcmDatabase(
            platformVrcmDatabaseBuilder(AndroidAppPlatform(context, EmptyLogger())),
        )
        try {
            val dao = database.friendActivityDao()
            dao.insertGeneration(FriendActivityGenerationEntity("usr_test", generation = 3L))

            assertEquals(3L, dao.generation("usr_test"))
        } finally {
            database.close()
        }
    }
}
