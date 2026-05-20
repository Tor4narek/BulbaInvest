package com.bulbainvest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bulbainvest.repository.Repository
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    repository: Repository,
    onLoginSuccess: (String) -> Unit,  // ← только token, без userId
    onError: (String) -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var isCodeSent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🌾 Bulba Invest",
            fontSize = 32.sp
        )

        Text(
            text = "Инвестиции по-белорусски",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true
        )

        if (isCodeSent) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Код из письма") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = {
                if (email.isBlank()) {
                    errorMessage = "Введите email"
                    return@Button
                }

                if (!isCodeSent) {
                    isLoading = true
                    errorMessage = null

                    coroutineScope.launch {
                        val result = repository.requestCode(email)
                        isLoading = false
                        result.onSuccess {
                            isCodeSent = true
                            errorMessage = null
                        }.onFailure { error ->
                            errorMessage = "Ошибка: ${error.message}"
                        }
                    }
                } else {
                    if (code.length < 4) {
                        errorMessage = "Введите код"
                        return@Button
                    }

                    isLoading = true
                    errorMessage = null

                    coroutineScope.launch {
                        val result = repository.confirmCode(email, code)
                        isLoading = false
                        result.onSuccess { authResponse ->
                            onLoginSuccess(authResponse.token)
                        }.onFailure { error ->
                            errorMessage = "Ошибка: ${error.message}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(if (!isCodeSent) "Получить код" else "Войти")
            }
        }

        if (isCodeSent) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Код придёт на почту (проверьте MailHog https://bulba.onix.fun/mail/)",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}