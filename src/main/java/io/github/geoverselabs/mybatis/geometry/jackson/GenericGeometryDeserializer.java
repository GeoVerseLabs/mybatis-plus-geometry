package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import org.locationtech.jts.geom.Geometry;

import java.io.IOException;

/**
 * Jackson deserializer for any GeoJSON geometry type to the corresponding JTS Geometry subtype.
 *
 * <p>The GeoJSON {@code "type"} member selects the geometry: Point, LineString, Polygon,
 * MultiPoint, MultiLineString, MultiPolygon or GeometryCollection. The input is read in a single
 * streaming pass; {@code "type"} may appear after {@code "coordinates"}.</p>
 *
 * <p>Expected input format (example for Point):</p>
 * <pre>{@code
 * {
 *   "type": "Point",
 *   "coordinates": [100.0, 0.0]
 * }
 * }</pre>
 *
 * <p>Instances created with the no-arg constructor (for example through
 * {@code @JsonDeserialize(using = GenericGeometryDeserializer.class)}) use
 * {@link GeoJsonOptions#getGlobal()} at call time. See {@link GeometryJacksonModule} for the
 * parsing and validation rules.</p>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = GenericGeometrySerializer.class)
 * @JsonDeserialize(using = GenericGeometryDeserializer.class)
 * private Geometry geometry;
 * }</pre>
 */
public class GenericGeometryDeserializer extends GeoJsonGeometryDeserializer<Geometry> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a deserializer using the global {@link GeoJsonOptions} at call time.
     */
    public GenericGeometryDeserializer() {
        super(Geometry.class, null, null);
    }

    /**
     * Create a deserializer using the global {@link GeoJsonOptions} at call time, with the
     * coordinate range validation overridden.
     *
     * @param coordinateValidationEnabled when true, validates WGS84 range;
     *                                    when false, only validates Double.isFinite()
     */
    public GenericGeometryDeserializer(boolean coordinateValidationEnabled) {
        super(Geometry.class, null, coordinateValidationEnabled);
    }

    /**
     * Create a deserializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public GenericGeometryDeserializer(GeoJsonOptions options) {
        super(Geometry.class, options, null);
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
    public Geometry deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        return super.deserialize(parser, ctx);
    }
}
