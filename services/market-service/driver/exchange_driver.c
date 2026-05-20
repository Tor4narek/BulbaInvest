#include "exchange_driver.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static StockQuote* g_quotes = NULL;
static size_t g_quotes_len = 0;
static int g_initialized = 0;

static double round_price(double price) {
    unsigned long long cents;

    if (price < 0.0) {
        return 0.0;
    }

    cents = (unsigned long long)(price * 100.0 + 0.5);
    return (double)cents / 100.0;
}

static int copy_ticker(char dest[TICKER_MAX_LEN], const char* src) {
    size_t len;

    if (src == NULL || src[0] == '\0') {
        return -1;
    }

    len = strlen(src);
    if (len >= TICKER_MAX_LEN) {
        return -1;
    }

    memset(dest, 0, TICKER_MAX_LEN);
    memcpy(dest, src, len);
    return 0;
}

static void set_quote(
    StockQuote* quote,
    const char* ticker,
    double price,
    uint64_t available_quantity,
    double volatility
) {
    copy_ticker(quote->ticker, ticker);
    quote->price = round_price(price < 1.0 ? 1.0 : price);
    quote->available_quantity = available_quantity;
    quote->volatility = volatility < 0.0 ? 0.0 : volatility;
    quote->updated_at_unix = (int64_t)time(NULL);
}

static int append_quote(
    StockQuote** items,
    size_t* len,
    const char* ticker,
    double price,
    uint64_t available_quantity,
    double volatility
) {
    StockQuote* next;

    if (copy_ticker((char[TICKER_MAX_LEN]){0}, ticker) != 0) {
        return -1;
    }

    next = (StockQuote*)realloc(*items, (*len + 1) * sizeof(StockQuote));
    if (next == NULL) {
        return -1;
    }

    *items = next;
    set_quote(&(*items)[*len], ticker, price, available_quantity, volatility);
    *len += 1;
    return 0;
}

static int load_defaults(StockQuote** items, size_t* len) {
    static const struct {
        const char* ticker;
        double price;
        uint64_t quantity;
        double volatility;
    } defaults[] = {
        {"AAPL", 192.45, 10000, 2.20},
        {"MSFT", 421.30, 8500, 2.00},
        {"GOOGL", 174.85, 9000, 2.40},
        {"AMZN", 185.10, 12000, 2.80},
        {"TSLA", 182.55, 7000, 5.50},
        {"NVDA", 906.15, 6500, 6.00},
        {"META", 489.70, 7200, 3.20},
        {"NFLX", 628.40, 5000, 4.10},
        {"JPM", 198.25, 11000, 1.60},
        {"DIS", 112.35, 9500, 1.90},
    };
    size_t i;

    for (i = 0; i < sizeof(defaults) / sizeof(defaults[0]); ++i) {
        if (append_quote(items, len, defaults[i].ticker, defaults[i].price, defaults[i].quantity, defaults[i].volatility) != 0) {
            return -1;
        }
    }

    return 0;
}

static int parse_config_line(
    const char* line,
    char ticker[TICKER_MAX_LEN],
    double* price,
    uint64_t* quantity,
    double* volatility
) {
    unsigned long long parsed_quantity = 0;
    char parsed_ticker[TICKER_MAX_LEN] = {0};
    int matched;

    matched = sscanf(line, " %15[^,],%lf,%llu,%lf", parsed_ticker, price, &parsed_quantity, volatility);
    if (matched != 4) {
        return -1;
    }

    if (copy_ticker(ticker, parsed_ticker) != 0) {
        return -1;
    }

    *quantity = (uint64_t)parsed_quantity;
    return 0;
}

static int load_config(const char* config_path, StockQuote** items, size_t* len) {
    FILE* file;
    char line[256];

    if (config_path == NULL || config_path[0] == '\0') {
        return -1;
    }

    file = fopen(config_path, "r");
    if (file == NULL) {
        return -1;
    }

    while (fgets(line, sizeof(line), file) != NULL) {
        char ticker[TICKER_MAX_LEN] = {0};
        double price = 0.0;
        uint64_t quantity = 0;
        double volatility = 0.0;

        if (line[0] == '#' || line[0] == '\n' || line[0] == '\r') {
            continue;
        }

        if (parse_config_line(line, ticker, &price, &quantity, &volatility) != 0) {
            continue;
        }

        if (append_quote(items, len, ticker, price, quantity, volatility) != 0) {
            fclose(file);
            return -1;
        }
    }

    fclose(file);
    return *len == 0 ? -1 : 0;
}

