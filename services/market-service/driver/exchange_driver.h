#ifndef EXCHANGE_DRIVER_H
#define EXCHANGE_DRIVER_H

#include <stdint.h>
#include <stddef.h>

#define TICKER_MAX_LEN 16

typedef struct {
    char ticker[TICKER_MAX_LEN];
    double price;
    uint64_t available_quantity;
    double volatility;
    int64_t updated_at_unix;
} StockQuote;

typedef struct {
    StockQuote* items;
    size_t len;
} MarketSnapshot;

int exchange_init(const char* config_path);
int exchange_tick(void);
int exchange_get_snapshot(MarketSnapshot* out);
int exchange_apply_buy(const char* ticker, uint64_t quantity);
int exchange_apply_sell(const char* ticker, uint64_t quantity);
int exchange_set_available_quantity(const char* ticker, uint64_t quantity);
void exchange_free_snapshot(MarketSnapshot* snapshot);
void exchange_shutdown(void);

#endif
