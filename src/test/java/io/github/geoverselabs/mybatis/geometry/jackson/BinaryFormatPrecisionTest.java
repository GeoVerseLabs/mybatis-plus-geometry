package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binary Jackson formats cannot take formatted number text ({@code writeNumber(char[], ...)} becomes a
 * text string in CBOR), so rounded coordinates must be written as numbers there.
 */
class BinaryFormatPrecisionTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    static Stream<Arguments> mappers() {
        return Stream.of(Arguments.of("CBOR", new CBORMapper()), Arguments.of("Smile", new SmileMapper()),
            Arguments.of("JSON", new ObjectMapper()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mappers")
    void roundedCoordinatesAreNumbersAndRoundTrip(String name, ObjectMapper mapper) throws Exception {
        mapper.registerModule(new GeometryJacksonModule(GeoJsonOptions.DEFAULTS.withCoordinatePrecision(6)));
        Point point = FACTORY.createPoint(new Coordinate(116.4074123456, -39.9042987654));

        byte[] bytes = mapper.writeValueAsBytes(point);

        JsonNode coordinates = mapper.readTree(bytes).get("coordinates");
        assertThat(coordinates.get(0).isNumber()).as("x is a number").isTrue();
        assertThat(coordinates.get(0).doubleValue()).isEqualTo(116.407412);
        assertThat(coordinates.get(1).doubleValue()).isEqualTo(-39.904299);
        Point back = mapper.readValue(bytes, Point.class);
        assertThat(back.getX()).isEqualTo(116.407412);
        assertThat(back.getY()).isEqualTo(-39.904299);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mappers")
    void largeAndZeroValuesAreRoundedConsistently(String name, ObjectMapper mapper) throws Exception {
        mapper.registerModule(new GeometryJacksonModule(
            GeoJsonOptions.DEFAULTS.withCoordinatePrecision(3).withCoordinateRangeValidation(false)));
        LineString line = FACTORY.createLineString(new Coordinate[] {
            new Coordinate(-0.0001, 1e13 + 0.4567), new Coordinate(0.0005, -2.0004)});

        LineString back = mapper.readValue(mapper.writeValueAsBytes(line), LineString.class);

        assertThat(back.getCoordinateN(0).x).isEqualTo(0.0);
        assertThat(Double.doubleToRawLongBits(back.getCoordinateN(0).x)).isZero();
        assertThat(back.getCoordinateN(0).y).isEqualTo(1e13 + 0.457);
        assertThat(back.getCoordinateN(1).x).isEqualTo(0.001);
        assertThat(back.getCoordinateN(1).y).isEqualTo(-2.0);
    }

    @org.junit.jupiter.api.Test
    void textualJsonKeepsFixedPointDigitsForBytesAndStrings() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new GeometryJacksonModule(
            GeoJsonOptions.DEFAULTS.withCoordinatePrecision(3).withCoordinateRangeValidation(false)));
        LineString line = FACTORY.createLineString(new Coordinate[] {
            new Coordinate(-0.0001, 1e13 + 0.4567), new Coordinate(121.0, -2.0004)});
        String expected = "{\"type\":\"LineString\",\"coordinates\":[[0,10000000000000.457],[121,-2]]}";

        // writeValueAsBytes uses UTF8JsonGenerator, writeValueAsString WriterBasedJsonGenerator
        assertThat(new String(mapper.writeValueAsBytes(line), java.nio.charset.StandardCharsets.UTF_8))
            .isEqualTo(expected);
        assertThat(mapper.writeValueAsString(line)).isEqualTo(expected);
    }
}
