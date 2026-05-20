package com.bulbainvest.gateway.domain

class BadGatewayException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class GatewayTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
