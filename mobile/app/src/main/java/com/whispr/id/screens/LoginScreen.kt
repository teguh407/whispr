package com.whispr.id.screens

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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.whispr.id.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.id.ui.theme.*
import com.whispr.id.util.GoogleAuthHelper
import com.whispr.id.viewmodel.WhisprViewModel
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A0A2E),
                        Background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Decorative glow behind logo
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                PrimaryPurple.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Logo area
                Image(
                    painter = painterResource(id = R.drawable.whispr_logo),
                    contentDescription = "Whispr",
                    modifier = Modifier.size(80.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(16.dp))

            // App name with gradient text effect
            Text(
                stringResource(R.string.app_name),
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Anonymous. Real. You.",
                fontSize = 13.sp,
                color = TextSecondary,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(48.dp))

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; emailError = null },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, null, tint = TextSecondary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.2f),
                    focusedContainerColor = PrimaryPurple.copy(alpha = 0.06f),
                    unfocusedContainerColor = CardBg.copy(alpha = 0.6f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = PrimaryPurple
                ),
                singleLine = true
            )
            if (emailError != null) {
                Text(emailError!!, color = ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
            Spacer(Modifier.height(16.dp))

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = TextSecondary) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            null,
                            tint = TextSecondary
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.2f),
                    focusedContainerColor = PrimaryPurple.copy(alpha = 0.06f),
                    unfocusedContainerColor = CardBg.copy(alpha = 0.6f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = PrimaryPurple
                ),
                singleLine = true
            )

            // Forgot password
            Text(
                "Forgot Password?",
                color = PrimaryPurple,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp)
                    .clickable { /* TODO: forgot password flow */ }
            )

            Spacer(Modifier.height(28.dp))

            // Login button with gradient
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
                    .height(54.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(PrimaryPurple, VioletBright)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ),
                shape = RoundedCornerShape(14.dp),
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank() && emailError == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = PrimaryPurple.copy(alpha = 0.3f)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 2.dp
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        "Login",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // Divider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Divider(modifier = Modifier.weight(1f), color = TextSecondary.copy(alpha = 0.15f))
                Text(
                    "  or  ",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Divider(modifier = Modifier.weight(1f), color = TextSecondary.copy(alpha = 0.15f))
            }
            Spacer(Modifier.height(20.dp))

            // Google Sign-In
            Surface(
                onClick = {
                    val intent = GoogleAuthHelper.getSignInIntent(context)
                    googleSignInLauncher.launch(intent)
                },
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, TextSecondary.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
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
                        color = Color(0xFF1A1A28),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(28.dp))

            // Error
            error?.let {
                Text(it, color = ErrorRed, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(Modifier.height(8.dp))
            }

            // Register link
            Row(
                modifier = Modifier.clickable { onGoToRegister() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Don't have an account? ",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    "Register",
                    color = PrimaryPink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            // Settings
            Row(
                modifier = Modifier.clickable { onSettings() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Server Settings",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
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
