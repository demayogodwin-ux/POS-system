package com.pos.db;

import com.pos.model.Category;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CategoryDao {
    public List<Category> findAll() throws SQLException {
        String sql = "SELECT category_id, name FROM categories ORDER BY name";
        List<Category> categories = new ArrayList<>();

        try (
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
        ) {
            while (rs.next()) {
                categories.add(new Category(rs.getInt("category_id"), rs.getString("name")));
            }
        }

        return categories;
    }
}
