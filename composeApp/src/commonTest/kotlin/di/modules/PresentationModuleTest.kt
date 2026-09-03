package io.github.vrcmteam.vrcm.di.modules

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarFallbackSetter
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarFallbackUserContext
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarFallbackResponse
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AuthenticatedAvatarDeletion
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarDeleter
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarImpostorDeletionSource
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileLoader
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarGalleryLoader
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarCoverFile
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarEditor
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarModerationSource
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarMetadataUpdateResponse
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarStylesResponse
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarImpostorBuilder
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarPublicationResponse
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarSelector
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarUserContext
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUpdateData
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntrySource
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
import io.github.vrcmteam.vrcm.service.FallbackAvatarUpdateResult
import io.github.vrcmteam.vrcm.service.SessionBoundResponse
import io.github.vrcmteam.vrcm.service.meetup.MeetupCardRepository
import io.github.vrcmteam.vrcm.service.meetup.MeetupCardState
import io.github.vrcmteam.vrcm.service.meetup.MeetupPhotoCandidate
import io.github.vrcmteam.vrcm.service.meetup.MeetupPhotoTarget
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfig
import io.github.vrcmteam.vrcm.storage.meetup.defaultMeetupCardConfig
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
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
    fun galleryScreenModelUsesFactoryScope() = runTest {
        val application = koinApplication {
            modules(
                presentationModule,
                module {
                    single<GalleryDataSource> { EmptyGalleryDataSource }
                    single<Logger> { EmptyLogger() }
                },
            )
        }

        val models = mutableListOf<ViewModel>()
        try {
            val first = application.koin.get<GalleryScreenModel>().also { models += it }
            val second = application.koin.get<GalleryScreenModel>().also { models += it }

            assertNotSame(first, second)
        } finally {
            try {
                clearViewModels(models)
            } finally {
                application.close()
            }
        }
    }

    @Test
    fun avatarProfileScreenModelUsesFactoryScope() = runTest {
        val application = koinApplication {
            modules(
                presentationModule,
                module {
                    single<AvatarProfileLoader> {
                        AvatarProfileLoader { Result.failure(IllegalStateException("unused")) }
                    }
                    single<AvatarGalleryLoader> {
                        AvatarGalleryLoader { _, _, _ -> Result.success(emptyList()) }
                    }
                    single<AvatarSelector> { EmptyAvatarSelector }
                    single<AvatarModerationSource> { EmptyAvatarModerationSource }
                    single<AvatarFallbackSetter> { EmptyAvatarFallbackSetter }
                    single<AvatarEditor> { EmptyAvatarEditor }
                    single<AvatarDeleter> { EmptyAvatarDeleter }
                    single<AvatarImpostorDeletionSource> { EmptyAvatarImpostorDeletionSource }
                    single<AvatarImpostorBuilder> { EmptyAvatarImpostorBuilder }
                    single<FavoriteEntrySource> { EmptyFavoriteEntrySource }
                },
            )
        }

        val models = mutableListOf<ViewModel>()
        try {
            val first = application.koin.get<AvatarProfileScreenModel>().also { models += it }
            val second = application.koin.get<AvatarProfileScreenModel>().also { models += it }

            assertNotSame(first, second)
        } finally {
            try {
                clearViewModels(models)
            } finally {
                application.close()
            }
        }
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
    fun meetupCardScreenModelResolvesPerRequestWithOwnerParameter() = runTest {
        val application = koinApplication {
            modules(
                presentationModule,
                module {
                    single<MeetupCardRepository> { FakeMeetupCardRepository }
                    single<PlatformImageCodec> { FakePlatformImageCodec }
                },
            )
        }

        val models = mutableListOf<ViewModel>()
        try {
            val first = application.koin.get<MeetupCardScreenModel> {
                parametersOf("usr_a")
            }.also { models += it }
            val second = application.koin.get<MeetupCardScreenModel> {
                parametersOf("usr_a")
            }.also { models += it }

            assertNotSame(first, second)
        } finally {
            try {
                clearViewModels(models)
            } finally {
                application.close()
            }
        }
    }
}

