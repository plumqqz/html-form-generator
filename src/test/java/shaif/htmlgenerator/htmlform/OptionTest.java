package shaif.htmlgenerator.htmlform;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OptionTest {
    @Test void parseValueEquals() { Option o = Option.parse("v=Label"); assertEquals("v", o.value); assertEquals("Label", o.label); }
    @Test void parseSingle() { Option o = Option.parse("x"); assertEquals("x", o.value); assertEquals("x", o.label); }
    @Test void parseSpaces() { Option o = Option.parse(" k = V "); assertEquals("k", o.value); assertEquals("V", o.label); }
}
