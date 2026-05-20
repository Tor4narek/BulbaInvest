package com.bulbainvest.domain.api

class BadRequestException(message: String) : RuntimeException(message)
class NotFoundException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)
class UnauthorizedException(message: String = "Unauthorized") : RuntimeException(message)
