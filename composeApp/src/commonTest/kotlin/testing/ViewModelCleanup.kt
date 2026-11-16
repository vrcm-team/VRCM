package io.github.vrcmteam.vrcm.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.job

/** Wait for in-flight ViewModel work to finish before the test resets Dispatchers.Main. */
suspend fun ViewModel.clearAndJoin() {
    val job = viewModelScope.coroutineContext.job
    ViewModelStore().apply {
        put("test", this@clearAndJoin)
        clear()
    }
    job.join()
}
