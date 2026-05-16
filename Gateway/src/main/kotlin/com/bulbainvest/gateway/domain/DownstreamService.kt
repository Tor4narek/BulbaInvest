package com.bulbainvest.gateway.domain

enum class DownstreamService {
    DOMAIN,
}

data class DownstreamTarget(
    val service: DownstreamService,
    val baseUrl: String,
)

interface DownstreamServiceRegistry {
    fun targetFor(service: DownstreamService): DownstreamTarget
}
