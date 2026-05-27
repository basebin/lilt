package com.lilt.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lilt.core.theme.LiltColors

@Composable
fun AuthOnboardingScreen(
    isBusy: Boolean = false,
    errorMessage: String? = null,
    onContinue: (String) -> Unit,
) {
    var phone by remember { mutableStateOf("") }
    val normalizedPhone = phone.trim().replace(" ", "").replace("%2B", "+")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LiltColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Lilt",
            color = LiltColors.Ink,
            fontWeight = FontWeight.Black,
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "A quiet messenger for close conversations.",
            color = LiltColors.Muted,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("+1 5555550100") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
            onClick = { onContinue(normalizedPhone) },
            modifier = Modifier.fillMaxWidth(),
            enabled = normalizedPhone.startsWith("+") && normalizedPhone.length >= 8 && !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = LiltColors.Ink),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (isBusy) "Sending" else "Continue")
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

@Composable
fun OtpVerificationScreen(
    phoneNumber: String,
    isBusy: Boolean = false,
    errorMessage: String? = null,
    onVerified: (String) -> Unit,
    onChangeNumber: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LiltColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Verify",
            color = LiltColors.Ink,
            fontWeight = FontWeight.Black,
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Code sent to $phoneNumber",
            color = LiltColors.Muted,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.take(6) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("6 digit code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
            onClick = { onVerified(code) },
            modifier = Modifier.fillMaxWidth(),
            enabled = code.length == 6 && !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = LiltColors.Ink),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (isBusy) "Checking" else "Verify")
        }
        errorMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                text = it,
                color = LiltColors.Teal,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }
        TextButton(onClick = onChangeNumber, modifier = Modifier.fillMaxWidth()) {
            Text("Change number", color = LiltColors.Muted)
        }
    }
}
