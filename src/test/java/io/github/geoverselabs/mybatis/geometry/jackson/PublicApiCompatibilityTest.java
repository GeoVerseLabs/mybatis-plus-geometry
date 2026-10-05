package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the public API kept from earlier versions: constructors and the method descriptors that
 * code compiled against those versions links to.
 */
class PublicApiCompatibilityTest {

    static Stream<Arguments> pairs() {
        return Stream.of(
            Arguments.of(PointSerializer.class, PointDeserializer.class, Point.class),
            Arguments.of(LineStringSerializer.class, LineStringDeserializer.class, LineString.class),
            Arguments.of(PolygonSerializer.class, PolygonDeserializer.class, Polygon.class),
            Arguments.of(MultiPointSerializer.class, MultiPointDeserializer.class, MultiPoint.class),
            Arguments.of(MultiLineStringSerializer.class, MultiLineStringDeserializer.class, MultiLineString.class),
            Arguments.of(MultiPolygonSerializer.class, MultiPolygonDeserializer.class, MultiPolygon.class),
            Arguments.of(GeometryCollectionSerializer.class, GeometryCollectionDeserializer.class,
                GeometryCollection.class),
            Arguments.of(GenericGeometrySerializer.class, GenericGeometryDeserializer.class, Geometry.class));
    }

    @ParameterizedTest
    @MethodSource("pairs")
    void serializerApi(Class<?> serializer, Class<?> deserializer, Class<?> jtsType) throws Exception {
        assertThat(Modifier.isPublic(serializer.getModifiers())).isTrue();
        assertThat(JsonSerializer.class).isAssignableFrom(serializer);
        assertThat(ContextualSerializer.class).isAssignableFrom(serializer);
        assertThat(serializer.getConstructor()).isNotNull();
        assertThat(serializer.getConstructor(GeoJsonOptions.class)).isNotNull();
        Method serialize = serializer.getDeclaredMethod("serialize", jtsType, JsonGenerator.class,
            SerializerProvider.class);
        assertThat(Modifier.isPublic(serialize.getModifiers())).isTrue();
        assertThat(serialize.getReturnType()).isEqualTo(void.class);
        assertThat(((JsonSerializer<?>) serializer.getConstructor().newInstance()).handledType()).isEqualTo(jtsType);
    }

    @ParameterizedTest
    @MethodSource("pairs")
    void deserializerApi(Class<?> serializer, Class<?> deserializer, Class<?> jtsType) throws Exception {
        assertThat(Modifier.isPublic(deserializer.getModifiers())).isTrue();
        assertThat(JsonDeserializer.class).isAssignableFrom(deserializer);
        assertThat(deserializer.getConstructor()).isNotNull();
        assertThat(deserializer.getConstructor(boolean.class)).isNotNull();
        assertThat(deserializer.getConstructor(GeoJsonOptions.class)).isNotNull();
        Method deserialize = deserializer.getDeclaredMethod("deserialize", JsonParser.class,
            DeserializationContext.class);
        assertThat(Modifier.isPublic(deserialize.getModifiers())).isTrue();
        assertThat(deserialize.getReturnType()).isEqualTo(jtsType);
    }

    @Test
    void moduleConstructors() throws Exception {
        assertThat(GeometryJacksonModule.class.getConstructor()).isNotNull();
        assertThat(GeometryJacksonModule.class.getConstructor(boolean.class)).isNotNull();
        assertThat(GeometryJacksonModule.class.getConstructor(GeoJsonOptions.class)).isNotNull();
        assertThat(new GeometryJacksonModule().getModuleName()).isEqualTo("GeometryJacksonModule");
        assertThat(new GeometryJacksonModule((GeoJsonOptions) null).getModuleName())
            .isEqualTo("GeometryJacksonModule");
    }

    @Test
    void precisionAnnotation() {
        assertThat(GeoJsonPrecision.FULL).isEqualTo(-1);
        assertThat(GeoJsonPrecision.class.getAnnotation(java.lang.annotation.Retention.class).value())
            .isEqualTo(java.lang.annotation.RetentionPolicy.RUNTIME);
        assertThat(GeoJsonPrecision.class.getAnnotation(java.lang.annotation.Target.class).value())
            .containsExactlyInAnyOrder(java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD,
                java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.ANNOTATION_TYPE);
    }
}
