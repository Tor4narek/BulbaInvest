package com.bulbainvest.gateway.infrastructure

import com.bulbainvest.gateway.config.AppConfig
import com.bulbainvest.gateway.domain.DownstreamService
import com.bulbainvest.gateway.domain.DownstreamServiceRegistry
import com.bulbainvest.gateway.domain.DownstreamTarget

class DefaultDownstreamServiceRegistry(
    private val config: AppConfig,
) : DownstreamServiceRegistry {
    override fun targetFor(service: DownstreamService): DownstreamTarget =
        when (service) {
            DownstreamService.DOMAIN -> DownstreamTarget(
                service = service,
                baseUrl = config.services.domain.baseUrl,
            )
            DownstreamService.GRAPH -> DownstreamTarget(
                service = service,
                baseUrl = config.services.graph.baseUrl,
            )
        }
}
