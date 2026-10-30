-- My Store POS - database for XAMPP (MariaDB 10.4+) or MySQL 8.
-- Import in phpMyAdmin (Import tab) or run:
--   C:\xampp\mysql\bin\mysql.exe -u root < database\pos_db.sql
-- Safe to run again: tables are only created if missing and seed rows are skipped if present.

CREATE DATABASE IF NOT EXISTS pos_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pos_db;

-- ------------------------------------------------------------------
-- Tables
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS users (
    user_id    INT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    full_name  VARCHAR(100) NOT NULL,
    pin_hash   CHAR(64)     NOT NULL COMMENT 'SHA-256 hex of the PIN',
    role       ENUM('ADMIN', 'CASHIER') NOT NULL DEFAULT 'CASHIER',
    active     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS products (
    product_id      INT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL UNIQUE,
    name            VARCHAR(150)  NOT NULL,
    category_id     INT           NULL,
    price           DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    stock           INT           NOT NULL DEFAULT 0,
    low_stock_level INT           NOT NULL DEFAULT 5,
    location        VARCHAR(100)  NULL COMMENT 'Where it is in the store, shown in the phone app',
    active          TINYINT(1)    NOT NULL DEFAULT 1,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id)
        REFERENCES categories (category_id) ON DELETE SET NULL,
    INDEX idx_products_name (name)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS sales (
    sale_id        INT AUTO_INCREMENT PRIMARY KEY,
    receipt_no     VARCHAR(20)   NOT NULL UNIQUE,
    user_id        INT           NOT NULL,
    sale_time      DATETIME      NOT NULL,
    subtotal       DECIMAL(10, 2) NOT NULL,
    tax            DECIMAL(10, 2) NOT NULL,
    total          DECIMAL(10, 2) NOT NULL,
    payment_method ENUM('CASH', 'CARD', 'EWALLET') NOT NULL DEFAULT 'CASH',
    amount_paid    DECIMAL(10, 2) NOT NULL,
    change_due     DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    status         VARCHAR(20)   NOT NULL DEFAULT 'PAID',
    CONSTRAINT fk_sales_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    INDEX idx_sales_time (sale_time)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS sale_items (
    sale_item_id INT AUTO_INCREMENT PRIMARY KEY,
    sale_id      INT           NOT NULL,
    product_id   INT           NOT NULL,
    quantity     INT           NOT NULL,
    unit_price   DECIMAL(10, 2) NOT NULL,
    line_total   DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_items_sale FOREIGN KEY (sale_id) REFERENCES sales (sale_id) ON DELETE CASCADE,
    CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS settings (
    setting_key   VARCHAR(50)  NOT NULL PRIMARY KEY,
    setting_value VARCHAR(255) NULL
) ENGINE = InnoDB;

-- Phone app: customers, branches and their online orders.
-- (The POS also creates these on start-up if they are missing, see com.pos.db.Schema.)

CREATE TABLE IF NOT EXISTS customers (
    customer_id   INT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    phone         VARCHAR(30)  NOT NULL UNIQUE,
    email         VARCHAR(150) NULL UNIQUE,
    address       VARCHAR(255) NOT NULL DEFAULT '',
    password_hash CHAR(64)     NOT NULL COMMENT 'PBKDF2-SHA256 hex',
    password_salt CHAR(32)     NOT NULL,
    token_hash    CHAR(64)     NULL COMMENT 'SHA-256 hex of the app sign-in token',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_customers_token (token_hash)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS stores (
    store_id  INT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(100)   NOT NULL,
    address   VARCHAR(255)   NOT NULL DEFAULT '',
    latitude  DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    phone     VARCHAR(30)    NOT NULL DEFAULT '',
    hours     VARCHAR(100)   NOT NULL DEFAULT '',
    active    TINYINT(1)     NOT NULL DEFAULT 1
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS online_orders (
    order_id         INT AUTO_INCREMENT PRIMARY KEY,
    order_no         VARCHAR(20)    NOT NULL UNIQUE,
    customer_id      INT            NOT NULL,
    store_id         INT            NOT NULL,
    order_type       ENUM('DELIVERY', 'IN_STORE') NOT NULL,
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    payment_method   ENUM('CASH', 'CARD', 'EWALLET') NOT NULL DEFAULT 'CASH',
    subtotal         DECIMAL(10, 2) NOT NULL,
    tax              DECIMAL(10, 2) NOT NULL,
    total            DECIMAL(10, 2) NOT NULL,
    delivery_address VARCHAR(255)   NULL,
    delivery_lat     DECIMAL(10, 7) NULL,
    delivery_lng     DECIMAL(10, 7) NULL,
    in_store_spot    VARCHAR(100)   NULL,
    note             VARCHAR(255)   NULL,
    rider_code       CHAR(8)        NOT NULL UNIQUE,
    rider_lat        DECIMAL(10, 7) NULL,
    rider_lng        DECIMAL(10, 7) NULL,
    rider_updated_at DATETIME       NULL,
    dispatched_at    DATETIME       NULL,
    sale_id          INT            NULL,
    created_at       DATETIME       NOT NULL,
    updated_at       DATETIME       NOT NULL,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id),
    CONSTRAINT fk_orders_store FOREIGN KEY (store_id) REFERENCES stores (store_id),
    CONSTRAINT fk_orders_sale FOREIGN KEY (sale_id) REFERENCES sales (sale_id) ON DELETE SET NULL,
    INDEX idx_orders_status (status, created_at)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS online_order_items (
    order_item_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id      INT            NOT NULL,
    product_id    INT            NOT NULL,
    product_name  VARCHAR(150)   NOT NULL,
    quantity      INT            NOT NULL,
    unit_price    DECIMAL(10, 2) NOT NULL,
    line_total    DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES online_orders (order_id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE = InnoDB;

-- Chat between the customer and the store about one order.
CREATE TABLE IF NOT EXISTS order_messages (
    message_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id   INT          NOT NULL,
    sender     ENUM('CUSTOMER', 'STAFF') NOT NULL,
    staff_name VARCHAR(100) NULL,
    body       VARCHAR(500) NOT NULL,
    created_at DATETIME     NOT NULL,
    read_at    DATETIME     NULL COMMENT 'When the other side saw it',
    CONSTRAINT fk_order_messages_order FOREIGN KEY (order_id) REFERENCES online_orders (order_id) ON DELETE CASCADE,
    INDEX idx_order_messages_unread (order_id, sender, read_at)
) ENGINE = InnoDB;

-- One photo per product, shown in the POS and the phone app.
CREATE TABLE IF NOT EXISTS product_images (
    product_id   INT          PRIMARY KEY,
    content_type VARCHAR(40)  NOT NULL,
    data         MEDIUMBLOB   NOT NULL COMMENT 'Shrunk to at most 600 px when saved',
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (product_id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ------------------------------------------------------------------
-- Starter data (skipped if it already exists)
-- ------------------------------------------------------------------

-- admin / PIN 1234, cashier / PIN 0000
INSERT IGNORE INTO users (username, full_name, pin_hash, role) VALUES
    ('admin',   'Store Admin',  SHA2('1234', 256), 'ADMIN'),
    ('cashier', 'Juan Cashier', SHA2('0000', 256), 'CASHIER');

INSERT IGNORE INTO categories (name) VALUES
    ('Hot Coffee'), ('Iced Coffee'), ('Cakes'), ('Cupcakes');

-- Drinks are made to order: their stock is the number of cups you can make today.
INSERT IGNORE INTO products (code, name, category_id, price, stock, low_stock_level, location)
SELECT p.code, p.name, c.category_id, p.price, p.stock, p.low_stock_level, p.location
FROM (
    SELECT 'HC01' AS code, 'Espresso' AS name, 'Hot Coffee' AS category, 90.00 AS price, 100 AS stock, 20 AS low_stock_level, 'Made at the bar' AS location
    UNION ALL SELECT 'HC02', 'Americano',                     'Hot Coffee',  110.00, 100, 20, 'Made at the bar'
    UNION ALL SELECT 'HC03', 'Cappuccino',                    'Hot Coffee',  140.00, 100, 20, 'Made at the bar'
    UNION ALL SELECT 'HC04', 'Cafe Latte',                    'Hot Coffee',  140.00, 100, 20, 'Made at the bar'
    UNION ALL SELECT 'HC05', 'Spanish Latte',                 'Hot Coffee',  155.00, 100, 20, 'Made at the bar'
    UNION ALL SELECT 'HC06', 'Caramel Macchiato',             'Hot Coffee',  160.00, 100, 20, 'Made at the bar'
    UNION ALL SELECT 'HC07', 'Cafe Mocha',                    'Hot Coffee',  150.00, 100, 20, 'Made at the bar'
    UNION ALL SELECT 'IC01', 'Iced Americano',                'Iced Coffee', 120.00, 100, 20, 'Made at the bar'
    UNION ALL SELECT 'IC02', 'Iced Latte',                    'Iced Coffee', 150.00, 100, 20, 'Made at the bar'
    UNION ALL SELECT 'IC03', 'Iced Spanish Latte',            'Iced Coffee', 165.00, 100, 20, 'Made at the bar'
    UNION ALL SELECT 'IC04', 'Iced Caramel Macchiato',        'Iced Coffee', 170.00, 100, 20, 'Made at the bar'
    UNION ALL SELECT 'IC05', 'Iced Mocha',                    'Iced Coffee', 160.00, 100, 20, 'Made at the bar'
    UNION ALL SELECT 'CK01', 'Chocolate Cake (slice)',        'Cakes',       145.00,  16,  4, 'Cake display'
    UNION ALL SELECT 'CK02', 'Red Velvet Cake (slice)',       'Cakes',       155.00,  16,  4, 'Cake display'
    UNION ALL SELECT 'CK03', 'Blueberry Cheesecake (slice)',  'Cakes',       165.00,  16,  4, 'Cake display'
    UNION ALL SELECT 'CK04', 'Carrot Cake (slice)',           'Cakes',       140.00,  16,  4, 'Cake display'
    UNION ALL SELECT 'CK05', 'Ube Cake (slice)',              'Cakes',       150.00,  16,  4, 'Cake display'
    UNION ALL SELECT 'CK06', 'Mango Cake (slice)',            'Cakes',       150.00,  16,  4, 'Cake display'
    UNION ALL SELECT 'CK07', 'Chocolate Cake (whole, 8 in)',  'Cakes',      1100.00,   2,  1, 'Cake display'
    UNION ALL SELECT 'CK08', 'Blueberry Cheesecake (whole, 8 in)', 'Cakes', 1250.00,   2,  1, 'Cake display'
    UNION ALL SELECT 'CC01', 'Chocolate Cupcake',             'Cupcakes',     75.00,  24,  6, 'Pastry case'
    UNION ALL SELECT 'CC02', 'Vanilla Cupcake',               'Cupcakes',     70.00,  24,  6, 'Pastry case'
    UNION ALL SELECT 'CC03', 'Red Velvet Cupcake',            'Cupcakes',     80.00,  24,  6, 'Pastry case'
    UNION ALL SELECT 'CC04', 'Salted Caramel Cupcake',        'Cupcakes',     85.00,  24,  6, 'Pastry case'
    UNION ALL SELECT 'CC05', 'Cookies and Cream Cupcake',     'Cupcakes',     85.00,  24,  6, 'Pastry case'
    UNION ALL SELECT 'CC06', 'Ube Cupcake',                   'Cupcakes',     80.00,  24,  6, 'Pastry case'
    UNION ALL SELECT 'CC07', 'Assorted Cupcakes (box of 6)',  'Cupcakes',    420.00,   6,  2, 'Pastry case'
) AS p
JOIN categories c ON c.name = p.category;

INSERT IGNORE INTO settings (setting_key, setting_value) VALUES
    ('store_name',         'My Store'),
    ('store_address',      ''),
    ('tax_rate',           '0.12'),
    ('prices_include_tax', 'false'),
    ('print_receipt',      'false'),
    ('open_cash_drawer',   'false'),
    ('beep_on_scan',       'true'),
    ('receipt_footer',     'Thank you for shopping!'),
    ('google_maps_api_key', ''),
    ('mobile_port',        '8080');

-- Demo branches for the phone app's store locator (edit them to your real branches).
INSERT INTO stores (name, address, latitude, longitude, phone, hours)
SELECT * FROM (
    SELECT 'My Store - City Proper' AS name, 'J.M. Basa St, Iloilo City Proper' AS address, 10.6969000 AS latitude, 122.5687000 AS longitude, '(033) 300 0001' AS phone, 'Daily 7:00 AM - 10:00 PM' AS hours
    UNION ALL SELECT 'My Store - Jaro', 'E. Lopez St, Jaro, Iloilo City', 10.7246000, 122.5576000, '(033) 300 0002', 'Daily 7:00 AM - 9:00 PM'
    UNION ALL SELECT 'My Store - Mandurriao', 'Benigno Aquino Ave, Mandurriao, Iloilo City', 10.7180000, 122.5410000, '(033) 300 0003', 'Daily 8:00 AM - 10:00 PM'
) AS s
WHERE NOT EXISTS (SELECT 1 FROM stores);
