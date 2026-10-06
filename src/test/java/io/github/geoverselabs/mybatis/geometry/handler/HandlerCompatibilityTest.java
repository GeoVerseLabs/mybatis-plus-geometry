package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.strategy.PostGISGeometryStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.WKBReader;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Compatibility of the TypeHandlers with subclasses written against 1.0.x.
 */
class HandlerCompatibilityTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    /**
     * 1.0.x handlers declared {@code validateGeometry(X)} and {@code X parseGeometry(String)}; subclasses
     * compiled against them call {@code super} with these descriptors and must keep linking.
     */
    @ParameterizedTest
    @ValueSource(strings = {"Point", "LineString", "Polygon", "MultiPoint", "MultiLineString", "MultiPolygon",
        "GeometryCollection"})
    void concreteHandlersKeepTypedDescriptors(String type) throws Exception {
        Class<?> handler = Class.forName("io.github.geoverselabs.mybatis.geometry.handler." + type + "TypeHandler");
        Class<?> geometry = Class.forName("org.locationtech.jts.geom." + type);

        Method validate = handler.getDeclaredMethod("validateGeometry", geometry);
        assertThat(Modifier.isProtected(validate.getModifiers())).isTrue();

        Method parse = null;
        for (Method m : handler.getDeclaredMethods()) {
            if (m.getName().equals("parseGeometry") && !m.isBridge() && m.getReturnType() == geometry) {
                parse = m;
            }
        }
        assertThat(parse).as(type + "TypeHandler." + type + " parseGeometry(String)").isNotNull();
        assertThat(parse.getParameterTypes()).containsExactly(String.class);
    }

    static class ForcedSridPointHandler extends PointTypeHandler {
        ForcedSridPointHandler() {
            super(4326, new PostGISGeometryStrategy());
        }

        @Override
        @SuppressWarnings("deprecation")
        protected void ensureSrid(Geometry geometry) {
            geometry.setSRID(3857);
        }
    }

    @Test
    void overriddenEnsureSridChoosesTheSridWithoutMutatingTheParameter() throws Exception {
        Point point = FACTORY.createPoint(new Coordinate(1, 2));
        PreparedStatement ps = mock(PreparedStatement.class);

        new ForcedSridPointHandler().setNonNullParameter(ps, 1, point, null);

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(ps).setObject(eq(1), value.capture(), eq(Types.OTHER));
        Geometry written = new WKBReader().read(WKBReader.hexToBytes((String) value.getValue()));
        assertThat(written.getSRID()).isEqualTo(3857);
        assertThat(point.getSRID()).isZero();
    }

    @Test
    void handlerWithoutOverrideUsesTheDefaultSrid() throws Exception {
        Point point = FACTORY.createPoint(new Coordinate(1, 2));
        PreparedStatement ps = mock(PreparedStatement.class);

        new PointTypeHandler(4326, new PostGISGeometryStrategy()).setNonNullParameter(ps, 1, point, null);

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(ps).setObject(eq(1), value.capture(), eq(Types.OTHER));
        assertThat(new WKBReader().read(WKBReader.hexToBytes((String) value.getValue())).getSRID()).isEqualTo(4326);
    }

    static class CustomFormatPointHandler extends PointTypeHandler {
        CustomFormatPointHandler() {
            super(4326, new PostGISGeometryStrategy());
        }

        @Override
        @SuppressWarnings("deprecation")
        protected Point parseGeometry(String value) {
            String[] xy = value.split(",");
            return FACTORY.createPoint(new Coordinate(Double.parseDouble(xy[0]), Double.parseDouble(xy[1])));
        }
    }

    @Test
    void overriddenParseGeometryReadsTheColumnAsStringLikeOneZero() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("location")).thenReturn("3,4");
        when(rs.getString(2)).thenReturn("5,6");

        CustomFormatPointHandler handler = new CustomFormatPointHandler();

        assertThat(handler.getNullableResult(rs, "location").getX()).isEqualTo(3);
        assertThat(handler.getNullableResult(rs, 2).getX()).isEqualTo(5);
        verify(rs, never()).getBytes("location");
        verify(rs, never()).getBytes(anyInt());
    }

    @Test
    void builtInHandlersDoNotCountAsLegacyOverrides() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getBytes("location")).thenReturn("0101000020E6100000000000000000F03F0000000000000040".getBytes());

        Point point = new PointTypeHandler(4326, new PostGISGeometryStrategy()).getNullableResult(rs, "location");

        assertThat(point.getX()).isEqualTo(1);
        assertThat(point.getSRID()).isEqualTo(4326);
        verify(rs, never()).getString("location");
    }
}
