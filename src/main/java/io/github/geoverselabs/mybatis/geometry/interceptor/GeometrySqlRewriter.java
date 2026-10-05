package io.github.geoverselabs.mybatis.geometry.interceptor;

import io.github.geoverselabs.mybatis.geometry.interceptor.SelectStatement.Item;
import io.github.geoverselabs.mybatis.geometry.interceptor.SelectStatement.TableRef;
import io.github.geoverselabs.mybatis.geometry.interceptor.SqlLexer.Kind;
import io.github.geoverselabs.mybatis.geometry.interceptor.SqlLexer.Token;
import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Rewrites the select list of a SELECT statement so that geometry columns are read through the
 * database-specific expression of a {@link GeometryHandlerStrategy}
 * ({@code HEX(location) AS location} on MySQL/MariaDB, a hex EWKB expression on PostGIS).
 *
 * <p>A small SQL scanner (quotes, comments, optimizer hints and bracket depth aware) locates the
 * top-level select list and FROM clause; nothing else in the statement is ever modified:</p>
 * <ul>
 *   <li>Only the outermost select list is rewritten. Subqueries ({@code EXISTS (SELECT * ...)},
 *       {@code IN (SELECT ...)}, scalar subqueries, derived tables) are left untouched, and
 *       statements starting with {@code WITH} or containing {@code UNION}/{@code INTERSECT}/
 *       {@code EXCEPT} are not rewritten at all.</li>
 *   <li>A select item is wrapped only when it is a plain column reference ({@code col},
 *       {@code t.col}, {@code col AS a}, {@code col a}, quoted names) whose unquoted name equals a
 *       geometry column (case-insensitive) and whose qualifier is absent or names the first table of
 *       the FROM clause (its alias when it has one). The original alias is kept; without one the
 *       column name becomes the alias, so result labels never change. Expressions, function calls,
 *       literals and subqueries are never touched.</li>
 *   <li>{@code SELECT *} and {@code SELECT t.*} are expanded to the entity's columns (key column
 *       first) when they are known and the FROM table is the entity's table. An unqualified
 *       {@code *} is not expanded when several tables are joined, because the other tables'
 *       columns are unknown.</li>
 *   <li>Statements the scanner does not understand are returned unchanged.</li>
 * </ul>
 *
 * <p>Results are cached per original SQL text (bounded), because MyBatis-Plus generates the same
 * SQL for every call of a statement.</p>
 */
public class GeometrySqlRewriter {

    private static final Logger log = LoggerFactory.getLogger(GeometrySqlRewriter.class);

    /** Maximum number of cached rewrite results; the cache is cleared when it grows beyond. */
    static final int CACHE_LIMIT = 2048;

    /** Longer statements are rewritten on every call instead of being cached. */
    static final int MAX_CACHED_SQL_LENGTH = 32 * 1024;

    /** Cache marker for "nothing to rewrite", so callers get their own SQL instance back. */
    private static final String UNCHANGED = new String("<unchanged>");

    private final Supplier<GeometryHandlerStrategy> strategySupplier;
    private final Map<CacheKey, String> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean legacyFailureLogged = new AtomicBoolean();

    /**
     * Create a rewriter that uses {@link GeometryStrategyFactory#getDefaultStrategy()}, resolved
     * on every call, so a default strategy configured after construction is honoured.
     */
    public GeometrySqlRewriter() {
        this.strategySupplier = GeometryStrategyFactory::getDefaultStrategy;
    }

    /**
     * Create a SQL rewriter with the specified strategy.
     *
     * @param strategy the geometry handler strategy for column wrapping; null uses
     *                 {@link GeometryStrategyFactory#getDefaultStrategy()} resolved on every call
     */
    public GeometrySqlRewriter(GeometryHandlerStrategy strategy) {
        this.strategySupplier = strategy == null ? GeometryStrategyFactory::getDefaultStrategy : () -> strategy;
    }

