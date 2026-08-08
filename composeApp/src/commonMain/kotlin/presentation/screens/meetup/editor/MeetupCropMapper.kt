package io.github.vrcmteam.vrcm.presentation.screens.meetup.editor

import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.CropTransform
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.CropTransformCalculator
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.ImageSize
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCrop
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation

/** 身份卡裁剪的标准化参考视口；实际展示按真实视口重新 clamp。 */
val MeetupOrientation.referenceViewport: ImageSize
    get() = when (this) {
        MeetupOrientation.Portrait -> ImageSize(1080, 1920)
        MeetupOrientation.Landscape -> ImageSize(1920, 1080)
    }

/** 在两个方向的视口之间迁移裁剪参数，保持焦点并保证 cover。 */
class MeetupCropMapper(
    private val calculator: CropTransformCalculator,
) {
    /** 以 [viewport] 的 cover 缩放生成居中默认裁剪。 */
    fun coverCrop(source: ImageSize, viewport: ImageSize): MeetupCrop =
        MeetupCrop(zoom = calculator.zoomLimits(source, viewport, 0).cover)

    /**
     * 把 [from] 从 [fromViewport] 迁移到 [toViewport]：
     * 保留相对焦点偏移，按两个视口 cover 缩放的比例换算 zoom，
     * 再通过 [CropTransformCalculator.transform] 的零手势调用 clamp 到合法范围。
     */
    fun derive(
        source: ImageSize,
        fromViewport: ImageSize,
        toViewport: ImageSize,
        from: MeetupCrop,
    ): MeetupCrop {
        val fromCover = calculator.zoomLimits(source, fromViewport, 0).cover
        val toLimits = calculator.zoomLimits(source, toViewport, 0)
        val zoom = (toLimits.cover * (from.zoom / fromCover)).coerceAtLeast(toLimits.cover)
        val clamped = calculator.transform(
            source = source,
            viewport = toViewport,
            current = CropTransform(
                centerOffsetX = from.centerOffsetX,
                centerOffsetY = from.centerOffsetY,
                zoom = zoom,
            ),
            panX = 0f,
            panY = 0f,
            zoomChange = 1f,
        )
        return MeetupCrop(
            centerOffsetX = clamped.centerOffsetX,
            centerOffsetY = clamped.centerOffsetY,
            zoom = clamped.zoom,
        )
    }
}
