package com.sensys.statement.t24;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One T24 record as stored by TAFJ in XMLRECORD:
 * {@code <row id="..."><c1>v</c1><c1 m="2">v2</c1><c6 m="1" s="2">sv</c6>...</row>}.
 * Tag cN is field number N, m is the multivalue position, s the subvalue position.
 * Subvalues are joined with a space; callers address fields by number from {@link FieldMap}.
 */
public final class T24Record {

    private static final Pattern CELL = Pattern.compile(
            "<c(\\d+)((?:\\s+[ms]=\"\\d+\")*)\\s*(?:/>|>(.*?)</c\\1>)", Pattern.DOTALL);
    private static final Pattern ATTR = Pattern.compile("([ms])=\"(\\d+)\"");

    private final String id;
    private final Map<Integer, List<String>> fields;

    private T24Record(String id, Map<Integer, List<String>> fields) {
        this.id = id;
        this.fields = fields;
    }

    public static T24Record parse(String id, String xml) {
        Map<Integer, List<String>> fields = new HashMap<>();
        if (xml != null && !xml.stripLeading().startsWith("<")) {
            return parseDynamicArray(id, xml);
        }
        if (xml != null) {
            Matcher m = CELL.matcher(xml);
            while (m.find()) {
                int field = Integer.parseInt(m.group(1));
                int mv = 1;
                Matcher a = ATTR.matcher(m.group(2));
                while (a.find()) {
                    if (a.group(1).equals("m")) mv = Integer.parseInt(a.group(2));
                }
                String value = m.group(3) == null ? "" : unescape(m.group(3));
                List<String> values = fields.computeIfAbsent(field, k -> new ArrayList<>());
                while (values.size() < mv) values.add("");
                String existing = values.get(mv - 1);
                values.set(mv - 1, existing.isEmpty() ? value : value.isEmpty() ? existing : existing + " " + value);
            }
        }
        return new T24Record(id, fields);
    }

    /**
     * Some tables keep the raw dynamic array instead of XML: fields split by FM, values by VM,
     * subvalues by SM. TAFJ writes the marks as U+F8FE/U+F8FD/U+F8FC; classic jBASE as 254/253/252.
     */
    private static T24Record parseDynamicArray(String id, String raw) {
        Map<Integer, List<String>> fields = new HashMap<>();
        String[] fms = raw.split("[þ]", -1);
        for (int f = 0; f < fms.length; f++) {
            if (fms[f].isEmpty()) continue;
            List<String> values = new ArrayList<>();
            for (String vm : fms[f].split("[ý]", -1)) {
                values.add(vm.replaceAll("[ü]", " ").trim());
            }
            fields.put(f + 1, values);
        }
        return new T24Record(id, fields);
    }

    private static String unescape(String s) {
        if (s.indexOf('&') < 0) return s;
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&");
    }

    public String id() {
        return id;
    }

    /** First multivalue of the field, or "" when absent. */
    public String first(int field) {
        List<String> v = fields.get(field);
        return v == null || v.isEmpty() ? "" : v.get(0);
    }

    /** All multivalues of the field in position order (blank positions kept). */
    public List<String> all(int field) {
        return fields.getOrDefault(field, Collections.emptyList());
    }

    /** Every non-blank value across all fields, in field order (concat records such as CUSTOMER.ACCOUNT). */
    public List<String> values() {
        return fields.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(e -> e.getValue().stream())
                .filter(s -> !s.isBlank())
                .toList();
    }

    /** Non-blank multivalues joined with a space. */
    public String joined(int field) {
        return String.join(" ", all(field).stream().filter(s -> !s.isBlank()).toList());
    }
}
