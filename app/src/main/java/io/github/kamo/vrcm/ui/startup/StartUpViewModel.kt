package io.github.kamo.vrcm.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kamo.vrcm.data.api.auth.AuthAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

class StartUpViewModel(private val authAPI: AuthAPI) : ViewModel() {
      suspend fun awaitAuth():Boolean? {
         return viewModelScope.async(Dispatchers.IO) {
              runCatching { authAPI.isAuthed() }.getOrNull()
         }.await()
    }
}