package com.pos.db;

import com.pos.model.DaySummary;
import com.pos.model.PaymentMethod;
import com.pos.model.Sale;
import com.pos.model.SaleItem;
import com.pos.service.Money;
import com.pos.service.SaleService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SaleDao {
    public Sale save(Sale sale) throws SQLException {
        String nextIdSql = "SELECT COALESCE(MAX(sale_id), 0) + 1 FROM sales";
        // PostgreSQL refuses FOR UPDATE with MAX(), so the table lock keeps two tills from taking the same number.
        String tableLockSql = "LOCK TABLE sales IN SHARE ROW EXCLUSIVE MODE";
        String saleSql = "INSERT INTO sales (receipt_no, user_id, sale_time, subtotal, tax, total, payment_method, amount_paid, change_due, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String stockSql = "UPDATE products SET stock = stock - ? WHERE product_id = ? AND stock >= ?";
        String itemSql = "INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, line_total) VALUES (?, ?, ?, ?, ?)";

        Sale var52;
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);

            try {
                try (PreparedStatement ps = con.prepareStatement(tableLockSql)) {
                    ps.execute();
                }

                String receiptNo;
                try (
                    PreparedStatement ps = con.prepareStatement(nextIdSql);
                    ResultSet rs = ps.executeQuery();
                ) {
                    rs.next();
                    receiptNo = SaleService.receiptNumber(rs.getInt(1));
                }

                int saleId;
                try (PreparedStatement ps = con.prepareStatement(saleSql, 1)) {
                    ps.setString(1, receiptNo);
                    ps.setInt(2, sale.getUserId());
                    ps.setTimestamp(3, Timestamp.valueOf(sale.getSaleTime()));
                    ps.setBigDecimal(4, sale.getSubtotal());
                    ps.setBigDecimal(5, sale.getTax());
                    ps.setBigDecimal(6, sale.getTotal());
                    ps.setString(7, sale.getPaymentMethod().name());
                    ps.setBigDecimal(8, sale.getAmountPaid());
                    ps.setBigDecimal(9, sale.getChangeDue());
                    ps.setString(10, sale.getStatus());
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("No sale_id was generated");
                        }

                        saleId = keys.getInt(1);
                    }
                }

                List<SaleItem> items = new ArrayList<>(sale.getItems());
                items.sort(Comparator.comparingInt(SaleItem::getProductId));

                try (
                    PreparedStatement stock = con.prepareStatement(stockSql);
                    PreparedStatement item = con.prepareStatement(itemSql);
                ) {
                    for (SaleItem line : items) {
                        stock.setInt(1, line.getQuantity());
                        stock.setInt(2, line.getProductId());
                        stock.setInt(3, line.getQuantity());
                        if (stock.executeUpdate() == 0) {
                            throw new InsufficientStockException(line.getProductName());
                        }

                        item.setInt(1, saleId);
                        item.setInt(2, line.getProductId());
                        item.setInt(3, line.getQuantity());
                        item.setBigDecimal(4, line.getUnitPrice());
                        item.setBigDecimal(5, line.getLineTotal());
                        item.addBatch();
                    }

                    item.executeBatch();
                }

                con.commit();
                sale.setId(saleId);
                sale.setReceiptNo(receiptNo);

                for (SaleItem line : sale.getItems()) {
                    line.setSaleId(saleId);
                }

                var52 = sale;
            } catch (RuntimeException | SQLException var44) {
                con.rollback();
                throw var44;
            } finally {
                con.setAutoCommit(true);
            }
        }

        return var52;
    }

    public List<Sale> findByDate(LocalDate date) throws SQLException {
        String sql = "SELECT s.sale_id, s.receipt_no, s.user_id, u.full_name AS cashier_name, s.sale_time, s.subtotal, s.tax, s.total, s.payment_method, s.amount_paid, s.change_due, s.status, (SELECT COALESCE(SUM(i.quantity), 0) FROM sale_items i WHERE i.sale_id = s.sale_id) AS item_quantity FROM sales s JOIN users u ON u.user_id = s.user_id WHERE s.sale_time >= ? AND s.sale_time < ? ORDER BY s.sale_time DESC, s.sale_id DESC";
        List<Sale> sales = new ArrayList<>();

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            bindDay(ps, date, 1);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.add(mapSale(rs));
                }
            }
        }

        return sales;
    }

    public List<SaleItem> findItems(int saleId) throws SQLException {
        String sql = "SELECT i.sale_item_id, i.sale_id, i.product_id, p.name AS product_name, i.quantity, i.unit_price, i.line_total FROM sale_items i JOIN products p ON p.product_id = i.product_id WHERE i.sale_id = ? ORDER BY i.sale_item_id";
        List<SaleItem> items = new ArrayList<>();

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setInt(1, saleId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(
                        new SaleItem(
                            rs.getInt("sale_item_id"),
                            rs.getInt("sale_id"),
                            rs.getInt("product_id"),
                            rs.getString("product_name"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("unit_price"),
                            rs.getBigDecimal("line_total")
                        )
                    );
                }
            }
        }

        return items;
    }

    public DaySummary daySummary(LocalDate date) throws SQLException {
        String totalsSql = "SELECT COALESCE(SUM(total), 0), COUNT(*), COALESCE(SUM(tax), 0) FROM sales WHERE status = ? AND sale_time >= ? AND sale_time < ?";
        String itemsSql = "SELECT COALESCE(SUM(i.quantity), 0) FROM sale_items i JOIN sales s ON s.sale_id = i.sale_id WHERE s.status = ? AND s.sale_time >= ? AND s.sale_time < ?";

        DaySummary var26;
        try (Connection con = DatabaseConnection.getConnection()) {
            BigDecimal total;
            int count;
            BigDecimal tax;
            try (PreparedStatement ps = con.prepareStatement(totalsSql)) {
                ps.setString(1, "PAID");
                bindDay(ps, date, 2);

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = Money.of(rs.getBigDecimal(1));
                    count = rs.getInt(2);
                    tax = Money.of(rs.getBigDecimal(3));
                }
            }

            long itemsSold;
            try (PreparedStatement ps = con.prepareStatement(itemsSql)) {
                ps.setString(1, "PAID");
                bindDay(ps, date, 2);

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    itemsSold = rs.getLong(1);
                }
            }

            BigDecimal average = count == 0 ? Money.ZERO : total.divide(BigDecimal.valueOf((long)count), 2, Money.ROUNDING);
            var26 = new DaySummary(total, count, average, itemsSold, tax);
        }

        return var26;
    }

    public void voidSale(int saleId) throws SQLException {
        String lockSql = "SELECT status FROM sales WHERE sale_id = ? FOR UPDATE";
        String itemsSql = "SELECT product_id, quantity FROM sale_items WHERE sale_id = ? ORDER BY product_id";
        String stockSql = "UPDATE products SET stock = stock + ? WHERE product_id = ?";
        String statusSql = "UPDATE sales SET status = ? WHERE sale_id = ?";

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);

            try {
                try (PreparedStatement ps = con.prepareStatement(lockSql)) {
                    ps.setInt(1, saleId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Sale " + saleId + " does not exist");
                        }

                        if (!"PAID".equals(rs.getString(1))) {
                            throw new SQLException("Only paid sales can be voided (this one is " + rs.getString(1) + ")");
                        }
                    }
                }

                try (
                    PreparedStatement items = con.prepareStatement(itemsSql);
                    PreparedStatement stock = con.prepareStatement(stockSql);
                ) {
                    items.setInt(1, saleId);

                    try (ResultSet rs = items.executeQuery()) {
                        while (rs.next()) {
                            stock.setInt(1, rs.getInt("quantity"));
                            stock.setInt(2, rs.getInt("product_id"));
                            stock.addBatch();
                        }
                    }

                    stock.executeBatch();
                }

                try (PreparedStatement ps = con.prepareStatement(statusSql)) {
                    ps.setString(1, "VOID");
                    ps.setInt(2, saleId);
                    ps.executeUpdate();
                }

                con.commit();
            } catch (RuntimeException | SQLException var42) {
                con.rollback();
                throw var42;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /** Binds the start and end of a day; a null date means every day, so findByDate and daySummary also cover all history. */
    private static void bindDay(PreparedStatement ps, LocalDate date, int first) throws SQLException {
        LocalDate from = date == null ? LocalDate.of(1000, 1, 1) : date;
        LocalDate to = date == null ? LocalDate.of(9999, 12, 31) : date.plusDays(1L);
        ps.setTimestamp(first, Timestamp.valueOf(from.atStartOfDay()));
        ps.setTimestamp(first + 1, Timestamp.valueOf(to.atStartOfDay()));
    }

    private static Sale mapSale(ResultSet rs) throws SQLException {
        Sale sale = new Sale();
        sale.setId(rs.getInt("sale_id"));
        sale.setReceiptNo(rs.getString("receipt_no"));
        sale.setUserId(rs.getInt("user_id"));
        sale.setCashierName(rs.getString("cashier_name"));
        sale.setSaleTime(rs.getTimestamp("sale_time").toLocalDateTime());
        sale.setSubtotal(rs.getBigDecimal("subtotal"));
        sale.setTax(rs.getBigDecimal("tax"));
        sale.setTotal(rs.getBigDecimal("total"));
        sale.setPaymentMethod(PaymentMethod.valueOf(rs.getString("payment_method")));
        sale.setAmountPaid(rs.getBigDecimal("amount_paid"));
        sale.setChangeDue(rs.getBigDecimal("change_due"));
        sale.setStatus(rs.getString("status"));
        sale.setItemQuantity(rs.getInt("item_quantity"));
        return sale;
    }
}
