CREATE SEQUENCE customer_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE product_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE tag_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE orders_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE order_line_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE customer (
    id           BIGINT        PRIMARY KEY,
    name         VARCHAR(120)  NOT NULL,
    email        VARCHAR(180)  NOT NULL UNIQUE,
    street       VARCHAR(120)  NOT NULL,
    city         VARCHAR(80)   NOT NULL,
    zip          VARCHAR(20)   NOT NULL,
    ship_street  VARCHAR(120)  NOT NULL,
    ship_city    VARCHAR(80)   NOT NULL,
    ship_zip     VARCHAR(20)   NOT NULL
);

CREATE TABLE product (
    id        BIGINT         PRIMARY KEY,
    sku       VARCHAR(20)    NOT NULL UNIQUE,
    name      VARCHAR(120)   NOT NULL,
    amount    DECIMAL(12, 2) NOT NULL,
    currency  CHAR(3)        NOT NULL,
    category  VARCHAR(20)    NOT NULL,
    stock     INT            NOT NULL DEFAULT 0 CHECK (stock >= 0)
);

CREATE TABLE tag (
    id    BIGINT      PRIMARY KEY,
    name  VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE product_tag (
    product_id  BIGINT NOT NULL,
    tag_id      BIGINT NOT NULL,
    PRIMARY KEY (product_id, tag_id),
    CONSTRAINT fk_product_tag_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_product_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id)
);

CREATE TABLE orders (
    id           BIGINT       PRIMARY KEY,
    customer_id  BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    placed_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
);

CREATE TABLE order_line (
    id          BIGINT         PRIMARY KEY,
    order_id    BIGINT         NOT NULL,
    product_id  BIGINT         NOT NULL,
    quantity    INT            NOT NULL CHECK (quantity > 0),
    amount      DECIMAL(12, 2) NOT NULL,
    currency    CHAR(3)        NOT NULL,
    CONSTRAINT fk_order_line_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_line_product FOREIGN KEY (product_id) REFERENCES product (id)
);
