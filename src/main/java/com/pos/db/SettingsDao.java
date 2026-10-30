package com.pos.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class SettingsDao {
    public Map<String, String> getAll() throws SQLException {
        String sql = "SELECT setting_key, setting_value FROM settings ORDER BY setting_key";
        Map<String, String> settings = new LinkedHashMap<>();

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
        ) {
            while (rs.next()) {
                settings.put(rs.getString("setting_key"), rs.getString("setting_value"));
            }
        }

        return settings;
    }

    public String get(String key, String defaultValue) throws SQLException {
        String sql = "SELECT setting_value FROM settings WHERE setting_key = ?";

        String value;
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setString(1, key);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    value = rs.getString(1);
                    return value != null && !value.isBlank() ? value : defaultValue;
                }

                value = defaultValue;
            }
        }

        return value;
    }

    public void saveAll(Map<String, String> settings) throws SQLException {
        String sql = "INSERT INTO settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value";

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (Entry<String, String> entry : settings.entrySet()) {
                    ps.setString(1, entry.getKey());
                    ps.setString(2, entry.getValue());
                    ps.addBatch();
                }

                ps.executeBatch();
                con.commit();
            } catch (RuntimeException | SQLException var17) {
                con.rollback();
                throw var17;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }
}
