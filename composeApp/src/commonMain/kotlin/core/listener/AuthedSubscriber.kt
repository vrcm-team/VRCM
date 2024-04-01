package io.github.vrcmteam.vrcm.core.listener

interface AuthedSubscriber  {
   suspend fun onAuthed()
}