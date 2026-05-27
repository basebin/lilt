package com.lilt.data.auth

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.lilt.data.local.LiltDatabase
import com.lilt.domain.auth.AuthSession
import com.lilt.domain.auth.OtpChallenge
import com.lilt.domain.auth.OtpRequest
import com.lilt.domain.auth.PhoneAuthRepository
import com.lilt.domain.auth.PhoneAuthStartResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Singleton
class FirebasePhoneAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val firebaseMessaging: FirebaseMessaging,
    private val database: LiltDatabase,
) : PhoneAuthRepository {
    override fun currentSession(): AuthSession? {
        val user = firebaseAuth.currentUser ?: return null
        return AuthSession(
            userId = user.uid,
            phoneNumber = user.phoneNumber,
            displayName = user.displayName,
        )
    }

    override suspend fun requestOtp(activity: Activity, request: OtpRequest): PhoneAuthStartResult =
        suspendCancellableCoroutine { continuation ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    firebaseAuth.signInWithCredential(credential)
                        .addOnSuccessListener { result ->
                            val user = result.user
                            if (!continuation.isActive) return@addOnSuccessListener
                            if (user == null) {
                                continuation.resumeWithException(
                                    IllegalStateException("Firebase did not return a user."),
                                )
                            } else {
                                continuation.resume(
                                    PhoneAuthStartResult.Verified(
                                        AuthSession(
                                            userId = user.uid,
                                            phoneNumber = user.phoneNumber ?: request.phoneNumber,
                                            displayName = user.displayName,
                                        ),
                                    ),
                                )
                            }
                        }
                        .addOnFailureListener { exception ->
                            if (continuation.isActive) {
                                continuation.resumeWithException(exception)
                            }
                        }
                }

                override fun onVerificationFailed(exception: com.google.firebase.FirebaseException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken,
                ) {
                    if (continuation.isActive) {
                        continuation.resume(
                            PhoneAuthStartResult.CodeSent(
                                OtpChallenge(
                                    verificationId = verificationId,
                                    phoneNumber = request.phoneNumber,
                                ),
                            ),
                        )
                    }
                }
            }

            val options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(request.phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        }

    override suspend fun verifyOtp(challenge: OtpChallenge, code: String): AuthSession =
        suspendCancellableCoroutine { continuation ->
            val credential = PhoneAuthProvider.getCredential(challenge.verificationId, code)
            firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user == null) {
                        continuation.resumeWithException(IllegalStateException("Firebase did not return a user."))
                    } else {
                        continuation.resume(
                            AuthSession(
                                userId = user.uid,
                                phoneNumber = user.phoneNumber ?: challenge.phoneNumber,
                                displayName = user.displayName,
                            ),
                        )
                    }
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }

    override suspend fun saveDisplayName(displayName: String): AuthSession {
        val cleanName = displayName.trim()
        val user = firebaseAuth.currentUser ?: error("Sign in before setting a profile name.")
        val profileUpdate = UserProfileChangeRequest.Builder()
            .setDisplayName(cleanName)
            .build()
        user.updateProfile(profileUpdate).await()
        firestore.collection("users")
            .document(user.uid)
            .set(
                mapOf(
                    "id" to user.uid,
                    "phoneNumber" to user.phoneNumber.orEmpty(),
                    "displayName" to cleanName,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()
        return AuthSession(
            userId = user.uid,
            phoneNumber = user.phoneNumber,
            displayName = cleanName,
        )
    }

    override suspend fun signOut() {
        val user = firebaseAuth.currentUser
        if (user != null) {
            runCatching {
                val token = firebaseMessaging.token.await()
                firestore.collection("users")
                    .document(user.uid)
                    .update(
                        mapOf(
                            "fcmTokens" to FieldValue.arrayRemove(token),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                    .await()
            }
        }
        firebaseAuth.signOut()
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
    }
}
