package io.github.geoverselabs.mybatis.geometry.interceptor;

import io.github.geoverselabs.mybatis.geometry.interceptor.SelectStatement.Item;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SelectStatementTest {

    private static SelectStatement parse(String sql) {
        return SelectStatement.parse(sql, SqlDialect.MYSQL);
    }

    private static List<String> items(SelectStatement statement) {
        return statement.items.stream().map(item -> text(statement, item)).collect(Collectors.toList());
    }

    private static String text(SelectStatement statement, Item item) {
        return statement.sql.substring(statement.tokens.get(item.first).start,
            statement.tokens.get(item.end - 1).end);
    }

    @Test
    void splitsTopLevelItemsOnly() {
        SelectStatement statement = parse(
            "SELECT id, COUNT(a, b) AS c, (SELECT x, y FROM z) q, 'a,b' lbl, `x,y` FROM shop WHERE id = ?");
        assertThat(statement).isNotNull();
        assertThat(items(statement))
            .containsExactly("id", "COUNT(a, b) AS c", "(SELECT x, y FROM z) q", "'a,b' lbl", "`x,y`");
        assertThat(statement.mainTable.nameKey).isEqualTo("shop");
        assertThat(statement.mainTable.aliasKey).isNull();
        assertThat(statement.multiTable).isFalse();
    }

    @Test
    void skipsSelectModifiers() {
        assertThat(items(parse("SELECT DISTINCT location FROM shop"))).containsExactly("location");
        assertThat(items(parse("SELECT SQL_NO_CACHE DISTINCT SQL_CALC_FOUND_ROWS a, b FROM shop")))
            .containsExactly("a", "b");
        assertThat(items(parse("SELECT /*+ MAX_EXECUTION_TIME(1000) */ ALL location FROM shop")))
            .containsExactly("location");
        assertThat(items(SelectStatement.parse("SELECT DISTINCT ON (a, b) location, a FROM shop",
            SqlDialect.POSTGRESQL))).containsExactly("location", "a");
        // a column whose name merely starts like a modifier
        assertThat(items(parse("SELECT all_count, distinctive FROM shop"))).containsExactly("all_count", "distinctive");
    }

    @Test
    void findsTheTopLevelFromOnly() {
        SelectStatement statement = parse(
            "SELECT EXTRACT(YEAR FROM created) AS y, from_date, TRIM(LEADING ' ' FROM x) FROM trip t");
        assertThat(items(statement)).containsExactly("EXTRACT(YEAR FROM created) AS y", "from_date",
            "TRIM(LEADING ' ' FROM x)");
        assertThat(statement.mainTable.nameKey).isEqualTo("trip");
        assertThat(statement.mainTable.aliasKey).isEqualTo("t");
    }

    @Test
    void isDistinctFromIsAnOperator() {
        SelectStatement statement = SelectStatement.parse(
            "SELECT location, a IS DISTINCT FROM b AS d, c IS NOT DISTINCT FROM d FROM shop", SqlDialect.POSTGRESQL);
        assertThat(items(statement)).containsExactly("location", "a IS DISTINCT FROM b AS d",
            "c IS NOT DISTINCT FROM d");
        assertThat(statement.mainTable.nameKey).isEqualTo("shop");
    }

    @Test
    void mainTableAliases() {
        assertThat(parse("SELECT * FROM shop s WHERE 1=1").mainTable.aliasText).isEqualTo("s");
        assertThat(parse("SELECT * FROM shop AS s").mainTable.aliasText).isEqualTo("s");
        assertThat(parse("SELECT * FROM shop AS `S`").mainTable.aliasKey).isEqualTo("s");
        assertThat(parse("SELECT * FROM shop `S`").mainTable.aliasText).isEqualTo("`S`");
        assertThat(parse("SELECT * FROM `db`.`shop` s").mainTable.nameKey).isEqualTo("shop");
        assertThat(SelectStatement.parse("SELECT * FROM ONLY public.\"Shop\" AS x", SqlDialect.POSTGRESQL)
            .mainTable.nameKey).isEqualTo("shop");
        assertThat(parse("SELECT * FROM shop s USE INDEX (idx) WHERE 1=1").mainTable.aliasKey).isEqualTo("s");
        assertThat(parse("SELECT * FROM shop;").mainTable.nameKey).isEqualTo("shop");
    }

    @ParameterizedTest
    @ValueSource(strings = {"WHERE id = 1", "ORDER BY id", "GROUP BY id", "LIMIT 10", "LIMIT ?, ?", "OFFSET 5",
        "HAVING 1=1", "FOR UPDATE", "LOCK IN SHARE MODE", "WINDOW w AS ()", "USE INDEX (i)", "FORCE INDEX (i)",
        "PARTITION (p0)", "UNION_X", "INTO @x", "FETCH FIRST 1 ROWS ONLY"})
    void keywordsAreNeverAliases(String tail) {
        SelectStatement statement = parse("SELECT * FROM shop " + tail);
        assertThat(statement).as(tail).isNotNull();
        if (tail.equals("UNION_X")) {
            assertThat(statement.mainTable.aliasText).isEqualTo("UNION_X");
        } else {
            assertThat(statement.mainTable.aliasKey).as(tail).isNull();
        }
        assertThat(statement.multiTable).as(tail).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"JOIN", "LEFT JOIN", "RIGHT OUTER JOIN", "INNER JOIN", "CROSS JOIN", "NATURAL JOIN",
        "STRAIGHT_JOIN"})
    void joinsAreDetected(String join) {
        SelectStatement statement = parse("SELECT * FROM shop " + join + " region r ON r.id = shop.region_id");
        assertThat(statement.multiTable).as(join).isTrue();
        assertThat(statement.mainTable.nameKey).isEqualTo("shop");
        assertThat(statement.mainTable.aliasKey).isNull();
    }

    @Test
    void commaJoinsAreDetectedButLaterCommasAreNot() {
        assertThat(parse("SELECT * FROM shop s, region r WHERE r.id = s.id").multiTable).isTrue();
        assertThat(parse("SELECT * FROM shop s WHERE a IN (1, 2) ORDER BY a, b LIMIT ?, ?").multiTable).isFalse();
        assertThat(parse("SELECT * FROM shop GROUP BY a, b").multiTable).isFalse();
    }

    @Test
    void nonPlainFromItemsHaveNoMainTable() {
        assertThat(parse("SELECT * FROM (SELECT * FROM shop) s").mainTable).isNull();
        assertThat(SelectStatement.parse("SELECT * FROM generate_series(1, 3) g", SqlDialect.POSTGRESQL).mainTable)
            .isNull();
        assertThat(SelectStatement.parse("SELECT * FROM shop AS s(a, b)", SqlDialect.POSTGRESQL).mainTable).isNull();
        assertThat(parse("SELECT * FROM LATERAL (SELECT 1) x").mainTable).isNull();
        assertThat(parse("SELECT * FROM shop AS").mainTable).isNull();
        assertThat(parse("SELECT * FROM shop.").mainTable).isNull();
        assertThat(parse("SELECT * FROM 'shop'").mainTable).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "WITH x AS (SELECT 1) SELECT * FROM x",
        "SELECT a FROM t UNION SELECT a FROM u",
        "SELECT a FROM t UNION ALL SELECT a FROM u",
        "SELECT a FROM t INTERSECT SELECT a FROM u",
        "SELECT a FROM t EXCEPT SELECT a FROM u",
        "(SELECT a FROM t)",
        "SELECT a FROM t; SELECT b FROM u",
        "SELECT 1",
        "SELECT FROM t",
        "UPDATE t SET a = 1",
        "INSERT INTO t SELECT * FROM u",
        "SELECT (a FROM t",
        "SELECT 'a FROM t",
        "",
    })
    void unsupportedStatements(String sql) {
        assertThat(parse(sql)).as(sql).isNull();
    }

    @Test
    void setOperationsInsideSubqueriesAreFine() {
        SelectStatement statement = parse("SELECT a FROM t WHERE a IN (SELECT a FROM u UNION SELECT a FROM v)");
        assertThat(statement).isNotNull();
        assertThat(items(statement)).containsExactly("a");
    }

    @Test
    void trailingSemicolonsAreAllowed() {
        assertThat(parse("SELECT a FROM t;;")).isNotNull();
    }

    @Test
    void emptyItemsAreSkipped() {
        assertThat(items(parse("SELECT a,,b, FROM t"))).containsExactly("a", "b");
    }

    @Test
    void qualifierMatching() {
        SelectStatement aliased = parse("SELECT 1 FROM shop s");
        assertThat(aliased.mainTable.isReferencedBy("s")).isTrue();
        assertThat(aliased.mainTable.isReferencedBy("shop")).isFalse();
        SelectStatement plain = parse("SELECT 1 FROM shop");
        assertThat(plain.mainTable.isReferencedBy("shop")).isTrue();
        assertThat(plain.mainTable.isReferencedBy("s")).isFalse();
    }
}
