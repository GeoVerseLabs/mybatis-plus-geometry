package io.github.geoverselabs.mybatis.geometry.interceptor;

import io.github.geoverselabs.mybatis.geometry.interceptor.SqlLexer.Kind;
import io.github.geoverselabs.mybatis.geometry.interceptor.SqlLexer.Token;
import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.Chars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SqlLexerTest {

    private static List<Token> lex(String sql, SqlDialect dialect) {
        return SqlLexer.tokenize(sql, dialect);
    }

    /** Token texts joined by spaces, each prefixed by its kind's first letter for readability. */
    private static List<String> texts(String sql, SqlDialect dialect) {
        List<Token> tokens = lex(sql, dialect);
        assertThat(tokens).as("tokens of %s", sql).isNotNull();
        return tokens.stream().map(t -> sql.substring(t.start, t.end)).collect(Collectors.toList());
    }

    private static List<Kind> kinds(String sql, SqlDialect dialect) {
        return lex(sql, dialect).stream().map(t -> t.kind).collect(Collectors.toList());
    }

    @Test
    void dialectFollowsDatabaseType() {
        assertThat(SqlDialect.of(DatabaseType.MYSQL)).isEqualTo(SqlDialect.MYSQL);
        assertThat(SqlDialect.of(DatabaseType.POSTGRESQL)).isEqualTo(SqlDialect.POSTGRESQL);
        assertThat(SqlDialect.of(null)).isEqualTo(SqlDialect.MYSQL);
    }

    @Test
    void splitsWordsPunctuationAndRecordsDepth() {
        String sql = "SELECT a.b, COUNT(*) FROM t WHERE x = ?;";
        assertThat(texts(sql, SqlDialect.MYSQL)).containsExactly(
            "SELECT", "a", ".", "b", ",", "COUNT", "(", "*", ")", "FROM", "t", "WHERE", "x", "=", "?", ";");
        List<Token> tokens = lex(sql, SqlDialect.MYSQL);
        assertThat(tokens.get(6).kind).isEqualTo(Kind.OPEN);
        assertThat(tokens.get(6).depth).isZero();
        assertThat(tokens.get(7).kind).isEqualTo(Kind.STAR);
        assertThat(tokens.get(7).depth).isEqualTo(1);
        assertThat(tokens.get(8).kind).isEqualTo(Kind.CLOSE);
        assertThat(tokens.get(8).depth).isZero();
        assertThat(tokens.get(15).kind).isEqualTo(Kind.SEMICOLON);
        assertThat(tokens.get(0).toString()).isEqualTo("WORD[0,6)@0");
    }

    @ParameterizedTest
    @EnumSource(SqlDialect.class)
    void commentsAndHintsProduceNoTokens(SqlDialect dialect) {
        String sql = "SELECT /*+ MAX_EXECUTION_TIME(1000) */ a /* x, FROM ( */ -- trailing, FROM\nFROM t";
        assertThat(texts(sql, dialect)).containsExactly("SELECT", "a", "FROM", "t");
    }

    @Test
    void hashCommentsOnlyInMySql() {
        assertThat(texts("SELECT a # comment FROM x\nFROM t", SqlDialect.MYSQL))
            .containsExactly("SELECT", "a", "FROM", "t");
        assertThat(texts("SELECT a #> b FROM t", SqlDialect.POSTGRESQL))
            .containsExactly("SELECT", "a", "#", ">", "b", "FROM", "t");
    }

    @Test
    void mysqlDashDashNeedsWhitespace() {
        assertThat(texts("SELECT 1--1 FROM t", SqlDialect.MYSQL))
            .containsExactly("SELECT", "1", "-", "-", "1", "FROM", "t");
        assertThat(texts("SELECT 1 --", SqlDialect.MYSQL)).containsExactly("SELECT", "1");
        assertThat(texts("SELECT 1 --\tx\nFROM t", SqlDialect.MYSQL)).containsExactly("SELECT", "1", "FROM", "t");
        assertThat(texts("SELECT 1--1 FROM t", SqlDialect.POSTGRESQL)).containsExactly("SELECT", "1");
    }

    @Test
    void blockCommentsNestOnlyInPostgres() {
        String sql = "SELECT /* a /* b */ c */ x FROM t";
        assertThat(texts(sql, SqlDialect.POSTGRESQL)).containsExactly("SELECT", "x", "FROM", "t");
        assertThat(texts(sql, SqlDialect.MYSQL)).containsExactly("SELECT", "c", "*", "/", "x", "FROM", "t");
    }

    @ParameterizedTest
    @EnumSource(SqlDialect.class)
    void stringLiteralsHideSeparatorsAndKeywords(SqlDialect dialect) {
        String sql = "SELECT 'a,b) FROM x', '' , 'it''s' FROM t";
        assertThat(texts(sql, dialect))
            .containsExactly("SELECT", "'a,b) FROM x'", ",", "''", ",", "'it''s'", "FROM", "t");
        assertThat(kinds(sql, dialect).get(1)).isEqualTo(Kind.STRING);
    }

    @Test
    void backslashEscapesDependOnDialect() {
        String sql = "SELECT 'a\\'b' FROM t";
        assertThat(texts(sql, SqlDialect.MYSQL)).containsExactly("SELECT", "'a\\'b'", "FROM", "t");
        // standard conforming strings: the backslash is literal, the quote ends the string
        assertThat(texts("SELECT 'a\\', b FROM t", SqlDialect.POSTGRESQL))
            .containsExactly("SELECT", "'a\\'", ",", "b", "FROM", "t");
        assertThat(texts("SELECT E'a\\'b', c FROM t", SqlDialect.POSTGRESQL))
            .containsExactly("SELECT", "E'a\\'b'", ",", "c", "FROM", "t");
    }

    @Test
    void prefixedLiteralsAreSingleStrings() {
        assertThat(texts("SELECT X'0A', b'01', N'x', _utf8mb4'y' FROM t", SqlDialect.MYSQL))
            .containsExactly("SELECT", "X'0A'", ",", "b'01'", ",", "N'x'", ",", "_utf8mb4'y'", "FROM", "t");
        assertThat(kinds("SELECT X'0A'", SqlDialect.MYSQL)).containsExactly(Kind.WORD, Kind.STRING);
        // E is not a prefix in MySQL
        assertThat(texts("SELECT E'x'", SqlDialect.MYSQL)).containsExactly("SELECT", "E", "'x'");
        assertThat(texts("SELECT B'01', X'1F' FROM t", SqlDialect.POSTGRESQL))
            .containsExactly("SELECT", "B'01'", ",", "X'1F'", "FROM", "t");
    }

    @Test
    void doubleQuotesAreIdentifiersInPostgresAndStringsInMySql() {
        String sql = "SELECT \"a\"\"b\" FROM t";
        assertThat(texts(sql, SqlDialect.POSTGRESQL)).containsExactly("SELECT", "\"a\"\"b\"", "FROM", "t");
        assertThat(kinds(sql, SqlDialect.POSTGRESQL).get(1)).isEqualTo(Kind.QUOTED_IDENT);
        assertThat(kinds(sql, SqlDialect.MYSQL).get(1)).isEqualTo(Kind.STRING);
    }

    @ParameterizedTest
    @EnumSource(SqlDialect.class)
    void backticksAreQuotedIdentifiers(SqlDialect dialect) {
        String sql = "SELECT `a``b, FROM` FROM t";
        assertThat(texts(sql, dialect)).containsExactly("SELECT", "`a``b, FROM`", "FROM", "t");
        assertThat(kinds(sql, dialect).get(1)).isEqualTo(Kind.QUOTED_IDENT);
    }

    @Test
    void dollarQuotedStringsInPostgres() {
        assertThat(texts("SELECT $$a, FROM 'x'$$, $tag$ $$ $tag$, b FROM t", SqlDialect.POSTGRESQL))
            .containsExactly("SELECT", "$$a, FROM 'x'$$", ",", "$tag$ $$ $tag$", ",", "b", "FROM", "t");
        // a dollar inside an identifier, and positional parameters, are not quotes
        assertThat(texts("SELECT a$b$, $1 FROM t", SqlDialect.POSTGRESQL))
            .containsExactly("SELECT", "a$b$", ",", "$", "1", "FROM", "t");
        assertThat(lex("SELECT $$unterminated", SqlDialect.POSTGRESQL)).isNull();
    }

    @Test
    void dollarSignsAreIdentifierCharactersInMySql() {
        assertThat(texts("SELECT geo$loc, $x FROM t", SqlDialect.MYSQL))
            .containsExactly("SELECT", "geo$loc", ",", "$x", "FROM", "t");
        assertThat(kinds("SELECT $x", SqlDialect.MYSQL)).containsExactly(Kind.WORD, Kind.WORD);
    }

    @Test
    void numbersAndDigitLeadingIdentifiers() {
        assertThat(kinds("SELECT 1, 1.5, 2e10, 0x1F, 0b101, .5", SqlDialect.MYSQL)).containsExactly(
            Kind.WORD, Kind.NUMBER, Kind.COMMA, Kind.NUMBER, Kind.COMMA, Kind.NUMBER, Kind.COMMA,
            Kind.NUMBER, Kind.COMMA, Kind.NUMBER, Kind.COMMA, Kind.DOT, Kind.NUMBER);
        assertThat(kinds("SELECT 2d_point", SqlDialect.MYSQL)).containsExactly(Kind.WORD, Kind.WORD);
        assertThat(kinds("SELECT 2d_point", SqlDialect.POSTGRESQL)).containsExactly(Kind.WORD, Kind.NUMBER);
        assertThat(kinds("SELECT 0xZZ", SqlDialect.MYSQL)).containsExactly(Kind.WORD, Kind.WORD);
    }

    @Test
    void bracketsMustPair() {
        assertThat(lex("SELECT ARRAY[1, (2)] {fn now()}", SqlDialect.POSTGRESQL)).isNotNull();
        assertThat(lex("SELECT (a", SqlDialect.MYSQL)).isNull();
        assertThat(lex("SELECT a)", SqlDialect.MYSQL)).isNull();
        assertThat(lex("SELECT (a]", SqlDialect.MYSQL)).isNull();
        assertThat(lex("SELECT [a}", SqlDialect.MYSQL)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SELECT 'abc", "SELECT `abc", "SELECT /* abc", "SELECT 'a\\'"})
    void unterminatedTextIsMalformed(String sql) {
        assertThat(lex(sql, SqlDialect.MYSQL)).isNull();
    }

    @Test
    void nullAndEmptyInput() {
        assertThat(lex(null, SqlDialect.MYSQL)).isNull();
        assertThat(lex("", SqlDialect.MYSQL)).isEmpty();
        assertThat(lex("  \n\t ", SqlDialect.MYSQL)).isEmpty();
    }

    @Test
    void manyNestedBracketsGrowTheStack() {
        String sql = "SELECT " + "(".repeat(100) + "1" + ")".repeat(100);
        List<Token> tokens = lex(sql, SqlDialect.MYSQL);
        assertThat(tokens).hasSize(202);
        assertThat(tokens.get(101).depth).isEqualTo(100);
    }

    @Test
    void keywordHelpers() {
        String sql = "select Distinct x";
        List<Token> tokens = lex(sql, SqlDialect.MYSQL);
        assertThat(SqlLexer.isKeyword(sql, tokens.get(0), "SELECT")).isTrue();
        assertThat(SqlLexer.isKeyword(sql, tokens.get(0), "SELEC")).isFalse();
        assertThat(SqlLexer.isAnyKeyword(sql, tokens.get(1), Set.of("DISTINCT"))).isTrue();
        assertThat(SqlLexer.isAnyKeyword(sql, tokens.get(2), Set.of("DISTINCT"))).isFalse();
        String quoted = "`select`";
        assertThat(SqlLexer.isKeyword(quoted, lex(quoted, SqlDialect.MYSQL).get(0), "SELECT")).isFalse();
        String longWord = "x".repeat(100);
        assertThat(SqlLexer.isAnyKeyword(longWord, lex(longWord, SqlDialect.MYSQL).get(0), Set.of(longWord))).isFalse();
    }

    @Test
    void longWhitespaceRunsAreLinear() {
        String sql = "SELECT" + " ".repeat(200_000) + "1";
        long start = System.nanoTime();
        assertThat(lex(sql, SqlDialect.MYSQL)).hasSize(2);
        assertThat(System.nanoTime() - start).isLessThan(2_000_000_000L);
    }

    @Property(tries = 2000)
    void neverThrowsAndTokensStayInBounds(
        @ForAll @StringLength(max = 60) @Chars({'\'', '"', '`', '(', ')', '[', ']', '{', '}', '-', '/', '*', '#',
            '$', '\\', ',', '.', ';', ' ', '\n', 'e', 'E', 'x', '_', '1', 'a'}) @CharRange(from = 'a', to = 'c')
        String sql) {
        for (SqlDialect dialect : SqlDialect.values()) {
            List<Token> tokens = SqlLexer.tokenize(sql, dialect);
            if (tokens == null) {
                continue;
            }
            int previousEnd = 0;
            for (Token token : tokens) {
                assertThat(token.start).isGreaterThanOrEqualTo(previousEnd);
                assertThat(token.end).isGreaterThan(token.start).isLessThanOrEqualTo(sql.length());
                assertThat(token.depth).isGreaterThanOrEqualTo(0);
                previousEnd = token.end;
            }
        }
    }
}
