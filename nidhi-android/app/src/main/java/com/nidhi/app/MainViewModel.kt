package com.nidhi.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class InitiateState {
    object Idle : InitiateState()
    object Loading : InitiateState()
    data class Ready(val response: InitiateResponse) : InitiateState()
    data class Error(val message: String) : InitiateState()
}

sealed class ConfirmState {
    object Idle : ConfirmState()
    object Loading : ConfirmState()
    data class Success(val result: ConfirmResponse) : ConfirmState()
    data class Error(val message: String) : ConfirmState()
}

class MainViewModel : ViewModel() {

    private val _initiateState = MutableStateFlow<InitiateState>(InitiateState.Idle)
    val initiateState: StateFlow<InitiateState> = _initiateState

    private val _confirmState = MutableStateFlow<ConfirmState>(ConfirmState.Idle)
    val confirmState: StateFlow<ConfirmState> = _confirmState

    private var pendingInit: InitiateResponse? = null
    private var pendingDeviceId: String = ""
    private var pendingNonce: String = ""
    var lastNonce: String = ""
        private set

    fun initiate(text: String, language: String, deviceId: String) {
        pendingDeviceId = deviceId
        pendingNonce = TransactionManager.generateNonce()
        lastNonce = pendingNonce
        viewModelScope.launch {
            _initiateState.value = InitiateState.Loading
            try {
                val resp = NidhiClient.api.initiate(
                    InitiateRequest(text, language, deviceId, pendingNonce)
                )
                if (resp.success) {
                    pendingInit = resp
                    _initiateState.value = InitiateState.Ready(resp)
                } else {
                    _initiateState.value = InitiateState.Error(resp.errorMessage ?: "Initiation failed")
                }
            } catch (e: Exception) {
                _initiateState.value = InitiateState.Error(e.message ?: "Network error")
            }
        }
    }

    fun confirm() {
        val init = pendingInit ?: return
        viewModelScope.launch {
            _confirmState.value = ConfirmState.Loading
            try {
                val signature = TransactionManager.signPayload(init.signingPayload!!, init.privateKeyBase64!!)
                val result = NidhiClient.api.confirm(
                    ConfirmRequest(init.txId!!, pendingDeviceId, pendingNonce, signature)
                )
                _confirmState.value = if (result.success) ConfirmState.Success(result)
                else ConfirmState.Error(result.errorMessage ?: result.errorCode ?: "Failed")
            } catch (e: Exception) {
                _confirmState.value = ConfirmState.Error(e.message ?: "Network error")
            }
        }
    }

    fun resetInitiate() { _initiateState.value = InitiateState.Idle }
    fun resetConfirm() { _confirmState.value = ConfirmState.Idle }
}
