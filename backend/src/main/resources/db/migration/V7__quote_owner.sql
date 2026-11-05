-- Quote ownership (P-8, quote-engine spec). The owner is the CUSTOMER whose valid credentials accompanied POST /api/quotes.
-- It is set once, when the quote is inserted, and never updated: quotes stay append-only (NFR-02), and claiming an
-- anonymous quote is recorded by the application row, not by editing the quote. NULL means an anonymous quote.
-- The owner is never returned by the API (NFR-04.2).
ALTER TABLE quote ADD COLUMN owner_id VARCHAR(100);
