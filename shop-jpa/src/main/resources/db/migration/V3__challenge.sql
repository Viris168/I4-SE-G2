-- Challenge: optimistic variant of the stock reservation (@Version on Product)
ALTER TABLE product ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Challenge: keyset scrolling of the order feed (ORDER BY placed_at DESC, id DESC)
CREATE INDEX idx_orders_placed_id ON orders (placed_at DESC, id DESC);

-- Challenge: bulk import. Every "next value for product_seq" call flushes the pending JDBC batch,
-- so with INCREMENT BY 50 a StatelessSession never sends more than 50 rows per batch.
-- 500 matches the import chunk size (Product.allocationSize must match).
ALTER SEQUENCE product_seq INCREMENT BY 500;
