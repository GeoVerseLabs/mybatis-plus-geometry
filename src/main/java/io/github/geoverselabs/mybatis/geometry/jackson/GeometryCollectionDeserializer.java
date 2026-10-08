package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import org.locationtech.jts.geom.GeometryCollection;

import java.io.IOException;

/**
 * Jackson deserializer for GeoJSON GeometryCollection to JTS GeometryCollection.
 *
 * <p>Besides {@code "GeometryCollection"} (members may themselves be collections), this
 * deserializer accepts {@code "MultiPoint"}, {@code "MultiLineString"} and {@code "MultiPolygon"}
 * objects, because the corresponding JTS classes are GeometryCollections and are serialized with
 * their own GeoJSON type.</p>
 *
 * <p>Expected input format ({@code "geometries": []} gives an empty collection):</p>
 * <pre>{@code
 * {
 *   "type": "GeometryCollection",
 *   "geometries": [
 *     { "type": "Point", "coordinates": [100.0, 0.0] },
 *     { "type": "LineString", "coordinates": [[101.0, 0.0], [102.0, 1.0]] }
 *   ]
 * }
 * }</pre>
 *
 * <p>Instances created with the no-arg constructor (for example through
 * {@code @JsonDeserialize(using = GeometryCollectionDeserializer.class)}) use
 * {@link GeoJsonOptions#getGlobal()} at call time. See {@link GeometryJacksonModule} for the
 * parsing and validation rules.</p>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = GeometryCollectionSerializer.class)
 * @JsonDeserialize(using = GeometryCollectionDeserializer.class)
 * private GeometryCollection collection;
 * }</pre>
 */
public class GeometryCollectionDeserializer extends GeoJsonGeometryDeserializer<GeometryCollection> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a deserializer using the global {@link GeoJsonOptions} at call time.
     */
    public GeometryCollectionDeserializer() {
        super(GeometryCollection.class, null, null);
    }

    /**
     * Create a deserializer using the global {@link GeoJsonOptions} at call time, with the
     * coordinate range validation overridden.
     *
     * @param coordinateValidationEnabled when true, validates WGS84 range;
     *                                    when false, only validates Double.isFinite()
     */
    public GeometryCollectionDeserializer(boolean coordinateValidationEnabled) {
        super(GeometryCollection.class, null, coordinateValidationEnabled);
    }

    /**
     * Create a deserializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public GeometryCollectionDeserializer(GeoJsonOptions options) {
        super(GeometryCollection.class, options, null);
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
    public GeometryCollection deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        return super.deserialize(parser, ctx);
    }
}
