package io.github.geoverselabs.mybatis.geometry.interceptor;

import io.github.geoverselabs.mybatis.geometry.interceptor.SqlLexer.Kind;
import io.github.geoverselabs.mybatis.geometry.interceptor.SqlLexer.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Structure of the top level of a single, plain {@code SELECT} statement: the select-list items, the
 * first table reference of the {@code FROM} clause and whether more tables are involved.
 *
 * <p>Only depth-0 tokens (outside every bracket) are inspected, so subqueries in the select list,
 * the {@code FROM} clause or the {@code WHERE} clause ({@code EXISTS (SELECT * ...)}, {@code IN
 * (SELECT ...)}, derived tables) are never treated as part of the statement's own select list.</p>
 */
final class SelectStatement {

    /** Words between {@code SELECT} and the select list (MySQL/MariaDB and standard modifiers). */
    private static final Set<String> SELECT_MODIFIERS = Set.of(
        "ALL", "DISTINCT", "DISTINCTROW", "HIGH_PRIORITY", "STRAIGHT_JOIN", "SQL_SMALL_RESULT",
        "SQL_BIG_RESULT", "SQL_BUFFER_RESULT", "SQL_NO_CACHE", "SQL_CACHE", "SQL_CALC_FOUND_ROWS");

    /** Set operations; statements containing one at the top level are never rewritten. */
    private static final Set<String> SET_OPERATIONS = Set.of("UNION", "INTERSECT", "EXCEPT", "MINUS");

    /** Clauses that end the FROM clause (commas after them are not join separators). */
    private static final Set<String> FROM_CLAUSE_END = Set.of(
        "WHERE", "GROUP", "HAVING", "ORDER", "LIMIT", "OFFSET", "FETCH", "WINDOW", "INTO", "PROCEDURE",
        "QUALIFY");

    /** Join operators. */
    private static final Set<String> JOIN_WORDS = Set.of("JOIN", "STRAIGHT_JOIN");

    /** Words that can follow a table reference but are never a table alias. */
    private static final Set<String> NOT_AN_ALIAS = Set.of(
        "WHERE", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "CROSS", "FULL", "NATURAL", "STRAIGHT_JOIN",
        "ON", "USING", "ORDER", "GROUP", "HAVING", "LIMIT", "OFFSET", "FOR", "UNION", "INTERSECT",
        "EXCEPT", "MINUS", "WINDOW", "FETCH", "LATERAL", "USE", "FORCE", "IGNORE", "PARTITION",
        "TABLESAMPLE", "LOCK", "INTO", "PROCEDURE", "RETURNING", "QUALIFY", "WITH", "AS", "SET",
        "VALUES", "START", "CONNECT", "AND", "OR", "NOT", "SELECT", "FROM", "BY", "ONLY", "KEY", "INDEX",
        "NULL", "TRUE", "FALSE", "IS", "IN", "LIKE", "BETWEEN", "CASE", "WHEN", "THEN", "ELSE", "END",
        "ASC", "DESC");

    /**
     * A select-list item: token indexes {@code [first, end)}.
     */
    static final class Item {
        final int first;
        final int end;

        Item(int first, int end) {
            this.first = first;
            this.end = end;
        }
    }

    /**
     * First table reference of the FROM clause.
     */
    static final class TableRef {
        /** Key of the last segment of the table name. */
        final String nameKey;
        /** Alias key, or null when the table has no alias. */
        final String aliasKey;
        /** Alias exactly as written, or null. */
        final String aliasText;

        TableRef(String nameKey, String aliasKey, String aliasText) {
            this.nameKey = nameKey;
            this.aliasKey = aliasKey;
            this.aliasText = aliasText;
        }

        /**
         * Whether a column qualifier whose last segment has key {@code qualifierKey} refers to this
         * table: the alias when there is one, otherwise the table name.
         */
        boolean isReferencedBy(String qualifierKey) {
            return aliasKey != null ? aliasKey.equals(qualifierKey) : nameKey.equals(qualifierKey);
        }
    }

    final String sql;
    final List<Token> tokens;
    final List<Item> items;
    /** First table of the FROM clause, or null when it is not a plain table (derived table, function...). */
    final TableRef mainTable;
    /** Whether the FROM clause joins more than one table. */
    final boolean multiTable;

