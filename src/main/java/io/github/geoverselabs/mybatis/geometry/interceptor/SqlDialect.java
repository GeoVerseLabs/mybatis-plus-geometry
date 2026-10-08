package io.github.geoverselabs.mybatis.geometry.interceptor;

import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;

/**
 * Lexical rules that differ between the supported databases.
 *
 * <p>Only the rules that change where quoted text, comments and identifiers begin or end are
 * modelled; everything else is shared.</p>
 */
enum SqlDialect {

    /**
     * MySQL and MariaDB (default {@code sql_mode}): backslash escapes inside {@code '...'} and
     * {@code "..."}, {@code "..."} is a string literal, {@code #} line comments, {@code --} starts a
     * comment only when followed by whitespace, block comments do not nest, unquoted identifiers may
     * start with a digit or {@code $}.
     */
    MYSQL(true, false, true, false, false, true, true),

    /**
     * PostgreSQL: standard conforming strings (backslash escapes only in {@code E'...'}),
     * {@code "..."} is an identifier, dollar-quoted strings, every {@code --} starts a comment,
     * block comments nest.
     */
    POSTGRESQL(false, true, false, true, true, false, false);

    private final boolean backslashEscapes;
    private final boolean doubleQuotedIdentifiers;
    private final boolean hashComments;
    private final boolean nestedBlockComments;
    private final boolean dollarQuotes;
    private final boolean dashDashNeedsSpace;
    private final boolean digitLeadingIdentifiers;

    SqlDialect(boolean backslashEscapes, boolean doubleQuotedIdentifiers, boolean hashComments,
               boolean nestedBlockComments, boolean dollarQuotes, boolean dashDashNeedsSpace,
               boolean digitLeadingIdentifiers) {
        this.backslashEscapes = backslashEscapes;
        this.doubleQuotedIdentifiers = doubleQuotedIdentifiers;
        this.hashComments = hashComments;
        this.nestedBlockComments = nestedBlockComments;
        this.dollarQuotes = dollarQuotes;
        this.dashDashNeedsSpace = dashDashNeedsSpace;
        this.digitLeadingIdentifiers = digitLeadingIdentifiers;
    }

    /**
     * Dialect for a database type; {@code null} (a custom strategy that reports no type) uses MySQL
     * rules, the same fallback the strategy factory uses.
     */
    static SqlDialect of(DatabaseType type) {
        return type == DatabaseType.POSTGRESQL ? POSTGRESQL : MYSQL;
    }

    boolean backslashEscapes() {
        return backslashEscapes;
    }

    boolean doubleQuotedIdentifiers() {
        return doubleQuotedIdentifiers;
    }

    boolean hashComments() {
        return hashComments;
    }

    boolean nestedBlockComments() {
        return nestedBlockComments;
    }

    boolean dollarQuotes() {
        return dollarQuotes;
    }

    boolean dashDashNeedsSpace() {
        return dashDashNeedsSpace;
    }

    boolean digitLeadingIdentifiers() {
        return digitLeadingIdentifiers;
    }
}
