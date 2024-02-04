package io.github.kamo.vrcm.ui.startup

import androidx.lifecycle.ViewModel
import io.github.kamo.vrcm.data.api.auth.AuthAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class StartUpViewModel(private val authAPI: AuthAPI) : ViewModel() {
      fun awaitAuth():Boolean? {
         return runBlocking(Dispatchers.IO) {
              runCatching { authAPI.isAuthed() }.getOrNull()
         }
    }
}