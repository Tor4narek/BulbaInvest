package com.bulbainvest.domain.service

import com.bulbainvest.domain.api.NotFoundException
import com.bulbainvest.domain.db.PortfolioPositions
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class PortfolioService {
    fun list(userId: UUID): List<ResultRow> = transaction {
        PortfolioPositions.selectAll()
            .where { PortfolioPositions.userId eq userId }
            .orderBy(PortfolioPositions.ticker to SortOrder.ASC)
            .toList()
    }

    fun get(userId: UUID, ticker: String): ResultRow = transaction {
        PortfolioPositions.selectAll()
            .where { (PortfolioPositions.userId eq userId) and (PortfolioPositions.ticker eq ticker) }
            .firstOrNull() ?: throw NotFoundException("Position not found")
    }
}
