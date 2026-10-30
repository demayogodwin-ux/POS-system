package com.pos.db;

import com.pos.model.Product;
import com.pos.service.Money;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductDao {
    private static final String SELECT = "SELECT p.product_id, p.code, p.name, p.category_id, c.name AS category_name, p.price, p.stock, p.low_stock_level, p.active, p.location, (SELECT CAST(EXTRACT(EPOCH FROM i.updated_at) AS BIGINT) FROM product_images i WHERE i.product_id = p.product_id) AS image_version FROM products p LEFT JOIN categories c ON c.category_id = p.category_id";

    public List<Product> findAll(boolean activeOnly) throws SQLException {
        String sql = "SELECT p.product_id, p.code, p.name, p.category_id, c.name AS category_name, p.price, p.stock, p.low_stock_level, p.active, p.location, (SELECT CAST(EXTRACT(EPOCH FROM i.updated_at) AS BIGINT) FROM product_images i WHERE i.product_id = p.product_id) AS image_version FROM products p LEFT JOIN categories c ON c.category_id = p.category_id"
            + (activeOnly ? " WHERE p.active = 1" : "")
            + " ORDER BY p.name";
        List<Product> products = new ArrayList<>();

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
        ) {
            while (rs.next()) {
                products.add(map(rs));
            }
        }

        return products;
    }

    public Optional<Product> findByCode(String code) throws SQLException {
        String sql = "SELECT p.product_id, p.code, p.name, p.category_id, c.name AS category_name, p.price, p.stock, p.low_stock_level, p.active, p.location, (SELECT CAST(EXTRACT(EPOCH FROM i.updated_at) AS BIGINT) FROM product_images i WHERE i.product_id = p.product_id) AS image_version FROM products p LEFT JOIN categories c ON c.category_id = p.category_id WHERE p.code = ?";

        Optional var6;
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setString(1, code);

            try (ResultSet rs = ps.executeQuery()) {
                var6 = rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }

        return var6;
    }

    public int insert(Product product) throws SQLException {
        String sql = "INSERT INTO products (code, name, category_id, price, stock, low_stock_level, location, active) VALUES (?, ?, ?, ?, ?, ?, ?, 1)";

        int var6;
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql, 1);
        ) {
            bindFields(ps, product);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No product_id was generated");
                }

                var6 = keys.getInt(1);
            }
        }

        return var6;
    }

    public void update(Product product) throws SQLException {
        String sql = "UPDATE products SET code = ?, name = ?, category_id = ?, price = ?, stock = ?, low_stock_level = ?, location = ? WHERE product_id = ?";

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            bindFields(ps, product);
            ps.setInt(8, product.getId());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Product " + product.getId() + " no longer exists");
            }
        }
    }

    public void softDelete(int productId) throws SQLException {
        String sql = "UPDATE products SET active = 0 WHERE product_id = ?";

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setInt(1, productId);
            ps.executeUpdate();
        }
    }

    public int countLowStock() throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE active = 1 AND stock <= low_stock_level";

        int var5;
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
        ) {
            rs.next();
            var5 = rs.getInt(1);
        }

        return var5;
    }

    public BigDecimal inventoryValue() throws SQLException {
        String sql = "SELECT COALESCE(SUM(price * stock), 0) FROM products WHERE active = 1";

        BigDecimal var5;
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
        ) {
            rs.next();
            var5 = Money.of(rs.getBigDecimal(1));
        }

        return var5;
    }

    public long totalUnits() throws SQLException {
        String sql = "SELECT COALESCE(SUM(stock), 0) FROM products WHERE active = 1";

        long var5;
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
        ) {
            rs.next();
            var5 = rs.getLong(1);
        }

        return var5;
    }

    private static void bindFields(PreparedStatement ps, Product product) throws SQLException {
        ps.setString(1, product.getCode());
        ps.setString(2, product.getName());
        if (product.getCategoryId() == null) {
            ps.setNull(3, 4);
        } else {
            ps.setInt(3, product.getCategoryId());
        }

        ps.setBigDecimal(4, product.getPrice());
        ps.setInt(5, product.getStock());
        ps.setInt(6, product.getLowStockLevel());
        ps.setString(7, product.getLocation());
    }

    private static Product map(ResultSet rs) throws SQLException {
        int categoryId = rs.getInt("category_id");
        Product product = new Product(
            rs.getInt("product_id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.wasNull() ? null : categoryId,
            rs.getString("category_name"),
            rs.getBigDecimal("price"),
            rs.getInt("stock"),
            rs.getInt("low_stock_level"),
            rs.getBoolean("active")
        );
        product.setLocation(rs.getString("location"));
        product.setImageVersion(rs.getLong("image_version"));
        return product;
    }
}
