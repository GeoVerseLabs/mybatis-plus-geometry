package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.geoverselabs.mybatis.geometry.exception.GeoJsonParseException;
import io.github.geoverselabs.mybatis.geometry.exception.InvalidCoordinateException;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Jackson deserializer for GeoJSON MultiLineString to JTS MultiLineString.
 *
 * <p>Expected input format:</p>
 * <pre>{@code
 * {
 *   "type": "MultiLineString",
 *   "coordinates": [
 *     [[lon1, lat1], [lon2, lat2], ...],
 *     [[lon3, lat3], [lon4, lat4], ...]
 *   ]
 * }
 * }</pre>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = MultiLineStringSerializer.class)
 * @JsonDeserialize(using = MultiLineStringDeserializer.class)
 * private MultiLineString routes;
 * }</pre>
 */
public class MultiLineStringDeserializer extends JsonDeserializer<MultiLineString> {

    private final boolean coordinateValidationEnabled;

    /**
     * Default constructor with coordinate validation enabled (WGS84 range).
     */
    public MultiLineStringDeserializer() {
        this(true);
    }

    /**
     * Constructor with configurable coordinate validation.
     *
     * @param coordinateValidationEnabled when true, validates WGS84 range;
     *                                    when false, only validates Double.isFinite()
     */
    public MultiLineStringDeserializer(boolean coordinateValidationEnabled) {
        this.coordinateValidationEnabled = coordinateValidationEnabled;
    }

    @Override
    public MultiLineString deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);

        if (node == null || node.isNull()) {
            return null;
        }

        // Validate GeoJSON type
        JsonNode typeNode = node.get("type");
        if (typeNode == null) {
            throw new GeoJsonParseException("Missing 'type' field", "type");
        }

        String type = typeNode.asText();
        if (!"MultiLineString".equals(type)) {
            throw GeoJsonParseException.forTypeMismatch("MultiLineString", type);
        }

        // Validate coordinates
        JsonNode coordinatesNode = node.get("coordinates");
        if (coordinatesNode == null || !coordinatesNode.isArray()) {
            throw new GeoJsonParseException("Missing or invalid 'coordinates' field", "coordinates");
        }

        // Parse each line string from the coordinates array
        LineString[] lineStrings = new LineString[coordinatesNode.size()];

        for (int i = 0; i < coordinatesNode.size(); i++) {
            JsonNode lineNode = coordinatesNode.get(i);
            if (!lineNode.isArray()) {
                throw new GeoJsonParseException(
                    "Invalid coordinate array at index " + i, "coordinates");
            }

            List<Coordinate> coordinates = new ArrayList<>();
            for (int j = 0; j < lineNode.size(); j++) {
                JsonNode coordNode = lineNode.get(j);
                if (!coordNode.isArray() || coordNode.size() < 2) {
                    throw new GeoJsonParseException(
                        "Invalid coordinate pair at line " + i + ", index " + j, "coordinates");
                }

                double longitude = coordNode.get(0).asDouble();
                double latitude = coordNode.get(1).asDouble();

                validateCoordinate(longitude, latitude);

                coordinates.add(new Coordinate(longitude, latitude));
            }

            if (coordinates.size() < 2) {
                throw new GeoJsonParseException(
                    "LineString at index " + i + " must have at least 2 points", "coordinates");
            }

            lineStrings[i] = GeometryFactoryProvider.getFactory()
                .createLineString(coordinates.toArray(new Coordinate[0]));
        }

        return GeometryFactoryProvider.getFactory().createMultiLineString(lineStrings);
    }

    private void validateCoordinate(double longitude, double latitude) throws IOException {
        if (coordinateValidationEnabled) {
            // WGS84 range validation
            if (longitude < -180 || longitude > 180) {
                throw InvalidCoordinateException.forLongitude(longitude);
            }
            if (latitude < -90 || latitude > 90) {
                throw InvalidCoordinateException.forLatitude(latitude);
            }
        } else {
            // Only validate that coordinates are finite (not NaN or Infinity)
            if (!Double.isFinite(longitude)) {
                throw new InvalidCoordinateException("longitude", longitude,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
            if (!Double.isFinite(latitude)) {
                throw new InvalidCoordinateException("latitude", latitude,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
        }
    }
}
