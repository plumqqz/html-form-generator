package shaif.htmlgenerator;
import shaif.htmlgenerator.htmlform.Option;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OptionDaoTest {
    OptionDao dao;
    @BeforeEach void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:o_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa"); ds.setPassword("");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE countries (code VARCHAR(2) PRIMARY KEY, name VARCHAR(100))");
            st.execute("CREATE TABLE cities (code VARCHAR(10) PRIMARY KEY, name VARCHAR(100), country_code VARCHAR(2))");
            st.execute("CREATE TABLE roles (code VARCHAR(20) PRIMARY KEY, label VARCHAR(100), group_name VARCHAR(50), is_disabled BOOLEAN)");
            st.execute("CREATE TABLE departments (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            st.execute("INSERT INTO countries VALUES ('RU','Россия'),('KZ','Казахстан')");
            st.execute("INSERT INTO cities VALUES ('MSK','Москва','RU'),('ALA','Алматы','KZ')");
            st.execute("INSERT INTO roles VALUES ('ADMIN','Админ','Sys',FALSE),('GUEST','Гость','User',TRUE)");
            st.execute("INSERT INTO departments VALUES (1,'IT'),(2,'Sales')");
        }
        dao = new OptionDao(ds);
    }
    @Test void countriesOrdered() {
        List<Option> c = dao.countries();
        assertEquals("KZ", c.get(0).value); assertEquals("RU", c.get(1).value);
    }
    @Test void citiesByCountry() {
        assertEquals(1, dao.cities("RU").size());
        assertTrue(dao.cities(null).isEmpty());
        assertTrue(dao.cities("XX").isEmpty());
    }
    @Test void rolesWithGroupAndDisabled() {
        List<Option> r = dao.roles();
        assertTrue(r.stream().anyMatch(o -> o.value.equals("ADMIN") && "Sys".equals(o.group)));
        assertTrue(r.stream().anyMatch(o -> o.value.equals("GUEST") && o.disabled));
    }
    @Test void departmentsAsStringIds() {
        List<Option> d = dao.departments();
        assertEquals("1", d.get(0).value); assertEquals("IT", d.get(0).label);
    }
}
