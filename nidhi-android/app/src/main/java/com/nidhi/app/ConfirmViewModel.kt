package com.nidhi.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ConfirmViewModel : ViewModel() {

    private val _state = MutableStateFlow<ConfirmState>(ConfirmState.Idle)
    val state: StateFlow<ConfirmState> = _state

    private var txId = ""
    private var deviceId = ""
    private var nonce = ""
    private var signingPayload = ""
    private var privateKeyB64 = ""

    fun setPending(txId: String, deviceId: String, nonce: String, payload: String, key: String) {
        this.txId = txId
        this.deviceId = deviceId
        this.nonce = nonce
        this.signingPayload = payload
        this.privateKeyB64 = key
    }

    fun confirm() {
        viewModelScope.launch {
            _state.value = ConfirmState.Loading
            try {
                val signature = TransactionManager.signPayload(signingPayload, privateKeyB64)
                val result = NidhiClient.api.confirm(
                    ConfirmRequest(txId, deviceId, nonce, signature)
                )
                _state.value = if (result.success) ConfirmState.Success(result)
                else ConfirmState.Error(result.errorMessage ?: result.errorCode ?: "Failed")
            } catch (e: Exception) {
                _state.value = ConfirmState.Error(e.message ?: "Network error")
            }
        }
    }

    fun reset() { _state.value = ConfirmState.Idle }
}
