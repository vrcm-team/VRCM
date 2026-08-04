package io.github.vrcmteam.vrcm.storage

import okio.FileSystem
import okio.IOException
import okio.Path

internal fun FileSystem.moveReplacing(source: Path, target: Path) {
    try {
        atomicMove(source, target)
    } catch (atomicMoveFailure: IOException) {
        try {
            copy(source, target)
            delete(source, mustExist = true)
        } catch (fallbackFailure: IOException) {
            fallbackFailure.addSuppressed(atomicMoveFailure)
            throw fallbackFailure
        }
    }
}
