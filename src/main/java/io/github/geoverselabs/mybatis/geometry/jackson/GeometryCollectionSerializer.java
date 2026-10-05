package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.GeometryCollection;

import java.io.IOException;

/**
 * Jackson serializer for JTS GeometryCollection to GeoJSON format.
 *
 * <p>Members, including nested collections, are written directly (no lookup of other
 * serializers), so this serializer works without {@link GeometryJacksonModule} being registered.
 * A MultiPoint, MultiLineString or MultiPolygon value (JTS subclasses of GeometryCollection) is
 * written with its own GeoJSON type; {@link GeometryCollectionDeserializer} accepts those types
 * back.</p>
 *
 * <p>Output format:</p>
 * <pre>{@code
 * {
 *   "type": "GeometryCollection",
 *   "geometries": [
 *     { "type": "Point", "coordinates": [x, y] },
 *     { "type": "LineString", "coordinates": [[x1, y1], [x2, y2]] }
 *   ]
 * }
 * }</pre>
 *
 * <p>Instances created with the no-arg constructor (for example through
 * {@code @JsonSerialize(using = GeometryCollectionSerializer.class)}) use
 * {@link GeoJsonOptions#getGlobal()} at call time;
 * {@link #GeometryCollectionSerializer(GeoJsonOptions)} fixes the options. {@link GeoJsonPrecision}
 * on a property overrides the coordinate precision for that property.</p>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = GeometryCollectionSerializer.class)
 * @JsonDeserialize(using = GeometryCollectionDeserializer.class)
 * private GeometryCollection geometryCollection;
 * }</pre>
 */
public class GeometryCollectionSerializer extends GeoJsonGeometrySerializer<GeometryCollection> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a serializer using the global {@link GeoJsonOptions} at call time.
     */
    public GeometryCollectionSerializer() {
        this(null, null);
    }

    /**
     * Create a serializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public GeometryCollectionSerializer(GeoJsonOptions options) {
        this(options, null);
    }

    private GeometryCollectionSerializer(GeoJsonOptions options, Integer precisionOverride) {
        super(GeometryCollection.class, options, precisionOverride);
    }

    @Override
    GeometryCollectionSerializer withSettings(GeoJsonOptions options, Integer precisionOverride) {
        return new GeometryCollectionSerializer(options, precisionOverride);
    }

    /**
     * Write {@code geometryCollection} as a GeoJSON object (JSON null for null).
     *
     * @param geometryCollection the geometry
     * @param gen the generator
     * @param provider the serializer provider
     * @throws IOException on write failure or non-finite ordinates
     */
    @Override
    public void serialize(GeometryCollection geometryCollection, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        super.serialize(geometryCollection, gen, provider);
    }
}
