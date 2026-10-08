package io.github.geoverselabs.mybatis.geometry.interceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable column metadata of one entity, as needed by {@link GeometrySqlRewriter}.
 *
 * <p>Column names are kept exactly as MyBatis-Plus emits them (quotes included) for building SQL,
 * and compared through {@linkplain #key(String) keys}: identifier quotes stripped, lower case.</p>
 */
final class GeometryColumns {

    /** Metadata without geometry columns; nothing is ever rewritten for it. */
    static final GeometryColumns EMPTY = new GeometryColumns(null, Collections.emptyList(), null);

    private final String tableName;
    private final String tableKey;
    private final Map<String, String> geometryByKey;
    private final Set<String> geometryNames;
    private final List<String> selectColumns;
    /** Whether every key can be found by a plain case-insensitive substring search. */
    private final boolean prefilterable;
    private final int hash;

    /**
     * @param tableName       table name as MyBatis-Plus emits it (may be schema qualified or quoted);
     *                        null when unknown
     * @param geometryColumns geometry column names
     * @param selectColumns   every column of the entity in select order (key column first), used to
     *                        expand {@code SELECT *}; null when unknown (no expansion)
     */
    GeometryColumns(String tableName, Collection<String> geometryColumns, List<String> selectColumns) {
        this.tableName = isBlank(tableName) ? null : tableName.trim();
        this.tableKey = this.tableName == null ? null : lastSegmentKey(this.tableName);
        Map<String, String> byKey = new LinkedHashMap<>();
        Set<String> names = new LinkedHashSet<>();
        for (String column : geometryColumns) {
            if (!isBlank(column)) {
                String name = column.trim();
                byKey.putIfAbsent(key(name), name);
                names.add(name);
            }
        }
        this.geometryByKey = Collections.unmodifiableMap(byKey);
        this.prefilterable = byKey.keySet().stream().allMatch(GeometryColumns::isPlainAscii);
        this.geometryNames = Collections.unmodifiableSet(names);
        if (selectColumns == null) {
            this.selectColumns = null;
        } else {
            List<String> columns = new ArrayList<>(selectColumns.size());
            for (String column : selectColumns) {
                if (!isBlank(column)) {
                    columns.add(column.trim());
                }
            }
            this.selectColumns = columns.isEmpty() ? null : Collections.unmodifiableList(columns);
        }
        this.hash = Objects.hash(this.tableName, this.geometryNames, this.selectColumns);
    }

    /**
     * Whether the entity has no geometry column.
     */
    boolean isEmpty() {
        return geometryByKey.isEmpty();
    }

    /**
     * Whether {@code key} (see {@link #key(String)}) names a geometry column.
     */
    boolean isGeometryKey(String key) {
        return geometryByKey.containsKey(key);
    }

    /**
     * Geometry column names as configured, in declaration order.
     */
    Set<String> geometryNames() {
        return geometryNames;
    }

    /**
     * Every column in select order, or null when unknown.
     */
    List<String> selectColumns() {
        return selectColumns;
    }

    /**
     * Table name as configured, or null when unknown.
     */
    String tableName() {
        return tableName;
    }

    /**
     * Key of the last segment of the table name ({@code "shop"} for {@code public."Shop"} is
     * {@code "shop"}), or null when unknown.
     */
    String tableKey() {
        return tableKey;
    }

    /**
     * Cheap pre-check: whether {@code sql} may need rewriting, i.e. it contains a {@code *} or the
     * text of a geometry column name (ASCII case-insensitive). A false result is definitive.
     */
    boolean mayAffect(String sql) {
        if (!prefilterable || sql.indexOf('*') >= 0) {
            return true;
        }
        for (String key : geometryByKey.keySet()) {
            if (containsIgnoreCase(sql, key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Comparison key of an identifier: surrounding identifier quotes (backticks, double quotes,
     * brackets) removed, doubled quotes unescaped, lower case.
     *
     * @param identifier a single identifier, quoted or not
     * @return the key
     */
    static String key(String identifier) {
        String s = identifier.trim();
        int n = s.length();
        if (n >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(n - 1);
            if ((first == '`' && last == '`') || (first == '"' && last == '"')) {
                String doubled = String.valueOf(first) + first;
                s = s.substring(1, n - 1).replace(doubled, String.valueOf(first));
            } else if (first == '[' && last == ']') {
                s = s.substring(1, n - 1);
            }
        }
        return s.toLowerCase(Locale.ROOT);
    }

    /**
     * Key of the last dot-separated segment of a possibly qualified and quoted name.
     */
    static String lastSegmentKey(String qualifiedName) {
        char quote = 0;
        int segmentStart = 0;
        for (int i = 0; i < qualifiedName.length(); i++) {
            char c = qualifiedName.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '`' || c == '"') {
                quote = c;
            } else if (c == '[') {
                quote = ']';
            } else if (c == '.') {
                segmentStart = i + 1;
            }
        }
        return key(qualifiedName.substring(segmentStart));
    }

    private static boolean containsIgnoreCase(String text, String lowerCaseNeedle) {
        int len = lowerCaseNeedle.length();
        if (len == 0) {
            return true;
        }
        int max = text.length() - len;
        char first = lowerCaseNeedle.charAt(0);
        char firstUpper = Character.toUpperCase(first);
        for (int i = 0; i <= max; i++) {
            char c = text.charAt(i);
            if ((c == first || c == firstUpper || Character.toLowerCase(c) == first)
                && text.regionMatches(true, i, lowerCaseNeedle, 0, len)) {
                return true;
            }
        }
        return false;
    }

    /** ASCII without quote characters, so the SQL spells the name exactly like its key. */
    private static boolean isPlainAscii(String key) {
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c > 0x7E || c < 0x20 || c == '`' || c == '"') {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GeometryColumns other)) {
            return false;
        }
        return hash == other.hash
            && Objects.equals(tableName, other.tableName)
            && geometryNames.equals(other.geometryNames)
            && Objects.equals(selectColumns, other.selectColumns);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "GeometryColumns{table=" + tableName + ", geometry=" + geometryNames
            + ", columns=" + selectColumns + '}';
    }
}
