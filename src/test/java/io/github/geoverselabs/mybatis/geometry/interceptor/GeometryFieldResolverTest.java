package io.github.geoverselabs.mybatis.geometry.interceptor;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.toolkit.AnnotationUtils;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.Camel;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.GeoAll;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.Site;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.Spot;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.UnregisteredChild;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.UnregisteredGeo;
import io.github.geoverselabs.mybatis.geometry.interceptor.TestEntities.UnregisteredNoTable;
import io.github.geoverselabs.mybatis.geometry.handler.PointTypeHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeometryFieldResolverTest {

    @BeforeAll
    static void registerEntities() {
        TestConfigurations.standard();
        TestConfigurations.quotedColumns();
        TestConfigurations.noCamelCase();
    }

    private final GeometryFieldResolver resolver = new GeometryFieldResolver();

    private static Field field(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    // ------------------------------------------------------------------ MyBatis-Plus metadata

    @Test
    void everyGeometryAnnotationIsRecognised() {
        assertThat(resolver.getGeometryFields(GeoAll.class)).containsExactly(
            "pt", "ls", "pg", "mpt", "mls", "mpg", "gc", "geom", "composed", "deep", "direct", "geo_loc");
    }

    @Test
    void allColumnsComeFromTableInfo() {
        assertThat(resolver.getAllFields(GeoAll.class)).containsExactly(
            "id", "name", "pt", "ls", "pg", "mpt", "mls", "mpg", "gc", "geom", "composed", "deep", "direct",
            "geo_loc", "text");
        GeometryColumns columns = resolver.resolve(GeoAll.class);
        assertThat(columns.tableName()).isEqualTo("geo_all");
        assertThat(columns.selectColumns()).isEqualTo(resolver.getAllFields(GeoAll.class));
    }

    @Test
    void columnNamesFollowMyBatisPlus() {
        // camelToUnderline puts '_' before every capital, @TableId(value) names the key column,
        // excludeProperty drops a field, and MyBatis-Plus picks the first-declared @TableField
        assertThat(resolver.getGeometryFields(Site.class))
            .containsExactly("geo_j_s_o_n_point", "location2_d", "`point`", "annotated_first");
        assertThat(resolver.getAllFields(Site.class)).containsExactly(
            "pk_id", "geo_j_s_o_n_point", "location2_d", "`point`", "annotated_first", "label");
    }

    @Test
    void globalColumnFormatIsHonoured() {
        // exactly as MyBatis-Plus applies it: only to fields without @TableField or with keepGlobalFormat
        assertThat(resolver.getGeometryFields(Spot.class)).containsExactly("`point`", "shape");
        GeometryColumns columns = resolver.resolve(Spot.class);
        assertThat(columns.isGeometryKey("point")).isTrue();
        assertThat(columns.isGeometryKey("shape")).isTrue();
        assertThat(columns.selectColumns()).contains("`point`", "shape", "`label`");
    }

    @Test
    void mapUnderscoreToCamelCaseFalseKeepsPropertyNames() {
        assertThat(resolver.getGeometryFields(Camel.class)).containsExactly("homeLocation");
        assertThat(resolver.resolve(Camel.class).isGeometryKey("homelocation")).isTrue();
    }

    // ------------------------------------------------------------------ reflection fallback

    @Test
    void reflectionFallbackForUnregisteredEntities() {
        assertThat(resolver.getGeometryFields(UnregisteredGeo.class))
            .containsExactly("geo_j_s_o_n_point", "composed", "geo_loc");
        assertThat(resolver.getAllFields(UnregisteredGeo.class))
            .containsExactly("pk", "place_name", "geo_j_s_o_n_point", "composed", "geo_loc");
        GeometryColumns columns = resolver.resolve(UnregisteredGeo.class);
        assertThat(columns.tableName()).isEqualTo("plain_geo");
        assertThat(columns.selectColumns()).as("unknown column list: no SELECT * expansion").isNull();
    }

    @Test
    void reflectionFallbackIncludesInheritedFieldsAndClassAnnotations() {
        assertThat(resolver.getGeometryFields(UnregisteredChild.class))
            .containsExactlyInAnyOrder("extra", "geo_j_s_o_n_point", "composed", "geo_loc");
        assertThat(resolver.resolve(UnregisteredChild.class).tableName()).isEqualTo("plain_geo");
        assertThat(resolver.getAllFields(UnregisteredChild.class)).doesNotContain("skipped", "not_persisted");
    }

    @Test
    void reflectionFallbackWithoutTableName() {
        GeometryColumns columns = resolver.resolve(UnregisteredNoTable.class);
        assertThat(columns.geometryNames()).containsExactly("shape");
        assertThat(columns.tableName()).isNull();
        assertThat(columns.tableKey()).isNull();
    }

    @Test
    void classesWithoutGeometry() {
        assertThat(resolver.getGeometryFields(String.class)).isEmpty();
        assertThat(resolver.resolve(Object.class).isEmpty()).isTrue();
        assertThat(resolver.getGeometryFields(null)).isEmpty();
        assertThat(resolver.getAllFields(null)).isEmpty();
        assertThat(resolver.resolve(null)).isSameAs(GeometryColumns.EMPTY);
    }

    // ------------------------------------------------------------------ field rules

    @ParameterizedTest
    @ValueSource(strings = {"pt", "ls", "pg", "mpt", "mls", "mpg", "gc", "geom", "composed", "deep", "direct",
        "renamed", "notPersisted", "transientPoint", "staticPoint"})
    void geometryFields(String name) {
        // isGeometryField looks at the annotations only; exist=false, static and transient are
        // excluded by the scans that call it
        assertThat(GeometryFieldResolver.isGeometryField(field(GeoAll.class, name))).as(name).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "name", "text"})
    void nonGeometryFields(String name) {
        assertThat(GeometryFieldResolver.isGeometryField(field(GeoAll.class, name))).as(name).isFalse();
    }

    @Test
    void annotationsAreFoundInMyBatisPlusOrder() {
        List<TableField> point = GeometryFieldResolver.findAnnotations(
            field(Site.class, "point").getDeclaredAnnotations(), TableField.class);
        assertThat(point).hasSize(2);
        assertThat(point.get(0).value()).isEqualTo("`point`");
        assertThat(point.get(1).typeHandler()).isEqualTo(PointTypeHandler.class);

        List<TableField> annotatedFirst = GeometryFieldResolver.findAnnotations(
            field(Site.class, "annotatedFirst").getDeclaredAnnotations(), TableField.class);
        assertThat(annotatedFirst).hasSize(2);
        assertThat(annotatedFirst.get(0).typeHandler()).isEqualTo(PointTypeHandler.class);
        assertThat(annotatedFirst.get(1).value()).isEqualTo("geo_first");
    }

    @Test
    void firstAnnotationMatchesMyBatisPlus() {
        for (Class<?> type : List.of(GeoAll.class, Site.class, UnregisteredGeo.class, UnregisteredChild.class)) {
            for (Field field : type.getDeclaredFields()) {
                TableField expected = AnnotationUtils.findFirstAnnotation(TableField.class, field);
                List<TableField> found = GeometryFieldResolver.findAnnotations(field.getDeclaredAnnotations(),
                    TableField.class);
                if (expected == null) {
                    assertThat(found).as(field.toString()).isEmpty();
                } else {
                    assertThat(found).as(field.toString()).isNotEmpty();
                    assertThat(found.get(0)).as(field.toString()).isEqualTo(expected);
                }
            }
        }
    }

    // ------------------------------------------------------------------ caching

    @Test
    void resultsAreCachedUntilCleared() {
        GeometryColumns first = resolver.resolve(GeoAll.class);
        assertThat(resolver.resolve(GeoAll.class)).isSameAs(first);
        List<String> columns = resolver.getAllFields(GeoAll.class);
        assertThat(resolver.getAllFields(GeoAll.class)).isSameAs(columns);

        resolver.clearCache();
        GeometryColumns second = resolver.resolve(GeoAll.class);
        assertThat(second).isNotSameAs(first).isEqualTo(first).hasSameHashCodeAs(first);
        assertThat(resolver.getAllFields(GeoAll.class)).isNotSameAs(columns).isEqualTo(columns);
    }

    @Test
    void returnedCollectionsAreUnmodifiable() {
        assertThatThrownBy(() -> resolver.getGeometryFields(GeoAll.class).add("x"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> resolver.getAllFields(GeoAll.class).add("x"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> resolver.getAllFields(UnregisteredGeo.class).add("x"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
