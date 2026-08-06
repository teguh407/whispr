package com.whispr.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.app.ui.theme.*
import androidx.compose.foundation.clickable
import com.whispr.app.viewmodel.WhisprViewModel

@Composable
fun RegisterScreen(
    viewModel: WhisprViewModel,
    onRegisterSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val isLoading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    LaunchedEffect(isLoggedIn) { if (isLoggedIn) onRegisterSuccess() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Background, Surface)))
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.PersonAdd, null, tint = PrimaryPink, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("Create Account", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text("Username") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryPurple,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                unfocusedContainerColor = CardBg,
                focusedContainerColor = Color.Transparent,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            ),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = displayName, onValueChange = { displayName = it },
            label = { Text("Display Name") },
            leadingIcon = { Icon(Icons.Default.Badge, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryPurple,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                unfocusedContainerColor = CardBg,
                focusedContainerColor = Color.Transparent,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            ),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryPurple,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                unfocusedContainerColor = CardBg,
                focusedContainerColor = Color.Transparent,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            ),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword, onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryPurple,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                unfocusedContainerColor = CardBg,
                focusedContainerColor = Color.Transparent,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            ),
            singleLine = true
        )
        Spacer(Modifier.height(24.dp))

        val passwordsMatch = password == confirmPassword || confirmPassword.isBlank()
        val canRegister = username.isNotBlank() && displayName.isNotBlank() &&
                password.isNotBlank() && passwordsMatch && password.length >= 6

        Button(
            onClick = { viewModel.register(username, password, displayName) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading && canRegister,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            else Text("Register", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        error?.let { Text(it, color = ErrorRed, textAlign = TextAlign.Center); Spacer(Modifier.height(8.dp)) }

        if (!passwordsMatch && confirmPassword.isNotBlank()) {
            Text("Passwords don't match", color = ErrorRed); Spacer(Modifier.height(8.dp))
        }

        Text("Already have an account? Login", color = PrimaryPurple,
            modifier = Modifier.clickable { onBack() })
    }
}