    /**
     * Rewrite a SQL SELECT statement to wrap geometry columns.
     *
     * <p>Columns are matched by name only (the table name is unknown here). Never throws: when the
     * statement cannot be rewritten the original SQL is returned.</p>
     *
     * @param sql             the original SQL
     * @param geometryColumns geometry column names (quoted or not)
     * @param allColumns      every column in select order, used to expand {@code SELECT *};
     *                        null or empty disables the expansion
     * @return rewritten SQL, or the original SQL if no changes are needed or possible
     */
    public String rewrite(String sql, Set<String> geometryColumns, List<String> allColumns) {
        if (sql == null || geometryColumns == null || geometryColumns.isEmpty()) {
            return sql;
        }
        try {
            return rewrite(sql, new GeometryColumns(null, geometryColumns, allColumns), false);
        } catch (RuntimeException e) {
            if (legacyFailureLogged.compareAndSet(false, true)) {
                log.warn("Geometry SQL rewrite failed, using the original SQL: {}", e.toString());
            }
            log.debug("Geometry SQL rewrite failed for: {}", sql, e);
            return sql;
        }
    }

    /**
     * Rewrite {@code sql} for an entity.
     *
     * @param sql               the original SQL
     * @param columns           the entity's column metadata
     * @param requireTableMatch when true and the entity's table name is known, nothing is rewritten
     *                          unless the first FROM table is the entity's table
     * @return the rewritten SQL, or {@code sql} itself when nothing changed
     * @throws RuntimeException when the strategy fails; the original SQL is cached for
     *                          {@code sql}, so the failure is reported once per statement text
     */
    String rewrite(String sql, GeometryColumns columns, boolean requireTableMatch) {
        if (sql == null || columns == null || columns.isEmpty()) {
            return sql;
        }
        GeometryHandlerStrategy strategy = strategySupplier.get();
        boolean cacheable = sql.length() <= MAX_CACHED_SQL_LENGTH;
        CacheKey key = cacheable ? new CacheKey(sql, columns, requireTableMatch, strategy) : null;
        if (cacheable) {
            String cached = cache.get(key);
            if (cached != null) {
                return cached == UNCHANGED ? sql : cached;
            }
        }
        String result;
        try {
            result = columns.mayAffect(sql) ? rewriteUncached(sql, columns, requireTableMatch, strategy) : sql;
        } catch (RuntimeException e) {
            if (cacheable) {
                store(key, UNCHANGED);
            }
            throw e;
        }
        if (cacheable) {
            store(key, result == sql ? UNCHANGED : result);
        }
        return result;
    }

    /**
     * Clear the cache of rewrite results.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * The strategy used for the next rewrite.
     */
    GeometryHandlerStrategy currentStrategy() {
        return strategySupplier.get();
    }

    int cacheSize() {
        return cache.size();
    }

    private void store(CacheKey key, String value) {
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(key, value);
    }

    private static String rewriteUncached(String sql, GeometryColumns columns, boolean requireTableMatch,
                                          GeometryHandlerStrategy strategy) {
        SqlDialect dialect = SqlDialect.of(databaseType(strategy));
        SelectStatement statement = SelectStatement.parse(sql, dialect);
        if (statement == null || statement.mainTable == null) {
            return sql;
        }
        TableRef main = statement.mainTable;
        boolean tableMatches = columns.tableKey() == null || columns.tableKey().equals(main.nameKey);
        if (requireTableMatch && !tableMatches) {
            return sql;
        }
        List<Token> tokens = statement.tokens;
        StringBuilder out = null;
        int copied = 0;
        for (Item item : statement.items) {
            String replacement = rewriteItem(statement, item, columns, tableMatches, strategy, dialect);
            if (replacement == null) {
                continue;
            }
            if (out == null) {
                out = new StringBuilder(sql.length() + 64);
            }
            int start = tokens.get(item.first).start;
            int end = tokens.get(item.end - 1).end;
            out.append(sql, copied, start).append(replacement);
            copied = end;
        }
        if (out == null) {
            return sql;
        }
        return out.append(sql, copied, sql.length()).toString();
    }

