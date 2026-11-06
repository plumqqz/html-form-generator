package shaif.htmlgenerator.htmlform;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TableTest {
    static class Row {
        Long id; String name; String email;
        Row(long i, String n, String e) { id = i; name = n; email = e; }
        public Long getId() { return id; } public String getName() { return name; } public String getEmail() { return email; }
    }
    static List<Row> rows = Arrays.asList(new Row(1,"Alice","alice@x.com"), new Row(2,"Bob","bob@x.com"));
    @Test void excludesHiddenFields() {
        String html = FormGen.of(Row.class).table(rows).html();
        assertFalse(html.contains(">Id<")); assertTrue(html.contains(">Alice<"));
    }
    @Test void linkTemplates() {
        String html = FormGen.of(Row.class).table(rows).link("/u/{id}").link("email","mailto:{_}").html();
        assertTrue(html.contains("href=\"/u/1\">Alice</a>"));
        assertTrue(html.contains("mailto:alice@x.com"));
    }
    @Test void actions() {
        String html = FormGen.of(Row.class).table(rows).action("Edit", r -> "/x/" + ((Row)r).getId() + "/edit").html();
        assertTrue(html.contains("href=\"/x/1/edit\""));
    }
    @Test void emptyMessage() {
        String html = FormGen.of(Row.class).table(Collections.<Row>emptyList()).empty("No rows").html();
        assertTrue(html.contains("No rows"));
    }
    @Test void customCell() {
        String html = FormGen.of(Row.class).table(rows)
            .cell("name", r -> "<b>" + ((Row) r).getName() + "</b>").html();
        assertTrue(html.contains("<td><b>Alice</b></td>"));
    }
    @Test void mapRowsWithExplicitColumns() {
        List<Map<String,Object>> mapRows = Arrays.asList(
            new LinkedHashMap<>(Map.of("a","A1","b","B1")), new LinkedHashMap<>(Map.of("a","A2","b","B2")));
        String html = FormGen.ofData(Collections.emptyMap()).table(mapRows).columns("a","b").html();
        assertTrue(html.contains("<th>A</th>")); assertTrue(html.contains("<td>A1</td>"));
    }
    @Test void tableColumnsExposesColumns() {
        List<FormGen.Column> cols = FormGen.of(Row.class).table(rows).columns();
        assertEquals(2, cols.size());
        assertEquals("Name", cols.get(0).label);
        assertEquals("Alice", cols.get(0).cell(rows.get(0)));
        assertEquals("Bob", cols.get(0).cell(rows.get(1)));
    }
    @Test void rowClassApplied() {
        String html = FormGen.of(Row.class).table(rows)
            .rowClass(r -> ((Row) r).getId() == 1L ? "vip" : "plain").html();
        assertTrue(html.contains("class=\"vip\""));
        assertTrue(html.contains("class=\"plain\""));
    }
    @Test void cellClassApplied() {
        String html = FormGen.of(Row.class).table(rows).cellClass("email", "text-muted").html();
        assertTrue(html.contains("<td class=\"text-muted\">"));
    }
    @Test void captionRendered() {
        String html = FormGen.of(Row.class).table(rows).caption("Users").html();
        assertTrue(html.contains("<caption>Users</caption>"));
    }
    @Test void footerRendered() {
        String html = FormGen.of(Row.class).table(rows).footer("Totals", "2", "x").html();
        assertTrue(html.contains("<tfoot>"));
        assertTrue(html.contains("<td>Totals</td>"));
        assertTrue(html.contains("<td>2</td>"));
    }
}
