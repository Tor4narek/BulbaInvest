CC ?= gcc
CFLAGS ?= -std=c11 -Wall -Wextra -pedantic -O2

.PHONY: all test clean

all: exchange_driver_test

exchange_driver_test: exchange_driver.c exchange_driver.h exchange_driver_test.c
	$(CC) $(CFLAGS) exchange_driver.c exchange_driver_test.c -o exchange_driver_test

test: exchange_driver_test
	./exchange_driver_test

clean:
	rm -f exchange_driver_test
