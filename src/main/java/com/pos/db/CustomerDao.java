package com.pos.db;

import com.pos.model.Customer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class CustomerDao {
    private static final String COLUMNS = "customer_id, full_name, phone, email, address";

    public int insert(String fullName, String phone, String email, String address, String passwordHash, String salt, String tokenHash) throws SQLException {
        String sql = "INSERT INTO customers (full_name, phone, email, address, password_hash, password_salt, token_hash) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql, 1);
        ) {
            ps.setString(1, fullName);
            ps.setString(2, phone);
            ps.setString(3, email);
            ps.setString(4, address);
            ps.setString(5, passwordHash);
            ps.setString(6, salt);
            ps.setString(7, tokenHash);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No customer_id was generated");
                }

                return keys.getInt(1);
            }
        }
    }

    /** Looks a customer up by phone number or e-mail, for signing in. */
    public Optional<Credentials> findCredentials(String phoneOrEmail) throws SQLException {
        String sql = "SELECT " + COLUMNS + ", password_hash, password_salt FROM customers WHERE phone = ? OR email = ?";

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setString(1, phoneOrEmail);
            ps.setString(2, phoneOrEmail);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Credentials(map(rs), rs.getString("password_hash"), rs.getString("password_salt"))) : Optional.empty();
            }
        }
    }

    public Optional<Customer> findByToken(String tokenHash) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM customers WHERE token_hash = ?";

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setString(1, tokenHash);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public void setToken(int customerId, String tokenHash) throws SQLException {
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("UPDATE customers SET token_hash = ? WHERE customer_id = ?");
        ) {
            ps.setString(1, tokenHash);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    public void updateProfile(int customerId, String fullName, String phone, String email, String address) throws SQLException {
        String sql = "UPDATE customers SET full_name = ?, phone = ?, email = ?, address = ? WHERE customer_id = ?";

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setString(1, fullName);
            ps.setString(2, phone);
            ps.setString(3, email);
            ps.setString(4, address);
            ps.setInt(5, customerId);
            ps.executeUpdate();
        }
    }

    private static Customer map(ResultSet rs) throws SQLException {
        return new Customer(rs.getInt("customer_id"), rs.getString("full_name"), rs.getString("phone"), rs.getString("email"), rs.getString("address"));
    }

    public record Credentials(Customer customer, String passwordHash, String salt) {
    }
}
