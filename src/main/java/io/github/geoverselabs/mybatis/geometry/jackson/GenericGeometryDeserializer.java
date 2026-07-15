package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.geoverselabs.mybatis.geometry.exception.GeoJsonParseException;
import org.locationtech.jts.geom.Geometry;

import java.io.IOException;

/**
 * Jackson deserializer for any GeoJSON geometry type to the corresponding JTS Geometry subtype.
 *
 * <p>This deserializer inspects the GeoJSON {@code "type"} field and dispatches to the
 * appropriate type-specific deserializer. Supported types: Point, LineString, Polygon,
 * MultiPoint, MultiLineString, MultiPolygon, and GeometryCollection.</p>
 *
 * <p>Expected input format (example for Point):</p>
 * <pre>{@code
 * {
 *   "type": "Point",
 *   "coordinates": [100.0, 0.0]
 * }
 * }</pre>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = GenericGeometrySerializer.class)
 * @JsonDeserialize(using = GenericGeometryDeserializer.class)
 * private Geometry geometry;
 * }</pre>
 */
public class GenericGeometryDeserializer extends JsonDeserializer<Geometry> {

    private final PointDeserializer pointDeserializer;
    private final LineStringDeserializer lineStringDeserializer;
    private final PolygonDeserializer polygonDeserializer;
    private final MultiPointDeserializer multiPointDeserializer;
    private final MultiLineStringDeserializer multiLineStringDeserializer;
    private final MultiPolygonDeserializer multiPolygonDeserializer;
    private final GeometryCollectionDeserializer geometryCollectionDeserializer;

    /**
     * Default constructor with coordinate validation enabled (WGS84 range).
     */
    public GenericGeometryDeserializer() {
        this(true);
    }

    /**
     * Constructor with configurable coordinate validation.
     *
     * @param coordinateValidationEnabled when true, validates WGS84 range;
     *                                    when false, only validates Double.isFinite()
     */
    public GenericGeometryDeserializer(boolean coordinateValidationEnabled) {
        this.pointDeserializer = new PointDeserializer(coordinateValidationEnabled);
        this.lineStringDeserializer = new LineStringDeserializer(coordinateValidationEnabled);
        this.polygonDeserializer = new PolygonDeserializer(coordinateValidationEnabled);
        this.multiPointDeserializer = new MultiPointDeserializer(coordinateValidationEnabled);
        this.multiLineStringDeserializer = new MultiLineStringDeserializer(coordinateValidationEnabled);
        this.multiPolygonDeserializer = new MultiPolygonDeserializer(coordinateValidationEnabled);
        this.geometryCollectionDeserializer = new GeometryCollectionDeserializer(coordinateValidationEnabled);
    }

    @Override
    public Geometry deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);

        if (node == null || node.isNull()) {
            return null;
        }

        JsonNode typeNode = node.get("type");
        if (typeNode == null) {
            throw new GeoJsonParseException("Missing 'type' field", "type");
        }

        String type = typeNode.asText();

        // Re-create parser from node for delegation to type-specific deserializer
        JsonParser nodeParser = node.traverse(parser.getCodec());
        nodeParser.nextToken();

        return switch (type) {
            case "Point" -> pointDeserializer.deserialize(nodeParser, ctx);
            case "LineString" -> lineStringDeserializer.deserialize(nodeParser, ctx);
            case "Polygon" -> polygonDeserializer.deserialize(nodeParser, ctx);
            case "MultiPoint" -> multiPointDeserializer.deserialize(nodeParser, ctx);
            case "MultiLineString" -> multiLineStringDeserializer.deserialize(nodeParser, ctx);
            case "MultiPolygon" -> multiPolygonDeserializer.deserialize(nodeParser, ctx);
            case "GeometryCollection" -> geometryCollectionDeserializer.deserialize(nodeParser, ctx);
            default -> throw new GeoJsonParseException("Unsupported GeoJSON type: " + type, "type");
        };
    }
}
