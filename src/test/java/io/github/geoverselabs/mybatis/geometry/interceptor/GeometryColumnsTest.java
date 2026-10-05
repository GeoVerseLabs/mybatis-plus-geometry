package io.github.geoverselabs.mybatis.geometry.interceptor;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeometryColumnsTest {

    @Test
    void keysStripQuotesAndIgnoreCase() {
        assertThat(GeometryColumns.key("Location")).isEqualTo("location");
        assertThat(GeometryColumns.key("`point`")).isEqualTo("point");
        assertThat(GeometryColumns.key("\"Geom\"")).isEqualTo("geom");
        assertThat(GeometryColumns.key("[geo]")).isEqualTo("geo");
        assertThat(GeometryColumns.key("`a``b`")).isEqualTo("a`b");
        assertThat(GeometryColumns.key("\"a\"\"b\"")).isEqualTo("a\"b");
        assertThat(GeometryColumns.key(" `x` ")).isEqualTo("x");
        assertThat(GeometryColumns.key("`")).isEqualTo("`");
        assertThat(GeometryColumns.key("`x\"")).isEqualTo("`x\"");
    }

    @Test
    void lastSegmentKeys() {
        assertThat(GeometryColumns.lastSegmentKey("shop")).isEqualTo("shop");
        assertThat(GeometryColumns.lastSegmentKey("db.shop")).isEqualTo("shop");
        assertThat(GeometryColumns.lastSegmentKey("`db`.`Shop`")).isEqualTo("shop");
        assertThat(GeometryColumns.lastSegmentKey("public.\"a.b\"")).isEqualTo("a.b");
        assertThat(GeometryColumns.lastSegmentKey("[dbo].[t.x]")).isEqualTo("t.x");
    }

    @Test
    void metadata() {
        GeometryColumns columns = new GeometryColumns(" public.\"Shop\" ",
            Arrays.asList("`Location`", " ", null, "area"), Arrays.asList("id", null, "`Location`", "area", ""));
        assertThat(columns.tableName()).isEqualTo("public.\"Shop\"");
        assertThat(columns.tableKey()).isEqualTo("shop");
        assertThat(columns.geometryNames()).containsExactly("`Location`", "area");
        assertThat(columns.isGeometryKey("location")).isTrue();
        assertThat(columns.isGeometryKey("`location`")).isFalse();
        assertThat(columns.selectColumns()).containsExactly("id", "`Location`", "area");
        assertThat(columns.isEmpty()).isFalse();
        assertThat(columns.toString()).contains("Shop", "area");

        assertThat(new GeometryColumns("", List.of(), List.of()).tableName()).isNull();
        assertThat(new GeometryColumns(null, List.of(), List.of()).selectColumns()).isNull();
        assertThat(GeometryColumns.EMPTY.isEmpty()).isTrue();
    }

    @Test
    void equality() {
        GeometryColumns a = new GeometryColumns("shop", List.of("location"), List.of("id", "location"));
        GeometryColumns b = new GeometryColumns("shop", List.of("location"), List.of("id", "location"));
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isEqualTo(a).isNotEqualTo("shop").isNotEqualTo(null);
        assertThat(a).isNotEqualTo(new GeometryColumns("city", List.of("location"), List.of("id", "location")));
        assertThat(a).isNotEqualTo(new GeometryColumns("shop", List.of("area"), List.of("id", "location")));
        assertThat(a).isNotEqualTo(new GeometryColumns("shop", List.of("location"), null));
    }

    @Test
    void prefilter() {
        GeometryColumns columns = new GeometryColumns("shop", List.of("location"), null);
        assertThat(columns.mayAffect("SELECT id FROM shop")).isFalse();
        assertThat(columns.mayAffect("SELECT id, LOCATION FROM shop")).isTrue();
        assertThat(columns.mayAffect("SELECT * FROM shop")).isTrue();
        assertThat(columns.mayAffect("")).isFalse();
        assertThat(columns.mayAffect("locatio")).isFalse();

        // quoted or non-ASCII names cannot be found by a plain substring search: never skip
        assertThat(new GeometryColumns("t", List.of("`a``b`"), null).mayAffect("SELECT x FROM t")).isTrue();
        assertThat(new GeometryColumns("t", List.of("géo"), null).mayAffect("SELECT x FROM t")).isTrue();
    }
}
