package io.github.kamo.vrcm.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kamo.vrcm.data.api.auth.AuthAPI
import io.github.kamo.vrcm.data.api.file.FileAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(
    private val authAPI: AuthAPI,
    private val fileAPI: FileAPI

) : ViewModel() {

    private val uiState = mutableStateOf(HomeUIState())
    val _uiState :HomeUIState by uiState

    fun init() {
        viewModelScope.launch {
            val friendInfoMap = authAPI.friends().associateBy ({ it.id }){ mutableStateOf(it) }
            uiState.value = uiState.value.copy(
                friendInfoMap,
                friendInfoMap.values.groupBy({it.value.location}){
                    launch(Dispatchers.IO) {
                        it.value = it.value.copy(currentAvatarThumbnailImageUrl =  fileAPI.getFileLocal(it.value.currentAvatarThumbnailImageUrl))
                    }
                   it
                }
            )
        }
    }

}