    private SelectStatement(String sql, List<Token> tokens, List<Item> items, TableRef mainTable,
                            boolean multiTable) {
        this.sql = sql;
        this.tokens = tokens;
        this.items = items;
        this.mainTable = mainTable;
        this.multiTable = multiTable;
    }

    /**
     * Parse the top level of {@code sql}.
     *
     * @return the structure, or null when {@code sql} is not a single plain SELECT with a FROM
     *         clause (malformed text, WITH, set operations, several statements, no FROM ...)
     */
    static SelectStatement parse(String sql, SqlDialect dialect) {
        List<Token> tokens = SqlLexer.tokenize(sql, dialect);
        if (tokens == null || tokens.isEmpty() || !SqlLexer.isKeyword(sql, tokens.get(0), "SELECT")) {
            return null;
        }
        int size = tokens.size();
        int statementEnd = size;
        int fromIndex = -1;
        Token previous = null;
        Token beforePrevious = null;
        for (int i = 0; i < size; i++) {
            Token token = tokens.get(i);
            if (token.depth != 0) {
                continue;
            }
            if (token.kind == Kind.SEMICOLON) {
                if (statementEnd == size) {
                    statementEnd = i;
                }
            } else if (statementEnd != size) {
                return null; // a second statement
            } else if (SqlLexer.isAnyKeyword(sql, token, SET_OPERATIONS)) {
                return null;
            } else if (fromIndex < 0 && SqlLexer.isKeyword(sql, token, "FROM")
                && !isDistinctFromOperator(sql, previous, beforePrevious)) {
                fromIndex = i;
            }
            beforePrevious = previous;
            previous = token;
        }
        if (fromIndex < 0) {
            return null;
        }
        int listStart = skipModifiers(sql, tokens, 1, fromIndex);
        if (listStart >= fromIndex) {
            return null;
        }
        if (isDistinct(sql, tokens, listStart) && hasTopLevelKeyword(sql, tokens, fromIndex + 1, statementEnd, "ORDER")) {
            // wrapping a column removes it from a DISTINCT select list, which ORDER BY may still reference
            return null;
        }
        List<Item> items = splitItems(tokens, listStart, fromIndex);

        int clauseEnd = statementEnd;
        boolean multiTable = false;
        for (int i = fromIndex + 1; i < statementEnd; i++) {
            Token token = tokens.get(i);
            if (token.depth != 0) {
                continue;
            }
            if (clauseEnd == statementEnd && SqlLexer.isAnyKeyword(sql, token, FROM_CLAUSE_END)) {
                clauseEnd = i;
            } else if (token.kind == Kind.COMMA && i < clauseEnd) {
                multiTable = true;
            } else if (SqlLexer.isAnyKeyword(sql, token, JOIN_WORDS)) {
                multiTable = true;
            }
        }
        TableRef mainTable = parseTableRef(sql, tokens, fromIndex + 1, clauseEnd);
        return new SelectStatement(sql, tokens, items, mainTable, multiTable);
    }

