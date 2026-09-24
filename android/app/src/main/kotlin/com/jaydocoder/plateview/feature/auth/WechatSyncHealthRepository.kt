package com.jaydocoder.plateview.feature.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WechatSyncHealth(
    val state: String,
    val lastSuccessfulSyncAt: String?,
    val lastHeartbeatAt: String?,
)

data class WechatSyncHealthDto(
    val state: String,
    val lastSuccessfulSyncAt: String?,
    val lastHeartbeatAt: String?,
)

@Singleton
class WechatSyncHealthRepository @Inject constructor() {
    private val mutableHealth = MutableStateFlow<WechatSyncHealth?>(null)
    val health: StateFlow<WechatSyncHealth?> = mutableHealth.asStateFlow()

    fun apply(remote: WechatSyncHealthDto?) {
        mutableHealth.value = remote?.let { WechatSyncHealth(it.state, it.lastSuccessfulSyncAt, it.lastHeartbeatAt) }
    }

    fun markUnknown() {
        mutableHealth.value = mutableHealth.value?.copy(state = "UNKNOWN")
    }

    fun clear() {
        mutableHealth.value = null
    }
}
