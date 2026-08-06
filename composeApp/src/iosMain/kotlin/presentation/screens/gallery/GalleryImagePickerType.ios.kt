package io.github.vrcmteam.vrcm.presentation.screens.gallery

import io.github.vinceglb.filekit.dialogs.FileKitType

@Suppress("UNUSED_PARAMETER")
internal actual fun galleryImagePickerType(extensions: List<String>): FileKitType =
    FileKitType.Image
