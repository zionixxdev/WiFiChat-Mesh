package com.wifichat.mesh.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Tiny ViewModel factory. The app has a single [com.wifichat.mesh.AppContainer]
 * so a lambda-based factory is clearer and lighter than a DI framework.
 */
class SimpleViewModelFactory<VM : ViewModel>(
    private val creator: () -> VM
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}

/** Convenience accessor for the app container from any UI component. */
val androidx.fragment.app.Fragment.container
    get() = (requireActivity().application as com.wifichat.mesh.MeshApplication).container

val androidx.activity.ComponentActivity.container
    get() = (application as com.wifichat.mesh.MeshApplication).container
