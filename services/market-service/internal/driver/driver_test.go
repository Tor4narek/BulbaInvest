package driver

import "testing"

func TestDriverSnapshotUsesDefaultQuotes(t *testing.T) {
	if err := Init(""); err != nil {
		t.Fatalf("Init() error = %v", err)
	}
	defer Shutdown()

	quotes, err := Snapshot()
	if err != nil {
		t.Fatalf("Snapshot() error = %v", err)
	}
	if len(quotes) != 10 {
		t.Fatalf("len(Snapshot()) = %d, want 10", len(quotes))
	}
	if quotes[0].Ticker == "" {
		t.Fatal("first quote ticker is empty")
	}
}

func TestDriverApplyBuyChangesQuantity(t *testing.T) {
	if err := Init(""); err != nil {
		t.Fatalf("Init() error = %v", err)
	}
	defer Shutdown()

	before, err := Snapshot()
	if err != nil {
		t.Fatalf("Snapshot() before error = %v", err)
	}

	if err := ApplyBuy(before[0].Ticker, 2); err != nil {
		t.Fatalf("ApplyBuy() error = %v", err)
	}

	after, err := Snapshot()
	if err != nil {
		t.Fatalf("Snapshot() after error = %v", err)
	}
	if after[0].AvailableQuantity != before[0].AvailableQuantity-2 {
		t.Fatalf("available quantity = %d, want %d", after[0].AvailableQuantity, before[0].AvailableQuantity-2)
	}
}
