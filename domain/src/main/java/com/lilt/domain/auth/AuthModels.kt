package com.lilt.domain.auth

import android.app.Activity

data class OtpRequest(
    val phoneNumber: String,
)

data class OtpChallenge(
    val verificationId: String,
    val phoneNumber: String,
)

data class AuthSession(
    val userId: String,
    val phoneNumber: String?,
    val displayName: String? = null,
)

sealed interface PhoneAuthStartResult {
    data class CodeSent(val challenge: OtpChallenge) : PhoneAuthStartResult
    data class Verified(val session: AuthSession) : PhoneAuthStartResult
}

interface PhoneAuthRepository {
    fun currentSession(): AuthSession?
    suspend fun requestOtp(activity: Activity, request: OtpRequest): PhoneAuthStartResult
    suspend fun verifyOtp(challenge: OtpChallenge, code: String): AuthSession
    suspend fun saveDisplayName(displayName: String): AuthSession
    suspend fun signOut()
}
