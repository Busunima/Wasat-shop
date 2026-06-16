package com.wasat.shop.core.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.wasat.shop.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Force-update (§11.5): сравнивает versionCode приложения с минимально
 * поддерживаемой версией из Remote Config. fail-open — по умолчанию (нет конфига
 * Firebase, сбой fetch, дефолт=1) обновление не навязывается.
 */
@HiltViewModel
class ForceUpdateViewModel @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig?,
) : ViewModel() {

    private val _updateRequired = MutableStateFlow(false)
    val updateRequired: StateFlow<Boolean> = _updateRequired.asStateFlow()

    init {
        val rc = remoteConfig
        if (rc != null) {
            viewModelScope.launch {
                runCatching { rc.fetchAndActivate().await() }
                val minVersion = rc.getLong("min_supported_version_code")
                _updateRequired.value = BuildConfig.VERSION_CODE < minVersion
            }
        }
    }
}
