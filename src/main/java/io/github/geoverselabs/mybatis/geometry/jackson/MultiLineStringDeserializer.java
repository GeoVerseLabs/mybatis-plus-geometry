package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import org.locationtech.jts.geom.MultiLineString;

import java.io.IOException;

/**
 * Jackson deserializer for GeoJSON MultiLineString to JTS MultiLineString.
 *
 * <p>Expected input format (each line needs at least 2 positions; {@code "coordinates": []}
 * gives an empty MultiLineString):</p>
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
 * <p>Instances created with the no-arg constructor (for example through
 * {@code @JsonDeserialize(using = MultiLineStringDeserializer.class)}) use
 * {@link GeoJsonOptions#getGlobal()} at call time. See {@link GeometryJacksonModule} for the
 * parsing and validation rules.</p>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = MultiLineStringSerializer.class)
 * @JsonDeserialize(using = MultiLineStringDeserializer.class)
 * private MultiLineString routes;
 * }</pre>
 */
public class MultiLineStringDeserializer extends GeoJsonGeometryDeserializer<MultiLineString> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a deserializer using the global {@link GeoJsonOptions} at call time.
     */
    public MultiLineStringDeserializer() {
        super(MultiLineString.class, null, null);
    }

    /**
     * Create a deserializer using the global {@link GeoJsonOptions} at call time, with the
     * coordinate range validation overridden.
     *
     * @param coordinateValidationEnabled when true, validates WGS84 range;
     *                                    when false, only validates Double.isFinite()
     */
    public MultiLineStringDeserializer(boolean coordinateValidationEnabled) {
        super(MultiLineString.class, null, coordinateValidationEnabled);
    }

    /**
     * Create a deserializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public MultiLineStringDeserializer(GeoJsonOptions options) {
        super(MultiLineString.class, options, null);
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
    public MultiLineString deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        return super.deserialize(parser, ctx);
    }
}