    private static boolean isDistinct(String sql, List<Token> tokens, int listStart) {
        for (int i = 1; i < listStart; i++) {
            if (SqlLexer.isKeyword(sql, tokens.get(i), "DISTINCT") || SqlLexer.isKeyword(sql, tokens.get(i), "DISTINCTROW")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTopLevelKeyword(String sql, List<Token> tokens, int from, int to, String keyword) {
        for (int i = from; i < to; i++) {
            Token token = tokens.get(i);
            if (token.depth == 0 && SqlLexer.isKeyword(sql, token, keyword)) {
                return true;
            }
        }
        return false;
    }

    /** {@code a IS [NOT] DISTINCT FROM b}: this FROM is an operator, not the FROM clause. */
    private static boolean isDistinctFromOperator(String sql, Token previous, Token beforePrevious) {
        return previous != null && beforePrevious != null
            && SqlLexer.isKeyword(sql, previous, "DISTINCT")
            && (SqlLexer.isKeyword(sql, beforePrevious, "IS") || SqlLexer.isKeyword(sql, beforePrevious, "NOT"));
    }

    /**
     * Index of the first select-list token after {@code SELECT} modifiers ({@code DISTINCT},
     * PostgreSQL {@code DISTINCT ON (...)}, MySQL {@code SQL_*} flags). Comments, including
     * optimizer hints, are not tokens and need no skipping.
     */
    private static int skipModifiers(String sql, List<Token> tokens, int from, int limit) {
        int i = from;
        while (i < limit && SqlLexer.isAnyKeyword(sql, tokens.get(i), SELECT_MODIFIERS)) {
            boolean distinct = SqlLexer.isKeyword(sql, tokens.get(i), "DISTINCT");
            i++;
            if (distinct && i + 1 < limit && SqlLexer.isKeyword(sql, tokens.get(i), "ON")
                && tokens.get(i + 1).kind == Kind.OPEN) {
                i = afterGroup(tokens, i + 1);
            }
        }
        return i;
    }

    /** Index after the bracket group that opens at {@code openIndex}. */
    private static int afterGroup(List<Token> tokens, int openIndex) {
        int depth = tokens.get(openIndex).depth;
        for (int i = openIndex + 1; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.kind == Kind.CLOSE && token.depth == depth) {
                return i + 1;
            }
        }
        return tokens.size();
    }

    private static List<Item> splitItems(List<Token> tokens, int from, int to) {
        List<Item> items = new ArrayList<>();
        int start = from;
        for (int i = from; i < to; i++) {
            Token token = tokens.get(i);
            if (token.depth == 0 && token.kind == Kind.COMMA) {
                if (i > start) {
                    items.add(new Item(start, i));
                }
                start = i + 1;
            }
        }
        if (to > start) {
            items.add(new Item(start, to));
        }
        return Collections.unmodifiableList(items);
    }

    /**
     * Parse {@code [ONLY] name[.name...] [[AS] alias]} at {@code from}.
     *
     * @return the reference, or null when the first FROM item is not a plain table
     */
    private static TableRef parseTableRef(String sql, List<Token> tokens, int from, int to) {
        int i = from;
        if (i < to && SqlLexer.isKeyword(sql, tokens.get(i), "ONLY")) {
            i++;
        }
        if (i >= to || !isTableIdentifier(sql, tokens.get(i))) {
            return null;
        }
        Token last = tokens.get(i);
        i++;
        while (i + 1 < to && tokens.get(i).kind == Kind.DOT && isIdentifier(tokens.get(i + 1))) {
            last = tokens.get(i + 1);
            i += 2;
        }
        if (i < to && (tokens.get(i).kind == Kind.OPEN || tokens.get(i).kind == Kind.DOT)) {
            return null; // table function such as generate_series(...), or something unexpected
        }
        String nameKey = identifierKey(sql, last);
        Token alias = null;
        if (i < to && SqlLexer.isKeyword(sql, tokens.get(i), "AS")) {
            if (i + 1 >= to || !isIdentifier(tokens.get(i + 1))) {
                return null;
            }
            alias = tokens.get(i + 1);
            i += 2;
        } else if (i < to && (tokens.get(i).kind == Kind.QUOTED_IDENT
            || (tokens.get(i).kind == Kind.WORD && !SqlLexer.isAnyKeyword(sql, tokens.get(i), NOT_AN_ALIAS)))) {
            alias = tokens.get(i);
            i++;
        }
        if (alias != null && i < to && tokens.get(i).kind == Kind.OPEN) {
            return null; // column alias list renames the columns: t AS x(a, b)
        }
        if (alias == null) {
            return new TableRef(nameKey, null, null);
        }
        return new TableRef(nameKey, identifierKey(sql, alias), sql.substring(alias.start, alias.end));
    }

    private static boolean isTableIdentifier(String sql, Token token) {
        return token.kind == Kind.QUOTED_IDENT
            || (token.kind == Kind.WORD && !SqlLexer.isAnyKeyword(sql, token, NOT_AN_ALIAS));
    }

    /**
     * Whether {@code token} can be an identifier (quoted or not).
     */
    static boolean isIdentifier(Token token) {
        return token.kind == Kind.WORD || token.kind == Kind.QUOTED_IDENT;
    }

    /**
     * Comparison key of an identifier token, see {@link GeometryColumns#key(String)}.
     */
    static String identifierKey(String sql, Token token) {
        return GeometryColumns.key(sql.substring(token.start, token.end));
    }
}
