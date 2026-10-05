package io.github.geoverselabs.mybatis.geometry.interceptor;

import io.github.geoverselabs.mybatis.geometry.strategy.MySQLGeometryStrategy;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests over random SQL-like token soups: the rewriter must never throw, must be
 * idempotent, must keep the text lexically well formed and must leave statements without a
 * geometry column untouched.
 */
class GeometrySqlRewriterPropertiesTest {

    private static final GeometryColumns SHOP =
        new GeometryColumns("shop", List.of("location"), List.of("id", "name", "location"));

    private static final List<String> FRAGMENTS = List.of(
        "SELECT", "SELECT", "*", "location", "s.location", "`location`", "\"location\"", "id", "name", ",", ",",
        "FROM", "FROM", "shop", "s", "AS", "loc", "(", ")", "'a,b'", "'it''s'", "WHERE", "x", "=", "?", "JOIN",
        "city", "c", "ON", "EXISTS", "-- c\n", "/* c */", "/*+ h */", "\n", "DISTINCT", "UNION", "WITH", ";",
        "COUNT(*)", "$$", "$$x$$", ".", "#", "s.*", "LIMIT", "E'\\''", "ORDER BY", "location AS l", "1");

    private final GeometrySqlRewriter mysql = new GeometrySqlRewriter(new MySQLGeometryStrategy());
    private final GeometrySqlRewriter postgis = new GeometrySqlRewriter(TestStrategies.postgisEwkb());

    @Provide
    Arbitrary<String> sqlSoup() {
        Arbitrary<String> fragment = Arbitraries.of(FRAGMENTS);
        Arbitrary<String> separator = Arbitraries.of(" ", " ", "", "\n");
        Arbitrary<String> piece = Combinators.combine(fragment, separator).as((f, s) -> f + s);
        Arbitrary<String> body = piece.list().ofMaxSize(24).map(parts -> String.join("", parts));
        return Arbitraries.oneOf(body, body.map(b -> "SELECT " + b));
    }

    @Property(tries = 3000)
    void neverThrowsIsIdempotentAndKeepsTheTextWellFormed(@ForAll("sqlSoup") String sql) {
        for (GeometrySqlRewriter rewriter : List.of(mysql, postgis)) {
            SqlDialect dialect = SqlDialect.of(rewriter.currentStrategy().getSupportedDatabaseType());
            String once = rewriter.rewrite(sql, SHOP, false);
            String legacy = rewriter.rewrite(sql, Set.of("location"), List.of("id", "name", "location"));
            assertThat(rewriter.rewrite(once, SHOP, false)).isEqualTo(once);
            if (SqlLexer.tokenize(sql, dialect) != null) {
                assertThat(SqlLexer.tokenize(once, dialect)).isNotNull();
                assertThat(SqlLexer.tokenize(legacy, dialect)).isNotNull();
            }
            if (!once.equals(sql)) {
                assertThat(SelectStatement.parse(sql, dialect)).isNotNull();
            }
        }
    }

    @Property(tries = 1000)
    void statementsWithoutGeometryOrStarAreUntouched(@ForAll("sqlSoup") String sql) {
        String withoutGeometry = sql.replace("location", "label").replace("*", "1");
        assertThat(mysql.rewrite(withoutGeometry, SHOP, false)).isSameAs(withoutGeometry);
        assertThat(postgis.rewrite(withoutGeometry, SHOP, false)).isSameAs(withoutGeometry);
    }
}
