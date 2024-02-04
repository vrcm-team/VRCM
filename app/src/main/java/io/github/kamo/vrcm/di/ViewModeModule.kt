package io.github.kamo.vrcm.di

import io.github.kamo.vrcm.ui.auth.AuthViewModel
import io.github.kamo.vrcm.ui.home.HomeViewModel
import io.github.kamo.vrcm.ui.startup.StartUpViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module


val viewModeModule = module {
    viewModelOf(::AuthViewModel)
    viewModelOf(::StartUpViewModel)
    viewModelOf(::HomeViewModel)
}
