package com.whispr.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.app.network.ApiClient
import com.whispr.app.network.UserCreate
import com.whispr.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoginMode by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Text(
                text = "Whispr",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Anonymous conversations, reimagined",
                color = Muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 40.dp)
            )

            // Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Background)
                            .padding(4.dp)
                    ) {
                        TabButton(
                            text = "Login",
                            isSelected = isLoginMode,
                            modifier = Modifier.weight(1f)
                        ) { isLoginMode = true }
                        
                        TabButton(
                            text = "Register",
                            isSelected = !isLoginMode,
                            modifier = Modifier.weight(1f)
                        ) { isLoginMode = false }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Error message
                    error?.let {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Red.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = it,
                                color = Red,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Username field
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Color(0xFF2a2a3a),
                            focusedContainerColor = Background,
                            unfocusedContainerColor = Background
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Color(0xFF2a2a3a),
                            focusedContainerColor = Background,
                            unfocusedContainerColor = Background
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Submit button
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                error = null
                                try {
                                    val token = if (isLoginMode) {
                                        ApiClient.api.login(username, password).access_token
                                    } else {
                                        ApiClient.api.register(UserCreate(username, password)).access_token
                                    }
                                    onLoginSuccess(token)
                                } catch (e: Exception) {
                                    error = e.message ?: "An error occurred"
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isLoading && username.isNotBlank() && password.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isLoginMode) "Login" else "Create Account",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Accent else Color.Transparent,
            contentColor = if (isSelected) Color.White else Muted
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = null
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}
