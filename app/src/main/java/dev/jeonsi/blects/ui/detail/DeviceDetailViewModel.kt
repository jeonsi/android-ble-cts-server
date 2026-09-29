package dev.jeonsi.blects.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jeonsi.blects.App
import dev.jeonsi.blects.data.Device
import dev.jeonsi.blects.data.Event
import dev.jeonsi.blects.service.ServiceState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DeviceDetailViewModel(app: App, val address: String) : ViewModel() {
    private val repo = app.container.repository

    val device: StateFlow<Device?> = repo.device(address)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val events: StateFlow<List<Event>> = repo.events(address)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val connected: StateFlow<Boolean> = ServiceState.connected.map { address in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    suspend fun remove() = repo.removeDevice(address)

    suspend fun exportText(): String = repo.exportText()

    companion object {
        fun factory(address: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DeviceDetailViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App, address)
            }
        }
    }
}
