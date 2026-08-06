package io.github.vrcmteam.vrcm.presentation.screens.gallery

import io.github.vinceglb.filekit.dialogs.FileKitType

internal expect fun galleryImagePickerType(extensions: List<String>): FileKitType
