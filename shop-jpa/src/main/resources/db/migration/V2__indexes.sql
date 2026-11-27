-- Task 5: indexes for the queries the app runs
CREATE INDEX idx_orders_customer_placed  ON orders (customer_id, placed_at DESC);
CREATE INDEX idx_order_line_order        ON order_line (order_id);
CREATE INDEX idx_product_category_amount ON product (category, amount);
