package io.github.vrcmteam.vrcm.presentation.screens.meetup.editor

import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.CropTransformCalculator
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.DecodeRequest
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageFailure
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageLimits
import io.github.vrcmteam.vrcm.service.meetup.MeetupPhotoCandidate
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import io.github.vrcmteam.vrcm.storage.meetup.MeetupPhotoSource
import kotlinx.coroutines.CancellationException

/**
 * 校验并解码候选照片：复用打印图片的大小/像素限制与平台解码器，
 * 以两个方向参考视口的 cover 缩放生成初始裁剪。不做任何网络提交。
 */
class MeetupPhotoPreparer(
    private val codec: PlatformImageCodec,
    private val calculator: CropTransformCalculator,
) {
    private val cropMapper = MeetupCropMapper(calculator)

    suspend fun prepare(
        source: MeetupPhotoSource,
        sourceId: String?,
        sourceUrl: String?,
        fileName: String,
        bytes: ByteArray,
    ): Result<MeetupPreparedPhoto> = try {
        if (bytes.size.toLong() > PrintImageLimits.MAX_FILE_BYTES) {
            throw PrintImageFailure.FileTooLarge
        }
        val decoded = codec.decode(
            bytes,
            DecodeRequest(
                maxDimension = PREVIEW_MAX_DIMENSION,
                maxPixels = PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS,
            ),
        )
        val originalSize = decoded.originalSize
        if (originalSize.width.toLong() * originalSize.height > PrintImageLimits.MAX_PIXELS) {
            throw PrintImageFailure.ImageDimensionsTooLarge
        }
        Result.success(
            MeetupPreparedPhoto(
                candidate = MeetupPhotoCandidate(
                    source = source,
                    sourceId = sourceId,
                    sourceUrl = sourceUrl,
                    fileName = fileName,
                    bytes = bytes,
                    width = originalSize.width,
                    height = originalSize.height,
                    portraitCrop = cropMapper.coverCrop(
                        originalSize,
                        MeetupOrientation.Portrait.referenceViewport,
                    ),
                    landscapeCrop = cropMapper.coverCrop(
                        originalSize,
                        MeetupOrientation.Landscape.referenceViewport,
                    ),
                ),
                preview = decoded.bitmap,
            ),
        )
    } catch (cause: CancellationException) {
        throw cause
    } catch (failure: PrintImageFailure) {
        Result.failure(failure)
    } catch (cause: Exception) {
        Result.failure(PrintImageFailure.DecodeFailed(cause))
    }

    private companion object {
        const val PREVIEW_MAX_DIMENSION = 2_048
    }
}
