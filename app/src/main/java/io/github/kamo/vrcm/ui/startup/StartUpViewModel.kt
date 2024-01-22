package io.github.kamo.vrcm.ui.startup

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class StartUpViewModel: ViewModel() {
   private val _uiStateFlow = MutableStateFlow(StartUpUIState(false, false))
}