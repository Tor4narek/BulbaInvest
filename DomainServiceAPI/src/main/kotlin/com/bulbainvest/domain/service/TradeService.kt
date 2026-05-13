package com.bulbainvest.domain.service

import com.bulbainvest.domain.api.BadRequestException
import com.bulbainvest.domain.api.NotFoundException
import com.bulbainvest.domain.db.CounterpartyType
import com.bulbainvest.domain.db.PortfolioPositions
import com.bulbainvest.domain.db.TradeType
import com.bulbainvest.domain.db.Trades
import com.bulbainvest.domain.db.UserWallets
import com.bulbainvest.domain.external.QuotesBrokerClient
import com.bulbainvest.domain.external.StocksMarketApiClient
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class TradeService(
    private val stocksMarketApi: StocksMarketApiClient,
    private val quotes: QuotesBrokerClient,
) {

    fun buyFromCompany(userId: UUID, ticker: String, quantity: BigDecimal, walletId: UUID?): ResultRow {
        require(quantity > BigDecimal.ZERO) { throw BadRequestException("quantity must be positive") }
        val price = quotes.getLastPrice(ticker)
        val total = price.multiply(quantity)

        val tradeId = UUID.randomUUID()
        // Сначала дёргаем внешний сервис (с идемпотентностью по tradeId).
        // Если он не вернёт OK — упадём раньше, чем тронем нашу БД.
        stocksMarketApi.decrementCompanyStocks(ticker, quantity, tradeId)

        transaction {
            val wallet = resolveWallet(userId, walletId)
            val available = wallet[UserWallets.amount].subtract(wallet[UserWallets.reservedAmount])
            if (available < total) throw BadRequestException("Insufficient funds")

            WalletUpdater.debit(wallet[UserWallets.id], total)
            PortfolioUpdater.addPosition(userId, ticker, quantity, price)

            Trades.insert {
                it[Trades.id] = tradeId
                it[buyerUserId] = userId
                it[sellerUserId] = null
                it[buyerWalletId] = wallet[UserWallets.id]
                it[sellerWalletId] = null
                it[counterpartyType] = CounterpartyType.COMPANY.name
                it[tradeType] = TradeType.BUY_FROM_COMPANY.name
                it[Trades.ticker] = ticker
                it[Trades.quantity] = quantity
                it[Trades.price] = price
                it[totalAmount] = total
                it[createdAt] = Instant.now()
            }
        }
        return transaction { Trades.selectAll().where { Trades.id eq tradeId }.first() }
    }

    fun sellToCompany(userId: UUID, ticker: String, quantity: BigDecimal, walletId: UUID?): ResultRow {
        require(quantity > BigDecimal.ZERO) { throw BadRequestException("quantity must be positive") }
        val price = quotes.getLastPrice(ticker)
        val total = price.multiply(quantity)

        val tradeId = UUID.randomUUID()

        // Проверки до внешнего вызова, чтобы не делать лишний increment.
        transaction {
            val pos = PortfolioPositions.selectAll()
                .where { (PortfolioPositions.userId eq userId) and (PortfolioPositions.ticker eq ticker) }
                .firstOrNull() ?: throw BadRequestException("No position for $ticker")
            val available = pos[PortfolioPositions.quantity].subtract(pos[PortfolioPositions.reservedQuantity])
            if (available < quantity) throw BadRequestException("Insufficient available quantity")
            resolveWallet(userId, walletId) // 404, если нет
        }

        stocksMarketApi.incrementCompanyStocks(ticker, quantity, tradeId)

        transaction {
            val wallet = resolveWallet(userId, walletId)
            PortfolioUpdater.removeFromPosition(userId, ticker, quantity)
            WalletUpdater.credit(wallet[UserWallets.id], total)

            Trades.insert {
                it[Trades.id] = tradeId
                it[buyerUserId] = null
                it[sellerUserId] = userId
                it[buyerWalletId] = null
                it[sellerWalletId] = wallet[UserWallets.id]
                it[counterpartyType] = CounterpartyType.COMPANY.name
                it[tradeType] = TradeType.SELL_TO_COMPANY.name
                it[Trades.ticker] = ticker
                it[Trades.quantity] = quantity
                it[Trades.price] = price
                it[totalAmount] = total
                it[createdAt] = Instant.now()
            }
        }
        return transaction { Trades.selectAll().where { Trades.id eq tradeId }.first() }
    }

    fun listMyTrades(userId: UUID): List<ResultRow> = transaction {
        Trades.selectAll()
            .where { (Trades.buyerUserId eq userId) or (Trades.sellerUserId eq userId) }
            .orderBy(Trades.createdAt to SortOrder.DESC)
            .toList()
    }

    private fun resolveWallet(userId: UUID, walletId: UUID?): ResultRow {
        val row = if (walletId != null) {
            UserWallets.selectAll()
                .where { (UserWallets.id eq walletId) and (UserWallets.userId eq userId) }
                .firstOrNull()
        } else {
            UserWallets.selectAll()
                .where { (UserWallets.userId eq userId) and (UserWallets.isDefault eq true) }
                .firstOrNull()
        }
        return row ?: throw NotFoundException("Wallet not found")
    }
}