private suspend fun clearViewModels(models: List<ViewModel>) {
    val jobs = models.mapNotNull { it.viewModelScope.coroutineContext[Job] }
    ViewModelStore().apply {
        models.forEachIndexed { index, model -> put(index.toString(), model) }
        clear()
    }
    jobs.joinAll()
}

private data object EmptyFavoriteEntrySource : FavoriteEntrySource {
    private val favorites = MutableStateFlow<Map<FavoriteGroupData, List<FavoriteData>>>(emptyMap())

    override fun favoritesByGroup(type: FavoriteType): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        favorites

    override suspend fun load(type: FavoriteType): Result<Unit> = Result.success(Unit)
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

private data object EmptyAvatarModerationSource : AvatarModerationSource {
    override suspend fun isBlocked(avatarId: String): Result<Boolean> =
        Result.failure(IllegalStateException("unused"))

    override suspend fun block(avatarId: String): Result<Unit> =
        Result.failure(IllegalStateException("unused"))

    override suspend fun unblock(avatarId: String): Result<Unit> =
        Result.failure(IllegalStateException("unused"))
}

private data object EmptyAvatarFallbackSetter : AvatarFallbackSetter {
    override val currentUser = flowOf<AvatarFallbackUserContext?>(null)

    override suspend fun set(
        avatarId: String,
        sessionToken: AccountSessionToken,
    ): AvatarFallbackResponse? = null

    override suspend fun apply(
        avatarId: String,
        sessionToken: AccountSessionToken,
        response: CurrentUserData,
        commitIfCurrent: (update: () -> Unit) -> Boolean,
    ): FallbackAvatarUpdateResult = FallbackAvatarUpdateResult.Stale

    override fun isCurrentSession(sessionToken: AccountSessionToken): Boolean = false
}

private data object EmptyAvatarEditor : AvatarEditor {
    override suspend fun loadStyles(
        sessionToken: AccountSessionToken,
    ): AvatarStylesResponse? = error("unused")

    override suspend fun updateMetadata(
        sessionToken: AccountSessionToken,
        avatarId: String,
        update: AvatarUpdateData,
    ): AvatarMetadataUpdateResponse? = error("unused")

    override suspend fun updatePublication(
        sessionToken: AccountSessionToken,
        avatarId: String,
        releaseStatus: String,
    ): AvatarPublicationResponse? = null

    override suspend fun uploadCover(cover: AvatarCoverFile): Result<String> =
        Result.failure(IllegalStateException("unused"))

    override suspend fun assignCover(
        avatarId: String,
        imageUrl: String,
    ): Result<AvatarData> = Result.failure(IllegalStateException("unused"))
}

private data object EmptyAvatarDeleter : AvatarDeleter {
    override fun isCurrentSession(token: AccountSessionToken): Boolean = false

    override suspend fun delete(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): AuthenticatedAvatarDeletion? = null
}

private data object EmptyAvatarImpostorDeletionSource : AvatarImpostorDeletionSource {
    override suspend fun delete(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<Unit>? = null

    override suspend fun load(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<AvatarData>? = null
}

private data object EmptyAvatarImpostorBuilder : AvatarImpostorBuilder {
    override suspend fun enqueue(
        sessionToken: io.github.vrcmteam.vrcm.core.shared.AccountSessionToken,
        avatarId: String,
    ) = null

    override suspend fun queueStats(
        sessionToken: io.github.vrcmteam.vrcm.core.shared.AccountSessionToken,
    ) = null

    override fun isCurrentSession(
        sessionToken: io.github.vrcmteam.vrcm.core.shared.AccountSessionToken,
    ) = false
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

    override suspend fun encodePng(bitmap: ImageBitmap, maxBytes: Int): ByteArray = error("unused")
}
