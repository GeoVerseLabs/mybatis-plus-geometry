package io.github.geoverselabs.mybatis.geometry.strategy;

import io.github.geoverselabs.mybatis.geometry.codec.WkbCodec;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static io.github.geoverselabs.mybatis.geometry.codec.TestGeometries.wkt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Default methods keep third-party strategies and codecs (written against the old interfaces) working.
 */
class GeometryHandlerStrategyDefaultsTest {

    /**
     * A strategy implementing only the original abstract methods, storing "SRID;WKT" text.
     */
    static final class WktStrategy implements GeometryHandlerStrategy {

        final List<Geometry> converted = new ArrayList<>();

        @Override
        public DatabaseType getSupportedDatabaseType() {
            return DatabaseType.MYSQL;
        }

        @Override
        public String wrapColumnForSelect(String columnName) {
            return "ST_AsText(" + columnName + ")";
        }

        @Override
        public String getGeometryInputFunction() {
            return "ST_GeomFromText(?)";
        }

        @Override
        public Object convertForDatabase(Geometry geometry) {
            converted.add(geometry);
            return geometry.getSRID() + ";" + new WKTWriter().write(geometry);
        }

        @Override
        public Geometry parseFromDatabase(Object dbValue) {
            String[] parts = dbValue.toString().split(";", 2);
            try {
                Geometry g = new WKTReader().read(parts[1]);
                g.setSRID(Integer.parseInt(parts[0]));
                return g;
            } catch (ParseException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    @Test
    void defaultReadUsesGetStringAndParseFromDatabase() throws SQLException {
        WktStrategy strategy = new WktStrategy();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("geom")).thenReturn("3857;POINT (1 2)");
        when(rs.getString(4)).thenReturn("4326;LINESTRING (0 0, 1 1)");
        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getString(2)).thenReturn("0;POINT (5 6)");

        assertThat(strategy.read(rs, "geom").getSRID()).isEqualTo(3857);
        assertThat(strategy.read(rs, 4).getGeometryType()).isEqualTo("LineString");
        assertThat(strategy.read(cs, 2).getCoordinate().x).isEqualTo(5.0);
        assertThat(strategy.read(rs, "missing")).isNull();
        assertThat(strategy.read(rs, 1)).isNull();
        assertThat(strategy.read(cs, 1)).isNull();
    }

    @Test
    void defaultConvertWithSridCopiesInsteadOfMutating() {
        WktStrategy strategy = new WktStrategy();
        Geometry original = wkt("POINT (1 2)");

        assertThat(strategy.convertForDatabase(original, 3857)).isEqualTo("3857;POINT (1 2)");
        assertThat(original.getSRID()).isZero();
        assertThat(strategy.converted.get(0)).isNotSameAs(original);

        Geometry tagged = wkt("POINT (1 2)", 4326);
        assertThat(strategy.convertForDatabase(tagged, 4326)).isEqualTo("4326;POINT (1 2)");
        assertThat(strategy.converted.get(1)).isSameAs(tagged);

        assertThat(strategy.convertForDatabase(null, 4326)).isNull();
    }

    @Test
    void defaultCodecEncodeWithSridCopiesInsteadOfMutating() {
        List<Geometry> seen = new ArrayList<>();
        WkbCodec codec = new WkbCodec() {
            @Override
            public Object encode(Geometry geometry) {
                seen.add(geometry);
                return geometry.getSRID();
            }

            @Override
            public Geometry decode(Object dbValue) {
                return null;
            }
        };
        Geometry original = wkt("POLYGON ((0 0, 1 0, 1 1, 0 0))");

        assertThat(codec.encode(original, 2154)).isEqualTo(2154);
        assertThat(original.getSRID()).isZero();
        assertThat(seen.get(0)).isNotSameAs(original);
        assertThat(seen.get(0).equalsExact(original)).isTrue();

        assertThat(codec.encode(original, 0)).isEqualTo(0);
        assertThat(seen.get(1)).isSameAs(original);
        assertThat(codec.encode(null, 1)).isNull();
    }
}
