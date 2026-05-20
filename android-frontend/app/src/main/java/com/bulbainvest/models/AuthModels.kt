package com.bulbainvest.models

data class CodeRequest(
    val email: String
)

data class ConfirmRequest(
    val email: String,
    val code: String
)

data class AuthResponse(
    val token: String  // ← только token, без userId
)