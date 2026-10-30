package com.pos.db;

import com.pos.model.Store;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StoreDao {
    private static final String SELECT = "SELECT store_id, name, address, latitude, longitude, phone, hours FROM stores";

    public List<Store> findActive() throws SQLException {
        List<Store> stores = new ArrayList<>();

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(SELECT + " WHERE active = 1 ORDER BY store_id");
            ResultSet rs = ps.executeQuery();
        ) {
            while (rs.next()) {
                stores.add(map(rs));
            }
        }

        return stores;
    }

    public Optional<Store> findActive(int storeId) throws SQLException {
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(SELECT + " WHERE active = 1 AND store_id = ?");
        ) {
            ps.setInt(1, storeId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static Store map(ResultSet rs) throws SQLException {
        return new Store(
            rs.getInt("store_id"),
            rs.getString("name"),
            rs.getString("address"),
            rs.getBigDecimal("latitude"),
            rs.getBigDecimal("longitude"),
            rs.getString("phone"),
            rs.getString("hours")
        );
    }
}