    /**
     * @return the replacement text of the item, or null to keep it unchanged
     */
    private static String rewriteItem(SelectStatement statement, Item item, GeometryColumns columns,
                                      boolean tableMatches, GeometryHandlerStrategy strategy,
                                      SqlDialect dialect) {
        String sql = statement.sql;
        List<Token> tokens = statement.tokens;
        TableRef main = statement.mainTable;
        int first = item.first;
        int end = item.end;

        if (end - first == 1 && tokens.get(first).kind == Kind.STAR) {
            // SELECT *: the columns of joined tables are unknown, so only a single table is expanded
            if (statement.multiTable || !tableMatches || columns.selectColumns() == null) {
                return null;
            }
            String prefix = main.aliasText == null ? "" : main.aliasText + ".";
            return expandStar(prefix, columns, strategy, dialect);
        }

        if (!SelectStatement.isIdentifier(tokens.get(first))) {
            return null;
        }
        // identifier chain: a[.b[.c]] possibly ending in .*
        int i = first + 1;
        while (i + 1 < end && tokens.get(i).kind == Kind.DOT) {
            Token next = tokens.get(i + 1);
            if (next.kind == Kind.STAR) {
                if (i + 2 != end || !tableMatches || columns.selectColumns() == null
                    || !main.isReferencedBy(SelectStatement.identifierKey(sql, tokens.get(i - 1)))) {
                    return null;
                }
                // t.* names the columns of one table, so it is expanded even when tables are joined
                String prefix = sql.substring(tokens.get(first).start, tokens.get(i).end);
                return expandStar(prefix, columns, strategy, dialect);
            }
            if (!SelectStatement.isIdentifier(next)) {
                return null;
            }
            i += 2;
        }
        int columnIndex = i - 1;
        Token column = tokens.get(columnIndex);
        Token alias;
        if (i == end) {
            alias = null;
        } else if (i + 2 == end && SqlLexer.isKeyword(sql, tokens.get(i), "AS")
            && (SelectStatement.isIdentifier(tokens.get(i + 1)) || tokens.get(i + 1).kind == Kind.STRING)) {
            alias = tokens.get(i + 1);
        } else if (i + 1 == end && SelectStatement.isIdentifier(tokens.get(i))
            && !SqlLexer.isKeyword(sql, tokens.get(i), "AS")) {
            alias = tokens.get(i);
        } else {
            return null; // an expression such as "col IS NULL" or "col COLLATE x"
        }
        if (!columns.isGeometryKey(SelectStatement.identifierKey(sql, column))) {
            return null;
        }
        if (columnIndex > first
            && !main.isReferencedBy(SelectStatement.identifierKey(sql, tokens.get(columnIndex - 2)))) {
            return null; // qualified with another table
        }
        String reference = sql.substring(tokens.get(first).start, column.end);
        String label = alias == null
            ? sql.substring(column.start, column.end)
            : sql.substring(alias.start, alias.end);
        return wrapExpression(strategy, reference, dialect) + " AS " + label;
    }

    private static String expandStar(String prefix, GeometryColumns columns, GeometryHandlerStrategy strategy,
                                     SqlDialect dialect) {
        StringBuilder sb = new StringBuilder();
        for (String column : columns.selectColumns()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            String reference = prefix + column;
            if (columns.isGeometryKey(GeometryColumns.key(column))) {
                sb.append(wrapExpression(strategy, reference, dialect)).append(" AS ").append(column);
            } else {
                sb.append(reference);
            }
        }
        return sb.toString();
    }

    /**
     * The strategy's read expression for {@code reference} without the alias the strategy appends
     * ({@code HEX(t.location) AS location} becomes {@code HEX(t.location)}).
     */
    static String wrapExpression(GeometryHandlerStrategy strategy, String reference, SqlDialect dialect) {
        String wrapped = strategy.wrapColumnForSelect(reference);
        if (wrapped == null || wrapped.isBlank()) {
            throw new IllegalStateException(strategy.getClass().getName()
                + ".wrapColumnForSelect returned no expression for column " + reference);
        }
        List<Token> tokens = SqlLexer.tokenize(wrapped, dialect);
        if (tokens != null && tokens.size() >= 3) {
            Token last = tokens.get(tokens.size() - 1);
            Token as = tokens.get(tokens.size() - 2);
            if (as.depth == 0 && last.depth == 0 && SqlLexer.isKeyword(wrapped, as, "AS")
                && (SelectStatement.isIdentifier(last) || last.kind == Kind.STRING)) {
                return wrapped.substring(0, as.start).strip();
            }
        }
        return wrapped.strip();
    }

    private static DatabaseType databaseType(GeometryHandlerStrategy strategy) {
        try {
            return strategy.getSupportedDatabaseType();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Cache key: the SQL text plus everything the result depends on.
     */
    private record CacheKey(String sql, GeometryColumns columns, boolean requireTableMatch,
                            GeometryHandlerStrategy strategy) {
    }
}
