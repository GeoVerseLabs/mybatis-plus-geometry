package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.geoverselabs.mybatis.geometry.exception.GeoJsonParseException;
import io.github.geoverselabs.mybatis.geometry.exception.InvalidCoordinateException;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Point;

import java.io.IOException;

/**
 * Jackson deserializer for GeoJSON MultiPoint to JTS MultiPoint.
 *
 * <p>Expected input format:</p>
 * <pre>{@code
 * {
 *   "type": "MultiPoint",
 *   "coordinates": [[lon1, lat1], [lon2, lat2], ...]
 * }
 * }</pre>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = MultiPointSerializer.class)
 * @JsonDeserialize(using = MultiPointDeserializer.class)
 * private MultiPoint locations;
 * }</pre>
 */
public class MultiPointDeserializer extends JsonDeserializer<MultiPoint> {

    private final boolean coordinateValidationEnabled;

    /**
     * Default constructor with coordinate validation enabled (WGS84 range).
     */
    public MultiPointDeserializer() {
        this(true);
    }

    /**
     * Constructor with configurable coordinate validation.
     *
     * @param coordinateValidationEnabled when true, validates WGS84 range;
     *                                    when false, only validates Double.isFinite()
     */
    public MultiPointDeserializer(boolean coordinateValidationEnabled) {
        this.coordinateValidationEnabled = coordinateValidationEnabled;
    }

    @Override
    public MultiPoint deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
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
        if (!"MultiPoint".equals(type)) {
            throw GeoJsonParseException.forTypeMismatch("MultiPoint", type);
        }

        // Validate coordinates
        JsonNode coordinatesNode = node.get("coordinates");
        if (coordinatesNode == null || !coordinatesNode.isArray()) {
            throw new GeoJsonParseException("Missing or invalid 'coordinates' field", "coordinates");
        }

        // Parse coordinates
        Point[] points = new Point[coordinatesNode.size()];

        for (int i = 0; i < coordinatesNode.size(); i++) {
            JsonNode coordNode = coordinatesNode.get(i);
            if (!coordNode.isArray() || coordNode.size() < 2) {
                throw new GeoJsonParseException("Invalid coordinate pair at index " + i, "coordinates");
            }

            double longitude = coordNode.get(0).asDouble();
            double latitude = coordNode.get(1).asDouble();

            // Validate coordinates
            validateCoordinate(longitude, latitude);

            points[i] = GeometryFactoryProvider.getFactory().createPoint(new Coordinate(longitude, latitude));
        }

        return GeometryFactoryProvider.getFactory().createMultiPoint(points);
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
                throw new InvalidCoordinateException("longitude", longitude, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
            if (!Double.isFinite(latitude)) {
                throw new InvalidCoordinateException("latitude", latitude, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
        }
    }
}
