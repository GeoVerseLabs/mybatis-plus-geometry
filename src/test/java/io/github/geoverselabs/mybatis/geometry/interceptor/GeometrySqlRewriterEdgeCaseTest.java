package io.github.geoverselabs.mybatis.geometry.interceptor;

import io.github.geoverselabs.mybatis.geometry.strategy.MySQLGeometryStrategy;
import io.github.geoverselabs.mybatis.geometry.strategy.PostGISGeometryStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Statements that must be left unchanged because wrapping a column would change their meaning.
 */
class GeometrySqlRewriterEdgeCaseTest {

    private static final GeometryColumns SHOP =
        new GeometryColumns("shop", List.of("location"), List.of("id", "name", "location"));

    private static String mysql(String sql) {
        return new GeometrySqlRewriter(new MySQLGeometryStrategy()).rewrite(sql, SHOP, false);
    }

    private static String postgis(String sql) {
        return new GeometrySqlRewriter(new PostGISGeometryStrategy()).rewrite(sql, SHOP, false);
    }

    @Test
    void distinctWithOrderByIsLeftAlone() {
        String sql = "SELECT DISTINCT s.location FROM shop s ORDER BY s.location";
        assertThat(mysql(sql)).isEqualTo(sql);
        assertThat(postgis(sql)).isEqualTo(sql);
        assertThat(mysql("SELECT DISTINCT * FROM shop ORDER BY location")).isEqualTo("SELECT DISTINCT * FROM shop ORDER BY location");
    }

    @Test
    void distinctWithoutOrderByIsStillWrapped() {
        assertThat(mysql("SELECT DISTINCT location FROM shop")).isEqualTo("SELECT DISTINCT HEX(location) AS location FROM shop");
        assertThat(mysql("SELECT location FROM shop ORDER BY id")).isEqualTo("SELECT HEX(location) AS location FROM shop ORDER BY id");
    }

    @Test
    void postfixNullTestsAreNotAliases() {
        assertThat(postgis("SELECT id, location NOTNULL FROM shop")).isEqualTo("SELECT id, location NOTNULL FROM shop");
        assertThat(postgis("SELECT id, location ISNULL FROM shop")).isEqualTo("SELECT id, location ISNULL FROM shop");
        assertThat(postgis("SELECT id, location loc FROM shop"))
            .isEqualTo("SELECT id, encode(ST_AsEWKB(location::geometry), 'hex') AS loc FROM shop");
    }

    @Test
    void mysqlExecutableCommentsPreventRewriting() {
        String sql = "SELECT location /*!AS loc*/ FROM shop";
        assertThat(mysql(sql)).isEqualTo(sql);
        assertThat(mysql("SELECT /*+ MAX_EXECUTION_TIME(100) */ location FROM shop"))
            .isEqualTo("SELECT /*+ MAX_EXECUTION_TIME(100) */ HEX(location) AS location FROM shop");
    }

    @Test
    void cacheIsBoundedByTotalCharacters() {
        GeometrySqlRewriter rewriter = new GeometrySqlRewriter(new MySQLGeometryStrategy());
        for (int n = 1000; n < 1000 + 3000; n++) {
            String sql = "SELECT id,name,location FROM shop WHERE id IN (" + "?,".repeat(n) + "?)";
            if (sql.length() <= GeometrySqlRewriter.MAX_CACHED_SQL_LENGTH) {
                rewriter.rewrite(sql, SHOP, false);
                assertThat(rewriter.cachedChars()).isLessThanOrEqualTo(GeometrySqlRewriter.CACHE_CHAR_BUDGET);
            }
        }
        assertThat(rewriter.cacheSize()).isPositive();
    }
}
