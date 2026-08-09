package io.github.vrcmteam.vrcm.di.modules

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileLoader
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarCoverFile
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarEditor
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarSelector
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarUserContext
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUpdateData
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryDataSource
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryScreenModel
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.network.api.prints.data.PrintData
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.DecodedImage
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.CropRenderRequest
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.DecodeRequest
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageProcessor
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardScreenModel
import io.github.vrcmteam.vrcm.service.meetup.MeetupCardRepository
import io.github.vrcmteam.vrcm.service.meetup.MeetupCardState
import io.github.vrcmteam.vrcm.service.meetup.MeetupPhotoCandidate
import io.github.vrcmteam.vrcm.service.meetup.MeetupPhotoTarget
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfig
import io.github.vrcmteam.vrcm.storage.meetup.defaultMeetupCardConfig
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.core.qualifier.named
import org.koin.core.logger.EmptyLogger
import org.koin.core.logger.Logger
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertNotNull

class PresentationModuleTest : MainDispatcherTest() {
    @Test
    fun galleryScreenModelUsesFactoryScope() {
        val application = koinApplication {
            modules(
                presentationModule,
                module {
                    single<GalleryDataSource> { EmptyGalleryDataSource }
                    single<Logger> { EmptyLogger() }
                },
            )
        }

        val first = application.koin.get<GalleryScreenModel>()
        val second = application.koin.get<GalleryScreenModel>()

        assertNotSame(first, second)
        application.close()
    }

    @Test
    fun avatarProfileScreenModelUsesFactoryScope() {
        val application = koinApplication {
            modules(
                presentationModule,
                module {
                    single<AvatarProfileLoader> {
                        AvatarProfileLoader { Result.failure(IllegalStateException("unused")) }
                    }
                    single<AvatarSelector> { EmptyAvatarSelector }
                    single<AvatarEditor> { EmptyAvatarEditor }
                },
            )
        }

        val first = application.koin.get<AvatarProfileScreenModel>()
        val second = application.koin.get<AvatarProfileScreenModel>()

        assertNotSame(first, second)
        application.close()
    }

    @Test
    fun printImageProcessorCanBeResolvedWithPlatformCodec() {
        val application = koinApplication {
            modules(
                presentationModule,
                module {
                    single<PlatformImageCodec> { FakePlatformImageCodec }
                },
            )
        }

        assertNotNull(application.koin.get<PrintImageProcessor>())
        application.close()
    }

    @Test
    fun avatarCoverImageProcessorCanBeResolvedByEditorQualifier() {
        val application = koinApplication {
            modules(
                presentationModule,
                module {
                    single<PlatformImageCodec> { FakePlatformImageCodec }
                },
            )
        }

        assertNotNull(
            application.koin.get<PrintImageProcessor>(named("avatar-cover-image-processor"))
        )
        application.close()
    }

    @Test
    fun meetupCardScreenModelResolvesPerRequestWithOwnerParameter() {
        val application = koinApplication {
            modules(
                presentationModule,
                module {
                    single<MeetupCardRepository> { FakeMeetupCardRepository }
                    single<PlatformImageCodec> { FakePlatformImageCodec }
                },
            )
        }

        val first = application.koin.get<MeetupCardScreenModel> { parametersOf("usr_a") }
        val second = application.koin.get<MeetupCardScreenModel> { parametersOf("usr_a") }

        assertNotSame(first, second)
        application.close()
    }
}

private data object FakeMeetupCardRepository : MeetupCardRepository {
    private val state = MutableStateFlow(
        MeetupCardState(config = defaultMeetupCardConfig("usr_a"), photoModel = null),
    )

    override fun isConfigured(ownerId: String): Boolean = false

    override fun observe(ownerId: String): StateFlow<MeetupCardState> = state

    override suspend fun ensureDefault(ownerId: String): MeetupCardConfig = state.value.config

    override fun refresh(ownerId: String): Job = Job().also(CompletableJob::complete)

    override suspend fun update(
        ownerId: String,
        transform: (MeetupCardConfig) -> MeetupCardConfig,
    ) = Unit

    override suspend fun replacePhoto(
        ownerId: String,
        candidate: MeetupPhotoCandidate,
        target: MeetupPhotoTarget,
    ): Result<Unit> = Result.failure(IllegalStateException("unused"))
}

private data object EmptyAvatarSelector : AvatarSelector {
    override val currentUser = flowOf<AvatarUserContext?>(null)

    override suspend fun select(avatarId: String): Result<Unit> =
        Result.failure(IllegalStateException("unused"))
}

private data object EmptyAvatarEditor : AvatarEditor {
    override suspend fun updateMetadata(
        avatarId: String,
        update: AvatarUpdateData,
    ): Result<AvatarData> = Result.failure(IllegalStateException("unused"))

    override suspend fun uploadCover(cover: AvatarCoverFile): Result<String> =
        Result.failure(IllegalStateException("unused"))

    override suspend fun assignCover(
        avatarId: String,
        imageUrl: String,
    ): Result<AvatarData> = Result.failure(IllegalStateException("unused"))
}

private data object EmptyGalleryDataSource : GalleryDataSource {
    override suspend fun isCurrentUserSupporter(): Boolean = false

    override suspend fun getFiles(tagType: FileTagType, n: Int, offset: Int): List<FileData> =
        emptyList()

    override suspend fun getPrints(n: Int, offset: Int): List<PrintData> = emptyList()

    override suspend fun uploadImage(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        tagType: FileTagType,
    ): Result<FileData> = Result.failure(IllegalStateException("unused"))

    override suspend fun deleteFile(id: String) = Unit

    override suspend fun deletePrint(id: String) = Unit
}

private data object FakePlatformImageCodec : PlatformImageCodec {
    override suspend fun decode(bytes: ByteArray, request: DecodeRequest): DecodedImage =
        error("unused")

    override suspend fun renderCrop(bytes: ByteArray, request: CropRenderRequest): ImageBitmap =
        error("unused")

    override suspend fun encodePng(bitmap: ImageBitmap): ByteArray = error("unused")
}
