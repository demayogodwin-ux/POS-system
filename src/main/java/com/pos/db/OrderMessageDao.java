package com.pos.db;

import com.pos.model.OrderMessage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrderMessageDao {
    private static final int MAX_SHOWN = 200;

    public OrderMessage add(int orderId, boolean fromStaff, String staffName, String body) throws SQLException {
        String sql = "INSERT INTO order_messages (order_id, sender, staff_name, body, created_at) VALUES (?, ?, ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now().withNano(0);

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql, 1);
        ) {
            ps.setInt(1, orderId);
            ps.setString(2, fromStaff ? "STAFF" : "CUSTOMER");
            ps.setString(3, fromStaff ? staffName : null);
            ps.setString(4, body);
            ps.setTimestamp(5, Timestamp.valueOf(now));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No message_id was generated");
                }

                return new OrderMessage(keys.getInt(1), orderId, fromStaff, fromStaff ? staffName : null, body, now);
            }
        }
    }

    /** The latest messages of an order, oldest first. */
    public List<OrderMessage> findForOrder(int orderId) throws SQLException {
        String sql = "SELECT * FROM (SELECT * FROM order_messages WHERE order_id = ? ORDER BY message_id DESC LIMIT " + MAX_SHOWN + ") m"
            + " ORDER BY message_id";
        List<OrderMessage> messages = new ArrayList<>();

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setInt(1, orderId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp created = rs.getTimestamp("created_at");
                    messages.add(new OrderMessage(
                        rs.getInt("message_id"), orderId, "STAFF".equals(rs.getString("sender")), rs.getString("staff_name"),
                        rs.getString("body"), created.toLocalDateTime()
                    ));
                }
            }
        }

        return messages;
    }

    /** Marks what the other side wrote as seen: staff reading clears customer messages and the other way round. */
    public void markRead(int orderId, boolean readByStaff) throws SQLException {
        String sql = "UPDATE order_messages SET read_at = ? WHERE order_id = ? AND sender = ? AND read_at IS NULL";

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, orderId);
            ps.setString(3, readByStaff ? "CUSTOMER" : "STAFF");
            ps.executeUpdate();
        }
    }
}