static int find_quote_index(const char* ticker) {
    size_t i;

    if (ticker == NULL || ticker[0] == '\0') {
        return -1;
    }

    for (i = 0; i < g_quotes_len; ++i) {
        if (strncmp(g_quotes[i].ticker, ticker, TICKER_MAX_LEN) == 0) {
            return (int)i;
        }
    }

    return -1;
}

int exchange_init(const char* config_path) {
    StockQuote* next_items = NULL;
    size_t next_len = 0;

    exchange_shutdown();

    if (load_config(config_path, &next_items, &next_len) != 0) {
        free(next_items);
        next_items = NULL;
        next_len = 0;
        if (load_defaults(&next_items, &next_len) != 0) {
            free(next_items);
            return -1;
        }
    }

    srand((unsigned int)time(NULL));
    g_quotes = next_items;
    g_quotes_len = next_len;
    g_initialized = 1;
    return 0;
}

int exchange_tick(void) {
    size_t i;

    if (!g_initialized) {
        return -1;
    }

    for (i = 0; i < g_quotes_len; ++i) {
        double unit = (double)rand() / (double)RAND_MAX;
        double delta = ((unit * 2.0) - 1.0) * g_quotes[i].volatility;
        double next_price = g_quotes[i].price + delta;

        if (next_price < 1.0) {
            next_price = 1.0;
        }

        g_quotes[i].price = round_price(next_price);
        g_quotes[i].updated_at_unix = (int64_t)time(NULL);
    }

    return 0;
}

int exchange_get_snapshot(MarketSnapshot* out) {
    StockQuote* copy;

    if (!g_initialized || out == NULL) {
        return -1;
    }

    out->items = NULL;
    out->len = 0;

    if (g_quotes_len == 0) {
        return 0;
    }

    copy = (StockQuote*)malloc(g_quotes_len * sizeof(StockQuote));
    if (copy == NULL) {
        return -1;
    }

    memcpy(copy, g_quotes, g_quotes_len * sizeof(StockQuote));
    out->items = copy;
    out->len = g_quotes_len;
    return 0;
}

int exchange_apply_buy(const char* ticker, uint64_t quantity) {
    int idx;

    if (!g_initialized) {
        return -1;
    }

    idx = find_quote_index(ticker);
    if (idx < 0) {
        return -2;
    }

    if (g_quotes[idx].available_quantity < quantity) {
        return -3;
    }

    g_quotes[idx].available_quantity -= quantity;
    g_quotes[idx].updated_at_unix = (int64_t)time(NULL);
    return 0;
}

int exchange_apply_sell(const char* ticker, uint64_t quantity) {
    int idx;

    if (!g_initialized) {
        return -1;
    }

    idx = find_quote_index(ticker);
    if (idx < 0) {
        return -2;
    }

    if (UINT64_MAX - g_quotes[idx].available_quantity < quantity) {
        return -4;
    }

    g_quotes[idx].available_quantity += quantity;
    g_quotes[idx].updated_at_unix = (int64_t)time(NULL);
    return 0;
}

int exchange_set_available_quantity(const char* ticker, uint64_t quantity) {
    int idx;

    if (!g_initialized) {
        return -1;
    }

    idx = find_quote_index(ticker);
    if (idx < 0) {
        return -2;
    }

    g_quotes[idx].available_quantity = quantity;
    g_quotes[idx].updated_at_unix = (int64_t)time(NULL);
    return 0;
}

void exchange_free_snapshot(MarketSnapshot* snapshot) {
    if (snapshot == NULL) {
        return;
    }

    free(snapshot->items);
    snapshot->items = NULL;
    snapshot->len = 0;
}

void exchange_shutdown(void) {
    free(g_quotes);
    g_quotes = NULL;
    g_quotes_len = 0;
    g_initialized = 0;
}
