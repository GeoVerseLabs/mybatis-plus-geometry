package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.MultiPoint;

import java.io.IOException;

/**
 * Jackson serializer for JTS MultiPoint to GeoJSON format.
 *
 * <p>Empty member points are skipped; an empty MultiPoint is written with
 * {@code "coordinates": []}.</p>
 *
 * <p>Output format:</p>
 * <pre>{@code
 * {
 *   "type": "MultiPoint",
 *   "coordinates": [[longitude, latitude], [longitude, latitude], ...]
 * }
 * }</pre>
 *
 * <p>Instances created with the no-arg constructor (for example through
 * {@code @JsonSerialize(using = MultiPointSerializer.class)}) use {@link GeoJsonOptions#getGlobal()}
 * at call time; {@link #MultiPointSerializer(GeoJsonOptions)} fixes the options.
 * {@link GeoJsonPrecision} on a property overrides the coordinate precision for that property.</p>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = MultiPointSerializer.class)
 * @JsonDeserialize(using = MultiPointDeserializer.class)
 * private MultiPoint locations;
 * }</pre>
 */
public class MultiPointSerializer extends GeoJsonGeometrySerializer<MultiPoint> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a serializer using the global {@link GeoJsonOptions} at call time.
     */
    public MultiPointSerializer() {
        this(null, null);
    }

    /**
     * Create a serializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public MultiPointSerializer(GeoJsonOptions options) {
        this(options, null);
    }

    private MultiPointSerializer(GeoJsonOptions options, Integer precisionOverride) {
        super(MultiPoint.class, options, precisionOverride);
    }

    @Override
    MultiPointSerializer withSettings(GeoJsonOptions options, Integer precisionOverride) {
        return new MultiPointSerializer(options, precisionOverride);
    }

    /**
     * Write {@code multiPoint} as a GeoJSON object (JSON null for null).
     *
     * @param multiPoint the geometry
     * @param gen the generator
     * @param provider the serializer provider
     * @throws IOException on write failure or non-finite ordinates
     */
    @Override
    public void serialize(MultiPoint multiPoint, JsonGenerator gen, SerializerProvider provider) throws IOException {
        super.serialize(multiPoint, gen, provider);
    }
}
