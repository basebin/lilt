package com.lilt.feature.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lilt.core.theme.LiltColors
import com.lilt.core.theme.LiltTheme
import com.lilt.feature.auth.AuthOnboardingScreen
import com.lilt.feature.auth.OtpVerificationScreen
import com.lilt.feature.chat.ChatRoute
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

private enum class RootStep {
    Onboarding,
    Otp,
    Profile,
    Contacts,
    Notifications,
    Home,
}

@Composable
fun LiltRootApp(authViewModel: LiltAuthViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val authState by authViewModel.uiState.collectAsState()
    val sessionStore = remember { LocalSessionStore(context) }
    val savedPhoneNumber = remember { sessionStore.phoneNumber() }
    val savedDisplayName = remember { sessionStore.displayName() }
    var step by remember {
        mutableStateOf(
            if (savedPhoneNumber.isBlank() && authState.session == null) {
                RootStep.Onboarding
            } else {
                RootStep.Home
            },
        )
    }
    var phoneNumber by remember {
        mutableStateOf(savedPhoneNumber.ifBlank { authState.session?.phoneNumber.orEmpty() })
    }
    var displayName by remember {
        mutableStateOf(savedDisplayName.ifBlank { authState.session?.displayName.orEmpty() })
    }

    LaunchedEffect(authState.challenge?.verificationId) {
        val challenge = authState.challenge ?: return@LaunchedEffect
        phoneNumber = challenge.phoneNumber
        step = RootStep.Otp
    }

    LaunchedEffect(authState.session?.userId) {
        val session = authState.session ?: return@LaunchedEffect
        val verifiedPhone = session.phoneNumber ?: phoneNumber
        phoneNumber = verifiedPhone
        displayName = session.displayName ?: displayName
        sessionStore.saveSession(verifiedPhone, displayName)
        if (step == RootStep.Onboarding || step == RootStep.Otp) {
            step = RootStep.Profile
        }
    }

    LaunchedEffect(authState.profileSaved) {
        if (!authState.profileSaved) return@LaunchedEffect
        val savedName = authState.session?.displayName ?: displayName
        displayName = savedName
        sessionStore.saveDisplayName(savedName)
        step = RootStep.Contacts
    }

    LiltTheme {
        when (step) {
            RootStep.Onboarding -> AuthOnboardingScreen(
                isBusy = authState.isBusy,
                errorMessage = authState.errorMessage,
                onContinue = {
                    phoneNumber = it
                    activity?.let { currentActivity ->
                        authViewModel.requestOtp(currentActivity, phoneNumber)
                    }
                },
            )

            RootStep.Otp -> OtpVerificationScreen(
                phoneNumber = phoneNumber,
                isBusy = authState.isBusy,
                errorMessage = authState.errorMessage,
                onVerified = authViewModel::verifyOtp,
                onChangeNumber = {
                    authViewModel.resetChallenge()
                    step = RootStep.Onboarding
                },
            )

            RootStep.Profile -> ProfileSetupScreen(
                initialName = displayName,
                isBusy = authState.isBusy,
                errorMessage = authState.errorMessage,
                onContinue = {
                    displayName = it
                    authViewModel.saveDisplayName(it)
                },
            )

            RootStep.Contacts -> ContactsSyncScreen(
                onSync = { step = RootStep.Notifications },
                onSkip = { step = RootStep.Notifications },
            )

            RootStep.Notifications -> NotificationPermissionScreen(
                onDone = { step = RootStep.Home },
            )

            RootStep.Home -> ChatRoute(
                profileLabel = displayName.ifBlank { phoneNumber.ifBlank { "You" } },
                onSignOut = {
                    authViewModel.signOut()
                    sessionStore.clear()
                    phoneNumber = ""
                    displayName = ""
                    step = RootStep.Onboarding
                },
            )
        }
    }
}

@Composable
private fun ProfileSetupScreen(
    initialName: String,
    isBusy: Boolean,
    errorMessage: String?,
    onContinue: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val cleanName = name.trim()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LiltColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Your Name",
            color = LiltColors.Ink,
            fontWeight = FontWeight.Black,
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Choose how you appear in chats.",
            color = LiltColors.Muted,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(32) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Name") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = LiltColors.Teal,
                unfocusedIndicatorColor = LiltColors.Line,
            ),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onContinue(cleanName) },
            modifier = Modifier.fillMaxWidth(),
            enabled = cleanName.length >= 2 && !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = LiltColors.Ink),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (isBusy) "Saving" else "Continue")
        }
        errorMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                text = it,
                color = LiltColors.Teal,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun NotificationPermissionScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LiltColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Stay Updated",
            color = LiltColors.Ink,
            fontWeight = FontWeight.Black,
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Allow notifications for new messages.",
            color = LiltColors.Muted,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    onDone()
                } else {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = LiltColors.Ink),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Allow notifications")
        }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Not now", color = LiltColors.Muted)
        }
    }
}

@Composable
private fun ContactsSyncScreen(
    onSync: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    var syncedCount by remember { mutableStateOf<Int?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            syncedCount = context.readContactNames(limit = 250).size
            onSync()
        } else {
            permissionDenied = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LiltColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Contacts",
            color = LiltColors.Ink,
            fontWeight = FontWeight.Black,
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = when {
                syncedCount != null -> "${syncedCount ?: 0} contacts ready."
                permissionDenied -> "Contacts permission was not granted."
                else -> "Keep contacts ready for chat matching."
            },
            color = if (permissionDenied) LiltColors.Teal else LiltColors.Muted,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    syncedCount = context.readContactNames(limit = 250).size
                    onSync()
                } else {
                    launcher.launch(Manifest.permission.READ_CONTACTS)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = LiltColors.Ink),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Sync contacts")
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Skip for now", color = LiltColors.Muted)
        }
    }
}

private fun Context.readContactNames(limit: Int): List<String> {
    val names = mutableListOf<String>()
    val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
    val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"

    contentResolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        projection,
        null,
        null,
        sortOrder,
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        while (cursor.moveToNext() && names.size < limit) {
            val name = cursor.getString(nameIndex).orEmpty().trim()
            if (name.isNotEmpty()) {
                names += name
            }
        }
    }

    return names
}
