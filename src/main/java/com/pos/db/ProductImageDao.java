package com.pos.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Product photos, kept in the database so backups and the phone app include them. */
public class ProductImageDao {
    public record StoredImage(String contentType, byte[] data) {
    }

    public Optional<StoredImage> find(int productId) throws SQLException {
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT content_type, data FROM product_images WHERE product_id = ?");
        ) {
            ps.setInt(1, productId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new StoredImage(rs.getString("content_type"), rs.getBytes("data"))) : Optional.empty();
            }
        }
    }

    public void save(int productId, String contentType, byte[] data) throws SQLException {
        String sql = "INSERT INTO product_images (product_id, content_type, data, updated_at) VALUES (?, ?, ?, LOCALTIMESTAMP)"
            + " ON CONFLICT (product_id) DO UPDATE SET content_type = EXCLUDED.content_type, data = EXCLUDED.data, updated_at = EXCLUDED.updated_at";

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setInt(1, productId);
            ps.setString(2, contentType);
            ps.setBytes(3, data);
            ps.executeUpdate();
        }
    }

    public void delete(int productId) throws SQLException {
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("DELETE FROM product_images WHERE product_id = ?");
        ) {
            ps.setInt(1, productId);
            ps.executeUpdate();
        }
    }
}
