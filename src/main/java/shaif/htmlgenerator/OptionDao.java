package shaif.htmlgenerator;
import shaif.htmlgenerator.htmlform.Option;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public final class OptionDao {
    private final DataSource ds;
    public OptionDao(DataSource ds) { this.ds = ds; }
    public List<Option> countries() { return q("SELECT code, name FROM countries ORDER BY name", ps -> {},
        rs -> new Option(rs.getString("code"), rs.getString("name"))); }
    public List<Option> cities(String c) {
        if (c == null || c.isEmpty()) return Collections.emptyList();
        return q("SELECT code, name FROM cities WHERE country_code = ? ORDER BY name",
            ps -> ps.setString(1, c), rs -> new Option(rs.getString("code"), rs.getString("name"))); }
    public List<Option> roles() { return q("SELECT code, label, group_name, is_disabled FROM roles ORDER BY group_name, label",
        ps -> {}, rs -> new Option(rs.getString("code"), rs.getString("label"),
            rs.getString("group_name"), rs.getBoolean("is_disabled"))); }
    public List<Option> departments() { return q("SELECT id, name FROM departments ORDER BY name", ps -> {},
        rs -> new Option(String.valueOf(rs.getLong("id")), rs.getString("name"))); }
    @FunctionalInterface interface Binder { void bind(PreparedStatement p) throws SQLException; }
    @FunctionalInterface interface Mapper { Option m(ResultSet r) throws SQLException; }
    private List<Option> q(String sql, Binder b, Mapper m) {
        List<Option> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            b.bind(ps);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(m.m(rs)); }
        } catch (SQLException e) { throw new IllegalStateException("query failed: " + sql, e); }
        return out;
    }
}
