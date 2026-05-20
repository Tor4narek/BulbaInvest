package com.bulbainvest.gateway.domain

enum class DownstreamService {
    DOMAIN,
    GRAPH,
}

data class DownstreamTarget(
    val service: DownstreamService,
    val baseUrl: String,
)

interface DownstreamServiceRegistry {
    fun targetFor(service: DownstreamService): DownstreamTarget
}
