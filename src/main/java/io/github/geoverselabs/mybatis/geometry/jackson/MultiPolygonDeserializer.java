package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import org.locationtech.jts.geom.MultiPolygon;

import java.io.IOException;

/**
 * Jackson deserializer for GeoJSON MultiPolygon to JTS MultiPolygon.
 *
 * <p>Applies the same ring rules as {@link PolygonDeserializer} to every member (closure, at least
 * 4 points, RFC 7946 orientation); with {@code GeometryValidation.FULL} the whole MultiPolygon must
 * be valid according to OGC rules (which also rejects overlapping members).</p>
 *
 * <p>Expected input format ({@code "coordinates": []} gives an empty MultiPolygon):</p>
 * <pre>{@code
 * {
 *   "type": "MultiPolygon",
 *   "coordinates": [
 *     [
 *       [[lon1, lat1], [lon2, lat2], ..., [lon1, lat1]],  // exterior ring
 *       [[lon1, lat1], ...]  // interior rings (holes)
 *     ],
 *     [
 *       [[lon1, lat1], [lon2, lat2], ..., [lon1, lat1]]   // another polygon
 *     ]
 *   ]
 * }
 * }</pre>
 *
 * <p>Instances created with the no-arg constructor (for example through
 * {@code @JsonDeserialize(using = MultiPolygonDeserializer.class)}) use
 * {@link GeoJsonOptions#getGlobal()} at call time. See {@link GeometryJacksonModule} for the
 * parsing and validation rules.</p>
 */
public class MultiPolygonDeserializer extends GeoJsonGeometryDeserializer<MultiPolygon> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a deserializer using the global {@link GeoJsonOptions} at call time.
     */
    public MultiPolygonDeserializer() {
        super(MultiPolygon.class, null, null);
    }

    /**
     * Create a deserializer using the global {@link GeoJsonOptions} at call time, with the
     * coordinate range validation overridden.
     *
     * @param coordinateValidationEnabled when true, validates WGS84 range;
     *                                    when false, only validates Double.isFinite()
     */
    public MultiPolygonDeserializer(boolean coordinateValidationEnabled) {
        super(MultiPolygon.class, null, coordinateValidationEnabled);
    }

    /**
     * Create a deserializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public MultiPolygonDeserializer(GeoJsonOptions options) {
        super(MultiPolygon.class, options, null);
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
    public MultiPolygon deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        return super.deserialize(parser, ctx);
    }
}
