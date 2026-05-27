package com.lilt.feature.home

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilt.domain.auth.AuthSession
import com.lilt.domain.auth.OtpChallenge
import com.lilt.domain.auth.OtpRequest
import com.lilt.domain.auth.PhoneAuthRepository
import com.lilt.domain.auth.PhoneAuthStartResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiltAuthUiState(
    val session: AuthSession? = null,
    val challenge: OtpChallenge? = null,
    val isBusy: Boolean = false,
    val profileSaved: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class LiltAuthViewModel @Inject constructor(
    private val phoneAuthRepository: PhoneAuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        LiltAuthUiState(session = phoneAuthRepository.currentSession()),
    )
    val uiState: StateFlow<LiltAuthUiState> = _uiState.asStateFlow()

    fun requestOtp(activity: Activity, phoneNumber: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, challenge = null) }
            runCatching {
                phoneAuthRepository.requestOtp(activity, OtpRequest(phoneNumber))
            }.onSuccess { result ->
                when (result) {
                    is PhoneAuthStartResult.CodeSent -> {
                        _uiState.update { it.copy(challenge = result.challenge, isBusy = false) }
                    }
                    is PhoneAuthStartResult.Verified -> {
                        _uiState.update {
                            it.copy(
                                session = result.session,
                                challenge = null,
                                isBusy = false,
                            )
                        }
                    }
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(isBusy = false, errorMessage = throwable.cleanMessage()) }
            }
        }
    }

    fun verifyOtp(code: String) {
        val challenge = _uiState.value.challenge ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            runCatching {
                phoneAuthRepository.verifyOtp(challenge, code)
            }.onSuccess { session ->
                _uiState.update { it.copy(session = session, isBusy = false, challenge = null) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(isBusy = false, errorMessage = throwable.cleanMessage()) }
            }
        }
    }

    fun resetChallenge() {
        _uiState.update { it.copy(challenge = null, errorMessage = null, isBusy = false) }
    }

    fun saveDisplayName(displayName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, profileSaved = false) }
            runCatching {
                phoneAuthRepository.saveDisplayName(displayName)
            }.onSuccess { session ->
                _uiState.update {
                    it.copy(
                        session = session,
                        isBusy = false,
                        profileSaved = true,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(isBusy = false, errorMessage = throwable.cleanMessage()) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            phoneAuthRepository.signOut()
            _uiState.value = LiltAuthUiState()
        }
    }
}

private fun Throwable.cleanMessage(): String =
    when {
        message?.contains("CONFIGURATION_NOT_FOUND") == true ->
            "Phone sign-in is not enabled for this Firebase project yet."
        message?.contains("INVALID_PHONE_NUMBER") == true ->
            "Enter the phone number with country code."
        else -> message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
    }
