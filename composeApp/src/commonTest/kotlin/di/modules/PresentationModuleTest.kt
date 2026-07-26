package io.github.vrcmteam.vrcm.di.modules

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileLoader
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreenModel
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
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.core.logger.EmptyLogger
import org.koin.core.logger.Logger
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertNotNull

class PresentationModuleTest {
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
