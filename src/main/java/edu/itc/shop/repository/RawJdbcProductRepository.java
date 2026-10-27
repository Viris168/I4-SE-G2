package edu.itc.shop.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;
import edu.itc.shop.domain.Product;

@Repository
public class RawJdbcProductRepository {
    private final DataSource dataSource;
    public RawJdbcProductRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public List<Product> findAll() {
        String sql = "SELECT id, name, price, stock FROM product ORDER BY id";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Product> products = new ArrayList<>();
            while (rs.next()) {
                products.add(new Product(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getBigDecimal("price"),
                        rs.getInt("stock")));
            }
            return products;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load products", e);
        }
    }
}
