package com.sensys.statement.t24;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-only access to T24 tables (TAFJ layout: RECID + XMLRECORD). Only SELECTs are issued.
 * Table names are built from the application name and the company mnemonic, never from free text.
 */
@Component
public class T24Reader {

    private static final Pattern MNEMONIC = Pattern.compile("[A-Z0-9]{1,4}");
    private static final Pattern APPLICATION = Pattern.compile("[A-Z0-9.]{1,40}");
    private static final int IN_CHUNK = 500;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final String schema;
    private final Map<String, String> xmlExpr = new ConcurrentHashMap<>();

    public T24Reader(JdbcTemplate jdbc, @Value("${T24_SCHEMA:}") String schema) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.schema = schema == null ? "" : schema.trim().toUpperCase();
    }

    /** Company-level table, e.g. ("BNK", "STMT.ENTRY") -> FBNK_STMT_ENTRY. */
    public String companyTable(String mnemonic, String application) {
        if (mnemonic == null || !MNEMONIC.matcher(mnemonic).matches()) {
            throw new IllegalArgumentException("Invalid company mnemonic");
        }
        return qualify("F" + mnemonic + "_" + app(application));
    }

    /** Global (INT-level) table, e.g. STANDARD.SELECTION -> F_STANDARD_SELECTION. */
    public String globalTable(String application) {
        return qualify("F_" + app(application));
    }

    private static String app(String application) {
        if (!APPLICATION.matcher(application).matches()) throw new IllegalArgumentException("Invalid application");
        return application.replace('.', '_');
    }

    private String qualify(String table) {
        return schema.isEmpty() ? table : schema + "." + table;
    }

    private static String bare(String table) {
        int dot = table.indexOf('.');
        return dot < 0 ? table : table.substring(dot + 1);
    }

    public boolean exists(String table) {
        String sql = schema.isEmpty()
                ? "select count(*) from user_tables where table_name = ?"
                : "select count(*) from all_tables where owner = '" + schema + "' and table_name = ?";
        Integer n = jdbc.queryForObject(sql, Integer.class, bare(table));
        return n != null && n > 0;
    }

    /** Company mnemonics that have a STMT.PRINTED table (FBNK_STMT_PRINTED -> BNK). */
    public List<String> companies() {
        String sql = schema.isEmpty()
                ? "select table_name from user_tables where regexp_like(table_name, '^F[A-Z0-9]{1,4}_STMT_PRINTED$') order by 1"
                : "select table_name from all_tables where owner = '" + schema + "' and regexp_like(table_name, '^F[A-Z0-9]{1,4}_STMT_PRINTED$') order by 1";
        return jdbc.queryForList(sql, String.class).stream()
                .map(t -> t.substring(1, t.indexOf("_STMT_PRINTED"))).toList();
    }

    /** XMLRECORD is XMLTYPE on some tables and CLOB on others; read both as text. */
    private String xml(String table) {
        return xmlExpr.computeIfAbsent(table, t -> {
            String sql = schema.isEmpty()
                    ? "select data_type from user_tab_columns where table_name = ? and column_name = 'XMLRECORD'"
                    : "select data_type from all_tab_columns where owner = '" + schema + "' and table_name = ? and column_name = 'XMLRECORD'";
            List<String> type = jdbc.queryForList(sql, String.class, bare(t));
            if (type.isEmpty()) throw new IllegalStateException("Table " + t + " not found or has no XMLRECORD");
            return "XMLTYPE".equals(type.get(0)) ? "t.XMLRECORD.getClobVal()" : "t.XMLRECORD";
        });
    }

    public Optional<T24Record> read(String table, String id) {
        List<T24Record> rows = jdbc.query(
                "select t.RECID, " + xml(table) + " from " + table + " t where t.RECID = ?",
                (rs, i) -> T24Record.parse(rs.getString(1), rs.getString(2)), id);
        return rows.stream().findFirst();
    }

    /** Records by id, in the order given; missing ids are skipped. */
    public Map<String, T24Record> readMany(String table, Collection<String> ids) {
        Map<String, T24Record> found = new java.util.HashMap<>();
        List<String> list = new ArrayList<>(ids);
        String sql = "select t.RECID, " + xml(table) + " from " + table + " t where t.RECID in (:ids)";
        for (int i = 0; i < list.size(); i += IN_CHUNK) {
            List<String> chunk = list.subList(i, Math.min(list.size(), i + IN_CHUNK));
            named.query(sql, new MapSqlParameterSource("ids", chunk), rs -> {
                found.put(rs.getString(1), T24Record.parse(rs.getString(1), rs.getString(2)));
            });
        }
        Map<String, T24Record> ordered = new LinkedHashMap<>();
        for (String id : list) {
            T24Record r = found.get(id);
            if (r != null) ordered.put(id, r);
        }
        return ordered;
    }

    /** Records whose RECID falls in [fromId, toId] (uses the RECID primary key). */
    public List<T24Record> readRange(String table, String fromId, String toId) {
        return jdbc.query(
                "select t.RECID, " + xml(table) + " from " + table + " t where t.RECID between ? and ? order by t.RECID",
                (rs, i) -> T24Record.parse(rs.getString(1), rs.getString(2)), fromId, toId);
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }
}
