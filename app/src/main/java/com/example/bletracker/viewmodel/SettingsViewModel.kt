package com.example.bletracker.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bletracker.data.ble.ScanSettings
import com.example.bletracker.data.datastore.SettingsStore
import com.example.bletracker.util.CrashGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore
) : ViewModel() {

    val scanSettings: StateFlow<ScanSettings> = runCatchingFlow(ScanSettings()) {
        settingsStore.scanSettingsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScanSettings())
    }

    val envFactor: StateFlow<Float> = runCatchingFlow(2.5f) {
        settingsStore.envFactorFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2.5f)
    }

    val txPower: StateFlow<Int> = runCatchingFlow(-59) {
        settingsStore.txPowerFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -59)
    }

    fun setScanInterval(interval: Long) {
        viewModelScope.launch(CrashGuard.coroutineHandler("SettingsVM")) {
            settingsStore.setScanInterval(interval)
        }
    }

    fun setEnvFactor(factor: Float) {
        viewModelScope.launch(CrashGuard.coroutineHandler("SettingsVM")) {
            settingsStore.setEnvFactor(factor)
        }
    }

    fun setTxPower(txPower: Int) {
        viewModelScope.launch(CrashGuard.coroutineHandler("SettingsVM")) {
            settingsStore.setTxPower(txPower)
        }
    }

    fun completeFirstLaunch() {
        viewModelScope.launch(CrashGuard.coroutineHandler("SettingsVM")) {
            settingsStore.completeFirstLaunch()
        }
    }

    companion object {
        internal const val TAG = "SettingsVM"

        inline fun <T> runCatchingFlow(fallback: T, block: () -> StateFlow<T>): StateFlow<T> {
            return try { block() } catch (e: Exception) {
                Log.e("SettingsVM", "Flow init error, using fallback", e)
                MutableStateFlow(fallback)
            }
        }
    }
}
