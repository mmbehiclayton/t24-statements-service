package com.sensys.statement.t24;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class T24RecordTest {

    @Test
    void parsesFieldsMultivaluesSubvaluesAndEntities() {
        T24Record r = T24Record.parse("X", "<row id=\"X\"><c1>A&amp;B</c1><c3>-12.50</c3>"
                + "<c6>first</c6><c6 m=\"3\">third</c6><c7 m=\"1\" s=\"1\">a</c7><c7 m=\"1\" s=\"2\">b</c7><c9/></row>");
        assertEquals("A&B", r.first(1));
        assertEquals("-12.50", r.first(3));
        assertEquals(List.of("first", "", "third"), r.all(6));
        assertEquals("first third", r.joined(6));
        assertEquals("a b", r.first(7));
        assertEquals("", r.first(9));
        assertEquals("", r.first(99));
    }

    @Test
    void parsesTafjDynamicArray() {
        T24Record r = T24Record.parse("123-20250331", "E1E2E3XYZ");
        assertEquals(List.of("E1", "E2", "E3"), r.all(1));
        assertEquals("X Y", r.first(2));
        assertEquals("", r.first(3));
        assertEquals("Z", r.first(4));
    }

    @Test
    void parsesClassicJbaseMarks() {
        T24Record r = T24Record.parse("A", "a1ýa2þb");
        assertEquals(List.of("a1", "a2"), r.all(1));
        assertEquals("b", r.first(2));
    }
}
