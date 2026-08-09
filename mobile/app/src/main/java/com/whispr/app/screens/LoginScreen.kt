package com.whispr.app.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.app.ui.theme.*
import com.whispr.app.util.GoogleAuthHelper
import com.whispr.app.viewmodel.WhisprViewModel
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: WhisprViewModel,
    onLoginSuccess: () -> Unit,
    onGoToRegister: () -> Unit,
    onSettings: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    val isLoading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val context = LocalContext.current
    var googleError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) onLoginSuccess()
    }

    // Google Sign-In launcher — uses classic GoogleSignInClient
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (val res = GoogleAuthHelper.handleResult(result)) {
            is GoogleAuthHelper.Result.Success ->
                viewModel.googleAuth(res.idToken)
            is GoogleAuthHelper.Result.Cancelled -> { /* user closed picker */ }
            is GoogleAuthHelper.Result.Error ->
                googleError = res.message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Background, Surface)
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo area
        Icon(
            Icons.Default.Lock,
            contentDescription = "Whispr",
            tint = PrimaryPurple,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Whispr",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            "Anonymous. Real. You.",
            fontSize = 14.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(48.dp))

        // Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryPurple,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = CardBg,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true
        )
        if (emailError != null) {
            Text(emailError!!, color = ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }
        Spacer(Modifier.height(12.dp))

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        null
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryPurple,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = CardBg,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
                if (!email.matches(emailRegex)) {
                    emailError = "Please enter a valid email address"
                } else {
                    emailError = null
                    viewModel.login(email, password)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank() && emailError == null,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))

        // Divider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Divider(modifier = Modifier.weight(1f), color = TextSecondary.copy(alpha = 0.3f))
            Text("  or  ", color = TextSecondary, fontSize = 12.sp)
            Divider(modifier = Modifier.weight(1f), color = TextSecondary.copy(alpha = 0.3f))
        }
        Spacer(Modifier.height(16.dp))

        // Google Sign-In
        Surface(
            onClick = {
                val intent = GoogleAuthHelper.getSignInIntent(context)
                googleSignInLauncher.launch(intent)
            },
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stylized Google "G" logo
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text("G", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Continue with Google",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // Error
        error?.let {
            Text(it, color = ErrorRed, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }

        // Register link
        Text(
            "Don't have an account? Register",
            color = PrimaryPink,
            modifier = Modifier.clickable { onGoToRegister() }
        )

        Spacer(Modifier.height(12.dp))

        // Settings
        Text(
            "⚙ Server Settings",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.clickable { onSettings() }
        )
    }

    // Full Google error dialog (debuggable — toast truncates the code)
    googleError?.let { msg ->
        AlertDialog(
            onDismissRequest = { googleError = null },
            containerColor = CardBg,
            title = { Text("Google Sign-In error", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    msg,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { googleError = null }) { Text("OK", color = PrimaryPurple) }
            }
        )
    }
}
