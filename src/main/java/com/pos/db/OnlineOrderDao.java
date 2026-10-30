package com.pos.db;

import com.pos.model.OnlineOrder;
import com.pos.model.OnlineOrderItem;
import com.pos.model.OrderStatus;
import com.pos.model.OrderType;
import com.pos.model.PaymentMethod;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OnlineOrderDao {
    private static final String SELECT = "SELECT o.*, c.full_name AS customer_name, c.phone AS customer_phone, s.name AS store_name,"
        + " (SELECT COALESCE(SUM(i.quantity), 0) FROM online_order_items i WHERE i.order_id = o.order_id) AS item_quantity,"
        + " (SELECT COUNT(*) FROM order_messages m WHERE m.order_id = o.order_id AND m.sender = 'CUSTOMER' AND m.read_at IS NULL) AS unread_by_staff,"
        + " (SELECT COUNT(*) FROM order_messages m WHERE m.order_id = o.order_id AND m.sender = 'STAFF' AND m.read_at IS NULL) AS unread_by_customer"
        + " FROM online_orders o JOIN customers c ON c.customer_id = o.customer_id JOIN stores s ON s.store_id = o.store_id";

    public OnlineOrder create(OnlineOrder order) throws SQLException {
        String nextIdSql = "SELECT COALESCE(MAX(order_id), 0) + 1 FROM online_orders";
        // PostgreSQL refuses FOR UPDATE with MAX(), so the table lock keeps two tills from taking the same number.
        String tableLockSql = "LOCK TABLE online_orders IN SHARE ROW EXCLUSIVE MODE";
        String orderSql = "INSERT INTO online_orders (order_no, customer_id, store_id, order_type, status, payment_method, subtotal, tax, total,"
            + " delivery_address, delivery_lat, delivery_lng, in_store_spot, note, rider_code, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String itemSql = "INSERT INTO online_order_items (order_id, product_id, product_name, quantity, unit_price, line_total) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);

            try {
                try (PreparedStatement ps = con.prepareStatement(tableLockSql)) {
                    ps.execute();
                }

                String orderNo;
                try (
                    PreparedStatement ps = con.prepareStatement(nextIdSql);
                    ResultSet rs = ps.executeQuery();
                ) {
                    rs.next();
                    orderNo = String.format("W%05d", rs.getInt(1));
                }

                int orderId;
                try (PreparedStatement ps = con.prepareStatement(orderSql, 1)) {
                    ps.setString(1, orderNo);
                    ps.setInt(2, order.getCustomerId());
                    ps.setInt(3, order.getStoreId());
                    ps.setString(4, order.getType().name());
                    ps.setString(5, order.getStatus().name());
                    ps.setString(6, order.getPaymentMethod().name());
                    ps.setBigDecimal(7, order.getSubtotal());
                    ps.setBigDecimal(8, order.getTax());
                    ps.setBigDecimal(9, order.getTotal());
                    ps.setString(10, order.getDeliveryAddress());
                    ps.setBigDecimal(11, order.getDeliveryLat());
                    ps.setBigDecimal(12, order.getDeliveryLng());
                    ps.setString(13, order.getInStoreSpot());
                    ps.setString(14, order.getNote());
                    ps.setString(15, order.getRiderCode());
                    ps.setTimestamp(16, Timestamp.valueOf(order.getCreatedAt()));
                    ps.setTimestamp(17, Timestamp.valueOf(order.getCreatedAt()));
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("No order_id was generated");
                        }

                        orderId = keys.getInt(1);
                    }
                }

                try (PreparedStatement item = con.prepareStatement(itemSql)) {
                    for (OnlineOrderItem line : order.getItems()) {
                        item.setInt(1, orderId);
                        item.setInt(2, line.productId());
                        item.setString(3, line.productName());
                        item.setInt(4, line.quantity());
                        item.setBigDecimal(5, line.unitPrice());
                        item.setBigDecimal(6, line.lineTotal());
                        item.addBatch();
                    }

                    item.executeBatch();
                }

                con.commit();
                order.setId(orderId);
                order.setOrderNo(orderNo);
                return order;
            } catch (RuntimeException | SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /** Open orders of every day plus everything placed on the given day, newest first. */
    public List<OnlineOrder> findForStaff(LocalDate day) throws SQLException {
        String sql = SELECT + " WHERE o.status NOT IN ('COMPLETED', 'CANCELLED') OR (o.created_at >= ? AND o.created_at < ?)"
            + " ORDER BY o.created_at DESC, o.order_id DESC";

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setTimestamp(1, Timestamp.valueOf(day.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(day.plusDays(1).atStartOfDay()));
            return list(ps);
        }
    }

    /** Every order ever placed, newest first; old orders are never deleted. */
    public List<OnlineOrder> findAllForStaff() throws SQLException {
        String sql = SELECT + " ORDER BY o.created_at DESC, o.order_id DESC LIMIT 2000";

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            return list(ps);
        }
    }

    public List<OnlineOrder> findForCustomer(int customerId) throws SQLException {
        String sql = SELECT + " WHERE o.customer_id = ? ORDER BY o.created_at DESC, o.order_id DESC LIMIT 200";

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setInt(1, customerId);
            return list(ps);
        }
    }

    public Optional<OnlineOrder> findById(int orderId) throws SQLException {
        return this.findOne(SELECT + " WHERE o.order_id = ?", orderId);
    }

    public Optional<OnlineOrder> findByRiderCode(String riderCode) throws SQLException {
        return this.findOne(SELECT + " WHERE o.rider_code = ?", riderCode);
    }

    private Optional<OnlineOrder> findOne(String sql, Object key) throws SQLException {
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setObject(1, key);
            List<OnlineOrder> found = list(ps);
            if (found.isEmpty()) {
                return Optional.empty();
            }

            OnlineOrder order = found.get(0);
            order.setItems(this.findItems(con, order.getId()));
            return Optional.of(order);
        }
    }

    public List<OnlineOrderItem> findItems(int orderId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            return this.findItems(con, orderId);
        }
    }

    private List<OnlineOrderItem> findItems(Connection con, int orderId) throws SQLException {
        String sql = "SELECT product_id, product_name, quantity, unit_price, line_total FROM online_order_items WHERE order_id = ? ORDER BY order_item_id";
        List<OnlineOrderItem> items = new ArrayList<>();

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(
                        new OnlineOrderItem(
                            rs.getInt("product_id"), rs.getString("product_name"), rs.getInt("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_total")
                        )
                    );
                }
            }
        }

        return items;
    }

    /**
     * Moves an order from one status to the next. Returns false when somebody else changed it first
     * (for example the customer cancelled while staff was accepting it).
     */
    public boolean changeStatus(int orderId, OrderStatus from, OrderStatus to, Integer saleId) throws SQLException {
        String sql = "UPDATE online_orders SET status = ?, updated_at = ?,"
            + " dispatched_at = CASE WHEN ? = 'OUT_FOR_DELIVERY' THEN ? ELSE dispatched_at END, sale_id = COALESCE(?, sale_id)"
            + " WHERE order_id = ? AND status = ?";

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            ps.setString(1, to.name());
            ps.setTimestamp(2, now);
            ps.setString(3, to.name());
            ps.setTimestamp(4, now);
            ps.setObject(5, saleId);
            ps.setInt(6, orderId);
            ps.setString(7, from.name());
            return ps.executeUpdate() == 1;
        }
    }

    public boolean updateRiderLocation(String riderCode, BigDecimal lat, BigDecimal lng) throws SQLException {
        String sql = "UPDATE online_orders SET rider_lat = ?, rider_lng = ?, rider_updated_at = ? WHERE rider_code = ? AND status = 'OUT_FOR_DELIVERY'";

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setBigDecimal(1, lat);
            ps.setBigDecimal(2, lng);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(4, riderCode);
            return ps.executeUpdate() == 1;
        }
    }

    public int countPending() throws SQLException {
        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM online_orders WHERE status = 'PENDING'");
            ResultSet rs = ps.executeQuery();
        ) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static List<OnlineOrder> list(PreparedStatement ps) throws SQLException {
        List<OnlineOrder> orders = new ArrayList<>();

        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                orders.add(map(rs));
            }
        }

        return orders;
    }

    private static OnlineOrder map(ResultSet rs) throws SQLException {
        OnlineOrder order = new OnlineOrder();
        order.setId(rs.getInt("order_id"));
        order.setOrderNo(rs.getString("order_no"));
        order.setCustomerId(rs.getInt("customer_id"));
        order.setCustomerName(rs.getString("customer_name"));
        order.setCustomerPhone(rs.getString("customer_phone"));
        order.setStoreId(rs.getInt("store_id"));
        order.setStoreName(rs.getString("store_name"));
        order.setType(OrderType.valueOf(rs.getString("order_type")));
        order.setStatus(OrderStatus.valueOf(rs.getString("status")));
        order.setPaymentMethod(PaymentMethod.valueOf(rs.getString("payment_method")));
        order.setSubtotal(rs.getBigDecimal("subtotal"));
        order.setTax(rs.getBigDecimal("tax"));
        order.setTotal(rs.getBigDecimal("total"));
        order.setDeliveryAddress(rs.getString("delivery_address"));
        order.setDeliveryLat(rs.getBigDecimal("delivery_lat"));
        order.setDeliveryLng(rs.getBigDecimal("delivery_lng"));
        order.setInStoreSpot(rs.getString("in_store_spot"));
        order.setNote(rs.getString("note"));
        order.setRiderCode(rs.getString("rider_code"));
        order.setRiderLat(rs.getBigDecimal("rider_lat"));
        order.setRiderLng(rs.getBigDecimal("rider_lng"));
        order.setRiderUpdatedAt(dateTime(rs, "rider_updated_at"));
        order.setDispatchedAt(dateTime(rs, "dispatched_at"));
        int saleId = rs.getInt("sale_id");
        order.setSaleId(rs.wasNull() ? null : saleId);
        order.setCreatedAt(dateTime(rs, "created_at"));
        order.setUpdatedAt(dateTime(rs, "updated_at"));
        order.setItemQuantity(rs.getInt("item_quantity"));
        order.setUnreadByStaff(rs.getInt("unread_by_staff"));
        order.setUnreadByCustomer(rs.getInt("unread_by_customer"));
        return order;
    }

    private static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
