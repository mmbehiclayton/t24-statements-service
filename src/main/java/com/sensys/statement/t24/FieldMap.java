package com.sensys.statement.t24;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Field name to field number, read from STANDARD.SELECTION (SYS.FIELD.NAME = 1,
 * SYS.TYPE = 2, SYS.FIELD.NO = 3). Nothing is hard-coded, so a T24 upgrade that
 * moves a field is picked up automatically.
 */
@Component
public class FieldMap {

    private static final int SYS_FIELD_NAME = 1;
    private static final int SYS_TYPE = 2;
    private static final int SYS_FIELD_NO = 3;

    private final T24Reader reader;
    private final Map<String, Map<String, Integer>> cache = new ConcurrentHashMap<>();

    public FieldMap(T24Reader reader) {
        this.reader = reader;
    }

    public Fields of(String application) {
        return new Fields(application, cache.computeIfAbsent(application, this::load));
    }

    public void clear() {
        cache.clear();
    }

    private Map<String, Integer> load(String application) {
        T24Record ss = reader.read(reader.globalTable("STANDARD.SELECTION"), application)
                .orElseThrow(() -> new IllegalStateException("No STANDARD.SELECTION record for " + application));
        List<String> names = ss.all(SYS_FIELD_NAME);
        List<String> types = ss.all(SYS_TYPE);
        List<String> numbers = ss.all(SYS_FIELD_NO);
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String type = i < types.size() ? types.get(i) : "";
            String no = i < numbers.size() ? numbers.get(i).trim() : "";
            if ("D".equals(type) && no.matches("\\d+") && !map.containsKey(names.get(i))) {
                map.put(names.get(i), Integer.parseInt(no));
            }
        }
        return map;
    }

    public record Fields(String application, Map<String, Integer> byName) {
        public int no(String name) {
            Integer n = byName.get(name);
            if (n == null) throw new IllegalStateException(application + " has no field " + name + " in STANDARD.SELECTION");
            return n;
        }

        public Optional<Integer> find(String name) {
            return Optional.ofNullable(byName.get(name));
        }
    }
}
