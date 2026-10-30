package com.pos.db;

import com.pos.model.Role;
import com.pos.model.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

public class UserDao {
    private static final String COLUMNS = "user_id, username, full_name, role, active";

    public Optional<User> findByUsername(String username) throws SQLException {
        String sql = "SELECT user_id, username, full_name, role, active FROM users WHERE username = ?";

        Optional var6;
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                var6 = rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }

        return var6;
    }

    public Optional<User> authenticate(String username, String pin) throws SQLException {
        String sql = "SELECT user_id, username, full_name, role, active FROM users WHERE username = ? AND pin_hash = ? AND active = 1";

        Optional var7;
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setString(1, username);
            ps.setString(2, sha256Hex(pin));

            try (ResultSet rs = ps.executeQuery()) {
                var7 = rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }

        return var7;
    }

    public List<User> findAll() throws SQLException {
        String sql = "SELECT user_id, username, full_name, role, active FROM users ORDER BY active DESC, full_name";
        List<User> users = new ArrayList<>();

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
        ) {
            while (rs.next()) {
                users.add(map(rs));
            }
        }

        return users;
    }

    public int insert(User user, String pin) throws SQLException {
        String sql = "INSERT INTO users (username, full_name, pin_hash, role, active) VALUES (?, ?, ?, ?, 1)";

        int var7;
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql, 1);
        ) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setString(3, sha256Hex(pin));
            ps.setString(4, user.getRole().name());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No user_id was generated");
                }

                var7 = keys.getInt(1);
            }
        }

        return var7;
    }

    public void setActive(int userId, boolean active) throws SQLException {
        String sql = "UPDATE users SET active = ? WHERE user_id = ?";

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, userId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("User " + userId + " no longer exists");
            }
        }
    }

    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException var2) {
            throw new IllegalStateException("SHA-256 is not available", var2);
        }
    }

    private static User map(ResultSet rs) throws SQLException {
        return new User(
            rs.getInt("user_id"),
            rs.getString("username"),
            rs.getString("full_name"),
            Role.valueOf(rs.getString("role").toUpperCase()),
            rs.getBoolean("active")
        );
    }
}
