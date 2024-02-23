package io.github.kamo.vrcm.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kamo.vrcm.data.api.users.UserData
import io.github.kamo.vrcm.data.api.users.UsersApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val usersApi: UsersApi
) :ViewModel(){

    private val _userState = mutableStateOf<UserData?>(null)
    val userState by _userState
   fun refreshUser (userId:String) =  viewModelScope.launch(Dispatchers.IO) {
       _userState.value = usersApi.fetchUser(userId)
   }

}