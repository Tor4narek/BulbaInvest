#include "exchange_driver.h"

#include <assert.h>
#include <stdio.h>

static StockQuote first_quote(void) {
    MarketSnapshot snapshot = {0};
    StockQuote quote;

    assert(exchange_get_snapshot(&snapshot) == 0);
    assert(snapshot.len >= 10);
    quote = snapshot.items[0];
    exchange_free_snapshot(&snapshot);
    return quote;
}

static void test_default_init(void) {
    MarketSnapshot snapshot = {0};

    assert(exchange_init("") == 0);
    assert(exchange_get_snapshot(&snapshot) == 0);
    assert(snapshot.len == 10);
    assert(snapshot.items != NULL);
    exchange_free_snapshot(&snapshot);
}

static void test_tick_changes_prices(void) {
    MarketSnapshot before = {0};
    MarketSnapshot after = {0};
    int changed = 0;
    size_t i;

    assert(exchange_get_snapshot(&before) == 0);
    for (i = 0; i < 20; ++i) {
        assert(exchange_tick() == 0);
    }
    assert(exchange_get_snapshot(&after) == 0);

    for (i = 0; i < before.len && i < after.len; ++i) {
        assert(after.items[i].price >= 1.0);
        if (before.items[i].price != after.items[i].price) {
            changed = 1;
        }
    }

    exchange_free_snapshot(&before);
    exchange_free_snapshot(&after);
    assert(changed == 1);
}

static void test_buy_and_sell_quantity(void) {
    StockQuote quote = first_quote();
    MarketSnapshot snapshot = {0};

    assert(exchange_apply_buy(quote.ticker, 3) == 0);
    assert(exchange_get_snapshot(&snapshot) == 0);
    assert(snapshot.items[0].available_quantity == quote.available_quantity - 3);
    exchange_free_snapshot(&snapshot);

    assert(exchange_apply_buy(quote.ticker, quote.available_quantity + 1) != 0);

    assert(exchange_apply_sell(quote.ticker, 3) == 0);
    assert(exchange_get_snapshot(&snapshot) == 0);
    assert(snapshot.items[0].available_quantity == quote.available_quantity);
    exchange_free_snapshot(&snapshot);
}

int main(void) {
    test_default_init();
    test_tick_changes_prices();
    test_buy_and_sell_quantity();
    exchange_shutdown();
    puts("exchange_driver tests passed");
    return 0;
}
