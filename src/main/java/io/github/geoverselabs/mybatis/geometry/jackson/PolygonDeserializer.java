package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;

/**
 * Jackson deserializer for GeoJSON Polygon to JTS Polygon.
 * Validates ring closure and corrects ring orientation (exterior counter-clockwise, holes
 * clockwise, per RFC 7946). With {@code GeometryValidation.FULL} the polygon must also be valid
 * according to OGC rules.
 *
 * <p>Expected input format ({@code "coordinates": []} gives an empty polygon):</p>
 * <pre>{@code
 * {
 *   "type": "Polygon",
 *   "coordinates": [
 *     [[lon1, lat1], [lon2, lat2], ..., [lon1, lat1]],  // exterior ring (CCW)
 *     [[lon1, lat1], ...]  // interior rings (CW)
 *   ]
 * }
 * }</pre>
 *
 * <p>Instances created with the no-arg constructor (for example through
 * {@code @JsonDeserialize(using = PolygonDeserializer.class)}) use {@link GeoJsonOptions#getGlobal()}
 * at call time. See {@link GeometryJacksonModule} for the parsing and validation rules.</p>
 */
public class PolygonDeserializer extends GeoJsonGeometryDeserializer<Polygon> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a deserializer using the global {@link GeoJsonOptions} at call time.
     */
    public PolygonDeserializer() {
        super(Polygon.class, null, null);
    }

    /**
     * Create a deserializer using the global {@link GeoJsonOptions} at call time, with the
     * coordinate range validation overridden.
     *
     * @param coordinateValidationEnabled when true, validates WGS84 range;
     *                                    when false, only validates Double.isFinite()
     */
    public PolygonDeserializer(boolean coordinateValidationEnabled) {
        super(Polygon.class, null, coordinateValidationEnabled);
    }

    /**
     * Create a deserializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public PolygonDeserializer(GeoJsonOptions options) {
        super(Polygon.class, options, null);
    }

    /**
     * Read a GeoJSON geometry object.
     *
     * @param parser the parser, positioned at the object's start (or a field name inside it)
     * @param ctx the deserialization context (may be null)
     * @return the geometry, or null for JSON null
     * @throws IOException on invalid GeoJSON ({@code GeoJsonParseException},
     *                     {@code InvalidCoordinateException}) or parser failure
     */
    @Override
    public Polygon deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        return super.deserialize(parser, ctx);
    }
}
