package io.github.geoverselabs.mybatis.geometry.exception;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class GeoJsonExceptionsTest {

    @Test
    void parseExceptionIsAJsonMappingException() {
        GeoJsonParseException e = new GeoJsonParseException("Missing 'type' field", "type");
        assertThat(e).isInstanceOf(JsonMappingException.class)
            .isInstanceOf(JsonProcessingException.class)
            .isInstanceOf(IOException.class);
        assertThat(e.getOriginalMessage()).isEqualTo("Missing 'type' field [field=type]");
        assertThat(e.getMessage()).isEqualTo("Missing 'type' field [field=type]");
        assertThat(e.getInvalidField()).isEqualTo("type");
        assertThat(e.getExpectedType()).isNull();
        assertThat(e.getActualType()).isNull();
        assertThat(e.getLocation()).isNull();
        assertThat(e.getCause()).isNull();
    }

    @Test
    void parseExceptionWithCause() {
        IllegalStateException cause = new IllegalStateException("boom");
        GeoJsonParseException e = new GeoJsonParseException("Invalid", "coordinates", cause);
        assertThat(e.getCause()).isSameAs(cause);
        assertThat(e.getInvalidField()).isEqualTo("coordinates");
        assertThat(e.getOriginalMessage()).isEqualTo("Invalid [field=coordinates]");
    }

    @Test
    void parseExceptionWithParserRecordsLocation() throws IOException {
        try (JsonParser parser = new JsonFactory().createParser("{\n  \"type\": 7\n}")) {
            parser.nextToken();
            parser.nextToken();
            parser.nextToken();
            GeoJsonParseException e = new GeoJsonParseException(parser, "Invalid 'type' field", "type");
            assertThat(e.getLocation()).isNotNull();
            assertThat(e.getLocation().getLineNr()).isEqualTo(2);
            assertThat(e.getProcessor()).isSameAs(parser);
            assertThat(e.getMessage()).startsWith("Invalid 'type' field [field=type]").contains("line: 2");

            IOException cause = new IOException("x");
            GeoJsonParseException withCause = new GeoJsonParseException(parser, "Invalid", "type", cause);
            assertThat(withCause.getCause()).isSameAs(cause);
            assertThat(withCause.getLocation()).isNotNull();
        }
    }

    @Test
    void typeMismatchPopulatesTypes() throws IOException {
        GeoJsonParseException e = GeoJsonParseException.forTypeMismatch("Point", "LineString");
        assertThat(e.getExpectedType()).isEqualTo("Point");
        assertThat(e.getActualType()).isEqualTo("LineString");
        assertThat(e.getInvalidField()).isEqualTo("type");
        assertThat(e.getOriginalMessage())
            .isEqualTo("Invalid GeoJSON type: expected Point, got LineString [field=type]");

        try (JsonParser parser = new JsonFactory().createParser("{\"type\":\"LineString\"}")) {
            parser.nextToken();
            parser.nextToken();
            parser.nextToken();
            GeoJsonParseException located = GeoJsonParseException.forTypeMismatch(parser, "Polygon", "LineString");
            assertThat(located.getExpectedType()).isEqualTo("Polygon");
            assertThat(located.getActualType()).isEqualTo("LineString");
            assertThat(located.getLocation()).isNotNull();
        }
    }

    @Test
    void invalidCoordinateException() {
        InvalidCoordinateException e = InvalidCoordinateException.forLongitude(181);
        assertThat(e).isInstanceOf(JsonMappingException.class).isInstanceOf(IOException.class);
        assertThat(e.getCoordinateType()).isEqualTo("longitude");
        assertThat(e.getValue()).isEqualTo(181);
        assertThat(e.getMinValue()).isEqualTo(-180);
        assertThat(e.getMaxValue()).isEqualTo(180);
        assertThat(e.getOriginalMessage())
            .isEqualTo("Coordinate out of range: longitude 181.000000 not in [-180.000000, 180.000000]");

        InvalidCoordinateException latitude = InvalidCoordinateException.forLatitude(-91.5);
        assertThat(latitude.getCoordinateType()).isEqualTo("latitude");
        assertThat(latitude.getMinValue()).isEqualTo(-90);
        assertThat(latitude.getMaxValue()).isEqualTo(90);

        InvalidCoordinateException custom = new InvalidCoordinateException("altitude", Double.NaN,
            Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        assertThat(custom.getOriginalMessage())
            .isEqualTo("Coordinate out of range: altitude NaN not in [-Infinity, Infinity]");
        assertThat(custom.getLocation()).isNull();
    }

    @Test
    void invalidCoordinateExceptionWithParser() throws IOException {
        try (JsonParser parser = new JsonFactory().createParser("[200, 95]")) {
            parser.nextToken();
            parser.nextToken();
            InvalidCoordinateException longitude = InvalidCoordinateException.forLongitude(parser, 200);
            assertThat(longitude.getLocation()).isNotNull();
            assertThat(longitude.getLocation().getColumnNr()).isEqualTo(2);
            parser.nextToken();
            InvalidCoordinateException latitude = InvalidCoordinateException.forLatitude(parser, 95);
            assertThat(latitude.getLocation().getColumnNr()).isEqualTo(7);
            InvalidCoordinateException custom = new InvalidCoordinateException(parser, "altitude", 1, 2, 3);
            assertThat(custom.getProcessor()).isSameAs(parser);
        }
    }

    @Test
    void messagesDoNotDependOnTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertThat(InvalidCoordinateException.forLatitude(90.5).getOriginalMessage())
                .isEqualTo("Coordinate out of range: latitude 90.500000 not in [-90.000000, 90.000000]");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void exceptionsAreSerializable() throws Exception {
        GeoJsonParseException parse = GeoJsonParseException.forTypeMismatch("Point", "Polygon");
        InvalidCoordinateException coordinate = InvalidCoordinateException.forLongitude(500);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(parse);
            out.writeObject(coordinate);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            GeoJsonParseException parseBack = (GeoJsonParseException) in.readObject();
            InvalidCoordinateException coordinateBack = (InvalidCoordinateException) in.readObject();
            assertThat(parseBack.getActualType()).isEqualTo("Polygon");
            assertThat(coordinateBack.getValue()).isEqualTo(500);
        }
    }
}
